package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.EmailVerifications
import com.codingplatform.services.email.EmailService
import com.codingplatform.utils.PasswordHasher
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

class EmailVerificationService(
    private val databaseFactory: DatabaseFactory,
    private val emailService: EmailService,
    private val expirationMinutes: Long = 10
) {
    suspend fun requestCode(email: String) {
        val normalizedEmail = email.lowercase(Locale.getDefault())
        val code = generateCode()
        val now = Instant.now()
        val expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES)

        databaseFactory.dbQuery {
            EmailVerifications.deleteWhere { EmailVerifications.email eq normalizedEmail }
            EmailVerifications.insert {
                it[EmailVerifications.email] = normalizedEmail
                it[codeHash] = PasswordHasher.hash(code)
                it[createdAt] = now.toLocalDateTimeUtc()
                it[EmailVerifications.expiresAt] = expiresAt.toLocalDateTimeUtc()
                it[verified] = false
            }
        }

        emailService.sendVerificationCode(normalizedEmail, code)
    }

    suspend fun consumeCode(email: String, code: String) {
        val normalizedEmail = email.lowercase(Locale.getDefault())
        val record = databaseFactory.dbQuery {
            EmailVerifications
                .select { EmailVerifications.email eq normalizedEmail }
                .limit(1)
                .firstOrNull()
                ?.let(::toRecord)
        } ?: throw IllegalArgumentException("인증 코드가 존재하지 않습니다. 인증 코드를 다시 요청해주세요.")

        val now = Instant.now()
        require(record.expiresAt.isAfter(now)) { "인증 코드가 만료되었습니다. 다시 요청해주세요." }
        require(!record.verified) { "이미 사용된 인증 코드입니다." }
        require(PasswordHasher.verify(code, record.codeHash)) { "인증 코드가 올바르지 않습니다." }

        databaseFactory.dbQuery {
            EmailVerifications.deleteWhere { EmailVerifications.email eq normalizedEmail }
        }
    }

    private fun generateCode(length: Int = 6): String =
        (1..length).joinToString("") { Random.nextInt(0, 10).toString() }

    private fun Instant.toLocalDateTimeUtc() =
        kotlinx.datetime.Instant.fromEpochMilliseconds(toEpochMilli()).toLocalDateTime(TimeZone.UTC)

    private data class VerificationRecord(
        val email: String,
        val codeHash: String,
        val expiresAt: Instant,
        val verified: Boolean
    )

    private fun toRecord(row: ResultRow) = VerificationRecord(
        email = row[EmailVerifications.email],
        codeHash = row[EmailVerifications.codeHash],
        expiresAt = row[EmailVerifications.expiresAt].toInstant(TimeZone.UTC).toJavaInstant(),
        verified = row[EmailVerifications.verified]
    )
}
