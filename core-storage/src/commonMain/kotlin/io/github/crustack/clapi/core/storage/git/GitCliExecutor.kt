package io.github.crustack.clapi.core.storage.git

open class GitException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class GitNotInstalledException(
    message: String = "Git CLI is not installed or not found in system PATH.",
    cause: Throwable? = null,
) : GitException(message, cause)

class GitAuthException(
    message: String,
    cause: Throwable? = null,
) : GitException(message, cause)

class GitRemoteNotFoundException(
    message: String,
    cause: Throwable? = null,
) : GitException(message, cause)

class GitConflictException(
    message: String,
    val conflictedFiles: List<String> = emptyList(),
    cause: Throwable? = null,
) : GitException(message, cause)

class GitExecutionException(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    message: String,
) : GitException(message)

data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

interface GitCliExecutor {
    suspend fun execute(
        workingDir: String?,
        args: List<String>,
        timeoutMillis: Long = 30_000,
    ): GitCommandResult
}
