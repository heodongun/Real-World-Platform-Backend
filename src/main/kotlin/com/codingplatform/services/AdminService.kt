package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.Submissions
import com.codingplatform.database.tables.Users
import com.codingplatform.models.User
import com.codingplatform.models.UserRole
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class AdminService(
    private val databaseFactory: DatabaseFactory
) {
    /**
     * Get all users (admin only)
     */
    suspend fun listAllUsers(): List<User> = databaseFactory.dbQuery {
        Users.selectAll()
            .map { it.toUserModel() }
    }

    /**
     * Update user role (admin only)
     */
    suspend fun updateUserRole(userId: UUID, newRole: UserRole): User? {
        val now = Instant.now()
        val updated = databaseFactory.dbQuery {
            Users.update({ Users.id eq userId }) {
                it[role] = newRole.name
                it[updatedAt] = now.toLocalDateTimeUtc()
            }
        }

        return if (updated > 0) {
            getUserById(userId)
        } else {
            null
        }
    }

    /**
     * Delete user (admin only)
     */
    suspend fun deleteUser(userId: UUID): Boolean = databaseFactory.dbQuery {
        // First delete all submissions by this user
        Submissions.deleteWhere { Submissions.userId eq userId }
        // Then delete the user
        Users.deleteWhere { Users.id eq userId } > 0
    }

    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: UUID): User? = databaseFactory.dbQuery {
        Users.select { Users.id eq userId }
            .map { it.toUserModel() }
            .singleOrNull()
    }

    /**
     * Get detailed statistics for admin
     */
    suspend fun getDetailedStats(): AdminStats = databaseFactory.dbQuery {
        val totalUsers = Users.selectAll().count()
        val adminCount = Users.select { Users.role eq UserRole.ADMIN.name }.count()
        val userCount = Users.select { Users.role eq UserRole.USER.name }.count()

        AdminStats(
            totalUsers = totalUsers,
            adminUsers = adminCount,
            regularUsers = userCount
        )
    }

    private fun ResultRow.toUserModel() = User(
        id = this[Users.id],
        email = this[Users.email],
        name = this[Users.name],
        role = UserRole.valueOf(this[Users.role]),
        createdAt = this[Users.createdAt].toInstant(TimeZone.UTC).toJavaInstant(),
        updatedAt = this[Users.updatedAt].toInstant(TimeZone.UTC).toJavaInstant(),
        lastLoginAt = this[Users.lastLoginAt]?.toInstant(TimeZone.UTC)?.toJavaInstant()
    )

    private fun Instant.toLocalDateTimeUtc() =
        KInstant.fromEpochMilliseconds(toEpochMilli()).toLocalDateTime(TimeZone.UTC)
}

data class AdminStats(
    val totalUsers: Long,
    val adminUsers: Long,
    val regularUsers: Long
)
