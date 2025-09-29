package service

import com.svebrant.model.product.Country
import com.svebrant.repository.ProductRepository
import com.svebrant.repository.dto.ProductDto
import com.svebrant.service.DiscountService
import com.svebrant.service.ProductService
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class ProductServiceTest : KoinTest {
    private val repository = mockk<ProductRepository>(relaxed = true)
    private val discountService = mockk<DiscountService>(relaxed = true)

    private val productService: ProductService by inject()

    val productId = "product-1"

    @BeforeEach
    fun setUp() {
        stopKoin() // Stop any previous Koin context
        startKoin {
            modules(
                module {
                    single { ProductService(discountService, repository) }
                },
            )
        }
    }

    @Nested inner class GetById {
        @Test
        fun `get product by id`() =
            runTest {
                // Given
                coEvery { repository.findByProductId(productId) } returns
                    ProductDto(
                        productId = productId,
                        id = ObjectId(),
                        name = "Test Product",
                        basePrice = 100.0,
                        country = Country.SWEDEN.name,
                    )
                coEvery { discountService.getDiscountsForProduct(productId) } returns emptyList()

                // When
                val product = productService.getByProductId(productId)

                // Then
                product shouldNotBe null

                coVerify(exactly = 1) { repository.findByProductId(productId) }
                coVerify(exactly = 1) { discountService.getDiscountsForProduct(productId) }
            }
    }
}
