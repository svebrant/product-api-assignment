package com.svebrant.model.discount

import kotlinx.serialization.Serializable

@Serializable
data class BatchDiscountRequest(
    val discounts: List<DiscountApiRequest>,
)

@Serializable
data class BatchDiscountApplicationResponse(
    val results: List<DiscountApplicationResult>,
    val summary: BatchSummary,
)

@Serializable
data class DiscountApplicationResult(
    val productId: String,
    val discountId: String,
    val success: Boolean,
    val alreadyApplied: Boolean,
    val error: String? = null,
)

@Serializable
data class BatchSummary(
    val total: Int,
    val successful: Int,
    val failed: Int,
    val alreadyApplied: Int,
)
