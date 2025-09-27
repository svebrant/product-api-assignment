package com.svebrant.scheduled

import com.svebrant.model.ingest.IngestStatus
import com.svebrant.service.IngestService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduler responsible for polling and processing pending ingestion jobs.
 * Only starts a new job when no other jobs are currently being processed.
 */
class IngestionScheduler(
    private val pollIntervalSeconds: Long = 5,
) : KoinComponent {
    private val ingestService: IngestService by inject()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info { "Starting ingestion job scheduler with poll interval of $pollIntervalSeconds seconds" }
        scheduler.scheduleAtFixedRate(
            { pollAndProcessJobs() },
            0,
            pollIntervalSeconds,
            TimeUnit.SECONDS,
        )
    }

    fun stop() {
        log.info { "Stopping ingestion job scheduler" }
        try {
            scheduler.shutdown()
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: Exception) {
            log.error(e) { "Error shutting down ingestion scheduler" }
            scheduler.shutdownNow()
        }
    }

    private fun pollAndProcessJobs() {
        if (isRunning.compareAndSet(false, true)) {
            coroutineScope.launch {
                try {
                    // Check if any job is currently running
                    val runningJobs = ingestService.findByStatus(IngestStatus.STARTED)

                    if (runningJobs.isEmpty()) {
                        // Get the next pending job
                        val pendingJobs = ingestService.findByStatus(IngestStatus.PENDING)

                        // Process the first pending job if available
                        pendingJobs.firstOrNull()?.let { pendingJob ->
                            log.info { "Starting to process pending ingestion job: ${pendingJob.ingestionId}" }

                            try {
                                // Mark the job as STARTED before processing
                                updateJobStatus(pendingJob.ingestionId, IngestStatus.STARTED)

                                // Process the job
                                ingestService.processIngestion(pendingJob.ingestionId)

                                // Mark as COMPLETED if no exceptions
                                updateJobStatus(pendingJob.ingestionId, IngestStatus.COMPLETED)
                                log.info { "Successfully completed ingestion job: ${pendingJob.ingestionId}" }
                            } catch (e: Exception) {
                                // Mark as FAILED if exception occurs
                                updateJobStatus(pendingJob.ingestionId, IngestStatus.FAILED)
                                log.error(e) { "Failed to process ingestion job: ${pendingJob.ingestionId}" }
                            }
                        }
                    } else {
                        log.debug { "Found ${runningJobs.size} running job(s), skipping this poll cycle" }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Error in polling and processing ingestion jobs" }
                } finally {
                    isRunning.set(false)
                }
            }
        } else {
            log.debug { "Scheduler is already running, skipping this poll cycle" }
        }
    }

    private suspend fun updateJobStatus(
        ingestionId: String,
        status: IngestStatus,
    ) {
        try {
            ingestService.updateStatus(ingestionId, status)
        } catch (e: Exception) {
            log.error(e) { "Failed to update status to $status for job $ingestionId" }
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
