package com.codingplatform.models

import kotlinx.serialization.Serializable

@Serializable
data class ExecutionResult(
    val executionId: String,
    val status: ExecutionStatus,
    val output: String,
    val error: String?,
    val exitCode: Int,
    val executionTime: Long,
    val memoryUsed: Long
)

@Serializable
enum class ExecutionStatus {
    SUCCESS,
    FAILED,
    ERROR,
    TIMEOUT
}

enum class Language {
    KOTLIN,
    JAVA,
    PYTHON
}

@Serializable
data class TestResults(
    val passed: Int,
    val failed: Int,
    val total: Int,
    val details: List<TestResult>,
    val coverage: CoverageReport = CoverageReport(0, 0, emptyList())
)

@Serializable
data class TestResult(
    val testId: String,
    val name: String,
    val status: TestStatus,
    val error: String?,
    val duration: Long
)

enum class TestStatus {
    PASSED,
    FAILED,
    SKIPPED
}

@Serializable
data class CoverageReport(
    val line: Int,
    val branch: Int,
    val uncoveredLines: List<Int>
)
