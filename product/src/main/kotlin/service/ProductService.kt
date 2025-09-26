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
    suspend fun getProducts(country: Country? = null): List<Product> {
        log.info { "Retrieving products${country?.let { " for country: $it" } ?: ""}" }
        val result = repository.find(country = country).map { it.mapToResponse() }
        log.info { "Found products: $result" }
        return result
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

    suspend fun createProduct(product: Product): String? {
        log.info { "Creating product: $product" }
        val saved = repository.save(product)
        log.info { "Saved product: $saved" }
        return saved?.productId
    }

    private fun ProductDto.mapToResponse(): Product =
        Product(this.productId, this.name, this.basePrice, Country.valueOf(this.country), this.taxedPrice)

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
