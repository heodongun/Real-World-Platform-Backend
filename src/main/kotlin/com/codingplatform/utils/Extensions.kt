package com.codingplatform.utils

import com.codingplatform.models.UserRole
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.UUID

fun ApplicationCall.userId(): UUID? =
    principal<JWTPrincipal>()?.subject?.let(UUID::fromString)

fun ApplicationCall.userEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

fun ApplicationCall.userRole(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

fun ApplicationCall.isAdmin(): Boolean =
    userRole()?.equals(UserRole.ADMIN.name, ignoreCase = true) == true
