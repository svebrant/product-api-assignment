package com.svebrant.configuration

import com.svebrant.routes.discountRoutes
import com.svebrant.routes.mainRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

fun Application.configureRoutes() {
    routing {
        mainRoutes()
        authenticate("auth-bearer") {
            discountRoutes()
        }
    }
}
