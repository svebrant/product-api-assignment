package com.svebrant.repository.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class DiscountDto(
    val productId: String,
    val discountId: String,
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val percent: Double,
)
