package com.svebrant.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class ProductService : KoinComponent {
    suspend fun getProducts(): List<String> {
        log.info { "Retrieving products" }
        return listOf("Product1", "Product2", "Product3")
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
