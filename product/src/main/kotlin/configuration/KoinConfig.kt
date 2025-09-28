package com.svebrant.configuration

import com.svebrant.client.DiscountClient
import com.svebrant.repository.IngestRepository
import com.svebrant.repository.ProductRepository
import com.svebrant.service.DiscountService
import com.svebrant.service.IngestService
import com.svebrant.service.ProductService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.configureDependencyInjection(
    productsIngestFilePath: String,
    discountsIngestFilePath: String,
    mongoDbConnectionString: String,
) {
    val discountServiceUrl =
        System.getenv("http.discount-client.base-url")
            ?: environment.config.propertyOrNull("http.discount-client.base-url")?.getString()
            ?: throw Exception("Discount service base URL not configured")

    val expectedToken = System.getenv("AUTH_TOKEN") ?: "secret-dev-token-please-change"
    val httpClientModule =
        module {
            single {
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = false
                            },
                        )
                    }
                    install(Auth) {
                        bearer {
                            loadTokens {
                                BearerTokens(expectedToken, expectedToken)
                            }
                        }
                    }
                }
            }
        }

    val clientModule =
        module {
            single { DiscountClient(discountServiceUrl, get()) }
        }

    val serviceModule =
        module {
            single { ProductService(get(), get()) }
            single { DiscountService(get()) }
            single { IngestService(get(), get(), get(), productsIngestFilePath, discountsIngestFilePath) }
        }

    val repositoryModule =
        module {
            single { ProductRepository(get(named(MONGO_PRODUCT_COLLECTION))) }
            single { IngestRepository(get(named(MONGO_INGEST_COLLECTION))) }
        }

    startKoin {
        modules(mongoModule(mongoDbConnectionString), httpClientModule, repositoryModule, serviceModule, clientModule)
    }
}
