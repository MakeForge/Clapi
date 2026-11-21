package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultClient
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableContext
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.makeforge.clapi.core.network.dynamicvar.OAuth2ResolvedConfig
import io.github.makeforge.clapi.core.scripting.RequestContext
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.makeforge.clapi.core.network.getEnvironmentVariable
import io.github.makeforge.clapi.core.network.keeper.KeeperClient
import io.github.makeforge.clapi.core.network.keepassxc.KeePassXcClient
import io.github.makeforge.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenManager
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState
import io.github.makeforge.clapi.core.network.vault.AwsProviderConfig
import io.github.makeforge.clapi.core.network.vault.AzureProviderConfig
import io.github.makeforge.clapi.core.network.vault.GcpProviderConfig
import io.github.makeforge.clapi.core.network.vault.KeeperProviderConfig
import io.github.makeforge.clapi.core.network.vault.KeepassXcProviderConfig
import io.github.makeforge.clapi.core.network.vault.SecretDeclaration
import io.github.makeforge.clapi.core.network.vault.SecretResolutionContext
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.core.network.vault.VaultProviderConfig
import io.github.makeforge.clapi.ui.shared.persistence.SettingsStorageKeys
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.CancellationException

internal suspend fun resolveExternalSecretsForTab(
    state: AppState,
    tab: RequestTabState,
    textsToScan: Iterable<String>,
    secretResolutionService: SecretResolutionService = SharedExecutionClients.secretResolutionService,
    keepassClient: KeePassXcClient? = null,
    awsClient: AwsSecretsManagerClient? = null,
    azureClient: AzureKeyVaultClient? = null,
    gcpClient: GcpSecretManagerClient? = null,
    keeperClient: KeeperClient? = null,
    processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
): Map<String, String>? {
    val secretVariableNames = state.selectedEnvironment?.variables
        ?.filter { it.enabled && it.secret && it.key.isNotBlank() }
        ?.map { it.key.trim() }
        ?.toSet()
        ?: emptySet()

    val provider = state.selectedEnvironment?.externalSecretsProvider?.ifBlank { "hashicorp-vault-server" } ?: "hashicorp-vault-server"

    val declarations = state.selectedEnvironment?.externalSecrets?.map {
        val defaultTarget = when (ExternalSecretType.fromValue(provider)) {
            ExternalSecretType.AWS_SECRETS_MANAGER,
            ExternalSecretType.AZURE_KEY_VAULT,
            ExternalSecretType.GCP_SECRETS_MANAGER -> ""
            else -> "password"
        }
        SecretDeclaration(
            name = it.name.trim(),
            path = it.path.trim(),
            enabled = it.enabled,
            providerType = provider,
            url = it.url.trim(),
            targetField = it.targetField.ifBlank { defaultTarget }.trim(),
            customFilters = it.customFilters.toMap(),
            secretId = it.path.trim(),
            recordTitle = it.path.trim(),
        )
    } ?: emptyList()

    val envVarsMap = state.selectedEnvironment?.toVariableMap(state.environments) ?: emptyMap()
    val variableLayers = state.activeVariableLayers(tab)

    return try {
        val vaultToken = state.settings.vaultToken.ifBlank {
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.VAULT_TOKEN).orEmpty()
        }
        val awsSecretAccessKey = state.settings.awsSecretAccessKey.ifBlank {
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.AWS_SECRET_ACCESS_KEY).orEmpty()
        }
        val awsSessionToken = state.settings.awsSessionToken.ifBlank {
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.AWS_SESSION_TOKEN).orEmpty()
        }
        val azureClientSecret = state.settings.azureClientSecret.ifBlank {
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.AZURE_CLIENT_SECRET).orEmpty()
        }
        val gcpAccessToken = state.settings.gcpAccessToken.ifBlank {
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.GCP_ACCESS_TOKEN).orEmpty()
        }
        val keeperConfigJson = state.settings.keeperConfigJson.ifBlank {
            SettingsStorageKeys.getSecretWithFallback(SettingsStorageKeys.KEEPER_CONFIG).orEmpty()
        }

        val context = SecretResolutionContext(
            declarations = declarations,
            secretVariableNames = secretVariableNames,
            vault = VaultProviderConfig(
                url = state.settings.vaultServerUrl,
                token = vaultToken,
                mountPath = state.settings.vaultMountPath.ifBlank { "secret" },
                namespace = state.settings.vaultNamespace.ifBlank { null },
            ),
            aws = AwsProviderConfig(
                region = state.settings.awsRegion.ifBlank { "us-east-1" },
                accessKeyId = state.settings.awsAccessKeyId.ifBlank { null },
                secretAccessKey = awsSecretAccessKey.ifBlank { null },
                sessionToken = awsSessionToken.ifBlank { null },
                endpointUrl = state.settings.awsEndpointUrl.ifBlank { null },
                versionStage = state.settings.awsVersionStage.ifBlank { "AWSCURRENT" },
                clientOverride = awsClient,
            ),
            azure = AzureProviderConfig(
                vaultName = state.settings.azureVaultName,
                tenantId = state.settings.azureTenantId,
                clientId = state.settings.azureClientId,
                clientSecret = azureClientSecret,
                authorityHost = state.settings.azureAuthorityHost,
                apiVersion = state.settings.azureApiVersion,
                clientOverride = azureClient,
            ),
            gcp = GcpProviderConfig(
                projectId = state.settings.gcpProjectId,
                accessToken = gcpAccessToken,
                endpointUrl = state.settings.gcpEndpointUrl,
                clientOverride = gcpClient,
            ),
            keeper = KeeperProviderConfig(
                configJson = keeperConfigJson,
                clientOverride = keeperClient,
            ),
            keepassXc = KeepassXcProviderConfig(
                clientOverride = keepassClient,
            ),
            variableLayers = variableLayers,
            variableValues = envVarsMap,
            processEnvProvider = processEnvProvider,
        )

        secretResolutionService.resolveAllSecrets(
            texts = textsToScan,
            context = context,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val errorMsg = e.message ?: "Failed to resolve external secrets"
        tab.lastError = errorMsg
        tab.isLoading = false
        tab.currentJob = null
        state.log("Secret resolution error: $errorMsg", LogLevel.ERROR)
        null
    }
}

