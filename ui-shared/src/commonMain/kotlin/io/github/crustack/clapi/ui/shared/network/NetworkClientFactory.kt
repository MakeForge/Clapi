package io.github.crustack.clapi.ui.shared.network

import io.github.crustack.clapi.core.network.KtorApiClient
import io.github.crustack.clapi.core.network.NetworkLogger
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.ui.shared.state.AppSettings

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
