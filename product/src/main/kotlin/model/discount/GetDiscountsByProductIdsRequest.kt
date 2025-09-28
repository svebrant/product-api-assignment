package com.svebrant.model.discount

data class GetDiscountsByProductIdsRequest(
    val productIds: Set<String>,
)
