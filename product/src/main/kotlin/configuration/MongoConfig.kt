package com.svebrant.configuration

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.model.Product
import org.koin.dsl.module

val mongoModule =
    module {
        single<MongoCollection<Product>> {
            val settings =
                MongoClientSettings
                    .builder()
                    .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
                    .build()
            val client = MongoClient.create(settings)
            val database = client.getDatabase("products_db")
            database.getCollection<Product>("products")
        }
    }
