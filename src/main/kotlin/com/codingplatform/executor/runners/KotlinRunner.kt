package com.codingplatform.executor.runners

import com.codingplatform.executor.LanguageRunner
import com.codingplatform.models.Language

class KotlinRunner : LanguageRunner {
    override val language: Language = Language.KOTLIN

    override fun getBuildCommand(): List<String> =
        listOf("sh", "-c", "chmod +x gradlew && ./gradlew build --no-daemon")

    override fun getTestCommand(): List<String> =
        listOf("sh", "-c", "chmod +x gradlew && ./gradlew test --no-daemon")

    override fun getRunCommand(mainClass: String): List<String> =
        listOf("sh", "-c", "chmod +x gradlew && ./gradlew run --no-daemon -PmainClass=$mainClass")
}

