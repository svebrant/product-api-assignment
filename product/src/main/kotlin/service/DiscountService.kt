package com.svebrant.service

import com.svebrant.client.DiscountClient
import com.svebrant.model.discount.DiscountRequest
import com.svebrant.model.discount.validate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class DiscountService(
    private val discountClient: DiscountClient,
) : KoinComponent {
    suspend fun create(discount: DiscountRequest): String {
        discount.validate()
        val saved = discountClient.createRequest(discount)
        // log.info { "Saved product: $saved" }
        return saved
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
