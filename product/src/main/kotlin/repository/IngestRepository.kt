package com.svebrant.repository

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestStatus
import com.svebrant.repository.dto.IngestionDto
import com.svebrant.repository.dto.IngestionSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent

class IngestRepository(
    private val ingestCollection: MongoCollection<IngestionDto>,
) : KoinComponent {
    suspend fun createIngestionRecord(
        ingestionId: String,
        ingestRequest: IngestRequest,
    ): String? {
        try {
            val ingestion =
                IngestionDto(
                    ingestionId = ingestionId,
                    mode = ingestRequest.mode,
                    status = IngestStatus.PENDING,
                    workers = ingestRequest.workers,
                    chunkSize = ingestRequest.chunkSize,
                    retries = ingestRequest.retries,
                    failFast = ingestRequest.failFast,
                    products =
                        IngestionSummaryDto(
                            parsed = 0,
                            ingested = 0,
                            failed = 0,
                            deduplicated = null,
                        ),
                    discounts =
                        IngestionSummaryDto(
                            parsed = 0,
                            ingested = 0,
                            failed = 0,
                            deduplicated = null,
                        ),
                    startedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )
            log.debug { "Creating ingestion record $ingestion" }
            val created = ingestCollection.insertOne(ingestion)

            log.debug { "Successfully inserted ingestion $created" }

            return ingestionId
        } catch (e: Exception) {
            log.error(e) { "Error creating ingestion record for ingestionId $ingestionId" }
            return null
        }
    }

    suspend fun findByIngestionId(ingestionId: String): IngestionDto? {
        log.debug { "Getting ingestion with ingestionId $ingestionId" }
        return ingestCollection.find(eq("ingestionId", ingestionId)).firstOrNull()
    }

    suspend fun findByStatus(status: IngestStatus): List<IngestionDto> {
        log.debug { "Getting ingestions with status $status" }
        return ingestCollection.find(eq("status", status)).toList()
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
