package com.codingplatform.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Executions : UUIDTable("executions") {
    val submissionId = reference("submission_id", Submissions, onDelete = ReferenceOption.CASCADE).nullable()
    val language = varchar("language", 30)
    val status = varchar("status", 30)
    val output = text("output")
    val error = text("error").nullable()
    val exitCode = integer("exit_code")
    val executionTime = long("execution_time")
    val memoryUsed = long("memory_used")
    val createdAt = datetime("created_at")
}

