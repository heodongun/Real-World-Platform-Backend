package com.codingplatform.executor

import java.nio.file.InvalidPathException
import java.nio.file.Path

class SecurityManager {
    fun validateFiles(files: Map<String, String>) {
        files.keys.forEach { path ->
            val normalized = normalize(path)
            require(!normalized.startsWith("..")) { "상대 경로는 허용되지 않습니다: $path" }
        }
    }

    private fun normalize(path: String): String = try {
        Path.of(path).normalize().toString().replace("\\", "/")
    } catch (ex: InvalidPathException) {
        throw IllegalArgumentException("잘못된 파일 경로입니다: $path")
    }
}

