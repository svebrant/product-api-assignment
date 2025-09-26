package com.svebrant.service

import com.svebrant.model.discount.DiscountRequest
import com.svebrant.model.ingest.IngestMode
import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestResponse
import com.svebrant.model.ingest.IngestStatus
import com.svebrant.model.product.ProductRequest
import com.svebrant.repository.IngestRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

enum class IngestEntity {
    PRODUCT,
    DISCOUNT,
}

class IngestService(
    private val ingestRepository: IngestRepository,
    private val productService: ProductService,
    private val discountService: DiscountService,
    private val productsFilePath: String,
    private val discountsFilePath: String,
) : KoinComponent {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createIngestJob(ingestRequest: IngestRequest): IngestResponse {
        val ingestionId = generateIngestionId()
        val startedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        var status = IngestStatus.PENDING
        try {
            log.info { "Received ingest request $ingestRequest" }
            coroutineScope {
                val channel = Channel<Pair<IngestEntity, String>>(capacity = Channel.UNLIMITED)
                // Producer: read files and send lines with type
                launch {
                    if (ingestRequest.mode == IngestMode.PRODUCTS || ingestRequest.mode == IngestMode.ALL) {
                        readFileLines(productsFilePath).forEach { line ->
                            channel.send(IngestEntity.PRODUCT to line)
                        }
                    }
                    if (ingestRequest.mode == IngestMode.DISCOUNTS || ingestRequest.mode == IngestMode.ALL) {
                        readFileLines(discountsFilePath).forEach { line ->
                            channel.send(IngestEntity.DISCOUNT to line)
                        }
                    }
                    channel.close()
                }
                // Workers
                var error: Throwable? = null
                repeat(ingestRequest.workers) {
                    launch(Dispatchers.IO) {
                        for ((entity, line) in channel) {
                            if (error != null) break
                            var attempt = 0
                            var success = false
                            while (attempt <= ingestRequest.retries && !success) {
                                try {
                                    when (entity) {
                                        IngestEntity.PRODUCT -> {
                                            val parsed = json.decodeFromString(ProductRequest.serializer(), line)
                                            if (!ingestRequest.dryRun) productService.create(parsed)
                                        }
                                        IngestEntity.DISCOUNT -> {
                                            val parsed = json.decodeFromString(DiscountRequest.serializer(), line)
                                            if (!ingestRequest.dryRun) discountService.create(parsed)
                                        }
                                    }
                                    success = true
                                } catch (e: Exception) {
                                    attempt++
                                    if (attempt > ingestRequest.retries) {
                                        log.error(e) { "Failed to ingest $entity after ${ingestRequest.retries} retries: $line" }
                                        if (ingestRequest.failFast) {
                                            error = e
                                            break
                                        }
                                    } else {
                                        log.warn(e) { "Retry $attempt for $entity: $line" }
                                    }
                                }
                            }
                        }
                    }
                }
                if (error != null) throw error
            }
            status = IngestStatus.STARTED
            return IngestResponse(
                startedAt,
                ingestRequest.mode,
                ingestRequest.workers,
                ingestRequest.chunkSize,
                ingestRequest.dryRun,
                ingestionId,
                status,
            )
        } catch (e: Exception) {
            log.error(e) { "Error during ingest ingest request $ingestRequest" }
            status = IngestStatus.FAILED
            throw e
        }
    }

    private fun readFileLines(file: String): Sequence<String> {
        val inputStream =
            this::class.java.getResourceAsStream(file)
                ?: return emptySequence()
        return inputStream
            .bufferedReader()
            .lineSequence()
            .toList()
            .asSequence()
    }

    private fun generateIngestionId(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "ing-${now.year}${now.monthNumber.toString().padStart(2, '0')}${now.dayOfMonth.toString().padStart(2, '0')}-" +
            "${now.hour.toString().padStart(
                2,
                '0',
            )}${now.minute.toString().padStart(2, '0')}${now.second.toString().padStart(2, '0')}-" +
            (0..999999).random().toString(16)
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
