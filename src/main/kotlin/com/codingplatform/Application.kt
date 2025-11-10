package com.codingplatform

import com.codingplatform.plugins.configureDatabase
import com.codingplatform.plugins.configureMonitoring
import com.codingplatform.plugins.configureRouting
import com.codingplatform.plugins.configureSecurity
import com.codingplatform.plugins.configureSerialization
import com.codingplatform.plugins.configureStatusPages
import com.codingplatform.services.ServiceRegistry
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

fun main() {
    embeddedServer(Netty, port = System.getenv("APP_PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val services = ServiceRegistry(environment)

    configureMonitoring(services.metricsRegistry)
    configureSerialization()
    configureStatusPages()
    configureSecurity(services.jwtConfig)
    configureDatabase(services.databaseFactory)
    configureRouting(services)

    environment.monitor.subscribe(ApplicationStopping) {
        runBlocking { services.shutdown() }
    }
}

