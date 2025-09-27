package com.svebrant.configuration

import com.svebrant.client.DiscountClient
import com.svebrant.repository.IngestRepository
import com.svebrant.repository.ProductRepository
import com.svebrant.service.DiscountService
import com.svebrant.service.IngestService
import com.svebrant.service.ProductService
import io.ktor.server.application.Application
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.configureDependencyInjection(
    productsIngestFilePath: String,
    discountsIngestFilePath: String,
    mongoDbConnectionString: String,
) {
    val clientModule =
        module {
            single { DiscountClient() }
        }

    val serviceModule =
        module {
            single { ProductService(get()) }
            single { DiscountService(get()) }
            single { IngestService(get(), get(), get(), productsIngestFilePath, discountsIngestFilePath) }
        }

    val repositoryModule =
        module {
            single { ProductRepository(get(named(MONGO_PRODUCT_COLLECTION))) }
            single { IngestRepository(get(named(MONGO_INGEST_COLLECTION))) }
        }

    startKoin {
        modules(mongoModule(mongoDbConnectionString), repositoryModule, serviceModule, clientModule)
    }
}