internal fun buildDynamicVariableContext(
    state: AppState,
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    tokenClient: OAuth2TokenClient = SharedExecutionClients.oauth2TokenClient,
    tokenManager: OAuth2TokenManager = OAuth2TokenManager.Shared,
): DynamicVariableContext {
    val effectiveAuthType = if (tab.authType == AuthType.INHERIT) {
        state.resolveInheritedAuth(tab)?.authType ?: AuthType.NONE
    } else {
        tab.authType
    }

    val inherited = if (tab.authType == AuthType.INHERIT) state.resolveInheritedAuth(tab) else null

    val flow = inherited?.oauth2Flow ?: tab.authOAuth2Flow
    val rawTokenUrl = inherited?.oauth2AccessTokenUrl ?: tab.authOAuth2AccessTokenUrl
    val rawRefreshTokenUrl = (inherited?.oauth2RefreshTokenUrl ?: tab.authOAuth2RefreshTokenUrl).ifBlank { rawTokenUrl }
    val rawClientId = inherited?.oauth2ClientId ?: tab.authOAuth2ClientId
    val rawClientSecret = inherited?.oauth2ClientSecret ?: tab.authOAuth2ClientSecret
    val credentialsPlacement = inherited?.oauth2CredentialsPlacement ?: tab.authOAuth2CredentialsPlacement
    val rawUsername = inherited?.oauth2Username ?: tab.authOAuth2Username
    val rawPassword = inherited?.oauth2Password ?: tab.authOAuth2Password
    val rawScope = inherited?.oauth2Scope ?: tab.authOAuth2Scope

    val oauth2Config = if (effectiveAuthType == AuthType.OAUTH2) {
        OAuth2ResolvedConfig(
            flow = flow,
            tokenUrl = rawTokenUrl,
            refreshTokenUrl = rawRefreshTokenUrl,
            clientId = rawClientId,
            clientSecret = rawClientSecret,
            credentialsPlacement = credentialsPlacement,
            username = rawUsername,
            password = rawPassword,
            scope = rawScope.ifBlank { null },
        )
    } else {
        null
    }

    val currentTokenState = OAuth2TokenState(
        accessToken = tab.authOAuth2AccessToken,
        refreshToken = tab.authOAuth2RefreshToken.ifBlank { null },
        idToken = tab.authOAuth2IdToken.ifBlank { null },
        expiresAtEpochMs = tab.authOAuth2ExpiresAtEpochMs,
    )

    val requestHeaders = tab.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .associate {
            VariableResolver.resolve(it.key, variableLayers) to VariableResolver.resolve(it.value, variableLayers)
        }
    val requestContext = RequestContext(
        url = VariableResolver.resolve(tab.url, variableLayers),
        method = tab.method.name,
        headers = requestHeaders,
        name = tab.name,
    )

    return DynamicVariableContext(
        effectiveAuthType = effectiveAuthType,
        oauth2Config = oauth2Config,
        oauth2TokenState = currentTokenState,
        variableLayers = variableLayers,
        oauth2TokenClient = tokenClient,
        oauth2TokenManager = tokenManager,
        onOAuth2TokenUpdated = { accessToken, refreshToken, idToken, expiresAtEpochMs ->
            tab.authOAuth2AccessToken = accessToken
            if (!refreshToken.isNullOrBlank()) {
                tab.authOAuth2RefreshToken = refreshToken
            }
            if (!idToken.isNullOrBlank()) {
                tab.authOAuth2IdToken = idToken
            }
            if (expiresAtEpochMs != null) {
                tab.authOAuth2ExpiresAtEpochMs = expiresAtEpochMs
            }
            tab.authOAuth2FetchError = null
        },
        log = { msg, isError ->
            state.log(msg, if (isError) LogLevel.ERROR else LogLevel.INFO)
        },
        userDynamicVariables = state.activeDynamicVariables(tab),
        requestContext = requestContext,
    )
}

internal suspend fun resolveDynamicVariablesForTab(
    state: AppState,
    tab: RequestTabState,
    textsToScan: Iterable<String>,
    variableLayers: List<Map<String, String>>,
    registry: DynamicVariableRegistry = state.dynamicVariableRegistry,
    tokenClient: OAuth2TokenClient = SharedExecutionClients.oauth2TokenClient,
    tokenManager: OAuth2TokenManager = OAuth2TokenManager.Shared,
): Map<String, String>? {
    val context = buildDynamicVariableContext(state, tab, variableLayers, tokenClient, tokenManager)
    return try {
        registry.resolveDynamicVariables(textsToScan, context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val errorMsg = e.message ?: "Failed to resolve dynamic variables"
        tab.lastError = errorMsg
        tab.isLoading = false
        tab.currentJob = null
        state.log("Dynamic variable resolution error: $errorMsg", LogLevel.ERROR)
        null
    }
}
