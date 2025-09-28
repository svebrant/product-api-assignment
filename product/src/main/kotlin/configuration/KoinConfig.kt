package com.svebrant.configuration

import com.svebrant.client.DiscountClient
import com.svebrant.repository.IngestRepository
import com.svebrant.repository.ProductRepository
import com.svebrant.service.DiscountService
import com.svebrant.service.IngestService
import com.svebrant.service.ProductService
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.configureDependencyInjection(
    appConfig: ApplicationConfig,
    bearerToken: String,
) {
    val productsIngestFilePath = appConfig.property("ingest.productsFilePath").getString()
    val discountsIngestFilePath = appConfig.property("ingest.discountsFilePath").getString()
    val mongoDbConnectionString = System.getenv("mongodb.uri") ?: appConfig.property("mongodb.uri").getString()

    val discountServiceUrl =
        System.getenv("http.discount-client.base-url")
            ?: appConfig.property("http.discount-client.base-url").getString()

    val httpClientModule =
        module {
            single {
                configureHttpClient(bearerToken)
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
