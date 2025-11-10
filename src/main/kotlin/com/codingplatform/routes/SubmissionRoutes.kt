package com.codingplatform.routes

import com.codingplatform.models.SubmissionRequest
import com.codingplatform.models.SubmissionResponse
import com.codingplatform.models.SubmissionResponseData
import com.codingplatform.models.SubmissionStatus
import com.codingplatform.services.ProblemService
import com.codingplatform.services.SubmissionService
import com.codingplatform.utils.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.configureSubmissionRoutes(
    submissionService: SubmissionService,
    problemService: ProblemService
) {
    authenticate("auth-jwt") {
        post("/api/submissions") {
            val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<SubmissionRequest>()

            // Ensure problem exists
            if (problemService.getProblem(request.problemId) == null) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "문제를 찾을 수 없습니다."))
            }

            val submission = submissionService.createSubmission(userId, request)
            call.respond(
                HttpStatusCode.Accepted,
                SubmissionResponse(
                    success = true,
                    data = SubmissionResponseData(
                        submissionId = submission.id,
                        status = SubmissionStatus.PENDING,
                        message = "코드 평가가 시작되었습니다."
                    )
                )
            )
        }

        get("/api/submissions") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val submissions = submissionService.listSubmissions(userId)
            call.respond(submissions)
        }

        get("/api/submissions/{id}") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val submissionId = runCatching { UUID.fromString(idParam) }.getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val submission = submissionService.getSubmission(submissionId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            if (submission.userId != userId) {
                return@get call.respond(HttpStatusCode.Forbidden)
            }
            call.respond(submission)
        }
    }
}
