package com.codingplatform.routes

import com.codingplatform.services.DashboardService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Route.configureDashboardRoutes(
    dashboardService: DashboardService,
    registry: PrometheusMeterRegistry
) {
    get("/health") {
        call.respond(mapOf("status" to "OK"))
    }

    route("/api/dashboard") {
        get("/stats") {
            call.respond(dashboardService.getStats())
        }
    }

    get("/api/leaderboard") {
        call.respond(dashboardService.getLeaderboard())
    }

    get("/metrics") {
        call.respondText(registry.scrape(), ContentType.parse("text/plain"))
    }
}

