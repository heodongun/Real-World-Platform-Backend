package com.codingplatform.plugins

import com.codingplatform.routes.configureAuthRoutes
import com.codingplatform.routes.configureDashboardRoutes
import com.codingplatform.routes.configureExecutionRoutes
import com.codingplatform.routes.configureProblemRoutes
import com.codingplatform.routes.configureSubmissionRoutes
import com.codingplatform.routes.configureUserRoutes
import com.codingplatform.services.ServiceRegistry
import io.ktor.server.application.Application
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

fun Application.configureRouting(services: ServiceRegistry) {
    routing {
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        configureAuthRoutes(services.authService, services.emailVerificationService)
        configureUserRoutes(services.userService)
        configureProblemRoutes(services.problemService)
        configureSubmissionRoutes(services.submissionService, services.problemService)
        configureExecutionRoutes(services.dockerExecutorService)
        configureDashboardRoutes(services.dashboardService, services.metricsRegistry)
    }
}
