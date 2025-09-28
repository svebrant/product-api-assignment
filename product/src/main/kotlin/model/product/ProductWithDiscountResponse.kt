package com.svebrant.model.product

import kotlinx.serialization.Serializable

@Serializable
data class ProductWithDiscountResponse(
    val productId: String,
    val discountId: String,
    val percent: Double,
    val applied: Boolean,
    val alreadyApplied: Boolean,
)
