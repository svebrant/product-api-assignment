package com.svebrant.configuration

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.repository.dto.IngestionDto
import com.svebrant.repository.dto.ProductDto
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val MONGO_PRODUCT_COLLECTION = "productCollection"
const val MONGO_INGEST_COLLECTION = "ingestCollection"

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
        single<MongoCollection<ProductDto>>(named(MONGO_PRODUCT_COLLECTION)) {
            val client: MongoClient = get()
            val database = client.getDatabase("products_db")
            val collection = database.getCollection<ProductDto>("products")

            runBlocking {
                migrateProducts(collection)
            }

            collection
        }
        single<MongoCollection<IngestionDto>>(named(MONGO_INGEST_COLLECTION)) {
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
