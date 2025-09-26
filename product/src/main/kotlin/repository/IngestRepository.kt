package com.svebrant.repository

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.repository.dto.IngestionDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class IngestRepository(
    private val client: MongoClient,
    private val productsCollection: MongoCollection<IngestionDto>,
) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger { }
    }
}
