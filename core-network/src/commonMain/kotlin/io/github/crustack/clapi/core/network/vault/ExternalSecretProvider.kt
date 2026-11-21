package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.crustack.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultClient
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultConfig
import io.github.crustack.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerConfig
import io.github.crustack.clapi.core.network.gcp.KtorGcpSecretManagerClient
import io.github.crustack.clapi.core.network.keeper.KeeperRecordFilter
import io.github.crustack.clapi.core.network.keeper.createKeeperClient
import io.github.crustack.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClientManager
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcEntryFilter
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus

/**
 * Pluggable strategy contract for resolving secrets from an external secret manager backend
 * (e.g., HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager,
 * Keeper Security Cloud, KeePassXC).
 */
interface ExternalSecretProvider {
    /** Canonical provider identifier (e.g., "hashicorp-vault-server", "aws-secrets-manager", etc.). */
    val providerId: String

    /** Returns true if required credentials/endpoints for this provider are present in [context]. */
    fun isConfigured(context: SecretResolutionContext): Boolean

    /**
     * Resolves a single secret by [secretName] and optional JSON [fieldKey] using [context].
     */
    suspend fun resolveSecret(
        secretName: String,
        fieldKey: String?,
        context: SecretResolutionContext,
    ): String? {
        val decl = context.declarations.find { it.name.trim() == secretName }
            ?: SecretDeclaration(
                name = secretName,
                path = secretName,
                secretId = secretName,
                recordTitle = secretName,
                targetField = fieldKey.orEmpty(),
                providerType = providerId,
            )
        val effectiveDecl = if (!fieldKey.isNullOrBlank()) decl.copy(targetField = fieldKey) else decl
        val mergedDecls = context.declarations.filterNot { it.name.trim() == secretName } + effectiveDecl
        val resolved = resolveDeclarations(setOf(secretName), mergedDecls, context)
        return resolved["_secret.$secretName"]
    }

    /**
     * Resolves all requested [requiredSecretNames] matching [declarations] for this provider.
     * Returns a map of `"_secret.<name>"` -> resolved secret value.
     */
    suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> = emptyMap()
}

internal fun interpolateCustomFilters(
    customFilters: Map<String, String>,
    context: SecretResolutionContext,
): Map<String, String> {
    if (customFilters.isEmpty()) return emptyMap()
    val layers = context.effectiveVariableLayers
    if (layers.isEmpty()) return customFilters
    return customFilters.mapValues { (_, v) ->
        VariableResolver.resolve(v, layers, removeUnresolved = false)
    }
}

