package com.svebrant.repository

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.model.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent

class ProductRepository(
    private val client: MongoClient,
    private val productsCollection: MongoCollection<Product>,
) : KoinComponent {
    suspend fun save(product: Product): Product? {
        val session = client.startSession()

        try {
            session.startTransaction()
            log.debug { "Saving $product" }
            val result = productsCollection.insertOne(product)

            val created = result.insertedId?.let { product.copy(id = it.asObjectId().value) }

            session.commitTransaction()
            return created
        } catch (e: Exception) {
            log.error(e) { "Error saving product $product" }
            session.abortTransaction()
            return null
        }
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
