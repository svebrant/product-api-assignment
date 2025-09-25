package service

import com.svebrant.service.ProductService
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class ProductServiceTest : KoinTest {
    val productService: ProductService by inject()

    @BeforeEach
    fun setUp() {
        stopKoin() // Stop any previous Koin context
        startKoin {
            modules(
                module {
                    single { ProductService() }
                },
            )
        }
    }

    @Test
    fun `retrieves products`() =
        runTest {
            // Given

            // When
            val products = productService.getProducts()

            // Then
            products.shouldNotBeEmpty()
        }
}
