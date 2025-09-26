package com.svebrant.routes

import com.svebrant.model.Country
import com.svebrant.model.ProductRequest
import com.svebrant.model.validate
import com.svebrant.service.ProductService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject
import kotlin.getValue

fun Route.productRoutes() {
    val productService: ProductService by inject<ProductService>()

    get("/products") {
        val countryParam =
            call.request.queryParameters["country"]?.let { country ->
                try {
                    Country.valueOf(country.uppercase())
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid country $country")
                }
            }
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val sortOrder = call.request.queryParameters["sortOrder"] ?: "ASC"

        val products =
            productService.getProducts(
                country = countryParam,
                limit = limit,
                offset = offset,
                sortOrder = sortOrder,
            )
        call.respond(products)
    }

    get("/products/{id}") {
        val id =
            call.parameters["id"] ?: return@get call.respondText(
                "Missing id",
                status = io.ktor.http.HttpStatusCode.BadRequest,
            )
        val product = productService.getByProductId(id)
        if (product != null) {
            call.respond(product)
        } else {
            call.respondText("Product not found", status = io.ktor.http.HttpStatusCode.NotFound)
        }
    }

    post("/products") {
        try {
            val productRequest = call.receive<ProductRequest>()
            productRequest.validate()

            println("Received product: $productRequest")
            val productId = productService.createProduct(productRequest)

            if (productId == null) {
                return@post call.respondText(
                    "Failed to save product",
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                )
            }

            call.respond(productId)
        } catch (e: IllegalArgumentException) {
            call.respondText(
                e.message ?: "Invalid request",
                status = io.ktor.http.HttpStatusCode.BadRequest,
            )
        }
    }
}
