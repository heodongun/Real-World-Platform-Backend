package com.codingplatform.executor

data class ContainerConfig(
    val memoryBytes: Long,
    val cpuShares: Int,
    val networkDisabled: Boolean,
    val timeoutSeconds: Long
)

