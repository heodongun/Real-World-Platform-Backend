package com.codingplatform.services

import com.codingplatform.database.DatabaseConfig
import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.EmailVerifications
import com.codingplatform.database.tables.Executions
import com.codingplatform.database.tables.Problems
import com.codingplatform.database.tables.Submissions
import com.codingplatform.database.tables.Users
import com.codingplatform.executor.DockerManager
import com.codingplatform.utils.JwtConfig
import com.codingplatform.services.email.EmailConfig
import com.codingplatform.services.email.EmailService
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.config.ApplicationConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking

class ServiceRegistry(
    environment: ApplicationEnvironment
) {
    private val config: ApplicationConfig = environment.config

    val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val jwtConfig: JwtConfig
    val databaseFactory: DatabaseFactory
    val redisClient: RedisClient
    val redisConnection: StatefulRedisConnection<String, String>

    val dockerManager: DockerManager
    val testRunnerService: TestRunnerService
    val dockerExecutorService: DockerExecutorService
    val emailService: EmailService
    val emailVerificationService: EmailVerificationService
    val authService: AuthService
    val userService: UserService
    val problemService: ProblemService
    val submissionService: SubmissionService
    val dashboardService: DashboardService

    init {
        val dbConfig = DatabaseConfig(
            jdbcUrl = value("database.jdbcUrl", "DATABASE_URL")
                ?: buildJdbcUrl(),
            driver = value("database.driver", "DATABASE_DRIVER") ?: "org.postgresql.Driver",
            username = value("database.username", "POSTGRES_USER") ?: "admin",
            password = value("database.password", "POSTGRES_PASSWORD") ?: "password",
            maxPoolSize = value("database.poolSize", "DB_POOL_SIZE")?.toIntOrNull() ?: 8
        )

        databaseFactory = DatabaseFactory(
            config = dbConfig,
            tables = listOf(Users, Problems, Submissions, Executions, EmailVerifications)
        ).also { it.init() }

        jwtConfig = JwtConfig(
            secret = value("jwt.secret", "JWT_SECRET") ?: error("JWT_SECRET is required"),
            issuer = value("jwt.issuer", "JWT_ISSUER") ?: "coding-platform",
            audience = value("jwt.audience", "JWT_AUDIENCE") ?: "coding-platform-users",
            realm = value("jwt.realm", "JWT_REALM") ?: "coding-platform-auth",
            expirationSeconds = value("jwt.expirationSeconds", "JWT_EXP_SECONDS")?.toLongOrNull() ?: 60L * 60 * 6
        )

        val redisHost = value("redis.host", "REDIS_HOST") ?: "localhost"
        val redisPort = value("redis.port", "REDIS_PORT") ?: "6379"
        val redisUrl = "redis://$redisHost:$redisPort"
        redisClient = RedisClient.create(redisUrl)
        redisConnection = redisClient.connect()

        dockerManager = DockerManager()
        testRunnerService = TestRunnerService()
        dockerExecutorService = DockerExecutorService(dockerManager, testRunnerService)

        emailService = EmailService(loadEmailConfig())
        emailVerificationService = EmailVerificationService(databaseFactory, emailService)

        authService = AuthService(databaseFactory, jwtConfig, emailVerificationService)
        problemService = ProblemService(databaseFactory).also { service ->
            runBlocking { service.seedDefaults() }
        }
        submissionService = SubmissionService(databaseFactory, problemService, dockerExecutorService)
        userService = UserService(databaseFactory, authService)
        dashboardService = DashboardService(databaseFactory)
    }

    suspend fun shutdown() {
        redisConnection.close()
        redisClient.shutdown()
        dockerManager.close()
        databaseFactory.close()
    }

    private fun value(path: String, envKey: String? = null): String? {
        val envValue = envKey?.let(System::getenv)
        if (!envValue.isNullOrBlank()) return envValue
        return config.propertyOrNull(path)?.getString()
    }

    private fun buildJdbcUrl(): String {
        val host = value("database.host", "POSTGRES_HOST") ?: "localhost"
        val port = value("database.port", "POSTGRES_PORT") ?: "5432"
        val db = value("database.name", "POSTGRES_DB") ?: "coding_platform"
        return "jdbc:postgresql://$host:$port/$db"
    }

    private fun loadEmailConfig(): EmailConfig {
        fun configValue(path: String, envKey: String): String? = value(path, envKey)

        val host = configValue("spring.mail.host", "SPRING_MAIL_HOST")
            ?: configValue("mail.host", "SMTP_HOST")
            ?: error("SMTP host is required")
        val port = (configValue("spring.mail.port", "SPRING_MAIL_PORT")
            ?: configValue("mail.port", "SMTP_PORT")
            ?: "587").toInt()
        val username = configValue("spring.mail.username", "SPRING_MAIL_USERNAME")
            ?: configValue("mail.username", "SMTP_USERNAME")
            ?: error("SMTP username is required")
        val password = configValue("spring.mail.password", "SPRING_MAIL_PASSWORD")
            ?: configValue("mail.password", "SMTP_PASSWORD")
            ?: error("SMTP password is required")
        val fromEmail = configValue("mail.from", "SMTP_FROM_EMAIL")
            ?: username
        val fromName = configValue("mail.fromName", "SMTP_FROM_NAME")
            ?: "Coding Platform"
        val startTls = (configValue("spring.mail.properties.mail.smtp.starttls.enable", "SPRING_MAIL_STARTTLS_ENABLE")
            ?: configValue("mail.starttls", "SMTP_STARTTLS_ENABLE")
            ?: "true").toBoolean()

        return EmailConfig(
            host = host,
            port = port,
            username = username,
            password = password,
            fromEmail = fromEmail,
            fromName = fromName,
            useStartTls = startTls
        )
    }
}
