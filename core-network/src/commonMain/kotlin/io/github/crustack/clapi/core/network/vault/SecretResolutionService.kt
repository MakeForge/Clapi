package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.crustack.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.crustack.clapi.core.network.getEnvironmentVariable
import io.github.crustack.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClient
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClientManager
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcEntryFilter
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus

data class SecretDeclaration(
    val name: String,
    val path: String = "",
    val enabled: Boolean = true,
    val providerType: String = "hashicorp-vault-server",
    val url: String = "",
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    val secretId: String = path,
)

class SecretResolutionService(
    private val vaultClient: HashiCorpVaultClient = KtorHashiCorpVaultClient(),
    private val keepassClientManager: KeePassXcClientManager = DefaultKeePassXcClientManager.Shared,
    private val awsClient: AwsSecretsManagerClient = KtorAwsSecretsManagerClient(),
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
     * Resolves external secret tokens against KeePassXC.
     *
     * @param requiredSecretNames Set of secret names referenced in requests (e.g. `["api_token"]`).
     * @param declarations List of declared external secrets in the environment.
     * @param variableValues Active environment variables used for filter interpolation.
     * @param client Optional client override.
     * @return Map of `_secret.<name>` to resolved secret value.
     */
    suspend fun resolveKeePassXcSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        variableValues: Map<String, String> = emptyMap(),
        client: KeePassXcClient? = null,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) {
            return emptyMap()
        }

        val declarationMap = declarations.associateBy { it.name.trim() }

        // Validate all required secrets exist and are enabled
        for (name in requiredSecretNames) {
            val decl = declarationMap[name]
                ?: throw IllegalStateException("External secret '_secret.$name' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$name' is disabled in the active environment.")
            }
            if (decl.url.isBlank()) {
                throw IllegalStateException("External secret '_secret.$name' does not have an entry URL configured.")
            }
        }

        val kpaClient = client ?: keepassClientManager.getClient()

        // Check database status
        when (val status = kpaClient.checkStatus()) {
            is KeePassXcStatus.DatabaseLocked -> throw IllegalStateException("KeePassXC database is locked. Please unlock your database.")
            is KeePassXcStatus.Unavailable -> throw IllegalStateException("Cannot connect to KeePassXC: ${status.reason}")
            is KeePassXcStatus.NotAssociated -> throw IllegalStateException("KeePassXC is not associated. Please approve the association prompt in KeePassXC.")
            is KeePassXcStatus.Connected -> Unit
        }

        // Group required secrets by URL to minimize queries
        val secretsByUrl = mutableMapOf<String, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            secretsByUrl.getOrPut(decl.url.trim()) { mutableListOf() }.add(name)
        }

        val resolved = mutableMapOf<String, String>()

        for ((url, names) in secretsByUrl) {
            val entries = kpaClient.queryLogins(url)

            for (secName in names) {
                val decl = declarationMap.getValue(secName)

                // Interpolate variables in customFilters
                val resolvedFilters = decl.customFilters.mapValues { (_, v) ->
                    var text = v
                    variableValues.forEach { (vk, vv) ->
                        text = text.replace("{{$vk}}", vv)
                    }
                    text
                }

                val matchedEntries = KeePassXcEntryFilter.filterEntries(entries, resolvedFilters)
                if (matchedEntries.isEmpty()) {
                    throw IllegalStateException("No matching KeePassXC entries found for secret '$secName' and URL '$url'.")
                }

                val entry = matchedEntries.first()
                val targetField = decl.targetField.ifBlank { "password" }.trim().lowercase()
                val value = when (targetField) {
                    "password" -> entry.password ?: throw IllegalStateException("Password not found on matched KeePassXC entry for '$secName'.")
                    "login", "username", "user" -> entry.login
                    "totp" -> entry.totp ?: entry.stringFields["totp"] ?: throw IllegalStateException("TOTP not found on matched KeePassXC entry for '$secName'.")
                    "name", "title" -> entry.name
                    "uuid" -> entry.uuid
                    "group" -> entry.group.orEmpty()
                    else -> entry.stringFields[decl.targetField]
                        ?: entry.stringFields[targetField]
                        ?: throw IllegalStateException("Field '${decl.targetField}' not found on matched KeePassXC entry for '$secName'.")
                }

                resolved["_secret.$secName"] = value
            }
        }

        return resolved
    }

    /**
     * Resolves external secret tokens against AWS Secrets Manager.
     *
     * @param requiredSecretNames Set of secret names referenced in requests (e.g. `["client_id"]`).
     * @param declarations List of declared external secrets in the environment.
     * @param region AWS region (e.g. "us-east-1").
     * @param accessKeyId AWS Access Key ID.
     * @param secretAccessKey AWS Secret Access Key.
     * @param sessionToken Optional AWS session token.
     * @param endpointUrl Optional endpoint URL override (e.g. for LocalStack).
     * @param versionStage Optional version stage (defaults to "AWSCURRENT").
     * @param variableValues Active environment variables used for filter interpolation.
     * @param client Optional client override.
     * @return Map of `_secret.<name>` to resolved secret value.
     */
    suspend fun resolveAwsSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        region: String = "us-east-1",
        accessKeyId: String?,
        secretAccessKey: String?,
        sessionToken: String? = null,
        endpointUrl: String? = null,
        versionStage: String? = "AWSCURRENT",
        variableValues: Map<String, String> = emptyMap(),
        client: AwsSecretsManagerClient? = null,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) {
            return emptyMap()
        }

        if (accessKeyId.isNullOrBlank()) {
            throw IllegalStateException("AWS Access Key ID is not configured. Please configure AWS Secrets Manager in Settings -> Secrets Manager.")
        }
        if (secretAccessKey.isNullOrBlank()) {
            throw IllegalStateException("AWS Secret Access Key is not configured. Please configure AWS Secrets Manager in Settings -> Secrets Manager.")
        }

        val declarationMap = declarations.associateBy { it.name.trim() }

        // Validate all required secrets exist and are enabled
        for (name in requiredSecretNames) {
            val decl = declarationMap[name]
                ?: throw IllegalStateException("External secret '_secret.$name' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$name' is disabled in the active environment.")
            }
            val secId = decl.secretId.ifBlank { decl.path }.trim()
            if (secId.isBlank()) {
                throw IllegalStateException("External secret '_secret.$name' does not have a Secret ID configured.")
            }
        }

        // Group required secrets by their Secret ID to avoid duplicate network queries
        val secretsBySecretId = mutableMapOf<String, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            val secId = decl.secretId.ifBlank { decl.path }.trim()
            secretsBySecretId.getOrPut(secId) { mutableListOf() }.add(name)
        }

        val resolved = mutableMapOf<String, String>()
        val aws = client ?: awsClient

        for ((secretId, names) in secretsBySecretId) {
            val secretData = aws.fetchSecret(
                secretId = secretId,
                accessKey = accessKeyId,
                secretKey = secretAccessKey,
                region = region.ifBlank { "us-east-1" },
                sessionToken = sessionToken?.ifBlank { null },
                endpointUrl = endpointUrl?.ifBlank { null },
                versionStage = versionStage?.ifBlank { "AWSCURRENT" },
            )

            for (secName in names) {
                val decl = declarationMap.getValue(secName)

                // Interpolate variables in customFilters
                val resolvedFilters = decl.customFilters.mapValues { (_, v) ->
                    var text = v
                    variableValues.forEach { (vk, vv) ->
                        text = text.replace("{{$vk}}", vv)
                    }
                    text
                }

                for ((filterKey, expectedValue) in resolvedFilters) {
                    val actualValue = secretData[filterKey]
                    if (actualValue != expectedValue) {
                        throw IllegalStateException(
                            "AWS secret '$secretId' did not match custom filter '$filterKey'. Expected: '$expectedValue', found: '$actualValue'."
                        )
                    }
                }

                val target = decl.targetField.trim()
                val value = when {
                    target.isNotBlank() && secretData.containsKey(target) -> secretData.getValue(target)
                    target.isNotBlank() && secretData.containsKey(target.lowercase()) -> secretData.getValue(target.lowercase())
                    target.isBlank() && secretData.containsKey(secName) -> secretData.getValue(secName)
                    target.isBlank() && secretData.containsKey("secret") -> secretData.getValue("secret")
                    target.isBlank() && secretData.containsKey("value") -> secretData.getValue("value")
                    target.isBlank() && secretData.size == 1 -> secretData.values.first()
                    target.isNotBlank() -> throw IllegalStateException(
                        "Field '$target' not found in AWS secret '$secretId'. Available keys: ${secretData.keys.joinToString(", ")}"
                    )
                    else -> throw IllegalStateException(
                        "Target field is not specified and AWS secret '$secretId' contains multiple keys: ${secretData.keys.joinToString(", ")}"
                    )
                }

                resolved["_secret.$secName"] = value
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
        variableValues: Map<String, String> = emptyMap(),
        keepassClient: KeePassXcClient? = null,
        awsRegion: String = "us-east-1",
        awsAccessKeyId: String? = null,
        awsSecretAccessKey: String? = null,
        awsSessionToken: String? = null,
        awsEndpointUrl: String? = null,
        awsVersionStage: String? = "AWSCURRENT",
        awsClient: AwsSecretsManagerClient? = null,
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
        val allSecretsToFetch = mutableSetOf<String>()
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
                allSecretsToFetch.add(varName)
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
            allSecretsToFetch.add(secName)
        }

        if (allSecretsToFetch.isEmpty()) {
            return resolved
        }

        val vaultSecretsToFetch = mutableSetOf<String>()
        val keepassSecretsToFetch = mutableSetOf<String>()
        val awsSecretsToFetch = mutableSetOf<String>()

        for (name in allSecretsToFetch) {
            val decl = declarationMap.getValue(name)
            when {
                decl.providerType.equals("keepassxc", ignoreCase = true) -> keepassSecretsToFetch.add(name)
                decl.providerType.equals("aws-secrets-manager", ignoreCase = true) -> awsSecretsToFetch.add(name)
                else -> vaultSecretsToFetch.add(name)
            }
        }

        // Resolve KeePassXC secrets
        if (keepassSecretsToFetch.isNotEmpty()) {
            val kpResolved = resolveKeePassXcSecrets(
                requiredSecretNames = keepassSecretsToFetch,
                declarations = declarations,
                variableValues = variableValues,
                client = keepassClient,
            )
            for (name in keepassSecretsToFetch) {
                val value = kpResolved["_secret.$name"] ?: continue
                if (name in directSecretNames) {
                    resolved["_secret.$name"] = value
                }
                if (name in secretVarToExternalSecret) {
                    val varName = secretVarToExternalSecret.getValue(name)
                    resolved[varName] = value
                }
            }
        }

        // Resolve AWS secrets
        if (awsSecretsToFetch.isNotEmpty()) {
            val awsResolved = resolveAwsSecrets(
                requiredSecretNames = awsSecretsToFetch,
                declarations = declarations,
                region = awsRegion,
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretAccessKey,
                sessionToken = awsSessionToken,
                endpointUrl = awsEndpointUrl,
                versionStage = awsVersionStage,
                variableValues = variableValues,
                client = awsClient,
            )
            for (name in awsSecretsToFetch) {
                val value = awsResolved["_secret.$name"] ?: continue
                if (name in directSecretNames) {
                    resolved["_secret.$name"] = value
                }
                if (name in secretVarToExternalSecret) {
                    val varName = secretVarToExternalSecret.getValue(name)
                    resolved[varName] = value
                }
            }
        }

        // Resolve Vault secrets
        if (vaultSecretsToFetch.isNotEmpty()) {
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
        }

        return resolved
    }
}
