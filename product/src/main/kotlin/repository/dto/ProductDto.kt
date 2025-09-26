package com.svebrant.repository.dto

import com.svebrant.model.Country
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
    val country: Country,
    val taxedPrice: Double,
)
