package com.svebrant.service

import com.svebrant.exception.DuplicateEntryException
import com.svebrant.exception.ValidationErrorException
import com.svebrant.model.discount.DiscountRequest
import com.svebrant.model.product.Country
import com.svebrant.model.product.ProductRequest
import com.svebrant.model.product.ProductResponse
import com.svebrant.model.product.ProductWithDiscountResponse
import com.svebrant.model.product.VAT_RATES
import com.svebrant.repository.ProductRepository
import com.svebrant.repository.dto.ProductDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent

class ProductService(
    private val discountService: DiscountService,
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
        val result: ProductResponse? =
            repository.findById(id)?.let {
                mapToResponseWithDiscounts(it)
            }
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun getByProductId(id: String): ProductResponse? {
        log.info { "Retrieving product by id: $id" }
        val result: ProductResponse? =
            repository.findByProductId(id)?.let {
                mapToResponseWithDiscounts(it)
            }
        log.info { "Found product by id: $result" }
        return result
    }

    suspend fun create(productRequest: ProductRequest): String? =
        try {
            val saved = repository.save(productRequest)
            saved?.productId
        } catch (e: DuplicateEntryException) {
            throw e
        } catch (e: ValidationErrorException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error creating product: ${e.message}" }
            throw e
        }

    private suspend fun mapToResponseWithDiscounts(product: ProductDto): ProductResponse {
        // First get the base taxed price
        val country = Country.valueOf(product.country)
        val vat: Double = VAT_RATES[country] ?: throw IllegalArgumentException("No VAT rate for country $country")
        val baseTaxedPrice = product.basePrice * (1.0 + vat)

        // Get all discounts for this product
        val discounts = discountService.getDiscountsForProduct(product.productId)

        // Apply discounts
        val finalPrice =
            if (discounts.isEmpty()) {
                baseTaxedPrice
            } else {
                // Apply all discounts to the taxed price
                val totalDiscountPercent = discounts.sumOf { it.percent }.coerceAtMost(100.0)
                baseTaxedPrice * (1.0 - totalDiscountPercent / 100.0)
            }

        return ProductResponse(
            product.productId,
            product.name,
            product.basePrice,
            country,
            taxedPrice = finalPrice,
            appliedDiscounts = discounts, // TODO remove later
        )
    }

    /**
     * Maps a ProductDto to ProductResponse without retrieving discounts
     */
    private fun ProductDto.mapToResponse(): ProductResponse {
        val country = Country.valueOf(this.country)
        val vat: Double = VAT_RATES[country] ?: throw IllegalArgumentException("No VAT rate for country $country")
        val taxedPrice = basePrice * (1.0 + vat)
        return ProductResponse(this.productId, this.name, this.basePrice, country, taxedPrice = taxedPrice)
    }

    suspend fun applyDiscount(
        productId: String,
        discountRequest: DiscountRequest,
    ): ProductWithDiscountResponse {
        log.info { "Applying discount $discountRequest to product $productId" }

        // Check if product exists first
        val product =
            repository.findByProductId(productId)
                ?: throw IllegalArgumentException("No product found with id $productId")

        // Add the productId to the discount request
        val completeDiscountRequest = discountRequest.copy(productId = productId)

        // Apply discount via discount service
        val applyDiscount = discountService.create(completeDiscountRequest)

        return ProductWithDiscountResponse(
            product.productId,
            discountRequest.discountId,
            discountRequest.percent,
            applyDiscount.applied,
            applyDiscount.alreadyApplied,
        )
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
