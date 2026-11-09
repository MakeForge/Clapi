package io.github.crustack.clapi.core.storage.git

import io.github.crustack.clapi.core.storage.opencollection.fileExists
import io.github.crustack.clapi.core.storage.opencollection.getDefaultStorageBaseDirectory

class GitRepositoryManager(
    val executor: GitCliExecutor,
    val baseStorageDir: String = getDefaultStorageBaseDirectory(),
) {
    fun deriveRepoId(remoteUrl: String): String {
        val sanitized = remoteUrl.lowercase().trim()
            .replace("https://", "")
            .replace("http://", "")
            .replace("git@", "")
            .replace(":", "_")
            .replace("/", "_")
            .replace(".git", "")
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        val hash = remoteUrl.trim().hashCode().toUInt().toString(16)
        val prefix = sanitized.take(30).ifBlank { "repo" }
        return "${prefix}_$hash"
    }

    fun sanitizeCollectionName(name: String): String {
        val slug = name.trim().replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return slug.ifBlank { "collection" }
    }

    fun getRepositoryDirectory(remoteUrl: String): String {
        val repoId = deriveRepoId(remoteUrl)
        return "$baseStorageDir/$repoId"
    }

    fun getCollectionDirectory(remoteUrl: String, collectionName: String): String {
        val repoDir = getRepositoryDirectory(remoteUrl)
        val colDir = sanitizeCollectionName(collectionName)
        return "$repoDir/$colDir"
    }

    suspend fun ensureClonedOrCheckedOut(remoteUrl: String, branch: String = "main"): GitRepositoryClone {
        val repoDir = getRepositoryDirectory(remoteUrl)
        val repoId = deriveRepoId(remoteUrl)

        val isGitRepo = fileExists("$repoDir/.git")
        if (!isGitRepo) {
            val cloneResult = executor.execute(
                workingDir = baseStorageDir,
                args = listOf("clone", "--branch", branch, remoteUrl, repoDir),
            )
            if (!cloneResult.isSuccess) {
                val fallbackClone = executor.execute(
                    workingDir = baseStorageDir,
                    args = listOf("clone", remoteUrl, repoDir),
                )
                if (!fallbackClone.isSuccess) {
                    val stderr = if (fallbackClone.stderr.isNotBlank()) fallbackClone.stderr else cloneResult.stderr
                    when {
                        stderr.contains("Permission denied", ignoreCase = true) ||
                        stderr.contains("authentication", ignoreCase = true) ->
                            throw GitAuthException("Authentication failed while cloning repository: $stderr")
                        stderr.contains("Repository not found", ignoreCase = true) ||
                        stderr.contains("Could not resolve host", ignoreCase = true) ->
                            throw GitRemoteNotFoundException("Remote repository not found or unreachable: $stderr")
                        else ->
                            throw GitExecutionException(
                                exitCode = fallbackClone.exitCode,
                                stdout = fallbackClone.stdout,
                                stderr = stderr,
                                message = "Failed to clone repository: $stderr",
                            )
                    }
                }
                val checkoutResult = executor.execute(
                    workingDir = repoDir,
                    args = listOf("checkout", "-B", branch),
                )
                if (!checkoutResult.isSuccess) {
                    throw GitExecutionException(
                        exitCode = checkoutResult.exitCode,
                        stdout = checkoutResult.stdout,
                        stderr = checkoutResult.stderr,
                        message = "Failed to checkout branch $branch: ${checkoutResult.stderr}",
                    )
                }
            }
        } else {
            val checkoutResult = executor.execute(
                workingDir = repoDir,
                args = listOf("checkout", branch),
            )
            if (!checkoutResult.isSuccess) {
                executor.execute(
                    workingDir = repoDir,
                    args = listOf("checkout", "-B", branch),
                )
            }
        }

        return GitRepositoryClone(
            repoId = repoId,
            remoteUrl = remoteUrl,
            workingDirectory = repoDir,
            activeBranch = branch,
        )
    }

    suspend fun checkStatus(repoDir: String): CollectionGitStatus {
        if (!fileExists("$repoDir/.git")) {
            return CollectionGitStatus(isClean = true)
        }

        val branchResult = executor.execute(
            workingDir = repoDir,
            args = listOf("rev-parse", "--abbrev-ref", "HEAD"),
        )
        val branch = if (branchResult.isSuccess) branchResult.stdout.trim() else "main"

        val statusResult = executor.execute(
            workingDir = repoDir,
            args = listOf("status", "--porcelain=v1", "-uall"),
        )

        if (!statusResult.isSuccess) {
            return CollectionGitStatus(
                branch = branch,
                isClean = false,
                lastError = statusResult.stderr,
            )
        }

        val rawLines = statusResult.stdout.lines().filter { it.isNotBlank() }
        var hasConflicts = false
        val changes = rawLines.mapNotNull { line ->
            if (line.length < 3) return@mapNotNull null
            val code = line.substring(0, 2)
            val filePath = line.substring(3).trim()
            val kind = when {
                code in listOf("UU", "AA", "DD", "AU", "UD", "UA", "DU") -> {
                    hasConflicts = true
                    GitChangeKind.CONFLICTED
                }
                code.contains("M") -> GitChangeKind.MODIFIED
                code.contains("A") || code == "??" -> GitChangeKind.ADDED
                code.contains("D") -> GitChangeKind.DELETED
                else -> GitChangeKind.MODIFIED
            }
            val collectionName = filePath.substringBefore('/').substringBefore('\\')
            GitFileChange(
                relativePath = filePath,
                collectionName = collectionName,
                changeKind = kind,
            )
        }

        return CollectionGitStatus(
            branch = branch,
            isClean = changes.isEmpty(),
            hasConflicts = hasConflicts,
            changes = changes,
        )
    }

    suspend fun commit(repoDir: String, message: String): GitCommandResult {
        if (message.isBlank()) {
            throw IllegalArgumentException("Commit message cannot be blank")
        }
        val addResult = executor.execute(
            workingDir = repoDir,
            args = listOf("add", "-A"),
        )
        if (!addResult.isSuccess) {
            return addResult
        }
        val commitResult = executor.execute(
            workingDir = repoDir,
            args = listOf("commit", "-m", message),
        )
        if (commitResult.exitCode == 1 &&
            (commitResult.stdout.contains("nothing to commit", ignoreCase = true) ||
             commitResult.stderr.contains("nothing to commit", ignoreCase = true))
        ) {
            return GitCommandResult(exitCode = 0, stdout = commitResult.stdout, stderr = commitResult.stderr)
        }
        return commitResult
    }

    suspend fun push(repoDir: String, branch: String): GitCommandResult {
        val pushResult = executor.execute(
            workingDir = repoDir,
            args = listOf("push", "origin", branch),
        )
        if (!pushResult.isSuccess && (pushResult.stderr.contains("no upstream", ignoreCase = true) || pushResult.stderr.contains("set-upstream", ignoreCase = true))) {
            return executor.execute(
                workingDir = repoDir,
                args = listOf("push", "--set-upstream", "origin", branch),
            )
        }
        return pushResult
    }

    suspend fun pull(repoDir: String, branch: String): GitCommandResult {
        val pullResult = executor.execute(
            workingDir = repoDir,
            args = listOf("pull", "origin", branch),
        )
        if (!pullResult.isSuccess) {
            val combined = pullResult.stdout + "\n" + pullResult.stderr
            if (combined.contains("CONFLICT", ignoreCase = true) ||
                combined.contains("Merge conflict", ignoreCase = true) ||
                combined.contains("Automatic merge failed", ignoreCase = true)
            ) {
                val status = checkStatus(repoDir)
                val conflictedFiles = status.changes
                    .filter { it.changeKind == GitChangeKind.CONFLICTED }
                    .map { it.relativePath }
                throw GitConflictException(
                    message = "Merge conflicts encountered during pull: ${pullResult.stderr}",
                    conflictedFiles = conflictedFiles,
                )
            }
        }
        return pullResult
    }
}
