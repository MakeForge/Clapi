package io.github.crustack.clapi.core.storage.git

object GitErrorTranslator {

    /**
     * Translates a Git-related exception or general throwable into a friendly, human-readable error message.
     */
    fun translate(error: Throwable): String {
        return when (error) {
            is GitNotInstalledException ->
                "Git CLI is not installed or not found in system PATH. Please install Git to use Git storage."

            is GitConflictException ->
                "Merge conflict detected. Please resolve conflicts using an external Git client."

            is GitAuthException ->
                "Authentication failed. Please verify your SSH keys, personal access token, or Git credentials."

            is GitRemoteNotFoundException ->
                "Remote repository not found or unreachable. Please check the remote URL and repository permissions."

            is GitExecutionException ->
                translateStderr(error.stderr).ifBlank {
                    error.message ?: "Git operation failed with exit code ${error.exitCode}."
                }

            is GitException ->
                translateStderr(error.message ?: "").ifBlank {
                    error.message ?: "Git operation failed."
                }

            else -> {
                val msg = error.message ?: return "An unexpected error occurred during Git operation."
                translateStderr(msg).ifBlank { msg }
            }
        }
    }

    /**
     * Inspects Git command stderr or error message text and returns a friendly explanation if recognized.
     */
    fun translateStderr(stderr: String): String {
        val lower = stderr.lowercase()
        return when {
            lower.contains("git: command not found") ||
            lower.contains("is not recognized as an internal or external command") ||
            lower.contains("no such file or directory") && lower.contains("git") ->
                "Git CLI is not installed or not found in system PATH. Please install Git to use Git storage."

            lower.contains("permission denied (publickey)") ||
            lower.contains("authentication failed") ||
            lower.contains("could not read username") ||
            lower.contains("invalid credentials") ||
            lower.contains("terminal prompts disabled") ->
                "Authentication failed. Please verify your SSH keys, personal access token, or Git credentials."

            lower.contains("could not resolve host") ||
            lower.contains("connection timed out") ||
            lower.contains("failed to connect to") ||
            lower.contains("network is unreachable") ||
            lower.contains("operation timed out") ->
                "Network connection failed or timed out. Please check your internet connection and remote URL."

            (lower.contains("branch") && lower.contains("not found")) ||
            lower.contains("did not match any file(s) known to git") ||
            lower.contains("couldn't find remote ref") ||
            (lower.contains("src refspec") && lower.contains("does not match any")) ->
                "Branch not found. Please verify the branch name exists on the remote repository."

            lower.contains("repository not found") ||
            lower.contains("does not appear to be a git repository") ||
            (lower.contains("remote:") && lower.contains("not found")) ->
                "Remote repository not found. Please verify the URL and your access permissions."

            lower.contains("conflict (content)") ||
            lower.contains("automatic merge failed") ||
            lower.contains("fix conflicts and then commit") ->
                "Merge conflict detected. Please resolve conflicts using an external Git client."

            lower.contains("nothing to commit") ->
                "Working directory is clean. Nothing to commit."

            lower.contains("commit message cannot be blank") ->
                "Commit message cannot be blank."

            else -> stderr.trim()
        }
    }
}
