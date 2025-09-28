package com.svebrant.configuration

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.repository.dto.DiscountDto
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val MONGO_DISCOUNT_COLLECTION = "discountCollection"

fun mongoModule(mongoDbConnectionString: String) =
    module {
        single<MongoClient> {
            val settings =
                MongoClientSettings
                    .builder()
                    .applyConnectionString(ConnectionString(mongoDbConnectionString))
                    .build()
            MongoClient.create(settings)
        }
        single<MongoCollection<DiscountDto>>(named(MONGO_DISCOUNT_COLLECTION)) {
            val client: MongoClient = get()
            val database = client.getDatabase("discounts_db")
            val collection = database.getCollection<DiscountDto>("discounts")

            runBlocking {
                migrateDiscounts(collection)
            }

            collection
        }
    }

private suspend fun migrateDiscounts(collection: MongoCollection<DiscountDto>) {
    // Create a compound index on both productId and discountId
    collection.createIndex(
        org.bson.Document().apply {
            append("productId", 1)
            append("discountId", 1)
        },
        com.mongodb.client.model
            .IndexOptions()
            .unique(true),
    )

    // Additional indexes to optimize queries
    collection.createIndex(
        org.bson.Document("productId", 1),
    )
}
