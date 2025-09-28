package com.svebrant.configuration

import com.svebrant.scheduled.IngestionScheduler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install

private val SchedulerPlugin =
    createApplicationPlugin(name = "SchedulerPlugin") {
        val scheduler = IngestionScheduler()

        application.monitor.subscribe(ApplicationStarted) {
            scheduler.start()
            log.info { "Application started, scheduler activated" }
        }

        application.monitor.subscribe(ApplicationStopped) {
            scheduler.stop()
            log.info { "Application stopping, scheduler deactivated" }
        }
    }

fun Application.configureSchedulers() {
    install(SchedulerPlugin)
}

private val log = KotlinLogging.logger {}
