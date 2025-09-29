package com.svebrant.model.discount

import kotlinx.serialization.Serializable

@Serializable
data class GetDiscountsByProductIdsRequest(
    val productIds: Set<String>,
)
