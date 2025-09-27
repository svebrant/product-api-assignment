package com.svebrant.model.ingest

data class IngestEntityData(
    val entity: IngestEntity,
    val line: String,
    val lineNumber: Int,
    val fileName: String,
)
