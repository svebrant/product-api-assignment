package com.svebrant.repository

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.model.Country
import com.svebrant.model.Product
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
    suspend fun save(product: Product): ProductDto? {
        val session = client.startSession()

        val productToInsert =
            ProductDto(
                productId = product.id,
                name = product.name,
                basePrice = product.basePrice,
                country = product.country,
                taxedPrice = product.taxedPrice,
            )

        try {
            session.startTransaction()
            log.debug { "Saving $product" }
            val result = productsCollection.insertOne(productToInsert)

            val created = result.insertedId?.let { productToInsert.copy(id = it.asObjectId().value) }

            session.commitTransaction()
            return created
        } catch (e: Exception) {
            log.error(e) { "Error saving product $product" }
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

    suspend fun findByCountry(country: Country): List<ProductDto> {
        log.debug { "Getting products for country $country" }
        return productsCollection.find(eq("country", country.name)).toList()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
