package com.svebrant.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Product(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val name: String,
    val basePrice: Double,
    val country: String,
    val taxedPrice: Double,
)
