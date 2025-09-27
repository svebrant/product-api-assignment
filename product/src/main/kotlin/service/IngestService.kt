package com.svebrant.service

import com.svebrant.exception.DuplicateEntryException
import com.svebrant.exception.FailFastException
import com.svebrant.exception.ValidationErrorException
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
import com.svebrant.repository.dto.ErrorDto
import com.svebrant.repository.dto.IngestionDto
import com.svebrant.repository.dto.IngestionSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

const val MAX_ERROR_SAMPLES = 5

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

        val result =
            ingestRepository.createIngestionRecord(
                ingestionId,
                ingestRequest,
            )

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

    suspend fun getIngestions(
        status: IngestStatus? = null,
        limit: Int = 20,
        offset: Int = 0,
        sortOrder: String = "ASC",
    ): List<IngestionStatusResponse> {
        log.info { "Retrieving ingestions ${status?.let { " for status: $it" } ?: ""}, limit $limit, offset $offset" }
        val result =
            ingestRepository
                .find(status = status, limit = limit, offset = offset, sortOrder = sortOrder)
                .map { it.toStatusResponse() }
        return result
    }

    suspend fun findByStatus(status: IngestStatus): List<IngestionStatusResponse> =
        ingestRepository.findByStatus(status).map {
            it.toStatusResponse()
        }

    suspend fun updateStatus(
        ingestionId: String,
        status: IngestStatus,
    ): Boolean {
        log.info { "Updating ingestion status for id $ingestionId to $status" }
        return ingestRepository.updateStatus(ingestionId, status)
    }

    suspend fun getIngestionStatus(ingestionId: String): IngestionStatusResponse? {
        log.info { "Retrieving ingestion status with id $ingestionId" }
        return ingestRepository.findByIngestionId(ingestionId)?.toStatusResponse()
    }

    suspend fun processIngestion(ingestionId: String) {
        val ingest =
            ingestRepository.findByIngestionId(ingestionId)
                ?: throw IllegalArgumentException("Ingestion with id $ingestionId not found")

        log.info { "Processing ingestion job ${ingest.ingestionId} with mode ${ingest.mode}" }

        // Set up progress tracking
        var productsParsed = 0
        var productsIngested = 0
        var productsFailed = 0
        var productsDeduplicated = 0
        var discountsParsed = 0
        var discountsIngested = 0
        var discountsFailed = 0
        var discountsDeduplicated = 0
        val errorSamples = mutableListOf<ErrorDto>()

        // Determine which files we're processing based on mode
        val filesDiscovered =
            when (ingest.mode) {
                IngestMode.PRODUCTS -> 1
                IngestMode.DISCOUNTS -> 1
                IngestMode.ALL -> 2
            }
        var filesProcessed = 0

        // Initial progress update
        ingestRepository.updateProgress(
            ingestionId = ingestionId,
            filesDiscovered = filesDiscovered,
            filesProcessed = filesProcessed,
        )

        try {
            // Update status to STARTED
            ingestRepository.updateStatus(ingestionId, IngestStatus.STARTED)

            coroutineScope {
                val channel = Channel<IngestEntityChannel>(capacity = Channel.UNLIMITED)
                processFilesAndSendToChannel(ingest.mode, channel)

                // If we're only processing one file type, mark file as processed
                if (ingest.mode != IngestMode.ALL) {
                    filesProcessed = 1
                    ingestRepository.updateProgress(
                        ingestionId = ingestionId,
                        filesProcessed = filesProcessed,
                    )
                }

                // Track last update time to avoid updating DB too frequently
                var lastUpdateTime = System.currentTimeMillis()
                val updateIntervalMs = 5000 // Update every 5 seconds

                // Set up atomic counters to track metrics across coroutines
                val atomicProductsParsed =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)
                val atomicProductsIngested =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)
                val atomicProductsFailed =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)
                val atomicProductsDeduplicated =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)

                val atomicDiscountsParsed =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)
                val atomicDiscountsIngested =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)
                val atomicDiscountsFailed =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)
                val atomicDiscountsDeduplicated =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)

                // Error collection needs synchronization
                val errorLock = Any()

                // Create worker coroutines
                val workerJobs =
                    List(ingest.workers) { workerId ->
                        launch(Dispatchers.IO) {
                            for ((entity: IngestEntity, line: String, index: Int, filename: String) in channel) {
                                var attempt = 0
                                var success = false

                                // Try to process with retries
                                while (attempt <= ingest.retries && !success) {
                                    try {
                                        when (entity) {
                                            IngestEntity.PRODUCT -> {
                                                val parsed = json.decodeFromString(ProductRequest.serializer(), line)
                                                atomicProductsParsed.incrementAndGet()
                                                try {
                                                    if (!ingest.dryRun) {
                                                        productService.create(parsed)
                                                        atomicProductsIngested.incrementAndGet()
                                                    }
                                                } catch (e: DuplicateEntryException) {
                                                    log.debug { "Duplicate entry for productId $ingestionId" }
                                                    atomicProductsDeduplicated.incrementAndGet()
                                                    synchronized(errorLock) {
                                                        maybeAddErrorSample(errorSamples, filename, index, e)
                                                    }
                                                    if (ingest.failFast) {
                                                        throw FailFastException(e.message ?: "Fail fast")
                                                    }
                                                    success = true
                                                    continue
                                                } catch (e: ValidationErrorException) {
                                                    log.debug { "Validation error: ${e.message}" }
                                                    atomicProductsFailed.incrementAndGet()
                                                    synchronized(errorLock) {
                                                        maybeAddErrorSample(errorSamples, filename, index, e)
                                                    }
                                                    if (ingest.failFast) {
                                                        throw FailFastException(e.message ?: "Fail fast")
                                                    }
                                                    success = true
                                                    continue
                                                } catch (e: Exception) {
                                                    throw e
                                                }
                                            }

                                            IngestEntity.DISCOUNT -> {
                                                val parsed = json.decodeFromString(DiscountRequest.serializer(), line)
                                                atomicDiscountsParsed.incrementAndGet()
                                                try {
                                                    if (!ingest.dryRun) {
                                                        discountService.create(parsed)
                                                        atomicDiscountsIngested.incrementAndGet()
                                                    }
                                                } catch (e: DuplicateEntryException) {
                                                    log.debug { "Duplicate entry for productId $ingestionId" }
                                                    atomicDiscountsDeduplicated.incrementAndGet()
                                                    synchronized(errorLock) {
                                                        maybeAddErrorSample(errorSamples, filename, index, e)
                                                    }
                                                    if (ingest.failFast) {
                                                        throw FailFastException(e.message ?: "Fail fast")
                                                    }
                                                    success = true
                                                    continue
                                                } catch (e: ValidationErrorException) {
                                                    log.debug { "Validation error: ${e.message}" }
                                                    atomicProductsFailed.incrementAndGet()
                                                    synchronized(errorLock) {
                                                        maybeAddErrorSample(errorSamples, filename, index, e)
                                                    }
                                                    if (ingest.failFast) {
                                                        throw FailFastException(e.message ?: "Fail fast ")
                                                    }
                                                    success = true
                                                    continue
                                                } catch (e: Exception) {
                                                    throw e
                                                }
                                            }
                                        }
                                        success = true
                                    } catch (e: Exception) {
                                        attempt++
                                        if (attempt > ingest.retries) {
                                            // Failed after all retries
                                            when (entity) {
                                                IngestEntity.PRODUCT -> atomicProductsFailed.incrementAndGet()
                                                IngestEntity.DISCOUNT -> atomicDiscountsFailed.incrementAndGet()
                                            }

                                            // Record error sample
                                            synchronized(errorLock) {
                                                maybeAddErrorSample(errorSamples, filename, index, e)
                                            }

                                            log.error(
                                                e,
                                            ) { "Worker $workerId: Failed to ingest $entity after ${ingest.retries} retries: $line" }
                                            if (ingest.failFast) {
                                                throw e
                                            }
                                        } else {
                                            log.warn { "Worker $workerId: Retry $attempt for $entity: $line" }
                                        }
                                    }
                                }

                                // Check if we should update progress in DB
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime > updateIntervalMs) {
                                    // Snapshot current counts
                                    productsParsed = atomicProductsParsed.get()
                                    productsIngested = atomicProductsIngested.get()
                                    productsFailed = atomicProductsFailed.get()
                                    productsDeduplicated = atomicProductsDeduplicated.get()
                                    discountsParsed = atomicDiscountsParsed.get()
                                    discountsIngested = atomicDiscountsIngested.get()
                                    discountsFailed = atomicDiscountsFailed.get()
                                    discountsDeduplicated = atomicDiscountsDeduplicated.get()

                                    // Get current error samples (synchronized)
                                    val currentErrors =
                                        synchronized(errorLock) {
                                            errorSamples.toList()
                                        }

                                    ingestRepository.updateProgress(
                                        ingestionId = ingestionId,
                                        productsParsed = productsParsed,
                                        productsIngested = productsIngested,
                                        productsFailed = productsFailed,
                                        productsDeduplicated = productsDeduplicated,
                                        discountsParsed = discountsParsed,
                                        discountsIngested = discountsIngested,
                                        discountsFailed = discountsFailed,
                                        discountsDeduplicated = discountsDeduplicated,
                                        errors = currentErrors,
                                    )

                                    lastUpdateTime = currentTime
                                }
                            }
                        }
                    }

                // Wait for all workers to complete
                workerJobs.joinAll()

                // Get the final updated counts
                productsParsed = atomicProductsParsed.get()
                productsIngested = atomicProductsIngested.get()
                productsFailed = atomicProductsFailed.get()
                productsDeduplicated = atomicProductsDeduplicated.get()
                discountsParsed = atomicDiscountsParsed.get()
                discountsIngested = atomicDiscountsIngested.get()
                discountsFailed = atomicDiscountsFailed.get()
                discountsDeduplicated = atomicDiscountsDeduplicated.get()

                // If we processed both product and discount files
                if (ingest.mode == IngestMode.ALL) {
                    filesProcessed = 2
                }
            }

            // Make a final progress update
            ingestRepository.updateProgress(
                ingestionId = ingestionId,
                filesDiscovered = filesDiscovered,
                filesProcessed = filesProcessed,
                productsParsed = productsParsed,
                productsIngested = productsIngested,
                productsFailed = productsFailed,
                productsDeduplicated = productsDeduplicated,
                discountsParsed = discountsParsed,
                discountsIngested = discountsIngested,
                discountsFailed = discountsFailed,
                discountsDeduplicated = discountsDeduplicated,
                errors = errorSamples,
            )

            // Update status to COMPLETED
            ingestRepository.updateStatus(ingestionId, IngestStatus.COMPLETED)
            log.info {
                "Successfully completed ingestion job $ingestionId"
            }
        } catch (e: Exception) {
            // Update status to FAILED
            ingestRepository.updateStatus(ingestionId, IngestStatus.FAILED)
            log.error(e) { "Error during ingestion process for job $ingestionId" }
            throw e
        }
    }

    private fun maybeAddErrorSample(
        errorSamples: MutableList<ErrorDto>,
        filename: String,
        index: Int,
        e: Exception,
    ) {
        if (errorSamples.size <= MAX_ERROR_SAMPLES && errorSamples.none { it.line == index && it.file == filename }) {
            errorSamples.add(
                ErrorDto(
                    file = filename,
                    line = index,
                    reason = e.message ?: "Unknown error",
                ),
            )
        }
    }

    data class IngestEntityChannel(
        val entity: IngestEntity,
        val line: String,
        val lineNumber: Int,
        val fileName: String,
    )

    private fun CoroutineScope.processFilesAndSendToChannel(
        mode: IngestMode,
        channel: Channel<IngestEntityChannel>,
    ) {
        launch {
            if (mode == IngestMode.PRODUCTS || mode == IngestMode.ALL) {
                readFileLines(productsFilePath).forEachIndexed { index, line ->
                    channel.send(IngestEntityChannel(IngestEntity.PRODUCT, line, index, productsFilePath))
                }
            }
            if (mode == IngestMode.DISCOUNTS || mode == IngestMode.ALL) {
                readFileLines(discountsFilePath).forEachIndexed { index, line ->
                    channel.send(IngestEntityChannel(IngestEntity.DISCOUNT, line, index, discountsFilePath))
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
            dryRun = this.dryRun,
            filesDiscovered = this.filesDiscovered,
            filesProcessed = this.filesProcessed,
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
