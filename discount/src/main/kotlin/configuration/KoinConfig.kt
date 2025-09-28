package com.svebrant.configuration

import com.svebrant.repository.DiscountRepository
import com.svebrant.service.DiscountService
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.configureDependencyInjection(appConfig: ApplicationConfig) {
    val mongoDbConnectionString = System.getenv("mongodb.uri") ?: appConfig.property("mongodb.uri").getString()

    val serviceModule =
        module {
            single { DiscountService(get()) }
        }

    val repositoryModule =
        module {
            single { DiscountRepository(get(named(MONGO_DISCOUNT_COLLECTION))) }
        }

    startKoin {
        modules(mongoModule(mongoDbConnectionString), repositoryModule, serviceModule)
    }
}
