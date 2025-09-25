package com.svebrant.configuration

import com.svebrant.routes.mainRoutes
import com.svebrant.routes.productRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRoutes() {
    routing {
        mainRoutes()
        productRoutes()
    }
}
