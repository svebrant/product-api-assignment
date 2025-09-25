package com.svebrant.routes

import com.svebrant.service.ProductService
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject
import kotlin.getValue

fun Route.productRoutes() {
    val productService: ProductService by inject<ProductService>()

    get("/products") {
        val products = productService.getProducts()
        call.respondText(products.joinToString(", "))
    }
}
