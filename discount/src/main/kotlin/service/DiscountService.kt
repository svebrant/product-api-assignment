package com.svebrant.service

import com.svebrant.exception.DuplicateEntryException
import com.svebrant.exception.ValidationErrorException
import com.svebrant.model.BatchDiscountApplicationResponse
import com.svebrant.model.BatchDiscountRequest
import com.svebrant.model.BatchSummary
import com.svebrant.model.DiscountApplicationResponse
import com.svebrant.model.DiscountApplicationResult
import com.svebrant.model.DiscountRequest
import com.svebrant.model.DiscountResponse
import com.svebrant.repository.DiscountRepository
import com.svebrant.repository.dto.DiscountDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class DiscountService(
    private val repository: DiscountRepository,
) : KoinComponent {
    suspend fun getDiscounts(
        productId: String?,
        discountId: String?,
        limit: Int = 20,
        offset: Int = 0,
        sortOrder: String = "ASC",
    ): List<DiscountResponse> {
        log.info { "Retrieving products, limit $limit, offset $offset" }
        val result =
            repository
                .find(productId, discountId, limit = limit, offset = offset, sortOrder = sortOrder)
                .map { it.mapToResponse() }
        log.info { "Found products: $result" }
        return result
    }

    suspend fun getById(id: String): DiscountResponse? {
        log.info { "Retrieving discount by id: $id" }
        val result: DiscountResponse? = repository.findById(id)?.mapToResponse()
        log.info { "Found discount by id: $result" }
        return result
    }

    suspend fun getByProductId(productId: String): List<DiscountResponse> {
        log.info { "Retrieving discounts by productId: $productId" }
        val result = repository.findByProductId(productId).map { it.mapToResponse() }
        log.info { "Found ${result.size} discounts by productId: $productId" }
        return result
    }

    suspend fun getByProductIds(productIds: Set<String>): Map<String, List<DiscountResponse>> {
        log.info { "Retrieving discounts by productId: $productIds" }
        val result =
            repository
                .findByProductIds(productIds)
                .mapValues { (_, discounts) -> discounts.map { it.mapToResponse() } }
        log.info { "Found ${result.values.sumOf { it.size }} discounts for ${productIds.size} productIds:" }
        return result
    }

    suspend fun applyDiscount(discountRequest: DiscountRequest): DiscountApplicationResponse =
        try {
            val saved: DiscountDto? = repository.save(discountRequest)
            DiscountApplicationResponse(applied = saved != null, alreadyApplied = false)
        } catch (e: DuplicateEntryException) {
            DiscountApplicationResponse(applied = false, alreadyApplied = true)
        } catch (e: ValidationErrorException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error creating discount: ${e.message}" }
            throw e
        }

    suspend fun applyDiscountBatch(batchRequest: BatchDiscountRequest): BatchDiscountApplicationResponse {
        log.info { "Processing batch discount application with ${batchRequest.discounts.size} discounts" }

        val results = mutableListOf<DiscountApplicationResult>()
        var successful = 0
        var failed = 0
        var alreadyApplied = 0

        // Process each discount in the batch
        for (request in batchRequest.discounts) {
            try {
                val result = applyDiscountWithResult(request)
                results.add(result)

                when {
                    result.success -> successful++
                    result.alreadyApplied -> alreadyApplied++
                    else -> failed++
                }
            } catch (e: Exception) {
                log.error(e) { "Error processing discount: $request" }
                val errorResult =
                    DiscountApplicationResult(
                        productId = request.productId,
                        discountId = request.discountId,
                        success = false,
                        alreadyApplied = false,
                        error = e.message,
                    )
                results.add(errorResult)
                failed++
            }
        }

        val summary =
            BatchSummary(
                total = batchRequest.discounts.size,
                successful = successful,
                failed = failed,
                alreadyApplied = alreadyApplied,
            )

        log.info { "Batch processing complete. Summary: $summary" }
        return BatchDiscountApplicationResponse(results, summary)
    }

    private suspend fun applyDiscountWithResult(request: DiscountRequest): DiscountApplicationResult =
        try {
            val response = applyDiscount(request)
            DiscountApplicationResult(
                productId = request.productId,
                discountId = request.discountId,
                success = response.applied,
                alreadyApplied = response.alreadyApplied,
            )
        } catch (e: Exception) {
            DiscountApplicationResult(
                productId = request.productId,
                discountId = request.discountId,
                success = false,
                alreadyApplied = false,
                error = e.message,
            )
        }

    private fun DiscountDto.mapToResponse(): DiscountResponse = DiscountResponse(this.productId, this.discountId, this.percent)

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