class VaultSecretProvider(
    private val defaultVaultClient: HashiCorpVaultClient = KtorHashiCorpVaultClient(),
) : ExternalSecretProvider {
    override val providerId: String = "hashicorp-vault-server"

    override fun isConfigured(context: SecretResolutionContext): Boolean =
        !context.vault.url.isNullOrBlank() && !context.vault.token.isNullOrBlank()

    override suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) return emptyMap()
        val vaultUrl = context.vault.url
        val vaultToken = context.vault.token
        if (vaultUrl.isNullOrBlank()) {
            throw IllegalStateException("Vault URL is not configured. Please configure HashiCorp Vault in Settings -> Secrets Manager.")
        }
        if (vaultToken.isNullOrBlank()) {
            throw IllegalStateException("Vault token is not configured. Please configure HashiCorp Vault in Settings -> Secrets Manager.")
        }

        val declarationMap = declarations.associateBy { it.name.trim() }
        for (name in requiredSecretNames) {
            val decl = declarationMap[name]
                ?: throw IllegalStateException("External secret '_secret.$name' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$name' is disabled in the active environment.")
            }
        }

        val secretsByPath = mutableMapOf<String, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            secretsByPath.getOrPut(decl.path.trim()) { mutableListOf() }.add(name)
        }

        val client = context.vault.clientOverride ?: defaultVaultClient
        val resolved = mutableMapOf<String, String>()
        for ((path, names) in secretsByPath) {
            val vaultData = client.fetchSecret(
                vaultUrl = vaultUrl,
                token = vaultToken,
                secretPath = path,
                mountPath = context.vault.mountPath,
                namespace = context.vault.namespace,
            )

            for (name in names) {
                val decl = declarationMap.getValue(name)
                val resolvedFilters = interpolateCustomFilters(decl.customFilters, context)
                for ((filterKey, expectedValue) in resolvedFilters) {
                    val actualValue = vaultData[filterKey]
                    if (actualValue != expectedValue) {
                        throw IllegalStateException(
                            "Vault secret '$path' did not match custom filter '$filterKey'. Expected: '$expectedValue', found: '$actualValue'."
                        )
                    }
                }
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

class AwsSecretProvider(
    private val defaultAwsClient: AwsSecretsManagerClient = KtorAwsSecretsManagerClient(),
) : ExternalSecretProvider {
    override val providerId: String = "aws-secrets-manager"

    override fun isConfigured(context: SecretResolutionContext): Boolean =
        !context.aws.accessKeyId.isNullOrBlank() && !context.aws.secretAccessKey.isNullOrBlank()

    override suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) return emptyMap()
        val accessKeyId = context.aws.accessKeyId
        val secretAccessKey = context.aws.secretAccessKey
        if (accessKeyId.isNullOrBlank()) {
            throw IllegalStateException("AWS Access Key ID is not configured. Please configure AWS Secrets Manager in Settings -> Secrets Manager.")
        }
        if (secretAccessKey.isNullOrBlank()) {
            throw IllegalStateException("AWS Secret Access Key is not configured. Please configure AWS Secrets Manager in Settings -> Secrets Manager.")
        }

        val declarationMap = declarations.associateBy { it.name.trim() }
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

        val secretsBySecretId = mutableMapOf<String, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            val secId = decl.secretId.ifBlank { decl.path }.trim()
            secretsBySecretId.getOrPut(secId) { mutableListOf() }.add(name)
        }

        val resolved = mutableMapOf<String, String>()
        val aws = context.aws.clientOverride ?: defaultAwsClient

        for ((secretId, names) in secretsBySecretId) {
            val secretData = aws.fetchSecret(
                secretId = secretId,
                accessKey = accessKeyId,
                secretKey = secretAccessKey,
                region = context.aws.region.ifBlank { "us-east-1" },
                sessionToken = context.aws.sessionToken?.ifBlank { null },
                endpointUrl = context.aws.endpointUrl?.ifBlank { null },
                versionStage = context.aws.versionStage?.ifBlank { "AWSCURRENT" },
            )

            for (secName in names) {
                val decl = declarationMap.getValue(secName)
                val resolvedFilters = interpolateCustomFilters(decl.customFilters, context)
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
}

class AzureSecretProvider(
    private val defaultAzureClient: AzureKeyVaultClient = KtorAzureKeyVaultClient(),
) : ExternalSecretProvider {
    override val providerId: String = "azure-key-vault"

    override fun isConfigured(context: SecretResolutionContext): Boolean =
        context.azure.vaultName.isNotBlank() &&
            context.azure.tenantId.isNotBlank() &&
            context.azure.clientId.isNotBlank() &&
            context.azure.clientSecret.isNotBlank()

    override suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) return emptyMap()
        val declarationMap = declarations.associateBy { it.name.trim() }

        for (name in requiredSecretNames) {
            val decl = declarationMap[name]
                ?: throw IllegalStateException("External secret '_secret.$name' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$name' is disabled in the active environment.")
            }
            val secId = decl.secretId.ifBlank { decl.path }.trim()
            if (secId.isBlank()) {
                throw IllegalStateException("External secret '_secret.$name' does not have a Secret Name configured.")
            }
        }

        val secretsByTarget = mutableMapOf<Pair<String, String>, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            val secName = decl.secretId.ifBlank { decl.path }.trim()
            val effectiveVault = decl.url.ifBlank { context.azure.vaultName }.trim()
            if (effectiveVault.isBlank()) {
                throw IllegalStateException("Azure Key Vault Name is not configured. Please configure Azure Key Vault in Settings -> Secrets Manager.")
            }
            secretsByTarget.getOrPut(Pair(effectiveVault, secName)) { mutableListOf() }.add(name)
        }

        val resolved = mutableMapOf<String, String>()
        val azClient = context.azure.clientOverride ?: defaultAzureClient

        for ((target, names) in secretsByTarget) {
            val (effectiveVault, secretName) = target
            val config = AzureKeyVaultConfig(
                vaultName = effectiveVault,
                secretName = secretName,
                tenantId = context.azure.tenantId,
                clientId = context.azure.clientId,
                clientSecret = context.azure.clientSecret,
                authorityHost = context.azure.authorityHost.ifBlank { "https://login.microsoftonline.com" },
                apiVersion = context.azure.apiVersion.ifBlank { "7.4" },
            )
            val secretData = azClient.fetchSecret(config)

            for (secName in names) {
                val decl = declarationMap.getValue(secName)
                val resolvedFilters = interpolateCustomFilters(decl.customFilters, context)
                for ((filterKey, expectedValue) in resolvedFilters) {
                    val actualValue = secretData[filterKey]
                    if (actualValue != expectedValue) {
                        throw IllegalStateException(
                            "Azure Key Vault secret '$secretName' did not match custom filter '$filterKey'. Expected: '$expectedValue', found: '$actualValue'."
                        )
                    }
                }

                val targetField = decl.targetField.trim()
                val value = when {
                    targetField.isNotBlank() && secretData.containsKey(targetField) -> secretData.getValue(targetField)
                    targetField.isNotBlank() && secretData.containsKey(targetField.lowercase()) -> secretData.getValue(targetField.lowercase())
                    targetField.isBlank() && secretData.containsKey(secName) -> secretData.getValue(secName)
                    targetField.isBlank() && secretData.containsKey("secret") -> secretData.getValue("secret")
                    targetField.isBlank() && secretData.containsKey("value") -> secretData.getValue("value")
                    targetField.isBlank() && secretData.size == 1 -> secretData.values.first()
                    targetField.isNotBlank() -> throw IllegalStateException(
                        "Field '$targetField' not found in Azure Key Vault secret '$secretName'. Available keys: ${secretData.keys.joinToString(", ")}"
                    )
                    else -> throw IllegalStateException(
                        "Target field is not specified and Azure Key Vault secret '$secretName' contains multiple keys: ${secretData.keys.joinToString(", ")}"
                    )
                }

                resolved["_secret.$secName"] = value
            }
        }
        return resolved
    }
}

