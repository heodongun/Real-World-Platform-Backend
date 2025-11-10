package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.Users
import com.codingplatform.models.User
import com.codingplatform.models.UserRole
import com.codingplatform.utils.JwtConfig
import com.codingplatform.utils.PasswordHasher
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class AuthService(
    private val databaseFactory: DatabaseFactory,
    private val jwtConfig: JwtConfig,
    private val emailVerificationService: EmailVerificationService
) {

    suspend fun requestVerificationCode(email: String) {
        require(findByEmail(email) == null) { "이미 사용 중인 이메일입니다." }
        emailVerificationService.requestCode(email)
    }

    suspend fun register(
        email: String,
        password: String,
        name: String,
        verificationCode: String,
        role: UserRole = UserRole.USER
    ): Pair<User, String> {
        require(findByEmail(email) == null) { "이미 사용 중인 이메일입니다." }
        emailVerificationService.consumeCode(email, verificationCode)

        val user = databaseFactory.dbQuery {
            val now = Instant.now()
            val id = UUID.randomUUID()
            Users.insert { row ->
                row[Users.id] = id
                row[Users.email] = email
                row[Users.passwordHash] = PasswordHasher.hash(password)
                row[Users.name] = name
                row[Users.role] = role.name
                row[Users.createdAt] = now.toLocalDateTimeUtc()
                row[Users.updatedAt] = now.toLocalDateTimeUtc()
                row[Users.lastLoginAt] = null
            }
            User(
                id = id,
                email = email,
                name = name,
                role = role,
                createdAt = now,
                updatedAt = now,
                lastLoginAt = null
            )
        }

        return user to jwtConfig.generateToken(user)
    }

    suspend fun login(email: String, password: String): Pair<User, String> {
        val record = findByEmail(email) ?: throw IllegalArgumentException("계정 정보를 확인해주세요.")
        require(PasswordHasher.verify(password, record.passwordHash)) { "계정 정보를 확인해주세요." }

        val now = Instant.now()
        databaseFactory.dbQuery {
            Users.update({ Users.id eq record.id }) {
                it[lastLoginAt] = now.toLocalDateTimeUtc()
                it[updatedAt] = now.toLocalDateTimeUtc()
            }
        }

        val model = record.toModel().copy(lastLoginAt = now, updatedAt = now)
        return model to jwtConfig.generateToken(model)
    }

    suspend fun findById(id: UUID): User? =
        databaseFactory.dbQuery {
            Users.select { Users.id eq id }.limit(1).firstOrNull()?.let(::toRecord)?.toModel()
        }

    private suspend fun findByEmail(email: String): UserRecord? =
        databaseFactory.dbQuery {
            Users.select { Users.email eq email }.limit(1).firstOrNull()?.let(::toRecord)
        }

    private fun toRecord(row: org.jetbrains.exposed.sql.ResultRow): UserRecord =
        UserRecord(
            id = row[Users.id].value,
            email = row[Users.email],
            passwordHash = row[Users.passwordHash],
            name = row[Users.name],
            role = row[Users.role],
            createdAt = row[Users.createdAt].toInstant(TimeZone.UTC).toJavaInstant(),
            updatedAt = row[Users.updatedAt].toInstant(TimeZone.UTC).toJavaInstant(),
            lastLoginAt = row[Users.lastLoginAt]?.toInstant(TimeZone.UTC)?.toJavaInstant()
        )

    data class UserRecord(
        val id: UUID,
        val email: String,
        val passwordHash: String,
        val name: String,
        val role: String,
        val createdAt: Instant,
        val updatedAt: Instant,
        val lastLoginAt: Instant?
    ) {
        fun toModel(): User =
            User(
                id = id,
                email = email,
                name = name,
                role = UserRole.valueOf(role),
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastLoginAt = lastLoginAt
            )
    }

    private fun Instant.toLocalDateTimeUtc() =
        KInstant.fromEpochMilliseconds(toEpochMilli()).toLocalDateTime(TimeZone.UTC)
}
