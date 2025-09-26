package com.svebrant.configuration

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.repository.dto.ProductDto
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

val mongoModule =
    module {
        single<MongoClient> {
            val settings =
                MongoClientSettings
                    .builder()
                    .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
                    .build()
            MongoClient.create(settings)
        }
        single<MongoCollection<ProductDto>> {
            val client: MongoClient = get()
            val database = client.getDatabase("products_db")
            val collection = database.getCollection<ProductDto>("products")

            runBlocking {
                migrate(collection)
            }

            collection
        }
    }

private suspend fun migrate(collection: MongoCollection<ProductDto>) {
    collection.createIndex(
        org.bson.Document("productId", 1),
        com.mongodb.client.model
            .IndexOptions()
            .unique(true),
    )
}
