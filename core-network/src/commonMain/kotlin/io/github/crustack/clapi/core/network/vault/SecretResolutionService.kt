package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.getEnvironmentVariable

data class SecretDeclaration(
    val name: String,
    val path: String,
    val enabled: Boolean = true,
)

class SecretResolutionService(
    private val vaultClient: HashiCorpVaultClient = KtorHashiCorpVaultClient(),
) {
    private val variablePattern = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")

    /**
     * Extracts all referenced variable tokens (`{{<token>}}`) from a collection of strings.
     */
    fun extractAllVariableTokens(texts: Iterable<String>): Set<String> {
        val result = mutableSetOf<String>()
        for (text in texts) {
            if (text.isBlank()) continue
            for (match in variablePattern.findAll(text)) {
                val token = match.groupValues[1].trim()
                if (token.isNotEmpty()) {
                    result.add(token)
                }
            }
        }
        return result
    }

    fun extractAllVariableTokens(text: String): Set<String> =
        extractAllVariableTokens(listOf(text))

    /**
     * Extracts all referenced secret variable names (`{{_secret.<name>}}`) from a collection of strings.
     * E.g., for `{{_secret.foo}}`, returns `"foo"`.
     */
    fun extractSecretReferences(texts: Iterable<String>): Set<String> {
        val result = mutableSetOf<String>()
        for (text in texts) {
            if (text.isBlank()) continue
            for (match in variablePattern.findAll(text)) {
                val token = match.groupValues[1].trim()
                if (token.startsWith("_secret.")) {
                    val name = token.removePrefix("_secret.").trim()
                    if (name.isNotEmpty()) {
                        result.add(name)
                    }
                }
            }
        }
        return result
    }

    fun extractSecretReferences(text: String): Set<String> =
        extractSecretReferences(listOf(text))

    /**
     * Resolves external secret tokens against HashiCorp Vault.
     *
     * @param requiredSecretNames Set of secret names referenced in requests (e.g. `["client_id"]`).
     * @param declarations List of declared external secrets in the environment.
     * @param vaultUrl Base URL of Vault server.
     * @param vaultToken Vault authentication token.
     * @param mountPath KV v2 mount path (defaults to "secret").
     * @param namespace Optional Vault enterprise namespace.
     * @return Map of `_secret.<name>` to resolved secret value.
     */
    suspend fun resolveSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        vaultUrl: String?,
        vaultToken: String?,
        mountPath: String = "secret",
        namespace: String? = null,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) {
            return emptyMap()
        }

        if (vaultUrl.isNullOrBlank()) {
            throw IllegalStateException("Vault URL is not configured. Please configure HashiCorp Vault in Settings -> Secrets Manager.")
        }
        if (vaultToken.isNullOrBlank()) {
            throw IllegalStateException("Vault token is not configured. Please configure HashiCorp Vault in Settings -> Secrets Manager.")
        }

        val declarationMap = declarations.associateBy { it.name.trim() }

        // Validate all required secrets exist and are enabled
        for (name in requiredSecretNames) {
            val decl = declarationMap[name]
                ?: throw IllegalStateException("External secret '_secret.$name' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$name' is disabled in the active environment.")
            }
        }

        // Group required secrets by their path to avoid duplicate network queries
        val secretsByPath = mutableMapOf<String, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            secretsByPath.getOrPut(decl.path.trim()) { mutableListOf() }.add(name)
        }

        val resolved = mutableMapOf<String, String>()
        for ((path, names) in secretsByPath) {
            val vaultData = vaultClient.fetchSecret(
                vaultUrl = vaultUrl,
                token = vaultToken,
                secretPath = path,
                mountPath = mountPath,
                namespace = namespace,
            )

            for (name in names) {
                val value = when {
                    vaultData.containsKey(name) -> vaultData.getValue(name)
                    vaultData.size == 1 -> vaultData.values.first()
                    vaultData.containsKey("value") -> vaultData.getValue("value")
                    else -> throw IllegalStateException(
                        "Secret key '$name' was not found in Vault path '$path'. Available keys: ${vaultData.keys.joinToString(", ")}"
                    )
                }
                resolved["_secret.$name"] = value
            }
        }

        return resolved
    }

    /**
     * Resolves secret tokens from both direct `{{_secret.<name>}}` references and OpenCollection
     * `secret: true` environment variables referenced as `{{<name>}}`.
     *
     * Order of resolution for `secret: true` variables:
     * 1. Process environment variable (`System.getenv(varName)`)
     * 2. Matching environment externalSecret (`declarations.find { it.name == varName }`)
     * 3. If neither matches, throws [IllegalStateException].
     *
     * Direct `_secret.<name>` tokens resolve directly against declared external secrets.
     */
    suspend fun resolveAllSecrets(
        texts: Iterable<String>,
        declarations: List<SecretDeclaration>,
        secretVariableNames: Set<String> = emptySet(),
        vaultUrl: String? = null,
        vaultToken: String? = null,
        mountPath: String = "secret",
        namespace: String? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
    ): Map<String, String> {
        val allTokens = extractAllVariableTokens(texts)
        if (allTokens.isEmpty()) return emptyMap()

        val directSecretNames = allTokens
            .filter { it.startsWith("_secret.") }
            .map { it.removePrefix("_secret.").trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val referencedSecretVars = allTokens
            .filter { it in secretVariableNames }
            .toSet()

        if (directSecretNames.isEmpty() && referencedSecretVars.isEmpty()) {
            return emptyMap()
        }

        val resolved = mutableMapOf<String, String>()
        val declarationMap = declarations.associateBy { it.name.trim() }
        val vaultSecretsToFetch = mutableSetOf<String>()
        val secretVarToExternalSecret = mutableMapOf<String, String>()

        for (varName in referencedSecretVars) {
            // 1. Process environment variable
            val envValue = processEnvProvider(varName)
            if (envValue != null) {
                resolved[varName] = envValue
                continue
            }

            // 2. Matching environment externalSecret
            val decl = declarationMap[varName]
            if (decl != null) {
                if (!decl.enabled) {
                    throw IllegalStateException("Secret variable '$varName' matches external secret '$varName' which is disabled in the active environment.")
                }
                vaultSecretsToFetch.add(varName)
                secretVarToExternalSecret[varName] = varName
                continue
            }

            // 3. Error if neither matches
            throw IllegalStateException("Secret variable '$varName' could not be resolved from process environment or external secrets.")
        }

        for (secName in directSecretNames) {
            val decl = declarationMap[secName]
                ?: throw IllegalStateException("External secret '_secret.$secName' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$secName' is disabled in the active environment.")
            }
            vaultSecretsToFetch.add(secName)
        }

        if (vaultSecretsToFetch.isEmpty()) {
            return resolved
        }

        if (vaultUrl.isNullOrBlank()) {
            throw IllegalStateException("Vault URL is not configured. Please configure HashiCorp Vault in Settings -> Secrets Manager.")
        }
        if (vaultToken.isNullOrBlank()) {
            throw IllegalStateException("Vault token is not configured. Please configure HashiCorp Vault in Settings -> Secrets Manager.")
        }

        val secretsByPath = mutableMapOf<String, MutableList<String>>()
        for (name in vaultSecretsToFetch) {
            val decl = declarationMap.getValue(name)
            secretsByPath.getOrPut(decl.path.trim()) { mutableListOf() }.add(name)
        }

        for ((path, names) in secretsByPath) {
            val vaultData = vaultClient.fetchSecret(
                vaultUrl = vaultUrl,
                token = vaultToken,
                secretPath = path,
                mountPath = mountPath,
                namespace = namespace,
            )

            for (name in names) {
                val value = when {
                    vaultData.containsKey(name) -> vaultData.getValue(name)
                    vaultData.size == 1 -> vaultData.values.first()
                    vaultData.containsKey("value") -> vaultData.getValue("value")
                    else -> throw IllegalStateException(
                        "Secret key '$name' was not found in Vault path '$path'. Available keys: ${vaultData.keys.joinToString(", ")}"
                    )
                }

                if (name in directSecretNames) {
                    resolved["_secret.$name"] = value
                }
                if (name in secretVarToExternalSecret) {
                    val varName = secretVarToExternalSecret.getValue(name)
                    resolved[varName] = value
                }
            }
        }

        return resolved
    }
}
