package com.svebrant.model.product

import com.svebrant.model.discount.DiscountResponse
import kotlinx.serialization.Serializable

@Serializable
data class ProductResponse(
    val id: String,
    val name: String,
    val basePrice: Double,
    val country: Country,
    val taxedPrice: Double,
    val appliedDiscounts: List<DiscountResponse> = emptyList(), // TODO just temporary for debugging, remove later
)
