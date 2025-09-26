package com.svebrant.service

import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestResponse
import com.svebrant.model.ingest.IngestStatus
import com.svebrant.repository.IngestRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent

class IngestService(
    private val ingestRepository: IngestRepository,
    private val productService: ProductService,
) : KoinComponent {
    fun createIngestJob(ingestRequest: IngestRequest): IngestResponse {
        log.info { "Received ingest request $ingestRequest" }

        return IngestResponse(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            ingestRequest.mode,
            ingestRequest.workers,
            ingestRequest.chunkSize,
            ingestRequest.dryRun,
            "12345",
            IngestStatus.PENDING,
        )
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
