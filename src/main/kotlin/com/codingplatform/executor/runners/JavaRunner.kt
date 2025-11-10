package com.codingplatform.executor.runners

import com.codingplatform.executor.LanguageRunner
import com.codingplatform.models.Language

class JavaRunner : LanguageRunner {
    override val language: Language = Language.JAVA

    override fun getBuildCommand(): List<String> =
        listOf("sh", "-c", "chmod +x gradlew && ./gradlew build --no-daemon")

    override fun getTestCommand(): List<String> =
        listOf("sh", "-c", "chmod +x gradlew && ./gradlew test --no-daemon")

    override fun getRunCommand(mainClass: String): List<String> =
        listOf("sh", "-c", "chmod +x gradlew && ./gradlew run --args='$mainClass'")
}

