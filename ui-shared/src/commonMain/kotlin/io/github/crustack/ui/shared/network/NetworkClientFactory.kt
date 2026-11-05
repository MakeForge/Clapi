package io.github.crustack.ui.shared.network

import io.github.crustack.core.network.KtorApiClient
import io.github.crustack.core.network.NetworkLogger
import io.github.crustack.core.network.RetryPolicy
import io.github.crustack.ui.shared.state.AppSettings

/**
 * Builds a [KtorApiClient] fully configured from the current [AppSettings].
 *
 * Platform actuals provide the correct Ktor engine (CIO on Desktop, Js on Web).
 */
expect object NetworkClientFactory {

    fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): KtorApiClient
}
