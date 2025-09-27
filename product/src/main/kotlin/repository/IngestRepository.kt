package com.svebrant.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.model.Filters.and
import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestStatus
import com.svebrant.repository.dto.ErrorDto
import com.svebrant.repository.dto.IngestionDto
import com.svebrant.repository.dto.IngestionSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bson.conversions.Bson
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
                    dryRun = ingestRequest.dryRun,
                    failFast = ingestRequest.failFast,
                    filesProcessed = 0,
                    filesDiscovered = 0,
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

    suspend fun find(
        status: IngestStatus? = null,
        limit: Int = 20,
        offset: Int = 0,
        sortOrder: String = "ASC",
    ): List<IngestionDto> {
        log.debug { "Getting ingestions with limit $limit, offset $offset" }

        val filters = mutableListOf<Bson>()
        if (status != null) filters += eq("status", status.name)
        val combinedFilter = if (filters.isEmpty()) Filters.empty() else and(filters)

        val sortDirection = if (sortOrder.equals("DESC", ignoreCase = true)) -1 else 1

        return ingestCollection
            .find(combinedFilter)
            .sort(org.bson.Document("startedAt", sortDirection))
            .skip(offset)
            .limit(limit)
            .toList()
    }

    suspend fun findByIngestionId(ingestionId: String): IngestionDto? {
        log.debug { "Getting ingestion with ingestionId $ingestionId" }
        return ingestCollection.find(eq("ingestionId", ingestionId)).firstOrNull()
    }

    suspend fun findByStatus(status: IngestStatus): List<IngestionDto> {
        log.debug { "Getting ingestions with status $status" }
        return ingestCollection.find(eq("status", status)).toList()
    }

    suspend fun updateStatus(
        ingestionId: String,
        status: IngestStatus,
    ): Boolean {
        log.debug { "Updating ingestion $ingestionId to status $status" }
        try {
            val updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val result =
                ingestCollection.updateOne(
                    eq("ingestionId", ingestionId),
                    Updates.combine(
                        Updates.set("status", status),
                        Updates.set("updatedAt", updatedAt),
                    ),
                )
            log.debug { "Updated status for ingestion $ingestionId: matched=${result.matchedCount}, modified=${result.modifiedCount}" }
            return result.modifiedCount > 0
        } catch (e: Exception) {
            log.error(e) { "Error updating status for ingestion $ingestionId" }
            return false
        }
    }

    suspend fun updateProgress(
        ingestionId: String,
        filesDiscovered: Int? = null,
        filesProcessed: Int? = null,
        productsParsed: Int? = null,
        productsIngested: Int? = null,
        productsFailed: Int? = null,
        productsDeduplicated: Int? = null,
        discountsParsed: Int? = null,
        discountsIngested: Int? = null,
        discountsFailed: Int? = null,
        discountsDeduplicated: Int? = null,
        errors: List<ErrorDto>? = null,
    ): Boolean {
        log.debug { "Updating progress for ingestion $ingestionId" }
        try {
            val updatesList = mutableListOf<Bson>()
            val updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            // Always update timestamp
            updatesList.add(Updates.set("updatedAt", updatedAt))

            // Update file counts if provided
            filesDiscovered?.let { updatesList.add(Updates.set("filesDiscovered", it)) }
            filesProcessed?.let { updatesList.add(Updates.set("filesProcessed", it)) }

            // Update product metrics if provided
            productsParsed?.let { updatesList.add(Updates.set("products.parsed", it)) }
            productsIngested?.let { updatesList.add(Updates.set("products.ingested", it)) }
            productsFailed?.let { updatesList.add(Updates.set("products.failed", it)) }
            productsDeduplicated?.let { updatesList.add(Updates.set("products.deduplicated", it)) }

            // Update discount metrics if provided
            discountsParsed?.let { updatesList.add(Updates.set("discounts.parsed", it)) }
            discountsIngested?.let { updatesList.add(Updates.set("discounts.ingested", it)) }
            discountsFailed?.let { updatesList.add(Updates.set("discounts.failed", it)) }
            discountsDeduplicated?.let { updatesList.add(Updates.set("discounts.deduplicated", it)) }

            // Add error samples if provided
            errors?.let {
                if (it.isNotEmpty()) {
                    updatesList.add(Updates.set("errors", it))
                }
            }

            if (updatesList.size > 1) { // At least one metric besides updatedAt
                val result =
                    ingestCollection.updateOne(
                        eq("ingestionId", ingestionId),
                        Updates.combine(updatesList),
                    )
                log.debug {
                    "Updated progress for ingestion $ingestionId: matched=${result.matchedCount}, modified=${result.modifiedCount}"
                }
                return result.modifiedCount > 0
            }
            return true
        } catch (e: Exception) {
            log.error(e) { "Error updating progress for ingestion $ingestionId" }
            return false
        }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
