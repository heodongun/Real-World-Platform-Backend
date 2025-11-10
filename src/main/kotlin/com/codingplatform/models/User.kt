@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.codingplatform.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.UseSerializers
import com.codingplatform.utils.InstantSerializer
import com.codingplatform.utils.UUIDSerializer

@Serializable
data class User(
    val id: UUID,
    val email: String,
    val name: String,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastLoginAt: Instant?
)

enum class UserRole {
    ADMIN,
    USER
}
