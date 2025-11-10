package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.Problems
import com.codingplatform.models.CreateProblemRequest
import com.codingplatform.models.EvaluationCriteria
import com.codingplatform.models.Language
import com.codingplatform.models.Problem
import com.codingplatform.models.UpdateProblemRequest
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class ProblemService(
    private val databaseFactory: DatabaseFactory
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listProblems(): List<Problem> =
        databaseFactory.dbQuery {
            Problems.selectAll().map(::toProblem)
        }

    suspend fun getProblem(id: UUID): Problem? =
        databaseFactory.dbQuery {
            Problems.select { Problems.id eq id }.limit(1).firstOrNull()?.let(::toProblem)
        }

    suspend fun getProblemBySlug(slug: String): Problem? =
        databaseFactory.dbQuery {
            Problems.select { Problems.slug eq slug }.limit(1).firstOrNull()?.let(::toProblem)
        }

    suspend fun createProblem(request: CreateProblemRequest): Problem {
        require(request.testFiles.isNotEmpty()) { "테스트 파일이 최소 1개 이상 필요합니다." }
        validateTestFiles(request.testFiles)

        val slug = normalizeSlug(request.slug)
        val id = UUID.randomUUID()
        val now = Instant.now()

        databaseFactory.dbQuery {
            val exists = Problems.select { Problems.slug eq slug }.limit(1).firstOrNull()
            require(exists == null) { "이미 사용 중인 슬러그입니다." }

            Problems.insert { row ->
                row[Problems.id] = id
                row[Problems.slug] = slug
                row[Problems.title] = request.title
                row[Problems.description] = request.description
                row[Problems.difficulty] = request.difficulty
                row[Problems.language] = request.language.name
                row[Problems.tags] = request.tags.joinToString(",")
                row[Problems.testFiles] = json.encodeToString(request.testFiles)
                row[Problems.starterCode] = request.starterCode
                row[Problems.evaluationCriteria] = json.encodeToString(defaultCriteria)
                row[Problems.performanceTarget] = DEFAULT_PERFORMANCE_TARGET
                row[Problems.createdAt] = now.toLocalDateTimeUtc()
                row[Problems.updatedAt] = now.toLocalDateTimeUtc()
            }
        }

        return requireNotNull(getProblem(id))
    }

    suspend fun updateProblem(id: UUID, request: UpdateProblemRequest): Problem? {
        val updatedRows = databaseFactory.dbQuery {
            Problems.update({ Problems.id eq id }) { row ->
                request.title?.let { row[Problems.title] = it }
                request.description?.let { row[Problems.description] = it }
                request.difficulty?.let { row[Problems.difficulty] = it }
                request.language?.let { row[Problems.language] = it.name }
                request.tags?.let { row[Problems.tags] = it.joinToString(",") }
                request.testFiles?.let {
                    require(it.isNotEmpty()) { "테스트 파일이 최소 1개 이상 필요합니다." }
                    validateTestFiles(it)
                    row[Problems.testFiles] = json.encodeToString(it)
                }
                request.starterCode?.let { row[Problems.starterCode] = it }
                row[Problems.updatedAt] = Instant.now().toLocalDateTimeUtc()
            }
        }

        return if (updatedRows > 0) getProblem(id) else null
    }

    suspend fun deleteProblem(id: UUID): Boolean =
        databaseFactory.dbQuery {
            Problems.deleteWhere { Problems.id eq id } > 0
        }

    suspend fun seedDefaults() {
        val now = Instant.now()
        val samples = listOf(
            SampleProblem(
                slug = "kotlin-shopping-cart",
                title = "장바구니 서비스 구현",
                difficulty = "MEDIUM",
                language = Language.KOTLIN,
                tags = listOf("kotlin", "service", "testing"),
                description = """
                    ## 요구사항
                    - 장바구니에 상품을 추가/삭제할 수 있어야 합니다.
                    - 동일 상품을 추가하면 수량이 증가해야 합니다.
                    - 전체 금액과 할인 금액을 계산하는 기능을 작성하세요.
                    - 단위 테스트를 포함해야 합니다.
                """.trimIndent(),
                testFiles = mapOf(
                    "src/test/kotlin/ShoppingCartTest.kt" to """
                        import org.junit.jupiter.api.Test
                        import org.junit.jupiter.api.Assertions.*

                        class ShoppingCartTest {
                            @Test
                            fun `장바구니에 상품 추가`() {
                                val cart = ShoppingCart()
                                cart.addItem("사과", 1000, 2)
                                assertEquals(2000, cart.getTotalPrice())
                            }

                            @Test
                            fun `동일 상품 추가 시 수량 증가`() {
                                val cart = ShoppingCart()
                                cart.addItem("사과", 1000, 1)
                                cart.addItem("사과", 1000, 2)
                                assertEquals(3000, cart.getTotalPrice())
                            }

                            @Test
                            fun `상품 삭제`() {
                                val cart = ShoppingCart()
                                cart.addItem("사과", 1000, 2)
                                cart.removeItem("사과")
                                assertEquals(0, cart.getTotalPrice())
                            }

                            @Test
                            fun `할인 적용`() {
                                val cart = ShoppingCart()
                                cart.addItem("사과", 10000, 1)
                                cart.applyDiscount(10) // 10% 할인
                                assertEquals(9000, cart.getTotalPrice())
                            }
                        }
                    """.trimIndent()
                ),
                starterCode = """
                    class ShoppingCart {
                        // 여기에 코드를 작성하세요
                        fun addItem(name: String, price: Int, quantity: Int) {
                            TODO("구현 필요")
                        }

                        fun removeItem(name: String) {
                            TODO("구현 필요")
                        }

                        fun getTotalPrice(): Int {
                            TODO("구현 필요")
                        }

                        fun applyDiscount(percentage: Int) {
                            TODO("구현 필요")
                        }
                    }
                """.trimIndent()
            )
        )

        databaseFactory.dbQuery {
            if (!Problems.selectAll().empty()) {
                return@dbQuery
            }
            samples.forEach { sample ->
                Problems.insertIgnore { row ->
                    row[Problems.id] = UUID.randomUUID()
                    row[Problems.slug] = sample.slug
                    row[Problems.title] = sample.title
                    row[Problems.description] = sample.description
                    row[Problems.difficulty] = sample.difficulty
                    row[Problems.language] = sample.language.name
                    row[Problems.tags] = sample.tags.joinToString(",")
                    row[Problems.testFiles] = json.encodeToString(sample.testFiles)
                    row[Problems.starterCode] = sample.starterCode
                    row[Problems.evaluationCriteria] = json.encodeToString(defaultCriteria)
                    row[Problems.performanceTarget] = DEFAULT_PERFORMANCE_TARGET
                    row[Problems.createdAt] = now.toLocalDateTimeUtc()
                    row[Problems.updatedAt] = now.toLocalDateTimeUtc()
                }
            }
        }
    }

    private fun toProblem(row: org.jetbrains.exposed.sql.ResultRow): Problem =
        Problem(
            id = row[Problems.id].value,
            slug = row[Problems.slug],
            title = row[Problems.title],
            description = row[Problems.description],
            difficulty = row[Problems.difficulty],
            language = Language.valueOf(row[Problems.language]),
            tags = row[Problems.tags].split(",").filter { it.isNotBlank() },
            testFiles = json.decodeFromString(row[Problems.testFiles]),
            starterCode = row[Problems.starterCode],
            evaluationCriteria = json.decodeFromString(row[Problems.evaluationCriteria]),
            performanceTarget = row[Problems.performanceTarget],
            createdAt = row[Problems.createdAt].toInstant(TimeZone.UTC).toJavaInstant(),
            updatedAt = row[Problems.updatedAt].toInstant(TimeZone.UTC).toJavaInstant()
        )

    private fun Instant.toLocalDateTimeUtc() =
        KInstant.fromEpochMilliseconds(toEpochMilli()).toLocalDateTime(TimeZone.UTC)

    private fun normalizeSlug(slug: String): String = slug.trim().lowercase()

    private fun validateTestFiles(testFiles: Map<String, String>) {
        require(testFiles.all { it.key.isNotBlank() && it.value.isNotBlank() }) {
            "모든 테스트 파일의 경로와 내용이 필요합니다."
        }
    }

    private data class SampleProblem(
        val slug: String,
        val title: String,
        val difficulty: String,
        val language: Language,
        val tags: List<String>,
        val description: String,
        val testFiles: Map<String, String>,
        val starterCode: String
    )

    companion object {
        private val defaultCriteria = EvaluationCriteria(functional = 40, codeQuality = 20, testCoverage = 20, performance = 20)
        private const val DEFAULT_PERFORMANCE_TARGET = 150
    }
}
