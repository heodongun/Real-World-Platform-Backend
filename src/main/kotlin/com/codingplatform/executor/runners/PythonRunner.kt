package com.codingplatform.executor.runners

import com.codingplatform.executor.LanguageRunner
import com.codingplatform.models.Language

class PythonRunner : LanguageRunner {
    override val language: Language = Language.PYTHON

    override fun getBuildCommand(): List<String> = emptyList()

    override fun getTestCommand(): List<String> =
        listOf("sh", "-c", "pytest --junitxml=test-results.xml")

    override fun getRunCommand(mainClass: String): List<String> =
        listOf("python", mainClass)
}

