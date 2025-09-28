package com.svebrant.configuration

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer

fun Application.configureAuthentication(bearerToken: String) {
    install(Authentication) {
        bearer("auth-bearer") {
            authenticate { tokenCredential ->
                if (tokenCredential.token == bearerToken) {
                    UserIdPrincipal("authenticated-user")
                } else {
                    null
                }
            }
        }
    }
}
