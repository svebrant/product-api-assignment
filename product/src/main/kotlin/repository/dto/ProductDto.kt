package com.svebrant.repository.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ProductDto(
    val productId: String,
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val name: String,
    val basePrice: Double,
    val country: String,
)
