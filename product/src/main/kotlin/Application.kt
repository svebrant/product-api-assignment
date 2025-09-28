package com.svebrant

import com.svebrant.configuration.configureAuthentication
import com.svebrant.configuration.configureDependencyInjection
import com.svebrant.configuration.configureLogging
import com.svebrant.configuration.configureRoutes
import com.svebrant.configuration.configureSchedulers
import com.svebrant.configuration.configureServerHttp
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import java.util.TimeZone

fun main(args: Array<String>) {
    // Set to UTC
    System.setProperty("user.timezone", "UTC")
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    val appConfig = environment.config
    val appName = appConfig.property("service.name").getString()
    val bearerToken = System.getenv("AUTH_TOKEN") ?: appConfig.property("auth.bearer.token").getString()

    val log = KotlinLogging.logger { }

    log.info { "Starting app $appName" }

    configureAuthentication(bearerToken)
    configureRoutes()
    configureLogging()
    configureDependencyInjection(appConfig, bearerToken)
    configureServerHttp()
    configureSchedulers()
}
