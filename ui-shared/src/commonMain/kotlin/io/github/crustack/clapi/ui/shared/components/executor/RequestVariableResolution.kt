package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultClient
import io.github.crustack.clapi.core.network.dynamicvar.DynamicVariableContext
import io.github.crustack.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.crustack.clapi.core.network.dynamicvar.OAuth2ResolvedConfig
import io.github.crustack.clapi.core.network.getEnvironmentVariable
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClient
import io.github.crustack.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenState
import io.github.crustack.clapi.core.network.vault.SecretDeclaration
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.ui.shared.platform.PlatformStorage
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState

internal suspend fun resolveExternalSecretsForTab(
    state: AppState,
    tab: RequestTabState,
    textsToScan: Iterable<String>,
    secretResolutionService: SecretResolutionService = SecretResolutionService(),
    keepassClient: KeePassXcClient? = null,
    awsClient: AwsSecretsManagerClient? = null,
    azureClient: AzureKeyVaultClient? = null,
    processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
): Map<String, String>? {
    val secretVariableNames = state.selectedEnvironment?.variables
        ?.filter { it.enabled && it.secret && it.key.isNotBlank() }
        ?.map { it.key.trim() }
        ?.toSet()
        ?: emptySet()

    val provider = state.selectedEnvironment?.externalSecretsProvider?.ifBlank { "hashicorp-vault-server" } ?: "hashicorp-vault-server"

    val declarations = state.selectedEnvironment?.externalSecrets?.map {
        val defaultTarget = if (provider.equals("aws-secrets-manager", ignoreCase = true) || provider.equals("azure-key-vault", ignoreCase = true)) "" else "password"
        SecretDeclaration(
            name = it.name.trim(),
            path = it.path.trim(),
            enabled = it.enabled,
            providerType = provider,
            url = it.url.trim(),
            targetField = it.targetField.ifBlank { defaultTarget }.trim(),
            customFilters = it.customFilters.toMap(),
            secretId = it.path.trim(),
        )
    } ?: emptyList()

    val envVarsMap = state.selectedEnvironment?.variables
        ?.filter { it.enabled && it.key.isNotBlank() }
        ?.associate { it.key.trim() to it.value }
        ?: emptyMap()

    return try {
        val awsSecretAccessKey = state.settings.awsSecretAccessKey.ifBlank {
            PlatformStorage.getSecretString("clapi.settings.awsSecretAccessKey").orEmpty()
        }
        val awsSessionToken = state.settings.awsSessionToken.ifBlank {
            PlatformStorage.getSecretString("clapi.settings.awsSessionToken").orEmpty()
        }
        val azureClientSecret = state.settings.azureClientSecret.ifBlank {
            PlatformStorage.getSecretString("settings.azureClientSecret").orEmpty()
        }

        secretResolutionService.resolveAllSecrets(
            texts = textsToScan,
            declarations = declarations,
            secretVariableNames = secretVariableNames,
            vaultUrl = state.settings.vaultServerUrl,
            vaultToken = state.settings.vaultToken.ifBlank {
                PlatformStorage.getSecretString("clapi.settings.vaultToken").orEmpty()
            },
            mountPath = state.settings.vaultMountPath.ifBlank { "secret" },
            namespace = state.settings.vaultNamespace.ifBlank { null },
            variableValues = envVarsMap,
            keepassClient = keepassClient,
            awsRegion = state.settings.awsRegion.ifBlank { "us-east-1" },
            awsAccessKeyId = state.settings.awsAccessKeyId.ifBlank { null },
            awsSecretAccessKey = awsSecretAccessKey.ifBlank { null },
            awsSessionToken = awsSessionToken.ifBlank { null },
            awsEndpointUrl = state.settings.awsEndpointUrl.ifBlank { null },
            awsVersionStage = state.settings.awsVersionStage.ifBlank { "AWSCURRENT" },
            awsClient = awsClient,
            azureVaultName = state.settings.azureVaultName,
            azureTenantId = state.settings.azureTenantId,
            azureClientId = state.settings.azureClientId,
            azureClientSecret = azureClientSecret,
            azureAuthorityHost = state.settings.azureAuthorityHost,
            azureApiVersion = state.settings.azureApiVersion,
            azureClient = azureClient,
            processEnvProvider = processEnvProvider,
        )
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
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
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

    return DynamicVariableContext(
        effectiveAuthType = effectiveAuthType,
        oauth2Config = oauth2Config,
        oauth2TokenState = currentTokenState,
        variableLayers = variableLayers,
        oauth2TokenClient = tokenClient,
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
    )
}

internal suspend fun resolveDynamicVariablesForTab(
    state: AppState,
    tab: RequestTabState,
    textsToScan: Iterable<String>,
    variableLayers: List<Map<String, String>>,
    registry: DynamicVariableRegistry = state.dynamicVariableRegistry,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
): Map<String, String>? {
    val context = buildDynamicVariableContext(state, tab, variableLayers, tokenClient)
    return try {
        registry.resolveDynamicVariables(textsToScan, context)
    } catch (e: Exception) {
        val errorMsg = e.message ?: "Failed to resolve dynamic variables"
        tab.lastError = errorMsg
        tab.isLoading = false
        tab.currentJob = null
        state.log("Dynamic variable resolution error: $errorMsg", LogLevel.ERROR)
        null
    }
}
