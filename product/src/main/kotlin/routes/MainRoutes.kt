package com.svebrant.routes

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.mainRoutes() {
    get("/") {
        call.respondText("Product service is running.")
    }
}
