package com.codingplatform.executor

import com.codingplatform.models.Language

interface LanguageRunner {
    val language: Language
    fun getBuildCommand(): List<String>
    fun getTestCommand(): List<String>
    fun getRunCommand(mainClass: String): List<String>
}

