@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.codingplatform.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.UseSerializers
import com.codingplatform.utils.InstantSerializer
import com.codingplatform.utils.UUIDSerializer

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val verificationCode: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class VerificationCodeRequest(
    val email: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: User
)

@Serializable
data class ExecuteCodeRequest(
    val language: Language,
    val files: Map<String, String>,
    val testCommand: String
)

@Serializable
data class ExecutionResponse(
    val success: Boolean,
    val data: ExecutionResult
)

@Serializable
data class SubmissionRequest(
    val problemId: UUID,
    val files: Map<String, String>
)

@Serializable
data class SubmissionResponse(
    val success: Boolean,
    val data: SubmissionResponseData
)

@Serializable
data class SubmissionResponseData(
    val submissionId: String,
    val status: SubmissionStatus,
    val message: String
)

@Serializable
data class UpdateProfileRequest(
    val name: String
)

@Serializable
data class ProblemResponse(
    val id: UUID,
    val title: String,
    val slug: String,
    val description: String,
    val difficulty: String,
    val language: Language,
    val tags: List<String>,
    val starterCode: String? = null // 사용자에게 제공되는 시작 코드
)

@Serializable
data class CreateProblemRequest(
    val title: String,
    val slug: String,
    val description: String,
    val difficulty: String,
    val language: Language,
    val tags: List<String> = emptyList(),
    val testFiles: Map<String, String>, // 테스트 파일 경로와 내용
    val starterCode: String? = null // 사용자에게 제공할 시작 코드 템플릿
)

@Serializable
data class UpdateProblemRequest(
    val title: String? = null,
    val description: String? = null,
    val difficulty: String? = null,
    val language: Language? = null,
    val tags: List<String>? = null,
    val testFiles: Map<String, String>? = null,
    val starterCode: String? = null
)

@Serializable
data class DashboardStats(
    val totalUsers: Long,
    val totalProblems: Long,
    val totalSubmissions: Long,
    val successRate: Double
)

@Serializable
data class LeaderboardEntry(
    val userId: UUID,
    val name: String,
    val score: Int
)
