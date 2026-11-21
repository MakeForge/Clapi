package io.github.makeforge.clapi.ui.shared.network

import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.NetworkLogger
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.network.grpc.ProtoParser
import io.github.makeforge.clapi.ui.shared.state.AppSettings

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

    fun createProtoParser(): ProtoParser

    fun buildGrpcClient(settings: AppSettings): DynamicGrpcClient
}
