package com.svebrant.service

import com.svebrant.client.DiscountClient
import com.svebrant.model.discount.DiscountApplicationResponse
import com.svebrant.model.discount.DiscountRequest
import com.svebrant.model.discount.DiscountResponse
import com.svebrant.model.discount.validate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class DiscountService(
    private val discountClient: DiscountClient,
) : KoinComponent {
    suspend fun create(discount: DiscountRequest): DiscountApplicationResponse {
        discount.validate()
        log.info { "Creating discount: $discount" }
        return try {
            val saved = discountClient.createRequest(discount)
            log.info { "Discount application result: $saved" }
            saved
        } catch (e: Exception) {
            log.error(e) { "Error creating discount: ${e.message}" }
            throw e
        }
    }

    /**
     * Retrieves all discounts for a specific product from the discount service
     */
    suspend fun getDiscountsForProduct(productId: String): List<DiscountResponse> {
        log.info { "Getting discounts for product: $productId" }
        return try {
            val discounts = discountClient.getDiscounts(productId)
            log.info { "Found ${discounts.size} discounts for product $productId" }
            discounts
        } catch (e: Exception) {
            log.error(e) { "Error retrieving discounts for product $productId: ${e.message}" }
            // Return empty list in case of error to avoid cascading failures
            emptyList()
        }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
