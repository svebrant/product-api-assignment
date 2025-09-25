package com.svebrant.configuration

import com.svebrant.repository.ProductRepository
import com.svebrant.service.ProductService
import io.ktor.server.application.Application
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun Application.configureDependencyInjection() {
    val serviceModule =
        module {
            single { ProductService(get()) }
        }

    val repositoryModule =
        module {
            single { ProductRepository(get()) }
        }

    startKoin {
        modules(mongoModule, repositoryModule, serviceModule)
    }
}
