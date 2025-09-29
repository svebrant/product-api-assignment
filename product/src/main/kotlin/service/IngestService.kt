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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import java.util.concurrent.ConcurrentHashMap

class IngestService(
    private val ingestRepository: IngestRepository,
    private val productService: ProductService,
    private val discountService: DiscountService,
    private val productsFilePath: String,
    private val discountsFilePath: String,
) : KoinComponent {
    private val json = Json { ignoreUnknownKeys = true }

    // SupervisorJob that will be parent to all worker coroutines
    private val supervisorJob = SupervisorJob()

    // Create a coroutine scope for worker management
    private val workerScope = CoroutineScope(Dispatchers.Default + supervisorJob)

    // Track active ingestion jobs
    private val activeIngestionJobs = ConcurrentHashMap<String, Job>()

    init {
        // Register a shutdown hook to cancel all workers on JVM shutdown
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutting down all workers..." }

                // Mark all active ingestion jobs as FAILED
                runBlocking {
                    activeIngestionJobs.keys().toList().forEach { ingestionId ->
                        try {
                            log.info { "Marking ingestion job $ingestionId as FAILED due to shutdown" }
                            ingestRepository.updateStatus(ingestionId, IngestStatus.FAILED)
                        } catch (e: Exception) {
                            log.error(e) { "Failed to mark ingestion job $ingestionId as FAILED during shutdown" }
                        }
                    }
                }

                // Cancel all worker coroutines
                supervisorJob.cancelChildren()
                log.info { "All workers have been shutdown." }
            },
        )
    }

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

        // Create a job to track this ingestion
        val ingestionJob = Job()

        // Register this job as active
        activeIngestionJobs[ingestionId] = ingestionJob

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
        } finally {
            // Remove from active jobs and cancel the job
            activeIngestionJobs.remove(ingestionId)
            ingestionJob.cancel()
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
        // Use a Job to track all coroutines in this processing batch
        val processingJob = Job()

        try {
            val channel = Channel<IngestEntityData>(capacity = Channel.UNLIMITED)

            val updateIntervalMs = 5000L

            // Start a separate coroutine for progress tracking
            val progressJob =
                workerScope.launch(processingJob) {
                    while (isActive) {
                        delay(updateIntervalMs)
                        updateProgressMetrics(ingestionId, metrics)
                        log.info { "Progress update: ${metrics.getSummary(ingest.dryRun)}" }
                    }
                }

            // Launch file reader in workerScope
            val fileReaderJob = launchFileReader(ingest, channel, processingJob)

            if (ingest.mode == IngestMode.DISCOUNTS || ingest.mode == IngestMode.ALL) {
                // For discounts, use batch processing
                processDiscountsBatched(ingest, channel, metrics)
            } else {
                // For products only, use existing worker approach
                val workerJobs =
                    createWorkers(
                        ingest = ingest,
                        channel = channel,
                        metrics = metrics,
                        parentJob = processingJob,
                    )

                workerJobs.joinAll()
            }

            // Wait for file reader to finish
            fileReaderJob.join()

            // Cancel the progress tracking job once processing is complete
            progressJob.cancel()

            // One final update to ensure latest metrics are saved
            updateProgressMetrics(ingestionId, metrics)
        } finally {
            // Ensure all coroutines are cancelled if something goes wrong
            processingJob.cancel()
        }
    }

    private suspend fun processDiscountsBatched(
        ingest: IngestionDto,
        channel: Channel<IngestEntityData>,
        metrics: IngestionMetrics,
    ) {
        // Buffer for collecting discount requests before sending as batch
        val discountBatchBuffer = mutableListOf<Pair<DiscountApiRequest, IngestEntityData>>()

        for (item in channel) {
            if (item.entity == IngestEntity.DISCOUNT) {
                try {
                    val discountRequest = json.decodeFromString(DiscountApiRequest.serializer(), item.line)
                    metrics.discountsParsed.incrementAndGet()

                    discountBatchBuffer.add(discountRequest to item)

                    // When we reach the chunk size, process the batch
                    if (discountBatchBuffer.size >= ingest.chunkSize) {
                        processDiscountBatch(ingest, discountBatchBuffer, metrics)
                        discountBatchBuffer.clear()
                    }
                } catch (e: Exception) {
                    // Handle parsing errors
                    metrics.discountsFailed.incrementAndGet()
                    metrics.addErrorSample(
                        item.fileName,
                        item.lineNumber,
                        "Parsing error: ${e.message ?: "Unknown error"}",
                        MAX_ERROR_SAMPLES,
                    )

                    if (ingest.failFast) {
                        metrics.discountsFailed.incrementAndGet()
                        throw FailFastException("Failed to parse discount: ${e.message}")
                    }
                }
            } else {
                // Process products normally
                processChannelItem(
                    data = item,
                    ingest = ingest,
                    metrics = metrics,
                    workerId = 0,
                )
            }
        }

        // Process remaining items in buffer
        if (discountBatchBuffer.isNotEmpty()) {
            processDiscountBatch(ingest, discountBatchBuffer, metrics)
        }
    }

    private suspend fun processDiscountBatch(
        ingest: IngestionDto,
        discountBatch: List<Pair<DiscountApiRequest, IngestEntityData>>,
        metrics: IngestionMetrics,
    ) {
        val discounts = discountBatch.map { it.first }
        log.debug { "Processing batch of ${discounts.size} discounts" }

        if (ingest.dryRun) {
            // In dry run mode, just update metrics
            discounts.forEach { _ ->
                metrics.discountsIngested.incrementAndGet()
            }
            return
        }

        try {
            // TODO metrics are not accurate for failures within the batch, leaving this for now.
            val batchResponse = discountService.createBatch(discounts)

            // Process the response for each discount
            batchResponse.results.forEachIndexed { index, result ->
                val (_, itemData) = discountBatch[index]

                when {
                    result.success -> {
                        metrics.discountsIngested.incrementAndGet()
                    }

                    result.alreadyApplied -> {
                        metrics.discountsDeduplicated.incrementAndGet()
                        val message = "Duplicate discount with composite key ${result.productId}-${result.discountId}"
                        metrics.addErrorSample(
                            itemData.fileName,
                            itemData.lineNumber,
                            message,
                            MAX_ERROR_SAMPLES,
                        )

                        if (ingest.failFast) {
                            metrics.discountsFailed.incrementAndGet()
                            throw FailFastException(message)
                        }
                    }

                    else -> {
                        metrics.discountsFailed.incrementAndGet()
                        val errorMessage = result.error ?: "Unknown error"
                        metrics.addErrorSample(
                            itemData.fileName,
                            itemData.lineNumber,
                            errorMessage,
                            MAX_ERROR_SAMPLES,
                        )

                        if (ingest.failFast) {
                            metrics.discountsFailed.incrementAndGet()
                            throw FailFastException(errorMessage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If the batch request fails completely
            log.error(e) { "Error processing discount batch of size ${discounts.size}: ${e.message}" }

            // Mark all items in batch as failed
            discountBatch.forEach { (_, itemData) ->
                metrics.discountsFailed.incrementAndGet()
                metrics.addErrorSample(
                    itemData.fileName,
                    itemData.lineNumber,
                    "Batch processing error: ${e.message ?: "Unknown error"}",
                    MAX_ERROR_SAMPLES,
                )
            }

            if (ingest.failFast) {
                metrics.discountsFailed.incrementAndGet()
                throw FailFastException("Failed to process discount batch: ${e.message}")
            }
        }
    }

    private fun launchFileReader(
        ingestion: IngestionDto,
        channel: Channel<IngestEntityData>,
        parentJob: Job,
    ) = workerScope.launch(parentJob) {
        val mode = ingestion.mode

        // Process products and discounts in parallel if mode is ALL
        if (mode == IngestMode.ALL) {
            // Launch parallel coroutines for reading each file
            val jobs = mutableListOf<Job>()

            val productsJob =
                launch {
                    readFileToChannel(
                        filePath = productsFilePath,
                        entity = IngestEntity.PRODUCT,
                        channel = channel,
                        ingest = ingestion,
                    )
                    log.info { "Completed reading products file" }
                }
            jobs.add(productsJob)

            val discountsJob =
                launch {
                    readFileToChannel(
                        filePath = discountsFilePath,
                        entity = IngestEntity.DISCOUNT,
                        channel = channel,
                        ingest = ingestion,
                    )
                    log.info { "Completed reading discounts file" }
                }
            jobs.add(discountsJob)

            jobs.joinAll()
        } else {
            if (mode == IngestMode.PRODUCTS) {
                readFileToChannel(
                    filePath = productsFilePath,
                    entity = IngestEntity.PRODUCT,
                    channel = channel,
                    ingest = ingestion,
                )
            } else if (mode == IngestMode.DISCOUNTS) {
                readFileToChannel(
                    filePath = discountsFilePath,
                    entity = IngestEntity.DISCOUNT,
                    channel = channel,
                    ingest = ingestion,
                )
            }
        }

        channel.close()
    }

    private suspend fun readFileToChannel(
        filePath: String,
        entity: IngestEntity,
        channel: Channel<IngestEntityData>,
        ingest: IngestionDto,
    ) {
        val inputStream =
            this::class.java.getResourceAsStream(filePath)
                ?: run {
                    log.error { "Resource not found: $filePath" }
                    return
                }

        log.info { "Starting to read file $filePath with chunkSize ${ingest.chunkSize}" }
        val startTime = System.currentTimeMillis()
        var lastLogTime = startTime
        val logIntervalMs = 10_000 // Log every 10 seconds

        val batchSize = ingest.chunkSize * 4 // Fixed multiplier for consistent batch sizes

        val atomicLineCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)

        withContext(Dispatchers.IO) {
            inputStream.bufferedReader().use { reader ->
                val lines = reader.lineSequence()
                val chunkedLines = lines.chunked(batchSize)

                for (batch in chunkedLines) {
                    if (!isActive) break

                    // Process the batch in parallel using smaller chunks
                    val chunks = batch.chunked(ingest.chunkSize)

                    // Process each chunk sequentially for controlled memory usage
                    for (chunk in chunks) {
                        // Send lines to channel in bulk for better performance
                        chunk.forEach { line ->
                            val currentLineNumber = atomicLineCount.getAndIncrement()

                            channel.send(
                                IngestEntityData(
                                    entity = entity,
                                    line = line,
                                    lineNumber = currentLineNumber,
                                    fileName = filePath,
                                ),
                            )
                        }

                        // Log progress at regular intervals
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLogTime >= logIntervalMs) {
                            val currentCount = atomicLineCount.get()
                            val elapsed = currentTime - startTime
                            val linesPerSecond = if (elapsed > 0) (currentCount * 1000.0 / elapsed).toInt() else 0
                            val elapsedSeconds = elapsed / 1000
                            log.info { "Read $currentCount lines from $filePath ($linesPerSecond lines/sec, elapsed: ${elapsedSeconds}s)" }
                            lastLogTime = currentTime
                        }

                        yield()
                    }
                }
            }
        }

        val totalCount = atomicLineCount.get()
        val totalTime = (System.currentTimeMillis() - startTime) / 1000
        log.info {
            "Finished reading $totalCount lines from $filePath in ${totalTime}s (avg: ${if (totalTime > 0) totalCount / totalTime else totalCount} lines/sec)"
        }
    }

    private fun createWorkers(
        ingest: IngestionDto,
        channel: Channel<IngestEntityData>,
        metrics: IngestionMetrics,
        parentJob: Job,
    ) = List(ingest.workers) { workerId ->
        // Use Dispatchers.IO.limitedParallelism for better control or virtual threads if available
        val virtualThreadDispatcher = Dispatchers.IO.limitedParallelism(ingest.workers * 2)

        workerScope.launch(virtualThreadDispatcher + parentJob) {
            for (data in channel) {
                processChannelItem(
                    data = data,
                    ingest = ingest,
                    metrics = metrics,
                    workerId = workerId,
                )
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
                metrics.productsFailed.incrementAndGet()
                throw FailFastException("Duplicate product: ${e.message}")
            }
        } catch (e: ValidationErrorException) {
            log.debug { "Product validation error: ${e.message}" }
            if (attempt == 0) {
                metrics.productsFailed.incrementAndGet()
            }
            metrics.addErrorSample(fileName, lineNumber, e.message ?: "Validation error", MAX_ERROR_SAMPLES)
            if (ingest.failFast) {
                metrics.productsFailed.incrementAndGet()
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
                        metrics.discountsFailed.incrementAndGet()
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
                metrics.discountsFailed.incrementAndGet()
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
            when (entity) {
                IngestEntity.PRODUCT -> metrics.productsFailed.incrementAndGet()
                IngestEntity.DISCOUNT -> metrics.discountsFailed.incrementAndGet()
            }
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
