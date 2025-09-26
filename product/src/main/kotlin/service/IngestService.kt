package com.svebrant.service

import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestResponse
import com.svebrant.model.ingest.IngestStatus
import com.svebrant.model.product.ProductRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

class IngestService(
    // private val ingestRepository: IngestRepository,
    private val productService: ProductService,
    private val productsFilePath: String,
    private val discountsFilePath: String,
) : KoinComponent {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createIngestJob(ingestRequest: IngestRequest): IngestResponse {
        try {
            log.info { "Received ingest request $ingestRequest" }

            val inputStream =
                this::class.java.getResourceAsStream(productsFilePath)
                    ?: throw IllegalArgumentException("Resource not found: $productsFilePath")
            inputStream.bufferedReader().use { reader ->
                val lineChunks = reader.lineSequence().chunked(ingestRequest.chunkSize)
                for (chunk in lineChunks) {
                    val parsedChunk = chunk.map { line -> json.decodeFromString<ProductRequest>(line) }

                    println("size: " + parsedChunk.size + " :  " + parsedChunk)

                    parsedChunk.asSequence().forEach { productService.createProduct(it) }
                    // Process parsedChunk
                }
            }

            return IngestResponse(
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                ingestRequest.mode,
                ingestRequest.workers,
                ingestRequest.chunkSize,
                ingestRequest.dryRun,
                "12345",
                IngestStatus.PENDING,
            )
        } catch (e: Exception) {
            log.error(e) { "Error during ingest ingest request $ingestRequest" }
            throw e
        }
    }

    fun readFileAsLineChunks(file: String): Sequence<String> {
        try {
            val inputStream =
                this::class.java.getResourceAsStream(file)
                    ?: throw IllegalArgumentException("Resource not found: $file")
            return inputStream.bufferedReader().use { reader ->
                reader.lineSequence()
            }
        } catch (e: Exception) {
            log.error(e) { "Error reading file $file" }
            return emptySequence()
        }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