class GcpSecretProvider(
    private val defaultGcpClient: GcpSecretManagerClient = KtorGcpSecretManagerClient(),
) : ExternalSecretProvider {
    override val providerId: String = "gcp-secrets-manager"

    override fun isConfigured(context: SecretResolutionContext): Boolean =
        context.gcp.projectId.isNotBlank() &&
            (context.gcp.accessToken.isNotBlank() || context.gcp.serviceAccountJson.isNotBlank())

    override suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) return emptyMap()
        val declarationMap = declarations.associateBy { it.name.trim() }

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

        val secretsByTarget = mutableMapOf<Triple<String, String, String>, MutableList<String>>()
        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            val secId = decl.secretId.ifBlank { decl.path }.trim()
            val effectiveProject = decl.url.ifBlank { context.gcp.projectId }.trim()
            if (effectiveProject.isBlank()) {
                throw IllegalStateException("GCP Project ID is not configured. Please configure GCP Secret Manager in Settings -> Secrets Manager.")
            }
            val version = decl.version.ifBlank { "latest" }.trim()
            secretsByTarget.getOrPut(Triple(effectiveProject, secId, version)) { mutableListOf() }.add(name)
        }

        val resolved = mutableMapOf<String, String>()
        val gcClient = context.gcp.clientOverride ?: defaultGcpClient

        for ((target, names) in secretsByTarget) {
            val (effectiveProject, secretId, version) = target
            val config = GcpSecretManagerConfig(
                projectId = effectiveProject,
                secretId = secretId,
                version = version,
                accessToken = context.gcp.accessToken,
                endpointUrl = context.gcp.endpointUrl.ifBlank { "https://secretmanager.googleapis.com" },
            )
            val secretData = gcClient.fetchSecret(config)

            for (secName in names) {
                val decl = declarationMap.getValue(secName)
                val resolvedFilters = interpolateCustomFilters(decl.customFilters, context)
                for ((filterKey, expectedValue) in resolvedFilters) {
                    val actualValue = secretData[filterKey]
                    if (actualValue != expectedValue) {
                        throw IllegalStateException(
                            "GCP Secret Manager secret '$secretId' did not match custom filter '$filterKey'. Expected: '$expectedValue', found: '$actualValue'."
                        )
                    }
                }

                val targetField = decl.targetField.trim()
                val value = when {
                    targetField.isNotBlank() && secretData.containsKey(targetField) -> secretData.getValue(targetField)
                    targetField.isNotBlank() && secretData.containsKey(targetField.lowercase()) -> secretData.getValue(targetField.lowercase())
                    targetField.isBlank() && secretData.containsKey(secName) -> secretData.getValue(secName)
                    targetField.isBlank() && secretData.containsKey("secret") -> secretData.getValue("secret")
                    targetField.isBlank() && secretData.containsKey("value") -> secretData.getValue("value")
                    targetField.isBlank() && secretData.size == 1 -> secretData.values.first()
                    targetField.isNotBlank() -> throw IllegalStateException(
                        "Field '$targetField' not found in GCP Secret Manager secret '$secretId'. Available keys: ${secretData.keys.joinToString(", ")}"
                    )
                    else -> throw IllegalStateException(
                        "Target field is not specified and GCP Secret Manager secret '$secretId' contains multiple keys: ${secretData.keys.joinToString(", ")}"
                    )
                }

                resolved["_secret.$secName"] = value
            }
        }
        return resolved
    }
}

