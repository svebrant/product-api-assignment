package com.svebrant.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.model.Filters.and
import com.svebrant.model.Country
import com.svebrant.model.ProductRequest
import com.svebrant.repository.dto.ProductDto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent

class ProductRepository(
    private val client: MongoClient,
    private val productsCollection: MongoCollection<ProductDto>,
) : KoinComponent {
    suspend fun save(productRequest: ProductRequest): ProductDto? {
        val session = client.startSession()

        val productToInsert =
            ProductDto(
                productId = productRequest.id,
                name = productRequest.name,
                basePrice = productRequest.basePrice,
                country = productRequest.country.name,
            )

        try {
            session.startTransaction()
            log.debug { "Saving $productRequest" }
            val result = productsCollection.insertOne(productToInsert)

            val created = result.insertedId?.let { productToInsert.copy(id = it.asObjectId().value) }

            session.commitTransaction()
            return created
        } catch (e: Exception) {
            log.error(e) { "Error saving product $productRequest" }
            session.abortTransaction()
            return null
        }
    }

    suspend fun findById(id: String): ProductDto? {
        val objectId = ObjectId(id)
        log.debug { "Getting product with id $id" }
        return productsCollection.find(eq("_id", objectId)).firstOrNull()
    }

    suspend fun findByProductId(id: String): ProductDto? {
        log.debug { "Getting product with productId $id" }
        return productsCollection.find(eq("productId", id)).firstOrNull()
    }

    suspend fun find(
        country: Country? = null,
        limit: Int = 20,
        offset: Int = 0,
        sortOrder: String = "ASC",
    ): List<ProductDto> {
        log.debug { "Getting products for country $country, limit $limit, offset $offset" }

        val filters = mutableListOf<org.bson.conversions.Bson>()
        if (country != null) filters += eq("country", country.name)
        val combinedFilter = if (filters.isEmpty()) Filters.empty() else and(filters)

        val sortDirection = if (sortOrder.equals("DESC", ignoreCase = true)) -1 else 1

        return productsCollection
            .find(combinedFilter)
            .sort(org.bson.Document("productId", sortDirection))
            .skip(offset)
            .limit(limit)
            .toList()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
