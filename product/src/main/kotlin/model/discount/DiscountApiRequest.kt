package com.svebrant.model.discount

import kotlinx.serialization.Serializable

@Serializable
data class DiscountApiRequest(
    val productId: String,
    val discountId: String,
    val percent: Double,
)

fun DiscountApiRequest.validate() {
    require(productId.isNotBlank()) { "productId must not be blank" }
    require(discountId.isNotBlank()) { "discountId must not be blank" }
    require(percent in 0.0..100.0) { "percent must be within range 0.0 and 100.0" }
}
