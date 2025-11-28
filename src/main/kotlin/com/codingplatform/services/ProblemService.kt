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
            ),
            SampleProblem(
                slug = "springboot-order-api",
                title = "Spring Boot 주문 & 재고 API",
                difficulty = "HARD",
                language = Language.SPRING_BOOT_KOTLIN,
                tags = listOf("spring-boot", "rest", "kotlin", "service"),
                description = """
                    ## 요구사항
                    - Spring Boot 기반으로 RESTful한 주문/재고 API를 구현합니다.
                    - 상품 등록/조회, 주문 생성, 재고 차감, 에러 응답까지 최소 3개 이상의 클래스로 분리하세요.
                    - Controller, Service, Repository/Storage 레이어를 나누고 DTO를 사용합니다.
                    - 다중 파일 구조를 유지해야 하며, 테스트에서 랜덤 포트로 API를 호출해 검증합니다.

                    ### 엔드포인트
                    - `POST /api/products` – `{sku, name, price, stock}`를 받아 상품을 등록하거나 업데이트합니다. 응답은 동일한 필드와 201 상태 코드를 반환하세요.
                    - `GET /api/products/{sku}` – 단일 상품을 조회합니다. 없으면 404를 내려주세요.
                    - `POST /api/orders` – `customer`와 `items[{sku, quantity}]`를 받아 주문을 생성합니다.
                        - 총액을 계산하고, 각 품목의 `linePrice`를 함께 응답하세요.
                        - 재고가 부족하면 400과 `{ "error": "INSUFFICIENT_STOCK" }`를 반환하세요.
                        - 주문이 성공하면 남은 재고가 차감되어야 합니다.
                """.trimIndent(),
                testFiles = mapOf(
                    "src/test/kotlin/com/codingplatform/order/OrderApiTest.kt" to """
                        package com.codingplatform.order

                        import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
                        import org.assertj.core.api.Assertions.assertThat
                        import org.junit.jupiter.api.Test
                        import org.springframework.beans.factory.annotation.Autowired
                        import org.springframework.boot.autoconfigure.SpringBootApplication
                        import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
                        import org.springframework.boot.test.context.SpringBootTest
                        import org.springframework.http.MediaType
                        import org.springframework.test.web.servlet.MockMvc
                        import org.springframework.test.web.servlet.get
                        import org.springframework.test.web.servlet.post

                        @SpringBootTest(classes = [OrderTestApplication::class])
                        @AutoConfigureMockMvc
                        class OrderApiTest(
                            @Autowired private val mockMvc: MockMvc
                        ) {
                            private val mapper = jacksonObjectMapper()

                            @Test
                            fun `상품 등록 후 조회`() {
                                mockMvc.post("/api/products") {
                                    contentType = MediaType.APPLICATION_JSON
                                    content = """{"sku":"A-100","name":"Starter Kit","price":12000,"stock":10}"""
                                }.andExpect {
                                    status { isCreated() }
                                    jsonPath("\$.sku") { value("A-100") }
                                    jsonPath("\$.stock") { value(10) }
                                }

                                mockMvc.get("/api/products/A-100")
                                    .andExpect {
                                        status { isOk() }
                                        jsonPath("\$.name") { value("Starter Kit") }
                                        jsonPath("\$.price") { value(12000) }
                                        jsonPath("\$.stock") { value(10) }
                                    }
                            }

                            @Test
                            fun `주문 생성 시 재고 차감과 총액 계산`() {
                                mockMvc.post("/api/products") {
                                    contentType = MediaType.APPLICATION_JSON
                                    content = """{"sku":"B-200","name":"API Guide","price":15000,"stock":5}"""
                                }.andExpect { status { isCreated() } }

                                val responseBody = mockMvc.post("/api/orders") {
                                    contentType = MediaType.APPLICATION_JSON
                                    content = """
                                        {
                                          "customer": "lee",
                                          "items": [
                                            {"sku": "B-200", "quantity": 2}
                                          ]
                                        }
                                    """.trimIndent()
                                }.andExpect {
                                    status { isCreated() }
                                    jsonPath("\$.items[0].sku") { value("B-200") }
                                    jsonPath("\$.items[0].quantity") { value(2) }
                                }.andReturn().response.contentAsString

                                val json = mapper.readTree(responseBody)
                                assertThat(json["totalPrice"].asInt()).isEqualTo(30000)
                                assertThat(json["items"].size()).isEqualTo(1)
                                assertThat(json["items"][0]["linePrice"].asInt()).isEqualTo(30000)

                                mockMvc.get("/api/products/B-200")
                                    .andExpect {
                                        status { isOk() }
                                        jsonPath("\$.stock") { value(3) }
                                    }
                            }

                            @Test
                            fun `재고 부족 시 400 응답`() {
                                mockMvc.post("/api/products") {
                                    contentType = MediaType.APPLICATION_JSON
                                    content = """{"sku":"C-300","name":"Monitor","price":210000,"stock":1}"""
                                }.andExpect { status { isCreated() } }

                                mockMvc.post("/api/orders") {
                                    contentType = MediaType.APPLICATION_JSON
                                    content = """
                                        {"customer":"kim","items":[{"sku":"C-300","quantity":2}]}
                                    """.trimIndent()
                                }.andExpect {
                                    status { isBadRequest() }
                                    jsonPath("\$.error") { value("INSUFFICIENT_STOCK") }
                                }
                            }
                        }

                        @SpringBootApplication(scanBasePackages = ["com.codingplatform.order"])
                        class OrderTestApplication
                    """.trimIndent()
                ),
                starterCode = """
                    package com.codingplatform.order

                    import org.springframework.boot.autoconfigure.SpringBootApplication
                    import org.springframework.boot.runApplication
                    import org.springframework.http.HttpStatus
                    import org.springframework.stereotype.Repository
                    import org.springframework.stereotype.Service
                    import org.springframework.web.bind.annotation.*
                    import org.springframework.web.server.ResponseStatusException

                    @SpringBootApplication
                    class OrderApplication

                    fun main(args: Array<String>) {
                        runApplication<OrderApplication>(*args)
                    }

                    data class Product(
                        val sku: String,
                        val name: String,
                        val price: Int,
                        val stock: Int
                    )

                    data class OrderItemRequest(val sku: String, val quantity: Int)
                    data class OrderRequest(val customer: String, val items: List<OrderItemRequest>)

                    data class OrderItemResponse(val sku: String, val quantity: Int, val linePrice: Int)
                    data class OrderResponse(val orderId: String, val customer: String, val totalPrice: Int, val items: List<OrderItemResponse>)

                    interface ProductRepository {
                        fun save(product: Product): Product
                        fun findBySku(sku: String): Product?
                        fun decrementStock(sku: String, quantity: Int): Product
                    }

                    @Repository
                    class InMemoryProductRepository : ProductRepository {
                        private val storage = linkedMapOf<String, Product>()

                        override fun save(product: Product): Product {
                            storage[product.sku] = product
                            return product
                        }

                        override fun findBySku(sku: String): Product? = storage[sku]

                        override fun decrementStock(sku: String, quantity: Int): Product {
                            val current = storage[sku] ?: throw IllegalArgumentException("product_not_found")
                            require(current.stock - quantity >= 0) { "INSUFFICIENT_STOCK" }
                            val updated = current.copy(stock = current.stock - quantity)
                            storage[sku] = updated
                            return updated
                        }
                    }

                    @Service
                    class OrderService(
                        private val repository: ProductRepository
                    ) {
                        fun registerProduct(request: Product): Product {
                            // TODO: validate input, handle duplicates and return saved product
                            return repository.save(request)
                        }

                        fun findProduct(sku: String): Product? = repository.findBySku(sku)

                        fun placeOrder(request: OrderRequest): OrderResponse {
                            // TODO: calculate totals, decrement stock, and build response DTOs
                            throw UnsupportedOperationException("implement order logic")
                        }
                    }

                    @RestController
                    @RequestMapping("/api")
                    class OrderController(
                        private val orderService: OrderService
                    ) {
                        @PostMapping("/products")
                        @ResponseStatus(HttpStatus.CREATED)
                        fun register(@RequestBody product: Product): Product =
                            orderService.registerProduct(product)

                        @GetMapping("/products/{sku}")
                        fun get(@PathVariable sku: String): Product =
                            orderService.findProduct(sku) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

                        @PostMapping("/orders")
                        @ResponseStatus(HttpStatus.CREATED)
                        fun place(@RequestBody request: OrderRequest): OrderResponse =
                            orderService.placeOrder(request)
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
