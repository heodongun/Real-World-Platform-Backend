@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.codingplatform.models

import com.codingplatform.utils.InstantSerializer
import com.codingplatform.utils.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Submission(
    val id: String,
    val userId: UUID,
    val problemId: UUID,
    val status: SubmissionStatus,
    val files: Map<String, String>,
    val score: Int,
    val feedback: SubmissionFeedback?,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class SubmissionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

@Serializable
data class SubmissionFeedback(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val passRate: Double,
    val score: Int,
    val status: ExecutionStatus,
    val testResults: TestResults,
    val output: String,
    val message: String
)
