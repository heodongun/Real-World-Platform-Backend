package com.codingplatform.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Problems : UUIDTable("problems") {
    val title = varchar("title", 255)
    val slug = varchar("slug", 255).uniqueIndex()
    val description = text("description")
    val difficulty = varchar("difficulty", 50)
    val language = varchar("language", 30)
    val tags = text("tags").default("")
    val testFiles = text("test_files") // JSON: Map<String, String> - test file paths and contents
    val starterCode = text("starter_code").nullable() // Initial code template for users
    val evaluationCriteria = text("evaluation_criteria")
    val performanceTarget = integer("performance_target").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

