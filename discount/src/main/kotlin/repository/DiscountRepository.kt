package com.svebrant.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.model.Filters.and
import com.svebrant.exception.DuplicateEntryException
import com.svebrant.exception.ValidationErrorException
import com.svebrant.model.DiscountRequest
import com.svebrant.model.validate
import com.svebrant.repository.dto.DiscountDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent

class DiscountRepository(
    private val discountsCollection: MongoCollection<DiscountDto>,
) : KoinComponent {
    suspend fun save(discountRequest: DiscountRequest): DiscountDto? {
        try {
            discountRequest.validate()
            val discountToInsert =
                DiscountDto(
                    productId = discountRequest.productId,
                    discountId = discountRequest.discountId,
                    percent = discountRequest.percent,
                )

            log.debug { "Saving $discountRequest" }
            val result = discountsCollection.insertOne(discountToInsert)

            val created = result.insertedId?.let { discountToInsert.copy(id = it.asObjectId().value) }

            return created
        } catch (e: IllegalArgumentException) {
            throw ValidationErrorException(
                "validation error for discount with with compound key \"${discountRequest.productId}-${discountRequest.discountId} : ${e.message}\"",
            )
        } catch (e: Exception) {
            if (e.message?.contains("E11000 duplicate key error") == true) {
                throw DuplicateEntryException(
                    "duplicate discount with compound key \"${discountRequest.productId}-${discountRequest.discountId}\"",
                )
            }
            log.error(e) { "Error saving discount $discountRequest" }
            return null
        }
    }

    suspend fun findById(id: String): DiscountDto? {
        val objectId = ObjectId(id)
        log.debug { "Getting discount with id $id" }
        return discountsCollection.find(eq("_id", objectId)).firstOrNull()
    }

    suspend fun findByProductId(productId: String): List<DiscountDto> {
        log.debug { "Getting discounts with productId:  $productId" }
        return discountsCollection
            .find(eq("productId", productId))
            .sort(org.bson.Document("percent", -1))
            .toList()
    }

    suspend fun findByProductIds(productIds: Set<String>): Map<String, List<DiscountDto>> {
        log.debug { "Getting discounts with ${productIds.size} productIds" }
        return discountsCollection
            .find(`in`("productId", productIds))
            .sort(org.bson.Document("percent", -1))
            .toList()
            .groupBy { it.productId }
    }

    suspend fun find(
        productId: String?,
        discountId: String?,
        limit: Int = 20,
        offset: Int = 0,
        sortOrder: String = "ASC",
    ): List<DiscountDto> {
        log.debug { "Getting discounts for limit $limit, offset $offset" }

        val filters = mutableListOf<org.bson.conversions.Bson>()
        if (productId != null) filters += eq("productId", productId)
        if (discountId != null) filters += eq("discountId", discountId)
        val combinedFilter = if (filters.isEmpty()) Filters.empty() else and(filters)

        val sortDirection = if (sortOrder.equals("DESC", ignoreCase = true)) -1 else 1

        return discountsCollection
            .find(combinedFilter)
            .sort(org.bson.Document("discountId", sortDirection))
            .skip(offset)
            .limit(limit)
            .toList()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
