package com.svebrant.configuration

import com.svebrant.repository.IngestRepository
import com.svebrant.repository.ProductRepository
import com.svebrant.service.IngestService
import com.svebrant.service.ProductService
import io.ktor.server.application.Application
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun Application.configureDependencyInjection() {
    val serviceModule =
        module {
            single { ProductService(get()) }
            single { IngestService(get(), get()) }
        }

    val repositoryModule =
        module {
            single { ProductRepository(get(), get()) }
            single { IngestRepository(get(), get()) }
        }

    startKoin {
        modules(mongoModule, repositoryModule, serviceModule)
    }
}
