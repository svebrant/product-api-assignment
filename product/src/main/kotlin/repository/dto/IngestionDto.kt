package com.svebrant.repository.dto

import com.svebrant.model.ingest.IngestMode
import com.svebrant.model.ingest.IngestStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class IngestionDto(
    val ingestionId: String,
    val mode: IngestMode,
    val status: IngestStatus,
    val workers: Int,
    val chunkSize: Int,
    val retries: Int,
    val failFast: Boolean,
    val summary: IngestionSummaryDto,
    val errors: List<ErrorDto> = emptyList(),
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime? = null,
)

@Serializable
data class IngestionSummaryDto(
    val parsed: Int,
    val ingested: Int,
    val failed: Int,
)

@Serializable
data class ErrorDto(
    val file: String,
    val line: Int,
    val reason: String,
)
