package com.svebrant.service

import com.svebrant.model.Product
import com.svebrant.repository.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class ProductService(
    private val repository: ProductRepository,
) : KoinComponent {
    suspend fun getProducts(): List<String> {
        log.info { "Retrieving products" }
        return listOf("Product1", "Product2", "Product3")
    }

    suspend fun getById(id: String): Product? {
        log.info { "Retrieving product by id: $id" }
        val result = repository.findById(id)
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun saveProduct(name: String): Product? {
        log.info { "Saving product: $name" }
        val saved = repository.save(Product(name = name, basePrice = 15.0, country = "Sweden", taxedPrice = 12.5))
        log.info { "Saved product: $saved" }
        return saved
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
