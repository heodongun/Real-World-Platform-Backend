package com.codingplatform.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object EmailVerifications : UUIDTable("email_verifications") {
    val email = varchar("email", 255).uniqueIndex()
    val codeHash = varchar("code_hash", 255)
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at")
    val verified = bool("verified").default(false)
}
