package com.svebrant.configuration

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.header
import java.util.UUID

const val HEADER_REQUEST_ID = "X-Request-Id"

const val MDC_KEY_REQUEST_ID = "requestId"

fun Application.configureLogging() {
    install(CallLogging) {
        mdc(MDC_KEY_REQUEST_ID) { call ->
            call.request.header(HEADER_REQUEST_ID) ?: UUID.randomUUID().toString()
        }
    }
}
