package com.svebrant.service

import com.svebrant.configuration.VAT_RATES
import com.svebrant.model.product.Country
import com.svebrant.model.product.ProductRequest
import com.svebrant.model.product.ProductResponse
import com.svebrant.repository.ProductRepository
import com.svebrant.repository.dto.ProductDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class ProductService(
    private val repository: ProductRepository,
) : KoinComponent {
    suspend fun getProducts(
        country: Country? = null,
        limit: Int = 20,
        offset: Int = 0,
        sortOrder: String = "ASC",
    ): List<ProductResponse> {
        log.info { "Retrieving products${country?.let { " for country: $it" } ?: ""}, limit $limit, offset $offset" }
        val result =
            repository
                .find(country = country, limit = limit, offset = offset, sortOrder = sortOrder)
                .map { it.mapToResponse() }
        log.info { "Found products: $result" }
        return result
    }

    suspend fun getById(id: String): ProductResponse? {
        log.info { "Retrieving product by id: $id" }
        val result: ProductResponse? = repository.findById(id)?.mapToResponse()
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun getByProductId(id: String): ProductResponse? {
        log.info { "Retrieving product by id: $id" }
        val result: ProductResponse? = repository.findByProductId(id)?.mapToResponse()
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun createProduct(productRequest: ProductRequest): String? {
        log.info { "Creating product: $productRequest" }
        val saved = repository.save(productRequest)
        log.info { "Saved product: $saved" }
        return saved?.productId
    }

    // TODO apply discount ontop of the taxedPrice. discount must be retrieved from discount service
    private fun ProductDto.mapToResponse(): ProductResponse {
        val country = Country.valueOf(this.country)
        val vat: Double = VAT_RATES[country] ?: throw IllegalArgumentException("No VAT rate for country $country")
        val taxedPrice = basePrice * (1.0 + vat)
        return ProductResponse(this.productId, this.name, this.basePrice, country, taxedPrice = taxedPrice)
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
