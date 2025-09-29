package com.svebrant.client

import com.svebrant.configuration.HEADER_REQUEST_ID
import com.svebrant.configuration.MDC_KEY_REQUEST_ID
import com.svebrant.model.discount.BatchDiscountApplicationResponse
import com.svebrant.model.discount.BatchDiscountRequest
import com.svebrant.model.discount.DiscountApiRequest
import com.svebrant.model.discount.DiscountApplicationResponse
import com.svebrant.model.discount.DiscountResponse
import com.svebrant.model.discount.GetDiscountsByProductIdsRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.koin.core.component.KoinComponent
import org.slf4j.MDC

class DiscountClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
) : KoinComponent {
    suspend fun createRequest(discount: DiscountApiRequest): DiscountApplicationResponse {
        log.info { "Applying discount: $discount to the discount service" }
        try {
            val response =
                httpClient.put("$baseUrl/discounts/apply") {
                    contentType(ContentType.Application.Json)
                    setBody(discount)
                    header(HEADER_REQUEST_ID, MDC.get(MDC_KEY_REQUEST_ID))
                }
            return response.body()
        } catch (e: Exception) {
            log.error(e) { "Error applying discount: ${e.message}" }
            throw e
        }
    }

    suspend fun createBatchRequest(discounts: List<DiscountApiRequest>): BatchDiscountApplicationResponse {
        if (discounts.isEmpty()) {
            log.info { "No discounts to apply in batch" }
            return BatchDiscountApplicationResponse(
                emptyList(),
                com.svebrant.model.discount
                    .BatchSummary(0, 0, 0, 0),
            )
        }

        log.debug { "Applying batch of ${discounts.size} discounts to the discount service" }
        try {
            val batchRequest = BatchDiscountRequest(discounts)
            val response =
                httpClient.post("$baseUrl/discounts/apply/batch") {
                    contentType(ContentType.Application.Json)
                    setBody(batchRequest)
                    header(HEADER_REQUEST_ID, MDC.get(MDC_KEY_REQUEST_ID))
                }
            val result: BatchDiscountApplicationResponse = response.body()
            log.debug { "Batch discount application result: ${result.summary}" }
            return result
        } catch (e: Exception) {
            log.error(e) { "Error applying batch of ${discounts.size} discounts: ${e.message}" }
            throw e
        }
    }

    suspend fun getDiscounts(productId: String): List<DiscountResponse> {
        log.info { "Retrieving discounts for product: $productId from the discount service" }
        try {
            val response =
                httpClient.get("$baseUrl/discounts/$productId") {
                    header(HEADER_REQUEST_ID, MDC.get(MDC_KEY_REQUEST_ID))
                }
            return response.body()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun getDiscountsByProductIds(productIds: Set<String>): Map<String, List<DiscountResponse>> {
        log.info { "Retrieving discounts for product: $productIds from the discount service" }
        try {
            val request = GetDiscountsByProductIdsRequest(productIds)
            val response =
                httpClient.get("$baseUrl/discounts/byProductIds") {
                    header(HEADER_REQUEST_ID, MDC.get(MDC_KEY_REQUEST_ID))
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            return response.body()
        } catch (e: Exception) {
            return emptyMap()
        }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
