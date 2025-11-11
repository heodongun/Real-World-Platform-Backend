package com.codingplatform.routes

import com.codingplatform.models.UserRole
import com.codingplatform.services.AdminService
import com.codingplatform.services.SubmissionService
import com.codingplatform.utils.isAdmin
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import java.util.UUID

fun Route.configureAdminRoutes(
    adminService: AdminService,
    submissionService: SubmissionService
) {
    authenticate("auth-jwt") {
        // Admin: List all users
        get("/api/admin/users") {
            if (!call.isAdmin()) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "ADMIN 권한이 필요합니다.")
                )
            }

            val users = adminService.listAllUsers()
            call.respond(users)
        }

        // Admin: Update user role
        put("/api/admin/users/{id}/role") {
            if (!call.isAdmin()) {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "ADMIN 권한이 필요합니다.")
                )
            }

            val userId = call.parameters["id"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))

            val request = call.receive<UpdateUserRoleRequest>()
            val updatedUser = adminService.updateUserRole(userId, request.role)

            if (updatedUser != null) {
                call.respond(updatedUser)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            }
        }

        // Admin: Delete user
        delete("/api/admin/users/{id}") {
            if (!call.isAdmin()) {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "ADMIN 권한이 필요합니다.")
                )
            }

            val userId = call.parameters["id"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))

            val deleted = adminService.deleteUser(userId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            }
        }

        // Admin: Get all submissions
        get("/api/admin/submissions") {
            if (!call.isAdmin()) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "ADMIN 권한이 필요합니다.")
                )
            }

            val submissions = submissionService.listAllSubmissions()
            call.respond(submissions)
        }

        // Admin: Get detailed statistics
        get("/api/admin/stats") {
            if (!call.isAdmin()) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "ADMIN 권한이 필요합니다.")
                )
            }

            val stats = adminService.getDetailedStats()
            call.respond(stats)
        }
    }
}

@Serializable
data class UpdateUserRoleRequest(
    val role: UserRole
)
