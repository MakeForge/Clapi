package io.github.crustack.clapi.core.storage.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitErrorTranslatorTest {

    @Test
    fun translates_git_not_installed_exception() {
        val ex = GitNotInstalledException()
        val translated = GitErrorTranslator.translate(ex)
        assertTrue(translated.contains("Git CLI is not installed"), "Should explain git is not installed")
    }

    @Test
    fun translates_git_auth_exception_and_stderr() {
        val ex = GitAuthException("fatal: Authentication failed for 'https://github.com/org/repo.git'")
        val translated = GitErrorTranslator.translate(ex)
        assertTrue(translated.contains("Authentication failed"), "Should explain auth failure")

        val sshErr = GitErrorTranslator.translateStderr("Permission denied (publickey). fatal: Could not read from remote repository.")
        assertTrue(sshErr.contains("Authentication failed"), "Should handle SSH publickey error")
    }

    @Test
    fun translates_network_timeout_and_unreachable_host() {
        val stderr1 = "fatal: unable to access 'https://github.com/org/repo.git/': Could not resolve host: github.com"
        val translated1 = GitErrorTranslator.translateStderr(stderr1)
        assertTrue(translated1.contains("Network connection failed or timed out"))

        val stderr2 = "fatal: Connection timed out after 30000 milliseconds"
        val translated2 = GitErrorTranslator.translateStderr(stderr2)
        assertTrue(translated2.contains("Network connection failed or timed out"))
    }

    @Test
    fun translates_branch_not_found() {
        val stderr1 = "fatal: Remote branch feature-xyz not found in upstream origin"
        val translated1 = GitErrorTranslator.translateStderr(stderr1)
        assertTrue(translated1.contains("Branch not found"))

        val stderr2 = "error: pathspec 'non-existent' did not match any file(s) known to git"
        val translated2 = GitErrorTranslator.translateStderr(stderr2)
        assertTrue(translated2.contains("Branch not found"))
    }

    @Test
    fun translates_conflict_exception_and_stderr() {
        val ex = GitConflictException("Merge conflict")
        val translated = GitErrorTranslator.translate(ex)
        assertTrue(translated.contains("Merge conflict detected"))

        val stderr = "CONFLICT (content): Merge conflict in collection.opencollection.yaml\nAutomatic merge failed; fix conflicts and then commit the result."
        val translatedStderr = GitErrorTranslator.translateStderr(stderr)
        assertTrue(translatedStderr.contains("Merge conflict detected"))
    }

    @Test
    fun preserves_custom_message_for_unrecognized_errors() {
        val custom = "Custom unexpected git issue"
        val translated = GitErrorTranslator.translateStderr(custom)
        assertEquals(custom, translated)
    }
}
