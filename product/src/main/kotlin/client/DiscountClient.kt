package com.svebrant.client

import com.svebrant.model.discount.DiscountRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class DiscountClient : KoinComponent {
    suspend fun createRequest(discount: DiscountRequest): String = discount.discountId

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
