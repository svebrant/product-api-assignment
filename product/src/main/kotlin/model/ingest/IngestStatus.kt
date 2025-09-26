package com.svebrant.model.ingest

import kotlinx.serialization.Serializable

@Serializable
enum class IngestStatus {
    PENDING,
    STARTED,
    COMPLETED,
    FAILED,
}
