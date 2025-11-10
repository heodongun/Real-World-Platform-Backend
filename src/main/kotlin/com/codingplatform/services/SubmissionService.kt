package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.Executions
import com.codingplatform.database.tables.Problems
import com.codingplatform.database.tables.Submissions
import com.codingplatform.database.tables.Users
import com.codingplatform.models.ExecutionResult
import com.codingplatform.models.ExecutionStatus
import com.codingplatform.models.Language
import com.codingplatform.models.Problem
import com.codingplatform.models.Submission
import com.codingplatform.models.SubmissionFeedback
import com.codingplatform.models.SubmissionRequest
import com.codingplatform.models.SubmissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class SubmissionService(
    private val databaseFactory: DatabaseFactory,
    private val problemService: ProblemService,
    private val dockerExecutorService: DockerExecutorService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(SubmissionService::class.java)

    suspend fun listSubmissions(userId: UUID): List<Submission> =
        databaseFactory.dbQuery {
            val userEntity = EntityID(userId, Users)
            Submissions.select { Submissions.userId eq userEntity }.map(::toSubmission)
        }

    suspend fun createSubmission(userId: UUID, request: SubmissionRequest): Submission {
        val problem = problemService.getProblem(request.problemId)
            ?: throw IllegalArgumentException("문제가 존재하지 않습니다.")

        val now = Instant.now()
        val submissionUuid = UUID.randomUUID()
        val submissionId = submissionUuid.toString()
        databaseFactory.dbQuery {
            Submissions.insert { row ->
                row[Submissions.id] = submissionUuid
                row[Submissions.userId] = EntityID(userId, Users)
                row[Submissions.problemId] = EntityID(problem.id, Problems)
                row[Submissions.status] = SubmissionStatus.PENDING.name
                row[Submissions.files] = json.encodeToString(request.files)
                row[Submissions.score] = 0
                row[Submissions.feedback] = null
                row[Submissions.createdAt] = now.toLocalDateTimeUtc()
                row[Submissions.updatedAt] = now.toLocalDateTimeUtc()
            }
        }

        enqueueEvaluation(submissionId, problem)
        return getSubmission(submissionUuid)!!
    }

    suspend fun getSubmission(id: UUID): Submission? =
        databaseFactory.dbQuery {
            val entityId = EntityID(id, Submissions)
            Submissions.select { Submissions.id eq entityId }.limit(1).firstOrNull()?.let(::toSubmission)
        }

    private fun enqueueEvaluation(submissionId: String, problem: Problem) {
        scope.launch {
            try {
                logger.info("Starting evaluation for submission: $submissionId")
                updateStatus(submissionId, SubmissionStatus.RUNNING)
                val submission = getSubmission(UUID.fromString(submissionId)) ?: run {
                    logger.error("Submission not found: $submissionId")
                    return@launch
                }
                logger.info("Evaluating submission with Docker...")
                val feedback = dockerExecutorService.evaluateSubmission(submission, problem)
                logger.info("Evaluation completed. Score: ${feedback.score}")
                val entityId = EntityID(UUID.fromString(submissionId), Submissions)
                databaseFactory.dbQuery {
                    Submissions.update({ Submissions.id eq entityId }) {
                        it[Submissions.status] = SubmissionStatus.COMPLETED.name
                        it[Submissions.feedback] = json.encodeToString(feedback)
                        it[Submissions.score] = feedback.score
                        it[Submissions.updatedAt] = Instant.now().toLocalDateTimeUtc()
                    }
                }
                logger.info("Submission $submissionId completed successfully")
            } catch (e: Exception) {
                logger.error("Failed to evaluate submission $submissionId", e)
                val entityId = EntityID(UUID.fromString(submissionId), Submissions)
                databaseFactory.dbQuery {
                    Submissions.update({ Submissions.id eq entityId }) {
                        it[Submissions.status] = SubmissionStatus.FAILED.name
                        it[Submissions.feedback] = null
                        it[Submissions.updatedAt] = Instant.now().toLocalDateTimeUtc()
                    }
                }
            }
        }
    }

    private suspend fun updateStatus(submissionId: String, status: SubmissionStatus) {
        val entityId = EntityID(UUID.fromString(submissionId), Submissions)
        databaseFactory.dbQuery {
            Submissions.update({ Submissions.id eq entityId }) {
                it[Submissions.status] = status.name
                it[Submissions.updatedAt] = Instant.now().toLocalDateTimeUtc()
            }
        }
    }

    suspend fun recordExecution(
        submissionId: UUID?,
        language: Language,
        result: ExecutionResult
    ) {
        val now = Instant.now()
        val submissionEntity = submissionId?.let { EntityID(it, Submissions) }
        databaseFactory.dbQuery {
            Executions.insert { row ->
                row[Executions.id] = UUID.randomUUID()
                submissionEntity?.let { row[Executions.submissionId] = it }
                row[Executions.language] = language.name
                row[Executions.status] = result.status.name
                row[Executions.output] = result.output
                row[Executions.error] = result.error
                row[Executions.exitCode] = result.exitCode
                row[Executions.executionTime] = result.executionTime
                row[Executions.memoryUsed] = result.memoryUsed
                row[Executions.createdAt] = now.toLocalDateTimeUtc()
            }
        }
    }

    private fun toSubmission(row: org.jetbrains.exposed.sql.ResultRow): Submission {
        val feedbackJson = row[Submissions.feedback]
        return Submission(
            id = row[Submissions.id].value.toString(),
            userId = row[Submissions.userId].value,
            problemId = row[Submissions.problemId].value,
            status = SubmissionStatus.valueOf(row[Submissions.status]),
            files = json.decodeFromString<Map<String, String>>(row[Submissions.files]),
            score = row[Submissions.score],
            feedback = feedbackJson?.let { json.decodeFromString<SubmissionFeedback>(it) },
            createdAt = row[Submissions.createdAt].toInstant(TimeZone.UTC).toJavaInstant(),
            updatedAt = row[Submissions.updatedAt].toInstant(TimeZone.UTC).toJavaInstant()
        )
    }

    private fun Instant.toLocalDateTimeUtc() =
        KInstant.fromEpochMilliseconds(toEpochMilli()).toLocalDateTime(TimeZone.UTC)
}
