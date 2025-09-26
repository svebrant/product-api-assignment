package com.svebrant.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val basePrice: Double,
    val country: Country,
    val taxedPrice: Double,
)
