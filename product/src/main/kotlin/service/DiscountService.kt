package com.svebrant.service

import com.svebrant.client.DiscountClient
import com.svebrant.exception.ValidationErrorException
import com.svebrant.model.discount.BatchDiscountApplicationResponse
import com.svebrant.model.discount.DiscountApiRequest
import com.svebrant.model.discount.DiscountApplicationResponse
import com.svebrant.model.discount.DiscountApplicationResult
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

        val apiResponse =
            try {
                if (validDiscounts.isNotEmpty()) {
                    discountClient.createBatchRequest(validDiscounts)
                } else {
                    BatchDiscountApplicationResponse(
                        results = emptyList(),
                        summary =
                            com.svebrant.model.discount
                                .BatchSummary(0, 0, 0, 0),
                    )
                }
            } catch (e: Exception) {
                log.error(e) { "Error processing discount batch: ${e.message}" }
                throw e
            }

        val validationResults =
            validationErrors.map { (discount, errorMessage) ->
                DiscountApplicationResult(
                    productId = discount.productId,
                    discountId = discount.discountId,
                    success = false,
                    alreadyApplied = false,
                    error = "Validation error: $errorMessage",
                )
            }

        val combinedResults = apiResponse.results + validationResults
        val combinedSummary =
            com.svebrant.model.discount.BatchSummary(
                total = apiResponse.summary.total + validationErrors.size,
                successful = apiResponse.summary.successful,
                failed = apiResponse.summary.failed + validationErrors.size,
                alreadyApplied = apiResponse.summary.alreadyApplied,
            )

        log.info {
            "Batch processing complete: ${combinedSummary.successful} successful, " +
                "${combinedSummary.failed} failed, ${combinedSummary.alreadyApplied} already applied"
        }

        return BatchDiscountApplicationResponse(
            results = combinedResults,
            summary = combinedSummary,
        )
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

    suspend fun getDiscountsForProducts(productIds: Set<String>): Map<String, List<DiscountResponse>> {
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
