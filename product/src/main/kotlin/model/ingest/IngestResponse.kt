package com.svebrant.model.ingest

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class IngestResponse(
    val startedAt: LocalDateTime,
    val mode: IngestMode,
    val workers: Int,
    val chuckSize: Int,
    val dryRun: Boolean,
    val ingestionId: String,
    val status: IngestStatus,
)
