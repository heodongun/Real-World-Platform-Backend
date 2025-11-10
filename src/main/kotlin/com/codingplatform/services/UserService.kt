package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.Users
import com.codingplatform.models.User
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class UserService(
    private val databaseFactory: DatabaseFactory,
    private val authService: AuthService
) {
    suspend fun getUser(id: UUID): User? = authService.findById(id)

    suspend fun updateProfile(id: UUID, name: String): User? {
        val now = Instant.now()
        databaseFactory.dbQuery {
            Users.update({ Users.id eq id }) {
                it[Users.name] = name
                it[Users.updatedAt] = now.toLocalDateTimeUtc()
            }
        }
        return getUser(id)?.copy(name = name, updatedAt = now)
    }

    private fun Instant.toLocalDateTimeUtc() =
        KInstant.fromEpochMilliseconds(toEpochMilli()).toLocalDateTime(TimeZone.UTC)
}

