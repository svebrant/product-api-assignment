package com.svebrant.routes

import com.svebrant.model.DiscountApplicationResponse
import com.svebrant.model.DiscountRequest
import com.svebrant.model.validate
import com.svebrant.service.DiscountService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.koin.ktor.ext.inject
import kotlin.getValue
import kotlin.text.toIntOrNull

fun Route.discountRoutes() {
    val discountService: DiscountService by inject<DiscountService>()

    put("/discounts/apply") {
        try {
            val discountRequest = call.receive<DiscountRequest>()
            discountRequest.validate()

            val response: DiscountApplicationResponse = discountService.applyDiscount(discountRequest)
            call.respond(response)
        } catch (e: IllegalArgumentException) {
            call.respondText(
                e.message ?: "Invalid request",
                status = io.ktor.http.HttpStatusCode.BadRequest,
            )
        }
    }

    get("/discounts") {
        val productId: String? = call.request.queryParameters[PARAM_PRODUCT_ID]
        val discountId: String? = call.request.queryParameters[PARAM_DISCOUNT_ID]
        val limit = call.request.queryParameters[PARAM_LIMIT]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters[PARAM_OFFSET]?.toIntOrNull() ?: 0
        val sortOrder = call.request.queryParameters[PARAM_SORT_ORDER] ?: "ASC"

        val discounts =
            discountService.getDiscounts(
                productId,
                discountId,
                limit = limit,
                offset = offset,
                sortOrder = sortOrder,
            )
        call.respond(discounts)
    }

    get("/discounts/{productId}") {
        val productId =
            call.parameters["productId"] ?: return@get call.respondText(
                "Missing id",
                status = io.ktor.http.HttpStatusCode.BadRequest,
            )
        val discounts = discountService.getByProductId(productId)
        if (discounts.isNotEmpty()) {
            call.respond(discounts)
        } else {
            call.respondText("Discount not found", status = io.ktor.http.HttpStatusCode.NotFound)
        }
    }

}
