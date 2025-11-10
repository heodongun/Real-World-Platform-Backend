package com.codingplatform.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Application.configureMonitoring(registry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        this.registry = registry
    }
}

