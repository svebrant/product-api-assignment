package com.svebrant.service

import com.svebrant.exception.DuplicateEntryException
import com.svebrant.exception.FailFastException
import com.svebrant.exception.ValidationErrorException
import com.svebrant.metrics.IngestionMetrics
import com.svebrant.metrics.MAX_ERROR_SAMPLES
import com.svebrant.model.discount.DiscountApiRequest
import com.svebrant.model.ingest.ErrorSample
import com.svebrant.model.ingest.IngestEntity
import com.svebrant.model.ingest.IngestEntityData
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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

        val metrics = IngestionMetrics()
        val filesDiscovered = calculateFilesDiscovered(ingest.mode)
        var filesProcessed = 0

        updateInitialProgress(ingestionId, filesDiscovered)

        try {
            ingestRepository.updateStatus(ingestionId, IngestStatus.STARTED)

            if (ingest.dryRun) {
                processDryRun(ingest, ingestionId, metrics, filesDiscovered)
            } else {
                processWithWorkers(ingest, ingestionId, metrics)

                filesProcessed = if (ingest.mode == IngestMode.ALL) 2 else 1
            }

            updateFinalProgress(ingestionId, metrics, filesDiscovered, filesProcessed)

            ingestRepository.updateStatus(ingestionId, IngestStatus.COMPLETED)
            log.info { "Successfully completed ingestion job $ingestionId - ${metrics.getSummary(ingest.dryRun)}" }
        } catch (e: Exception) {
            updateFinalProgress(ingestionId, metrics, filesDiscovered, filesProcessed)
            ingestRepository.updateStatus(ingestionId, IngestStatus.FAILED)
            log.error(e) { "Error during ingestion process for job $ingestionId" }
            throw e
        }
    }

    private fun calculateFilesDiscovered(mode: IngestMode): Int =
        when (mode) {
            IngestMode.PRODUCTS -> 1
            IngestMode.DISCOUNTS -> 1
            IngestMode.ALL -> 2
        }

    private suspend fun updateInitialProgress(
        ingestionId: String,
        filesDiscovered: Int,
    ) {
        ingestRepository.updateProgress(
            ingestionId = ingestionId,
            filesDiscovered = filesDiscovered,
            filesProcessed = 0,
        )
    }

    private suspend fun processDryRun(
        ingest: IngestionDto,
        ingestionId: String,
        metrics: IngestionMetrics,
        filesDiscovered: Int,
    ) {
        log.info { "Processing dry run for ingestion $ingestionId" }

        // Count lines in files to simulate processing
        val productLines =
            if (ingest.mode == IngestMode.PRODUCTS || ingest.mode == IngestMode.ALL) {
                countFileLines(productsFilePath)
            } else {
                0
            }

        val discountLines =
            if (ingest.mode == IngestMode.DISCOUNTS || ingest.mode == IngestMode.ALL) {
                countFileLines(discountsFilePath)
            } else {
                0
            }

        // Set metrics
        metrics.productsParsed.set(productLines)
        metrics.productsIngested.set(productLines)
        metrics.discountsParsed.set(discountLines)
        metrics.discountsIngested.set(discountLines)

        // Simulate processing time for monitoring
        delay(500)

        // Update progress to simulate completion
        val snapshot = metrics.snapshot()
        ingestRepository.updateProgress(
            ingestionId = ingestionId,
            filesDiscovered = filesDiscovered,
            filesProcessed = filesDiscovered, // All files processed in dry run
            productsParsed = snapshot.productsParsed,
            productsIngested = snapshot.productsIngested,
            discountsParsed = snapshot.discountsParsed,
            discountsIngested = snapshot.discountsIngested,
        )
    }

    private fun countFileLines(file: String): Int =
        try {
            this::class.java
                .getResourceAsStream(file)
                ?.bufferedReader()
                ?.lineSequence()
                ?.count() ?: 0
        } catch (e: Exception) {
            log.warn { "Failed to count lines in file $file: ${e.message}" }
            0
        }

    private suspend fun processWithWorkers(
        ingest: IngestionDto,
        ingestionId: String,
        metrics: IngestionMetrics,
    ) {
        coroutineScope {
            val channel = Channel<IngestEntityData>(capacity = Channel.UNLIMITED)

            launchFileReader(ingest.mode, channel)

            // Track last update time to avoid updating DB too frequently
            var lastUpdateTime = System.currentTimeMillis()
            val updateIntervalMs = 5000 // Update every 5 seconds

            val workerJobs =
                createWorkers(
                    ingest = ingest,
                    channel = channel,
                    metrics = metrics,
                    updateProgress = { currentTime ->
                        if (currentTime - lastUpdateTime > updateIntervalMs) {
                            // Launch a new coroutine to handle the database update
                            launch {
                                updateProgressMetrics(ingestionId, metrics)
                            }
                            lastUpdateTime = currentTime
                        }
                    },
                )

            workerJobs.joinAll()
        }
    }

    private fun CoroutineScope.launchFileReader(
        mode: IngestMode,
        channel: Channel<IngestEntityData>,
    ) = launch {
        if (mode == IngestMode.PRODUCTS || mode == IngestMode.ALL) {
            readFileToChannel(
                filePath = productsFilePath,
                entity = IngestEntity.PRODUCT,
                channel = channel,
            )
        }

        if (mode == IngestMode.DISCOUNTS || mode == IngestMode.ALL) {
            readFileToChannel(
                filePath = discountsFilePath,
                entity = IngestEntity.DISCOUNT,
                channel = channel,
            )
        }

        channel.close()
    }

    private suspend fun readFileToChannel(
        filePath: String,
        entity: IngestEntity,
        channel: Channel<IngestEntityData>,
    ) {
        val inputStream =
            this::class.java.getResourceAsStream(filePath)
                ?: return

        inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEachIndexed { index, line ->
                channel.send(
                    IngestEntityData(
                        entity = entity,
                        line = line,
                        lineNumber = index,
                        fileName = filePath,
                    ),
                )
            }
        }
    }

    private fun CoroutineScope.createWorkers(
        ingest: IngestionDto,
        channel: Channel<IngestEntityData>,
        metrics: IngestionMetrics,
        updateProgress: (Long) -> Unit,
    ) = List(ingest.workers) { workerId ->
        launch(Dispatchers.IO) {
            for (data in channel) {
                processChannelItem(
                    data = data,
                    ingest = ingest,
                    metrics = metrics,
                    workerId = workerId,
                )

                updateProgress(System.currentTimeMillis())
            }
        }
    }

    private suspend fun processChannelItem(
        data: IngestEntityData,
        ingest: IngestionDto,
        metrics: IngestionMetrics,
        workerId: Int,
    ) {
        val (entity, line, lineNumber, fileName) = data
        var attempt = 0
        var success = false

        while (attempt <= ingest.retries && !success) {
            try {
                when (entity) {
                    IngestEntity.PRODUCT -> {
                        processProduct(
                            line = line,
                            metrics = metrics,
                            ingest = ingest,
                            fileName = fileName,
                            lineNumber = lineNumber,
                            attempt = attempt,
                        )
                    }

                    IngestEntity.DISCOUNT -> {
                        processDiscount(
                            line = line,
                            metrics = metrics,
                            ingest = ingest,
                            fileName = fileName,
                            lineNumber = lineNumber,
                            attempt = attempt,
                        )
                    }
                }
                success = true
            } catch (e: Exception) {
                attempt++
                if (attempt > ingest.retries) {
                    handleProcessingFailure(
                        entity = entity,
                        metrics = metrics,
                        exception = e,
                        fileName = fileName,
                        lineNumber = lineNumber,
                        workerId = workerId,
                        line = line,
                        failFast = ingest.failFast,
                        attempt = attempt,
                    )
                } else {
                    log.warn { "Worker $workerId: Retry $attempt for $entity: $line" }
                }
            }
        }

        // Add small delay in dry run to allow tracking
        if (ingest.dryRun) {
            delay(10)
        }
    }

    private suspend fun processProduct(
        line: String,
        metrics: IngestionMetrics,
        ingest: IngestionDto,
        fileName: String,
        lineNumber: Int,
        attempt: Int,
    ) {
        val parsed = json.decodeFromString(ProductRequest.serializer(), line)
        if (attempt == 0) {
            metrics.productsParsed.incrementAndGet()
        }

        try {
            if (!ingest.dryRun) {
                productService.create(parsed)
            }
            metrics.productsIngested.incrementAndGet()
        } catch (e: DuplicateEntryException) {
            log.debug { "Duplicate product entry: ${e.message}" }
            if (attempt == 0) {
                metrics.productsDeduplicated.incrementAndGet()
            }
            metrics.addErrorSample(fileName, lineNumber, e.message ?: "Duplicate product", MAX_ERROR_SAMPLES)
            if (ingest.failFast) {
                throw FailFastException("Duplicate product: ${e.message}")
            }
        } catch (e: ValidationErrorException) {
            log.debug { "Product validation error: ${e.message}" }
            if (attempt == 0) {
                metrics.productsFailed.incrementAndGet()
            }
            metrics.addErrorSample(fileName, lineNumber, e.message ?: "Validation error", MAX_ERROR_SAMPLES)
            if (ingest.failFast) {
                throw FailFastException("Product validation error: ${e.message}")
            }
        }
    }

    private suspend fun processDiscount(
        line: String,
        metrics: IngestionMetrics,
        ingest: IngestionDto,
        fileName: String,
        lineNumber: Int,
        attempt: Int,
    ) {
        val parsed = json.decodeFromString(DiscountApiRequest.serializer(), line)
        if (attempt == 0) {
            metrics.discountsParsed.incrementAndGet()
        }

        try {
            if (!ingest.dryRun) {
                val discountApplicationResponse = discountService.create(parsed)
                if (discountApplicationResponse.applied && !discountApplicationResponse.alreadyApplied) {
                    metrics.discountsIngested.incrementAndGet()
                } else {
                    metrics.discountsDeduplicated.incrementAndGet()
                    val duplicationMessage =
                        "duplicate discount with composite key ${parsed.productId}-${parsed.discountId}"
                    metrics.addErrorSample(
                        fileName,
                        lineNumber,
                        duplicationMessage,
                        MAX_ERROR_SAMPLES,
                    )
                    if (ingest.failFast) {
                        throw FailFastException(duplicationMessage)
                    }
                }
            }
        } catch (e: ValidationErrorException) {
            log.debug { "Discount validation error: ${e.message}" }
            if (attempt == 0) {
                metrics.discountsFailed.incrementAndGet()
            }
            metrics.addErrorSample(fileName, lineNumber, e.message ?: "Validation error", MAX_ERROR_SAMPLES)
            if (ingest.failFast) {
                throw FailFastException("Discount validation error: ${e.message}")
            }
        }
    }

    private fun handleProcessingFailure(
        entity: IngestEntity,
        metrics: IngestionMetrics,
        exception: Exception,
        fileName: String,
        lineNumber: Int,
        workerId: Int,
        line: String,
        failFast: Boolean,
        attempt: Int,
    ) {
        if (attempt == 0) {
            when (entity) {
                IngestEntity.PRODUCT -> metrics.productsFailed.incrementAndGet()
                IngestEntity.DISCOUNT -> metrics.discountsFailed.incrementAndGet()
            }
        }

        metrics.addErrorSample(
            fileName = fileName,
            lineNumber = lineNumber,
            errorMessage = exception.message ?: "Unknown error",
        )

        log.error(exception) {
            "Worker $workerId: Failed to ingest $entity after retries: $line"
        }

        if (failFast) {
            throw FailFastException("${entity.name} processing failed: ${exception.message}")
        }
    }

    private suspend fun updateProgressMetrics(
        ingestionId: String,
        metrics: IngestionMetrics,
    ) {
        val snapshot = metrics.snapshot()
        ingestRepository.updateProgress(
            ingestionId = ingestionId,
            productsParsed = snapshot.productsParsed,
            productsIngested = snapshot.productsIngested,
            productsFailed = snapshot.productsFailed,
            productsDeduplicated = snapshot.productsDeduplicated,
            discountsParsed = snapshot.discountsParsed,
            discountsIngested = snapshot.discountsIngested,
            discountsFailed = snapshot.discountsFailed,
            discountsDeduplicated = snapshot.discountsDeduplicated,
            errors = snapshot.errorSamples,
        )
    }

    private suspend fun updateFinalProgress(
        ingestionId: String,
        metrics: IngestionMetrics,
        filesDiscovered: Int,
        filesProcessed: Int,
    ) {
        val snapshot = metrics.snapshot()
        ingestRepository.updateProgress(
            ingestionId = ingestionId,
            filesDiscovered = filesDiscovered,
            filesProcessed = filesProcessed,
            productsParsed = snapshot.productsParsed,
            productsIngested = snapshot.productsIngested,
            productsFailed = snapshot.productsFailed,
            productsDeduplicated = snapshot.productsDeduplicated,
            discountsParsed = snapshot.discountsParsed,
            discountsIngested = snapshot.discountsIngested,
            discountsFailed = snapshot.discountsFailed,
            discountsDeduplicated = snapshot.discountsDeduplicated,
            errors = snapshot.errorSamples,
        )
    }

    private fun IngestionDto.toResponse(): IngestResponse =
        IngestResponse(
            startedAt = this.startedAt,
            mode = this.mode,
            workers = this.workers,
            chunkSize = this.chunkSize,
            dryRun = this.dryRun,
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

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
