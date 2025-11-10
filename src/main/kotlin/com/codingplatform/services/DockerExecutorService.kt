package com.codingplatform.services

import com.codingplatform.executor.DockerManager
import com.codingplatform.models.ExecutionResult
import com.codingplatform.models.ExecutionStatus
import com.codingplatform.models.Language
import com.codingplatform.models.Problem
import com.codingplatform.models.Submission
import com.codingplatform.models.SubmissionFeedback
import com.codingplatform.models.TestResults
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class DockerExecutorService(
    private val dockerManager: DockerManager,
    private val testRunnerService: TestRunnerService
) {
    private val logger = LoggerFactory.getLogger(DockerExecutorService::class.java)

    suspend fun executeCode(language: Language, files: Map<String, String>, command: String): ExecutionResult =
        withContext(Dispatchers.IO) {
            dockerManager.executeCode(
                executionId = UUID.randomUUID().toString(),
                language = language,
                files = files,
                command = listOf("sh", "-c", command)
            )
        }

    suspend fun evaluateSubmission(submission: Submission, problem: Problem): SubmissionFeedback =
        withContext(Dispatchers.IO) {
            require(problem.testFiles.isNotEmpty()) { "테스트 코드가 등록되지 않은 문제입니다." }

            logger.info("DockerExecutorService: evaluating submission ${submission.id} with ${problem.testFiles.size} test files")
            val (executionResult, testResults) = runTests(submission, problem)

            val passRate = if (testResults.total == 0) 0.0 else testResults.passed.toDouble() / testResults.total
            val score = (passRate * MAX_SCORE).roundToInt()
            val message = when {
                testResults.total == 0 -> "등록된 테스트가 없어 평가할 수 없습니다."
                testResults.failed == 0 -> "모든 테스트를 통과했습니다."
                else -> "${testResults.failed}개의 테스트가 실패했습니다."
            }

            SubmissionFeedback(
                totalTests = testResults.total,
                passedTests = testResults.passed,
                failedTests = testResults.failed,
                passRate = passRate,
                score = score,
                status = executionResult.status,
                testResults = testResults,
                output = executionResult.output,
                message = message
            )
        }

    private fun runTests(submission: Submission, problem: Problem): TestRunResult {
        logger.info("Preparing execution files for submission ${submission.id}")

        val buildFiles = prepareBuildFiles(problem)
        val testFiles = problem.testFiles
        val userSolutionFiles = prepareUserSolutionFiles(submission.files, problem.language)

        val command = when (problem.language) {
            Language.KOTLIN, Language.JAVA -> listOf("sh", "-c", "gradle test --no-daemon")
            Language.PYTHON -> listOf("sh", "-c", "pytest --junitxml=test-results.xml")
        }
        logger.info("Test command: $command")

        val allFiles = userSolutionFiles + testFiles + buildFiles

        val executionResult = dockerManager.executeCode(
            executionId = submission.id,
            language = problem.language,
            files = allFiles,
            command = command
        )
        val testResults = testRunnerService.parseTestResults(executionResult)
        logger.info(
            "Test run finished for submission ${submission.id}: passed=${testResults.passed}, failed=${testResults.failed}, total=${testResults.total}"
        )

        return TestRunResult(executionResult, testResults)
    }

    private fun prepareUserSolutionFiles(userFiles: Map<String, String>, language: Language): Map<String, String> =
        when (language) {
            Language.KOTLIN, Language.JAVA -> {
                userFiles.mapKeys { (path, _) ->
                    if (!path.startsWith("src/")) {
                        "src/main/kotlin/$path"
                    } else {
                        path
                    }
                }
            }

            Language.PYTHON -> {
                userFiles.mapKeys { (path, _) ->
                    if (!path.startsWith("src/") && !path.endsWith(".py")) {
                        "$path.py"
                    } else {
                        path
                    }
                }
            }
        }

    private fun prepareBuildFiles(problem: Problem): Map<String, String> =
        when (problem.language) {
            Language.KOTLIN, Language.JAVA -> mapOf(
                "settings.gradle.kts" to "rootProject.name = \"solution\"",
                "build.gradle.kts" to generateGradleBuild(problem.language),
                "gradlew" to gradlewScript(),
                "gradle/wrapper/gradle-wrapper.properties" to gradleWrapperProperties()
            )

            Language.PYTHON -> mapOf(
                "requirements.txt" to pythonRequirements(),
                "pytest.ini" to pythonPytestConfig()
            )
        }

    private fun generateGradleBuild(language: Language): String =
        when (language) {
            Language.KOTLIN -> """
                plugins {
                    kotlin("jvm") version "1.9.20"
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-stdlib")
                    testImplementation("org.jetbrains.kotlin:kotlin-test")
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
                }

                tasks.test {
                    useJUnitPlatform()
                    testLogging {
                        events("passed", "skipped", "failed")
                    }
                }
            """.trimIndent()

            Language.JAVA -> """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
                }

                tasks.test {
                    useJUnitPlatform()
                }
            """.trimIndent()

            Language.PYTHON -> error("Python uses requirements.txt instead of Gradle.")
        }

    private fun gradlewScript(): String = """
        #!/bin/sh
        chmod +x gradlew 2>/dev/null
        if command -v gradle >/dev/null 2>&1; then
            exec gradle "$@"
        else
            exec /opt/gradle/bin/gradle "$@"
        fi
    """.trimIndent()

    private fun gradleWrapperProperties(): String = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
    """.trimIndent()

    private fun pythonRequirements(): String = """
        pytest==7.4.3
        pytest-cov==4.1.0
        pytest-timeout==2.2.0
    """.trimIndent()

    private fun pythonPytestConfig(): String = """
        [pytest]
        addopts = -v --tb=short --cov=. --cov-report=xml
    """.trimIndent()

    private data class TestRunResult(
        val executionResult: ExecutionResult,
        val testResults: TestResults
    )

    companion object {
        private const val MAX_SCORE = 100
    }
}
