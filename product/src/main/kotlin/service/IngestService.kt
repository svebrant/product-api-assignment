package com.svebrant.service

import com.svebrant.model.discount.DiscountRequest
import com.svebrant.model.ingest.ErrorSample
import com.svebrant.model.ingest.IngestEntity
import com.svebrant.model.ingest.IngestMode
import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestResponse
import com.svebrant.model.ingest.IngestStatus
import com.svebrant.model.ingest.IngestionStatusResponse
import com.svebrant.model.ingest.IngestionSummary
import com.svebrant.model.product.ProductRequest
import com.svebrant.repository.IngestRepository
import com.svebrant.repository.dto.IngestionDto
import com.svebrant.repository.dto.IngestionSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

class IngestService(
    private val ingestRepository: IngestRepository,
    private val productService: ProductService,
    private val discountService: DiscountService,
    private val productsFilePath: String,
    private val discountsFilePath: String,
) : KoinComponent {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createIngestJob(ingestRequest: IngestRequest): IngestResponse {
        log.info { "Received ingest request $ingestRequest" }
        val ingestionId = generateIngestionId()

        if (ingestRequest.dryRun) {
            return IngestResponse(
                startedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                mode = ingestRequest.mode,
                workers = ingestRequest.workers,
                chunkSize = ingestRequest.chunkSize,
                dryRun = true,
                ingestionId = ingestionId,
                status = IngestStatus.PENDING,
            )
        }

        // TODO if we have no ingestions running, we can start immediately, otherwise queue them
        val result =
            ingestRepository.createIngestionRecord(
                ingestionId,
                ingestRequest,
            )

        // TODO trigger this in the background somehow, we want to return the ingestionId immediately
        // if (ingestRepository.findByStatus(IngestStatus.STARTED).isEmpty()) {
        //     processIngestion(ingestionId)
        // }

        if (result != null) {
            log.info { "Created ingestion record with id $ingestionId" }
            val ingest = ingestRepository.findByIngestionId(result)

            return ingest?.toResponse()
                ?: throw IllegalStateException("Failed to retrieve created ingestion record for id $ingestionId")
        } else {
            log.error { "Failed to create ingestion record for id $ingestionId" }
            throw IllegalStateException("Failed to create ingestion record")
        }
    }

    suspend fun getIngestionStatus(ingestionId: String): IngestionStatusResponse? {
        log.info { "Retrieving ingestion status with id $ingestionId" }
        return ingestRepository.findByIngestionId(ingestionId)?.toStatusResponse()
    }

    suspend fun processIngestion(ingestionId: String) {
        val ingest =
            ingestRepository.findByIngestionId(ingestionId)
                ?: throw IllegalArgumentException("Ingestion with id $ingestionId not found")

        try {
            var status = IngestStatus.STARTED
            coroutineScope {
                val channel = Channel<Pair<IngestEntity, String>>(capacity = Channel.UNLIMITED)
                processFilesAndSendToChannel(ingest.mode, channel)

                // Workers
                var error: Throwable? = null
                repeat(ingest.workers) {
                    launch(Dispatchers.IO) {
                        for ((entity, line) in channel) {
                            if (error != null) break
                            var attempt = 0
                            var success = false
                            while (attempt <= ingest.retries && !success) {
                                try {
                                    when (entity) {
                                        IngestEntity.PRODUCT -> {
                                            val parsed = json.decodeFromString(ProductRequest.serializer(), line)
                                            productService.create(parsed)
                                        }

                                        IngestEntity.DISCOUNT -> {
                                            val parsed = json.decodeFromString(DiscountRequest.serializer(), line)
                                            discountService.create(parsed)
                                        }
                                    }
                                    success = true
                                } catch (e: Exception) {
                                    attempt++
                                    if (attempt > ingest.retries) {
                                        log.error(e) { "Failed to ingest $entity after ${ingest.retries} retries: $line" }
                                        if (ingest.failFast) {
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
        } catch (e: Exception) {
            log.error(e) { "Error during ingest ingest request $ingest" }
            // status = IngestStatus.FAILED
            throw e
        }
    }

    private fun CoroutineScope.processFilesAndSendToChannel(
        mode: IngestMode,
        channel: Channel<Pair<IngestEntity, String>>,
    ) {
        launch {
            if (mode == IngestMode.PRODUCTS || mode == IngestMode.ALL) {
                readFileLines(productsFilePath).forEach { line ->
                    channel.send(IngestEntity.PRODUCT to line)
                }
            }
            if (mode == IngestMode.DISCOUNTS || mode == IngestMode.ALL) {
                readFileLines(discountsFilePath).forEach { line ->
                    channel.send(IngestEntity.DISCOUNT to line)
                }
            }
            channel.close()
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
        return "ing-${now.year}${now.monthNumber.toString().padStart(2, '0')}${
            now.dayOfMonth.toString().padStart(2, '0')
        }-" +
            "${
                now.hour.toString().padStart(
                    2,
                    '0',
                )
            }${now.minute.toString().padStart(2, '0')}${now.second.toString().padStart(2, '0')}-" +
            (0..999999).random().toString(16)
    }

    private fun IngestionDto.toResponse(): IngestResponse =
        IngestResponse(
            startedAt = this.startedAt,
            mode = this.mode,
            workers = this.workers,
            chunkSize = this.chunkSize,
            dryRun = false,
            ingestionId = this.ingestionId,
            status = this.status,
        )

    private fun IngestionDto.toStatusResponse(): IngestionStatusResponse =
        IngestionStatusResponse(
            ingestionId = this.ingestionId,
            status = this.status,
            products = this.products?.toResponse(),
            discounts = this.discounts?.toResponse(),
            errorsSample = this.errors.map { ErrorSample(it.file, it.line, it.reason) },
            startedAt = this.startedAt,
            updatedAt = this.updatedAt,
        )

    private fun IngestionSummaryDto.toResponse(): IngestionSummary =
        IngestionSummary(this.parsed, this.ingested, this.failed, this.deduplicated)

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
