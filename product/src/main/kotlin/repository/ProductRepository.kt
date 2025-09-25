package com.svebrant.repository

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.model.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent

class ProductRepository(
    private val productsCollection: MongoCollection<Product>,
) : KoinComponent {
    suspend fun save(product: Product): Product? {
        log.debug { "Saving $product" }
        val result = productsCollection.insertOne(product)

        return result.insertedId?.let { product.copy(id = it.asObjectId().value) }
    }

    suspend fun findById(id: String): Product? {
        val objectId = ObjectId(id)
        log.debug { "Getting product with id $id" }
        return productsCollection.find(eq("_id", objectId)).firstOrNull()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
