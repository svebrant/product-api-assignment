package com.svebrant.service

import com.svebrant.model.Country
import com.svebrant.model.Product
import com.svebrant.repository.ProductRepository
import com.svebrant.repository.dto.ProductDto
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
        val result: Product? = repository.findById(id)?.mapToResponse()
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun getByProductId(id: String): Product? {
        log.info { "Retrieving product by id: $id" }
        val result: Product? = repository.findByProductId(id)?.mapToResponse()
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun getByCountry(country: Country): List<Product> {
        log.info { "Retrieving product by country: $country" }
        val result = repository.findByCountry(country).map { it.mapToResponse() }
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun createProduct(product: Product): String? {
        log.info { "Creating product: $product" }
        val saved = repository.save(product)
        log.info { "Saved product: $saved" }
        return saved?.productId
    }

    private fun ProductDto.mapToResponse(): Product = Product(this.productId, this.name, this.basePrice, this.country, this.taxedPrice)

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
