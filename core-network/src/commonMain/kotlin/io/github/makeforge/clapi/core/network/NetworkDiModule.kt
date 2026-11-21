// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenManager
import io.github.makeforge.clapi.core.network.vault.AwsSecretProvider
import io.github.makeforge.clapi.core.network.vault.AzureSecretProvider
import io.github.makeforge.clapi.core.network.vault.ExternalSecretProvider
import io.github.makeforge.clapi.core.network.vault.GcpSecretProvider
import io.github.makeforge.clapi.core.network.vault.KeeperSecretProvider
import io.github.makeforge.clapi.core.network.vault.KeepassXcSecretProvider
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.core.network.vault.VaultSecretProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun networkModule(
    retryPolicy: RetryPolicy = RetryPolicy(),
    interceptors: List<NetworkInterceptor> = emptyList(),
    logger: NetworkLogger = Slf4jNetworkLogger()
): Module = module {
    single<ApiClient> {
        KtorApiClient(
            retryPolicy = retryPolicy,
            interceptors = interceptors,
            logger = logger
        )
    }
    single { OAuth2TokenManager() }
    single<List<ExternalSecretProvider>> {
        listOf(
            VaultSecretProvider(),
            AwsSecretProvider(),
            AzureSecretProvider(),
            GcpSecretProvider(),
            KeeperSecretProvider(),
            KeepassXcSecretProvider(),
        )
    }
    single { SecretResolutionService(providers = get()) }
}
