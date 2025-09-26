package com.svebrant.model.ingest

import kotlinx.serialization.Serializable

@Serializable
data class IngestRequest(
    val workers: Int = 4,
    val chunkSize: Int = 100,
    val mode: IngestMode = IngestMode.ALL,
    val failFast: Boolean = false,
    val retries: Int = 2,
    val dryRun: Boolean = false,
)

fun IngestRequest.validate() {
    require(workers > 0) { "workers must be greater than 0" }
    require(chunkSize > 0) { "chunkSize must be greater than 0" }
    require(retries >= 0) { "retries must be non-negative" }
}
