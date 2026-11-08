package io.github.crustack.clapi.core.network.vault

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
}
