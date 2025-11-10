package com.codingplatform.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.codingplatform.models.User
import java.time.Instant
import java.util.Date

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expirationSeconds: Long
) {
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    fun generateToken(user: User): String {
        val now = Instant.now()
        val expiresAt = Date.from(now.plusSeconds(expirationSeconds))
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("name", user.name)
            .withClaim("role", user.role.name)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    fun verifier() = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()
}

