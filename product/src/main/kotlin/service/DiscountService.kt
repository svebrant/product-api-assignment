package com.svebrant.service

import com.svebrant.client.DiscountClient
import com.svebrant.exception.ValidationErrorException
import com.svebrant.model.discount.DiscountApiRequest
import com.svebrant.model.discount.DiscountApplicationResponse
import com.svebrant.model.discount.DiscountResponse
import com.svebrant.model.discount.validate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class DiscountService(
    private val discountClient: DiscountClient,
) : KoinComponent {
    suspend fun create(discount: DiscountApiRequest): DiscountApplicationResponse {
        try {
            discount.validate()
            log.debug { "Creating discount: $discount" }
            return try {
                val saved = discountClient.createRequest(discount)
                saved
            } catch (e: Exception) {
                log.error(e) { "Error creating discount: ${e.message}" }
                throw e
            }
        } catch (e: IllegalArgumentException) {
            throw ValidationErrorException(
                "validation error for discount with compose id ${discount.productId}-${discount.discountId} : ${e.message}",
            )
        } catch (e: Exception) {
            log.error(e) { "Error creating discount: ${e.message}" }
            throw e
        }
    }

    suspend fun getDiscountsForProduct(productId: String): List<DiscountResponse> {
        log.debug { "Getting discounts for product: $productId" }
        return try {
            val discounts = discountClient.getDiscounts(productId)
            log.debug { "Found ${discounts.size} discounts for product $productId" }
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
