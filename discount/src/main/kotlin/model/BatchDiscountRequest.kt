package com.svebrant.model

import kotlinx.serialization.Serializable

@Serializable
data class BatchDiscountRequest(
    val discounts: List<DiscountRequest>,
)

fun BatchDiscountRequest.validate() {
    require(discounts.isNotEmpty()) { "Batch cannot be empty" }
    discounts.forEach { it.validate() }
}
