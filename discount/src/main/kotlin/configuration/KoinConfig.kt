package com.svebrant.configuration

import com.svebrant.repository.DiscountRepository
import com.svebrant.service.DiscountService
import io.ktor.server.application.Application
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun Application.configureDependencyInjection(mongoDbConnectionString: String) {
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
