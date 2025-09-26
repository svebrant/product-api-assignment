package service

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.svebrant.repository.ProductRepository
import com.svebrant.repository.dto.ProductDto
import com.svebrant.service.ProductService
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.mockk
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
    val repository: ProductRepository by inject()

    private val mockClient = mockk<MongoClient>(relaxed = true)
    private val mockCollection = mockk<MongoCollection<ProductDto>>(relaxed = true)

    @BeforeEach
    fun setUp() {
        stopKoin() // Stop any previous Koin context
        startKoin {
            modules(
                module {
                    single { mockCollection }
                    single { mockClient }
                    single { ProductService(get()) }
                    single { ProductRepository(get(), get()) }
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
            products.shouldBeEmpty()
        }
}
