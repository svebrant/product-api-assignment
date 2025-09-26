package com.svebrant.configuration

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.repository.dto.IngestionDto
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
                migrateProducts(collection)
            }

            collection
        }
        single<MongoCollection<IngestionDto>> {
            val client: MongoClient = get()
            val database = client.getDatabase("ingestion_db")
            val collection = database.getCollection<IngestionDto>("ingestions")

            runBlocking {
                migrateIngestions(collection)
            }

            collection
        }
    }

private suspend fun migrateProducts(collection: MongoCollection<ProductDto>) {
    collection.createIndex(
        org.bson.Document("productId", 1),
        com.mongodb.client.model
            .IndexOptions()
            .unique(true),
    )
}

private suspend fun migrateIngestions(collection: MongoCollection<IngestionDto>) {
    collection.createIndex(
        org.bson.Document("ingestionId", 1),
        com.mongodb.client.model
            .IndexOptions()
            .unique(true),
    )
}
