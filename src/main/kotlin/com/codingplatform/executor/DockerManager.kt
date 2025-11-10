package com.codingplatform.executor

import com.codingplatform.models.ExecutionResult
import com.codingplatform.models.ExecutionStatus
import com.codingplatform.models.Language
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.BuildImageResultCallback
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.api.async.ResultCallback
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

class DockerManager(
    private val dockerClient: DockerClient = DockerClientBuilder.getInstance().build(),
    private val securityManager: SecurityManager = SecurityManager()
) : Closeable {
    private val logger = LoggerFactory.getLogger(DockerManager::class.java)

    private val executionWorkspace = System.getenv("EXECUTION_WORKSPACE") ?: "/tmp/executions"
    private val dockerHostWorkspace = System.getenv("DOCKER_HOST_WORKSPACE") ?: executionWorkspace
    private val timeoutSeconds = (System.getenv("EXECUTION_TIMEOUT") ?: "30").toLong()
    private val maxMemoryBytes = (System.getenv("MAX_MEMORY_MB") ?: "512").toLong() * 1024 * 1024
    private val maxCpuShares = (System.getenv("MAX_CPU_SHARES") ?: "512").toInt()
    private val dockerTemplatesDir = System.getenv("DOCKER_TEMPLATES_DIR") ?: "src/main/resources/docker"

    fun executeCode(
        executionId: String = UUID.randomUUID().toString(),
        language: Language,
        files: Map<String, String>,
        command: List<String>
    ): ExecutionResult {
        logger.info("=== DockerManager.executeCode START ===")
        logger.info("ExecutionId: $executionId, Language: $language")
        logger.info("Files count: ${files.size}, Command: $command")

        try {
            securityManager.validateFiles(files)
            logger.info("Security validation passed")
        } catch (ex: Exception) {
            logger.error("Security validation failed: ${ex.message}", ex)
            throw ex
        }

        val workspace = createWorkspace(executionId, files)
        logger.info("Workspace created at: ${workspace.absolutePath}")

        return try {
            logger.info("Ensuring Docker image for language: $language")
            val image = ensureImageBuilt(language)
            logger.info("Docker image ready: $image")

            logger.info("Creating container...")
            val container = createContainer(executionId, image, workspace, command)
            logger.info("Container created: ${container.id}")

            logger.info("Running container with timeout: ${timeoutSeconds}s")
            val result = runContainer(container, timeoutSeconds)
            logger.info("Container execution completed. Status: ${result.status}, Exit code: ${result.exitCode}")
            logger.info("Stdout length: ${result.stdout.length}, Stderr length: ${result.stderr.length}")

            ExecutionResult(
                executionId = executionId,
                status = result.status,
                output = result.stdout,
                error = result.stderr,
                exitCode = result.exitCode,
                executionTime = result.duration.toMillis(),
                memoryUsed = result.memoryUsed
            ).also {
                logger.info("=== DockerManager.executeCode END (SUCCESS) ===")
            }
        } catch (ex: Exception) {
            logger.error("Docker execution failed for $executionId", ex)
            ExecutionResult(
                executionId = executionId,
                status = ExecutionStatus.ERROR,
                output = "",
                error = ex.message,
                exitCode = -1,
                executionTime = 0,
                memoryUsed = 0
            ).also {
                logger.info("=== DockerManager.executeCode END (ERROR) ===")
            }
        } finally {
            logger.info("Cleaning up workspace: ${workspace.absolutePath}")
            cleanupWorkspace(workspace)
        }
    }

    private fun createWorkspace(executionId: String, files: Map<String, String>): File {
        val dir = File(executionWorkspace, executionId)
        Files.createDirectories(dir.toPath())
        files.forEach { (path, content) ->
            val file = File(dir, path)
            file.parentFile?.let { Files.createDirectories(it.toPath()) }
            file.writeText(content)
        }
        return dir
    }

    private fun ensureImageBuilt(language: Language): String {
        val imageName = when (language) {
            Language.KOTLIN -> "coding-platform-kotlin:latest"
            Language.JAVA -> "coding-platform-java:latest"
            Language.PYTHON -> "coding-platform-python:latest"
        }

        val images = dockerClient.listImagesCmd()
            .withImageNameFilter(imageName)
            .exec()

        if (images.isEmpty()) {
            buildImage(language, imageName)
        }
        return imageName
    }

    private fun buildImage(language: Language, imageName: String) {
        val baseDir = File(dockerTemplatesDir, language.name.lowercase())
        require(baseDir.exists()) { "Dockerfile 경로를 찾을 수 없습니다: ${baseDir.absolutePath}" }

        val imageId = dockerClient.buildImageCmd()
            .withDockerfile(File(baseDir, "Dockerfile"))
            .withBaseDirectory(baseDir)
            .withTags(setOf(imageName))
            .exec(BuildImageResultCallback())
            .awaitImageId()
        val (repository, tag) = imageName.split(':').let { parts ->
            when (parts.size) {
                1 -> parts[0] to "latest"
                else -> parts[0] to parts[1]
            }
        }
        dockerClient.tagImageCmd(imageId, repository, tag).exec()
    }

    private fun createContainer(
        executionId: String,
        image: String,
        workspace: File,
        command: List<String>
    ): CreateContainerResponse {
        // Convert container workspace path to host workspace path for Docker bind mount
        val hostPath = workspace.absolutePath.replace(executionWorkspace, dockerHostWorkspace)
        logger.info("Bind mount: host path=$hostPath → container path=/workspace")

        val hostConfig = HostConfig.newHostConfig()
            .withMemory(maxMemoryBytes)
            .withMemorySwap(maxMemoryBytes)
            .withCpuShares(maxCpuShares)
            .withNetworkMode("bridge")
            .withReadonlyRootfs(false)
            .withBinds(Bind(hostPath, Volume("/workspace"), AccessMode.rw))
            .withCapDrop(Capability.ALL)
            .withSecurityOpts(listOf("no-new-privileges"))

        return dockerClient.createContainerCmd(image)
            .withName("exec-$executionId")
            .withWorkingDir("/workspace")
            .withCmd(command)
            .withHostConfig(hostConfig)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withUser("1000:1000")
            .withEnv("EXECUTION_TIMEOUT=$timeoutSeconds")
            .exec()
    }

    private fun runContainer(container: CreateContainerResponse, timeout: Long): ContainerExecutionResult {
        val containerId = container.id
        val start = System.nanoTime()

        dockerClient.startContainerCmd(containerId).exec()

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val logCallback = object : LogContainerResultCallback() {
            override fun onNext(item: Frame) {
                when (item.streamType) {
                    StreamType.STDOUT -> stdout.append(item.payload.decodeToString())
                    StreamType.STDERR -> stderr.append(item.payload.decodeToString())
                    else -> {}
                }
            }
        }

        dockerClient.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .exec(logCallback)

        val exitCode = try {
            dockerClient.waitContainerCmd(containerId)
                .exec(WaitContainerResultCallback())
                .awaitStatusCode(timeout, TimeUnit.SECONDS)
        } catch (ex: Exception) {
            dockerClient.killContainerCmd(containerId).exec()
            -1
        } finally {
            logCallback.awaitCompletion()
            logCallback.close()
        }

        val duration = Duration.ofNanos(System.nanoTime() - start)

        dockerClient.removeContainerCmd(containerId)
            .withForce(true)
            .exec()

        val status = when {
            exitCode == 0 -> ExecutionStatus.SUCCESS
            exitCode == -1 -> ExecutionStatus.TIMEOUT
            else -> ExecutionStatus.FAILED
        }

        return ContainerExecutionResult(
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            exitCode = exitCode,
            status = status,
            duration = duration,
            memoryUsed = 0
        )
    }

    private fun cleanupWorkspace(workspace: File) {
        workspace.deleteRecursively()
    }

    override fun close() {
        if (dockerClient is Closeable) {
            dockerClient.close()
        }
    }

    private data class ContainerExecutionResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val status: ExecutionStatus,
        val duration: Duration,
        val memoryUsed: Long
    )
}
