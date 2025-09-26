package com.svebrant.routes

import com.svebrant.model.Country
import com.svebrant.model.Product
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
        val params = call.request.queryParameters

        if (params.get("country") != null) {
            val country = Country.valueOf(params.get("country")!!.uppercase())
            val products = productService.getByCountry(country)
            return@get call.respond(products)
        }

        val products = productService.getProducts()
        call.respondText(products.joinToString(", "))
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
        val productRequest = call.receive<Product>()
        println("Received product: $productRequest")
        val productId = productService.createProduct(productRequest)

        if (productId == null) {
            return@post call.respondText(
                "Failed to save product",
                status = io.ktor.http.HttpStatusCode.InternalServerError,
            )
        }

        call.respond(productId)
    }
}
