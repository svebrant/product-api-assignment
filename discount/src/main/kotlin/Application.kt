package com.svebrant

import com.svebrant.configuration.configureAuthentication
import com.svebrant.configuration.configureDependencyInjection
import com.svebrant.configuration.configureHttp
import com.svebrant.configuration.configureLogging
import com.svebrant.configuration.configureRoutes
import com.svebrant.configuration.configureSerialization
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
    val mongoDbConnectionString = System.getenv("mongodb.uri") ?: appConfig.property("mongodb.uri").getString()
    val log = KotlinLogging.logger { }

    log.info { "Starting app $appName" }

    configureAuthentication()
    configureRoutes()
    configureLogging()
    configureDependencyInjection(mongoDbConnectionString)
    configureHttp()
    configureSerialization()
}
