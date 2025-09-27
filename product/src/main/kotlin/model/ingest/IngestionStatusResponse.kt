package com.svebrant.model.ingest

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class IngestionStatusResponse(
    val ingestionId: String,
    val status: IngestStatus,
    val filesDiscovered: Int = 0,
    val filesProcessed: Int = 0,
    val products: IngestionSummary? = null,
    val discounts: IngestionSummary? = null,
    val errorsSample: List<ErrorSample> = emptyList(),
    val startedAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,
)

@Serializable
data class IngestionSummary(
    val parsed: Int,
    val ingested: Int,
    val failed: Int,
    val deduplicated: Int? = null,
)

@Serializable
data class ErrorSample(
    val file: String,
    val line: Int,
    val reason: String,
)
