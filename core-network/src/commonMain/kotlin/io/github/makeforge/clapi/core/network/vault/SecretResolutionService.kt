package io.github.makeforge.clapi.core.network.vault

import io.github.makeforge.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultClient
import io.github.makeforge.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.makeforge.clapi.core.network.gcp.KtorGcpSecretManagerClient
import io.github.makeforge.clapi.core.network.getEnvironmentVariable
import io.github.makeforge.clapi.core.network.keeper.KeeperClient
import io.github.makeforge.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import io.github.makeforge.clapi.core.network.keepassxc.KeePassXcClient
import io.github.makeforge.clapi.core.network.keepassxc.KeePassXcClientManager

data class SecretDeclaration(
    val name: String,
    val path: String = "",
    val enabled: Boolean = true,
    val providerType: String = "hashicorp-vault-server",
    val url: String = "",
    val targetField: String = "password",
    val customFilters: Map<String, String> = emptyMap(),
    val secretId: String = path,
    val version: String = "latest",
    val recordTitle: String = path,
)

class SecretResolutionService(
    private val vaultClient: HashiCorpVaultClient = KtorHashiCorpVaultClient.Shared,
    private val keepassClientManager: KeePassXcClientManager = DefaultKeePassXcClientManager.Shared,
    private val awsClient: AwsSecretsManagerClient = KtorAwsSecretsManagerClient.Shared,
    private val azureClient: AzureKeyVaultClient = KtorAzureKeyVaultClient.Shared,
    private val gcpClient: GcpSecretManagerClient = KtorGcpSecretManagerClient.Shared,
    val providers: List<ExternalSecretProvider> = listOf(
        VaultSecretProvider(vaultClient),
        AwsSecretProvider(awsClient),
        AzureSecretProvider(azureClient),
        GcpSecretProvider(gcpClient),
        KeeperSecretProvider(),
        KeepassXcSecretProvider(keepassClientManager),
    ),
) {
    companion object {
        val Shared: SecretResolutionService by lazy { SecretResolutionService() }
    }
    private val variablePattern = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")

    private fun providerFor(providerType: String): ExternalSecretProvider {
        val normalized = providerType.trim().lowercase()
        return providers.firstOrNull { it.providerId.equals(normalized, ignoreCase = true) }
            ?: providers.first { it.providerId == "hashicorp-vault-server" }
    }

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
     */
    suspend fun resolveSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        vaultUrl: String?,
        vaultToken: String?,
        mountPath: String = "secret",
        namespace: String? = null,
    ): Map<String, String> = providerFor("hashicorp-vault-server").resolveDeclarations(
        requiredSecretNames = requiredSecretNames,
        declarations = declarations,
        context = SecretResolutionContext(
            declarations = declarations,
            vault = VaultProviderConfig(
                url = vaultUrl,
                token = vaultToken,
                mountPath = mountPath,
                namespace = namespace,
            ),
        ),
    )

    /**
     * Resolves external secret tokens against KeePassXC.
     */
    suspend fun resolveKeePassXcSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        variableValues: Map<String, String> = emptyMap(),
        client: KeePassXcClient? = null,
    ): Map<String, String> = providerFor("keepassxc").resolveDeclarations(
        requiredSecretNames = requiredSecretNames,
        declarations = declarations,
        context = SecretResolutionContext(
            declarations = declarations,
            keepassXc = KeepassXcProviderConfig(clientOverride = client),
            variableValues = variableValues,
        ),
    )

    /**
     * Resolves external secret tokens against AWS Secrets Manager.
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
    ): Map<String, String> = providerFor("aws-secrets-manager").resolveDeclarations(
        requiredSecretNames = requiredSecretNames,
        declarations = declarations,
        context = SecretResolutionContext(
            declarations = declarations,
            aws = AwsProviderConfig(
                region = region,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                sessionToken = sessionToken,
                endpointUrl = endpointUrl,
                versionStage = versionStage,
                clientOverride = client,
            ),
            variableValues = variableValues,
        ),
    )

    /**
     * Resolves external secret tokens against Azure Key Vault.
     */
    suspend fun resolveAzureSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        vaultName: String,
        tenantId: String,
        clientId: String,
        clientSecret: String,
        authorityHost: String = "https://login.microsoftonline.com",
        apiVersion: String = "7.4",
        variableValues: Map<String, String> = emptyMap(),
        client: AzureKeyVaultClient? = null,
    ): Map<String, String> = providerFor("azure-key-vault").resolveDeclarations(
        requiredSecretNames = requiredSecretNames,
        declarations = declarations,
        context = SecretResolutionContext(
            declarations = declarations,
            azure = AzureProviderConfig(
                vaultName = vaultName,
                tenantId = tenantId,
                clientId = clientId,
                clientSecret = clientSecret,
                authorityHost = authorityHost,
                apiVersion = apiVersion,
                clientOverride = client,
            ),
            variableValues = variableValues,
        ),
    )

    /**
     * Resolves external secret tokens against GCP Secret Manager.
     */
    suspend fun resolveGcpSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        projectId: String,
        accessToken: String,
        endpointUrl: String = "https://secretmanager.googleapis.com",
        variableValues: Map<String, String> = emptyMap(),
        client: GcpSecretManagerClient? = null,
    ): Map<String, String> = providerFor("gcp-secrets-manager").resolveDeclarations(
        requiredSecretNames = requiredSecretNames,
        declarations = declarations,
        context = SecretResolutionContext(
            declarations = declarations,
            gcp = GcpProviderConfig(
                projectId = projectId,
                accessToken = accessToken,
                endpointUrl = endpointUrl,
                clientOverride = client,
            ),
            variableValues = variableValues,
        ),
    )

    /**
     * Resolves external secret tokens against Keeper Security Cloud.
     */
    suspend fun resolveKeeperSecrets(
        requiredSecretNames: Set<String>,
        declarations: List<SecretDeclaration>,
        configJson: String,
        variableValues: Map<String, String> = emptyMap(),
        client: KeeperClient? = null,
    ): Map<String, String> = providerFor("keeper-security-cloud").resolveDeclarations(
        requiredSecretNames = requiredSecretNames,
        declarations = declarations,
        context = SecretResolutionContext(
            declarations = declarations,
            keeper = KeeperProviderConfig(
                configJson = configJson,
                clientOverride = client,
            ),
            variableValues = variableValues,
        ),
    )

    /**
     * Resolves secret tokens from both direct `{{_secret.<name>}}` references and OpenCollection
     * `secret: true` environment variables referenced as `{{<name>}}` using [SecretResolutionContext]
     * and the registered [ExternalSecretProvider] strategies.
     */
    suspend fun resolveAllSecrets(
        texts: Iterable<String>,
        context: SecretResolutionContext,
    ): Map<String, String> {
        val allTokens = extractAllVariableTokens(texts)
        if (allTokens.isEmpty()) return emptyMap()

        val directSecretNames = allTokens
            .filter { it.startsWith("_secret.") }
            .map { it.removePrefix("_secret.").trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val referencedSecretVars = allTokens
            .filter { it in context.secretVariableNames }
            .toSet()

        if (directSecretNames.isEmpty() && referencedSecretVars.isEmpty()) {
            return emptyMap()
        }

        val resolved = mutableMapOf<String, String>()
        val declarationMap = context.declarations.associateBy { it.name.trim() }
        val allSecretsToFetch = mutableSetOf<String>()
        val secretVarToExternalSecret = mutableMapOf<String, String>()

        for (varName in referencedSecretVars) {
            // 1. Process environment variable
            val envValue = context.processEnvProvider(varName)
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

        val secretsByProvider = mutableMapOf<ExternalSecretProvider, MutableSet<String>>()
        for (name in allSecretsToFetch) {
            val decl = declarationMap.getValue(name)
            val provider = providerFor(decl.providerType)
            secretsByProvider.getOrPut(provider) { mutableSetOf() }.add(name)
        }

        for ((provider, names) in secretsByProvider) {
            val providerResolved = provider.resolveDeclarations(
                requiredSecretNames = names,
                declarations = context.declarations,
                context = context,
            )
            for (name in names) {
                val value = providerResolved["_secret.$name"] ?: continue
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

    /**
     * Backwards-compatible overload of [resolveAllSecrets] that constructs a [SecretResolutionContext]
     * and delegates to `resolveAllSecrets(texts, context)`.
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
        azureVaultName: String = "",
        azureTenantId: String = "",
        azureClientId: String = "",
        azureClientSecret: String = "",
        azureAuthorityHost: String = "https://login.microsoftonline.com",
        azureApiVersion: String = "7.4",
        azureClient: AzureKeyVaultClient? = null,
        gcpProjectId: String = "",
        gcpAccessToken: String = "",
        gcpEndpointUrl: String = "https://secretmanager.googleapis.com",
        gcpClient: GcpSecretManagerClient? = null,
        keeperConfigJson: String = "",
        keeperClient: KeeperClient? = null,
        processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
    ): Map<String, String> = resolveAllSecrets(
        texts = texts,
        context = SecretResolutionContext(
            declarations = declarations,
            secretVariableNames = secretVariableNames,
            vault = VaultProviderConfig(
                url = vaultUrl,
                token = vaultToken,
                mountPath = mountPath,
                namespace = namespace,
            ),
            aws = AwsProviderConfig(
                region = awsRegion,
                accessKeyId = awsAccessKeyId,
                secretAccessKey = awsSecretAccessKey,
                sessionToken = awsSessionToken,
                endpointUrl = awsEndpointUrl,
                versionStage = awsVersionStage,
                clientOverride = awsClient,
            ),
            azure = AzureProviderConfig(
                vaultName = azureVaultName,
                tenantId = azureTenantId,
                clientId = azureClientId,
                clientSecret = azureClientSecret,
                authorityHost = azureAuthorityHost,
                apiVersion = azureApiVersion,
                clientOverride = azureClient,
            ),
            gcp = GcpProviderConfig(
                projectId = gcpProjectId,
                accessToken = gcpAccessToken,
                endpointUrl = gcpEndpointUrl,
                clientOverride = gcpClient,
            ),
            keeper = KeeperProviderConfig(
                configJson = keeperConfigJson,
                clientOverride = keeperClient,
            ),
            keepassXc = KeepassXcProviderConfig(
                clientOverride = keepassClient,
            ),
            variableValues = variableValues,
            processEnvProvider = processEnvProvider,
        ),
    )
}
