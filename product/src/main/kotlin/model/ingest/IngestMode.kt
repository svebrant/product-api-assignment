package com.svebrant.model.ingest

import kotlinx.serialization.Serializable

@Serializable
enum class IngestMode {
    PRODUCTS,
    DISCOUNTS,
    ALL,
}
