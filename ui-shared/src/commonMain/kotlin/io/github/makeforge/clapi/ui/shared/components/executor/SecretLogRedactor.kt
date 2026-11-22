package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.RequestTabState

/**
 * Redacts sensitive secret values (from external secret managers and `secret = true` environment
 * variables) before strings such as resolved request URLs are written to network or console logs.
 */
object SecretLogRedactor {
    const val MASK = "••••••••"
    private const val MIN_SECRET_LENGTH = 3

    /**
     * Collects all active secret values for [tab] from [resolvedSecrets] (Vault, AWS, GCP, Azure,
     * KeePassXC, Keeper), enabled `secret = true` variables in the active environment and all
     * inherited parent environments (`extends` chain), and enabled `secret = true` global variables.
     */
    fun collectSecretValues(
        state: AppState,
        tab: RequestTabState? = null,
        resolvedSecrets: Map<String, String>? = null,
    ): Set<String> = buildSet {
        fun addIfValid(value: String?) {
            if (!value.isNullOrBlank() && value.length >= MIN_SECRET_LENGTH) {
                add(value)
            }
        }
        resolvedSecrets?.values?.forEach { addIfValid(it) }

        // Walk active environment + inherited parent environments safely (cycle-protected)
        val visitedEnvs = mutableSetOf<String>()
        var currentEnv = state.selectedEnvironment
        while (currentEnv != null && visitedEnvs.add(currentEnv.name)) {
            currentEnv.variables.forEach { kv ->
                if (kv.enabled && kv.secret) addIfValid(kv.value)
            }
            val parentName = currentEnv.extends?.trim()
            currentEnv = if (!parentName.isNullOrEmpty()) {
                state.environments.firstOrNull { it.name == parentName }
            } else {
                null
            }
        }

        // Global secret variables
        state.globalVariables.forEach { kv ->
            if (kv.enabled && kv.secret) addIfValid(kv.value)
        }
    }

    /**
     * Replaces all occurrences of secrets in [secretValues] (filtered to `length >= 3` and
     * sorted by descending length so longer secrets containing shorter prefixes are masked whole)
     * with [MASK].
     */
    fun redact(text: String, secretValues: Set<String>): String {
        if (text.isEmpty() || secretValues.isEmpty()) return text
        val sortedSecrets = secretValues
            .filter { it.isNotBlank() && it.length >= MIN_SECRET_LENGTH }
            .sortedByDescending { it.length }
        if (sortedSecrets.isEmpty()) return text

        var redacted = text
        for (secret in sortedSecrets) {
            if (secret in redacted) {
                redacted = redacted.replace(secret, MASK)
            }
        }
        return redacted
    }
}
