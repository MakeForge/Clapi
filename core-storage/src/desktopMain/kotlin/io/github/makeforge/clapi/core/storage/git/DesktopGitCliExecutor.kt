package io.github.makeforge.clapi.core.storage.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DesktopGitCliExecutor(
    private val gitBinary: String = "git",
) : GitCliExecutor {

    override suspend fun execute(
        workingDir: String?,
        args: List<String>,
        timeoutMillis: Long,
    ): GitCommandResult = withContext(Dispatchers.IO) {
        val command = buildList {
            add(gitBinary)
            addAll(args)
        }
        val builder = ProcessBuilder(command)
        if (workingDir != null) {
            val dir = File(workingDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            builder.directory(dir)
        }

        val process = try {
            builder.start()
        } catch (e: IOException) {
            throw GitNotInstalledException("Failed to run git command: ${e.message}", e)
        } catch (e: Exception) {
            throw GitNotInstalledException("Failed to run git command: ${e.message}", e)
        }

        try {
            val stdoutDeferred = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrDeferred = async(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { it.readText() }
            }

            val completed = withTimeout(timeoutMillis) {
                process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            }

            if (!completed) {
                process.destroyForcibly()
                stdoutDeferred.cancel()
                stderrDeferred.cancel()
                throw GitExecutionException(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Git command timed out after ${timeoutMillis}ms",
                    message = "Git command timed out: ${command.joinToString(" ")}",
                )
            }

            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()
            val exitCode = process.exitValue()

            GitCommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            process.destroyForcibly()
            throw GitExecutionException(
                exitCode = -1,
                stdout = "",
                stderr = e.message.orEmpty(),
                message = "Git process error: ${e.message}",
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            process.destroyForcibly()
            throw e
        } catch (e: Exception) {
            process.destroyForcibly()
            if (e is GitException) throw e
            throw GitExecutionException(
                exitCode = -1,
                stdout = "",
                stderr = e.message.orEmpty(),
                message = "Git process error: ${e.message}",
            )
        }
    }
}

actual fun createDefaultGitCliExecutor(): GitCliExecutor = DesktopGitCliExecutor()

