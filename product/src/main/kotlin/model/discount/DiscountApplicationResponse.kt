package com.svebrant.model.discount

import kotlinx.serialization.Serializable

@Serializable
data class DiscountApplicationResponse(
    val applied: Boolean,
    val alreadyApplied: Boolean,
)
