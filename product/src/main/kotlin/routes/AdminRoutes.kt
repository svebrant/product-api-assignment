package com.svebrant.routes

import com.svebrant.model.ingest.IngestRequest
import com.svebrant.model.ingest.IngestStatus
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

fun Route.adminRoutes() {
    val ingestService: IngestService by inject<IngestService>()

    get("/admin/ingest/{$PARAM_INGESTION_ID}/status") {
        val id =
            call.parameters[PARAM_INGESTION_ID] ?: return@get call.respondText(
                "Missing parameter: $PARAM_INGESTION_ID",
                status = HttpStatusCode.BadRequest,
            )

        val response =
            ingestService.getIngestionStatus(id) ?: return@get call.respondText(
                "Ingestion not found",
                status = HttpStatusCode.NotFound,
            )

        call.respond(response)
    }

    get("/admin/ingest") {
        val statusParam =
            call.request.queryParameters[PARAM_STATUS]?.let { status ->
                try {
                    IngestStatus.valueOf(status.uppercase())
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid status $status")
                }
            }
        val limit = call.request.queryParameters[PARAM_LIMIT]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters[PARAM_OFFSET]?.toIntOrNull() ?: 0
        val sortOrder = call.request.queryParameters[PARAM_SORT_ORDER] ?: "ASC"

        val ingestions =
            ingestService.getIngestions(
                status = statusParam,
                limit = limit,
                offset = offset,
                sortOrder = sortOrder,
            )

        call.respond(ingestions)
    }

    post("/admin/ingest") {
        try {
            val ingestRequest = call.receive<IngestRequest>()
            ingestRequest.validate()

            val response = ingestService.createIngestJob(ingestRequest)

            call.respond(response)
        } catch (e: IllegalArgumentException) {
            call.respondText(
                e.message ?: "Invalid request",
                status = HttpStatusCode.BadRequest,
            )
        }
    }
}
