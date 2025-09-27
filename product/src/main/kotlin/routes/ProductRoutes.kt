package com.svebrant.routes

import com.svebrant.model.product.Country
import com.svebrant.model.product.ProductRequest
import com.svebrant.model.product.validate
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
            call.request.queryParameters[PARAM_COUNTRY]?.let { country ->
                try {
                    Country.valueOf(country.uppercase())
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid country $country")
                }
            }
        val limit = call.request.queryParameters[PARAM_LIMIT]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters[PARAM_OFFSET]?.toIntOrNull() ?: 0
        val sortOrder = call.request.queryParameters[PARAM_SORT_ORDER] ?: "ASC"

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

            val productId = productService.create(productRequest)

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
