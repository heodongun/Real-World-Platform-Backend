package com.codingplatform.routes

import com.codingplatform.models.UpdateProfileRequest
import com.codingplatform.services.UserService
import com.codingplatform.utils.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.configureUserRoutes(userService: UserService) {
    authenticate("auth-jwt") {
        get("/api/users/me") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val user = userService.getUser(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user)
        }

        put("/api/users/me") {
            val userId = call.userId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<UpdateProfileRequest>()
            val updated = userService.updateProfile(userId, request.name)
                ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
    }
}

