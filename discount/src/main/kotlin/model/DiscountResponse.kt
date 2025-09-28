package com.svebrant.model

import kotlinx.serialization.Serializable

@Serializable
data class DiscountResponse(
    val productId: String,
    val discountId: String,
    val percent: Double,
)
