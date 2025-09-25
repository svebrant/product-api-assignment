package com.svebrant.configuration

import com.svebrant.service.ProductService
import io.ktor.server.application.Application
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun Application.configureDependencyInjection() {
    val productModule =
        module {
            single { ProductService() }
        }

    startKoin {
        modules(productModule)
    }
}
