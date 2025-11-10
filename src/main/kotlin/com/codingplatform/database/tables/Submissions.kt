package com.codingplatform.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Submissions : UUIDTable("submissions") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val problemId = reference("problem_id", Problems, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 30)
    val files = text("files_json")
    val score = integer("score").default(0)
    val feedback = text("feedback_json").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

