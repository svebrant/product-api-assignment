package com.svebrant.model

data class GetDiscountsByProductIdsRequest(
    val productIds: Set<String>,
)
