package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.aws.KtorAwsSecretsManagerClient
import io.github.makeforge.clapi.core.network.azure.AzureKeyVaultClient
import io.github.makeforge.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.github.makeforge.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.makeforge.clapi.core.network.gcp.KtorGcpSecretManagerClient
import io.github.makeforge.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.vault.HashiCorpVaultClient
import io.github.makeforge.clapi.core.network.vault.KtorHashiCorpVaultClient
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService

/**
 * Session-wide shared default clients for request execution and variable/secret resolution.
 *
 * Prevents default parameter expressions in `sendRequest`, `sendGraphQlRequest`, `sendGrpcRequest`,
 * and `RequestVariableResolution` from allocating unclosed `HttpClient(CIO)` engines on every call.
 */
object SharedExecutionClients {
    val oauth2TokenClient: OAuth2TokenClient
        get() = KtorOAuth2TokenClient.Shared

    val vaultClient: HashiCorpVaultClient
        get() = KtorHashiCorpVaultClient.Shared

    val awsSecretsClient: AwsSecretsManagerClient
        get() = KtorAwsSecretsManagerClient.Shared

    val gcpSecretsClient: GcpSecretManagerClient
        get() = KtorGcpSecretManagerClient.Shared

    val azureKeyVaultClient: AzureKeyVaultClient
        get() = KtorAzureKeyVaultClient.Shared

    val secretResolutionService: SecretResolutionService
        get() = SecretResolutionService.Shared
}
