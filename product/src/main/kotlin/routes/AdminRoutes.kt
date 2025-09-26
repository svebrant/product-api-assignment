package com.svebrant.routes

import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.validate
import com.svebrant.service.IngestService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

const val PARAM_INGESTION_ID = "ingestionId"

fun Route.adminRoutes() {
    val ingestService: IngestService by inject<IngestService>()

    get("/admin/ingest/{$PARAM_INGESTION_ID}/status") {
        val id =
            call.parameters[PARAM_INGESTION_ID] ?: return@get call.respondText(
                "Missing parameter: $PARAM_INGESTION_ID",
                status = HttpStatusCode.BadRequest,
            )

        call.respondText("Ingestion status")
    }

    post("/admin/ingest") {
        try {
            val ingestRequest = call.receive<IngestRequest>()
            ingestRequest.validate()

            println("Received product: $ingestRequest")
            val productId = ingestService.createIngestJob(ingestRequest)

//            if (productId == null) {
//                return@post call.respondText(
//                    "Failed to save product",
//                    status = HttpStatusCode.InternalServerError,
//                )
//            }

            call.respond("Ingestion started")
        } catch (e: IllegalArgumentException) {
            call.respondText(
                e.message ?: "Invalid request",
                status = HttpStatusCode.BadRequest,
            )
        }
    }
}
