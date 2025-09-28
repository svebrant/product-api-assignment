package com.svebrant.service

import com.svebrant.client.DiscountClient
import com.svebrant.exception.ValidationErrorException
import com.svebrant.model.discount.BatchDiscountApplicationResponse
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
            log.info { "Creating discount: $discount" }
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

    /**
     * Process a batch of discounts in a single API call
     * @param discounts The list of discount requests to process
     * @return BatchDiscountApplicationResponse with details on successes and failures
     */
    suspend fun createBatch(discounts: List<DiscountApiRequest>): BatchDiscountApplicationResponse {
        if (discounts.isEmpty()) {
            log.info { "No discounts to process in batch" }
            return BatchDiscountApplicationResponse(
                results = emptyList(),
                summary =
                    com.svebrant.model.discount
                        .BatchSummary(0, 0, 0, 0),
            )
        }

        log.info { "Processing batch of ${discounts.size} discounts" }

        // Validate all discounts before sending
        val validationErrors = mutableListOf<Pair<DiscountApiRequest, String>>()
        val validDiscounts =
            discounts.filter { discount ->
                try {
                    discount.validate()
                    true
                } catch (e: IllegalArgumentException) {
                    validationErrors.add(discount to (e.message ?: "Unknown validation error"))
                    false
                }
            }

        if (validationErrors.isNotEmpty()) {
            log.warn { "${validationErrors.size} discounts failed validation and will be excluded from batch" }
        }

        return try {
            val response = discountClient.createBatchRequest(validDiscounts)
            log.info {
                "Batch processing complete: ${response.summary.successful} successful, " +
                    "${response.summary.failed} failed, ${response.summary.alreadyApplied} already applied"
            }
            response
        } catch (e: Exception) {
            log.error(e) { "Error processing discount batch: ${e.message}" }
            throw e
        }
    }

    suspend fun getDiscountsForProduct(productId: String): List<DiscountResponse> {
        log.info { "Getting discounts for product: $productId" }
        return try {
            val discounts = discountClient.getDiscounts(productId)
            log.info { "Found ${discounts.size} discounts for product $productId" }
            discounts
        } catch (e: Exception) {
            log.error(e) { "Error retrieving discounts for product $productId: ${e.message}" }
            emptyList()
        }
    }

    suspend fun getDiscountsForProduct(productIds: Set<String>): Map<String, List<DiscountResponse>> {
        log.info { "Getting discounts for ${productIds.size} productIds" }
        return try {
            val discounts = discountClient.getDiscountsByProductIds(productIds)
            discounts
        } catch (e: Exception) {
            log.error(e) { "Error retrieving discounts for ${productIds.size} productIds: ${e.message}" }
            emptyMap()
        }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
