package com.svebrant.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductRequest(
    val id: String,
    val name: String,
    val basePrice: Double,
    val country: Country,
)

fun ProductRequest.validate() {
    require(id.isNotBlank()) { "id must not be blank" }
    require(name.isNotBlank()) { "name must not be blank" }
    require(basePrice >= 0) { "basePrice must be non-negative" }
}