class KeeperSecretProvider : ExternalSecretProvider {
    override val providerId: String = "keeper-security-cloud"

    override fun isConfigured(context: SecretResolutionContext): Boolean =
        context.keeper.configJson.isNotBlank() || context.keeper.clientOverride != null

    override suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) return emptyMap()
        if (!isConfigured(context)) {
            throw IllegalStateException("Keeper Secrets Manager configuration is missing. Please connect to Keeper Security Cloud in Settings -> Secrets Manager.")
        }

        val declarationMap = declarations.associateBy { it.name.trim() }
        for (name in requiredSecretNames) {
            val decl = declarationMap[name]
                ?: throw IllegalStateException("External secret '_secret.$name' is not defined in the active environment.")
            if (!decl.enabled) {
                throw IllegalStateException("External secret '_secret.$name' is disabled in the active environment.")
            }
            val recordTitle = decl.recordTitle.ifBlank { decl.path }.trim()
            if (recordTitle.isBlank()) {
                throw IllegalStateException("External secret '_secret.$name' does not have a Record Title or UID configured.")
            }
        }

        val keeper = context.keeper.clientOverride ?: createKeeperClient(context.keeper.configJson)
        val titlesToFetch = requiredSecretNames.map { name ->
            val decl = declarationMap.getValue(name)
            decl.recordTitle.ifBlank { decl.path }.trim()
        }.distinct()

        val allRecords = keeper.getRecords(titlesToFetch)
        val resolved = mutableMapOf<String, String>()

        for (name in requiredSecretNames) {
            val decl = declarationMap.getValue(name)
            val recordTitle = decl.recordTitle.ifBlank { decl.path }.trim()
            val resolvedFilters = interpolateCustomFilters(decl.customFilters, context)

            val matchingRecords = KeeperRecordFilter.filterRecords(allRecords, recordTitle, resolvedFilters)
            if (matchingRecords.isEmpty()) {
                val filterMsg = if (resolvedFilters.isNotEmpty()) " with filters $resolvedFilters" else ""
                throw IllegalStateException("No Keeper record found matching title/UID '$recordTitle'$filterMsg for external secret '_secret.$name'.")
            }

            val matchedRecord = matchingRecords.first()
            val targetField = decl.targetField.ifBlank { "password" }.trim()
            val value = KeeperRecordFilter.extractTargetFieldValue(matchedRecord, targetField)
                ?: throw IllegalStateException("Field '$targetField' not found in Keeper record '$recordTitle' (${matchedRecord.recordUid}) for external secret '_secret.$name'.")

            resolved["_secret.$name"] = value
        }
        return resolved
    }
}

class KeepassXcSecretProvider(
    private val keepassClientManager: KeePassXcClientManager = DefaultKeePassXcClientManager.Shared,
) : ExternalSecretProvider {
    override val providerId: String = "keepassxc"

    override fun isConfigured(context: SecretResolutionContext): Boolean = true

    override suspend fun resolveDeclarations(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        if (requiredSecretNames.isEmpty()) return emptyMap()
        val declarationMap = declarations.associateBy { it.name.trim() }

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

        val kpaClient = context.keepassXc.clientOverride ?: keepassClientManager.getClient()
        when (val status = kpaClient.checkStatus()) {
            is KeePassXcStatus.DatabaseLocked -> throw IllegalStateException("KeePassXC database is locked. Please unlock your database.")
            is KeePassXcStatus.Unavailable -> throw IllegalStateException("Cannot connect to KeePassXC: ${status.reason}")
            is KeePassXcStatus.NotAssociated -> throw IllegalStateException("KeePassXC is not associated. Please approve the association prompt in KeePassXC.")
            is KeePassXcStatus.Connected -> Unit
        }

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
                val resolvedFilters = interpolateCustomFilters(decl.customFilters, context)
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
}
