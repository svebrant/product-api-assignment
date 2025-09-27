package com.svebrant.repository.dto

import com.svebrant.model.ingest.IngestMode
import com.svebrant.model.ingest.IngestStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class IngestionDto(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val ingestionId: String,
    val mode: IngestMode,
    val status: IngestStatus,
    val workers: Int,
    val chunkSize: Int,
    val retries: Int,
    val failFast: Boolean,
    val products: IngestionSummaryDto? = null,
    val discounts: IngestionSummaryDto? = null,
    val errors: List<ErrorDto> = emptyList(),
    val startedAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,
)

@Serializable
data class IngestionSummaryDto(
    val parsed: Int,
    val ingested: Int,
    val failed: Int,
    val deduplicated: Int? = null,
)

@Serializable
data class ErrorDto(
    val file: String,
    val line: Int,
    val reason: String,
)
