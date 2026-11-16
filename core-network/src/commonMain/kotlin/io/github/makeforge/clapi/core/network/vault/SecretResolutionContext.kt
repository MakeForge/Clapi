package io.github.makeforge.clapi.core.network.vault

import io.github.makeforge.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultClient
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.makeforge.clapi.core.network.getEnvironmentVariable
import io.github.makeforge.clapi.core.network.keeper.KeeperClient
import io.github.makeforge.clapi.core.network.keepassxc.KeePassXcClient

data class VaultProviderConfig(
    val url: String? = null,
    val token: String? = null,
    val mountPath: String = "secret",
    val namespace: String? = null,
    val clientOverride: HashiCorpVaultClient? = null,
)

data class AwsProviderConfig(
    val region: String = "us-east-1",
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
    val endpointUrl: String? = null,
    val versionStage: String? = "AWSCURRENT",
    val clientOverride: AwsSecretsManagerClient? = null,
)

data class AzureProviderConfig(
    val vaultName: String = "",
    val tenantId: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val authorityHost: String = "https://login.microsoftonline.com",
    val apiVersion: String = "7.4",
    val clientOverride: AzureKeyVaultClient? = null,
)

data class GcpProviderConfig(
    val projectId: String = "",
    val accessToken: String = "",
    val serviceAccountJson: String = "",
    val endpointUrl: String = "https://secretmanager.googleapis.com",
    val clientOverride: GcpSecretManagerClient? = null,
)

data class KeeperProviderConfig(
    val configJson: String = "",
    val proxyUrl: String = "",
    val clientOverride: KeeperClient? = null,
)

data class KeepassXcProviderConfig(
    val dbPath: String = "",
    val masterKey: String = "",
    val keyFilePath: String = "",
    val clientOverride: KeePassXcClient? = null,
)

data class CustomSecretFilter(
    val name: String,
    val pattern: String,
)

/**
 * Encapsulates all provider credentials, variable layers, declarations, and environment lookups
 * required during secret resolution.
 */
data class SecretResolutionContext(
    val declarations: List<SecretDeclaration> = emptyList(),
    val secretVariableNames: Set<String> = emptySet(),
    val vault: VaultProviderConfig = VaultProviderConfig(),
    val aws: AwsProviderConfig = AwsProviderConfig(),
    val azure: AzureProviderConfig = AzureProviderConfig(),
    val gcp: GcpProviderConfig = GcpProviderConfig(),
    val keeper: KeeperProviderConfig = KeeperProviderConfig(),
    val keepassXc: KeepassXcProviderConfig = KeepassXcProviderConfig(),
    val variableLayers: List<Map<String, String>> = emptyList(),
    val variableValues: Map<String, String> = emptyMap(),
    val customFilters: List<CustomSecretFilter> = emptyList(),
    val processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
) {
    /**
     * Effective variable layers for `VariableResolver.resolve(...)`.
     * Uses [variableLayers] when non-empty; otherwise wraps [variableValues].
     */
    val effectiveVariableLayers: List<Map<String, String>>
        get() = when {
            variableLayers.isNotEmpty() -> variableLayers
            variableValues.isNotEmpty() -> listOf(variableValues)
            else -> emptyList()
        }
}
