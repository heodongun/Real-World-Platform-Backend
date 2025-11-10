package com.codingplatform.services

import com.codingplatform.database.DatabaseFactory
import com.codingplatform.database.tables.Problems
import com.codingplatform.database.tables.Submissions
import com.codingplatform.database.tables.Users
import com.codingplatform.models.DashboardStats
import com.codingplatform.models.LeaderboardEntry
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class DashboardService(
    private val databaseFactory: DatabaseFactory
) {
    suspend fun getStats(): DashboardStats =
        databaseFactory.dbQuery {
            val totalUsers = Users.selectAll().count()
            val totalProblems = Problems.selectAll().count()
            val totalSubmissions = Submissions.selectAll().count()
            val successCount = Submissions.select { Submissions.status eq "COMPLETED" }.count()

            val successRate = if (totalSubmissions > 0L) {
                successCount.toDouble() / totalSubmissions.toDouble()
            } else {
                0.0
            }

            DashboardStats(
                totalUsers = totalUsers,
                totalProblems = totalProblems,
                totalSubmissions = totalSubmissions,
                successRate = "%.2f".format(successRate * 100).toDouble()
            )
        }

    suspend fun getLeaderboard(limit: Int = 10): List<LeaderboardEntry> =
        databaseFactory.dbQuery {
            val submissions = Submissions.selectAll().toList()
            if (submissions.isEmpty()) return@dbQuery emptyList()

            val grouped = submissions.groupBy { it[Submissions.userId].value }
            val userIds = grouped.keys.toList()
            val names = Users.select { Users.id inList userIds }
                .associate { it[Users.id].value to it[Users.name] }

            grouped.map { (userId, rows) ->
                val avgScore = rows.map { it[Submissions.score] }.average()
                LeaderboardEntry(
                    userId = userId,
                    name = names[userId] ?: "Unknown",
                    score = avgScore.toInt()
                )
            }.sortedByDescending { it.score }
                .take(limit)
        }
}
