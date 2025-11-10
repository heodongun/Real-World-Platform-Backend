@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.codingplatform.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.UseSerializers
import com.codingplatform.utils.InstantSerializer
import com.codingplatform.utils.UUIDSerializer

@Serializable
data class Problem(
    val id: UUID,
    val slug: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val language: Language,
    val tags: List<String>,
    val testFiles: Map<String, String>, // Test file name -> test code content
    val starterCode: String? = null, // Optional starter code template for users
    val evaluationCriteria: EvaluationCriteria,
    val performanceTarget: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class EvaluationCriteria(
    val functional: Int,
    val codeQuality: Int,
    val testCoverage: Int,
    val performance: Int
) {
    val total: Int get() = functional + codeQuality + testCoverage + performance
}
