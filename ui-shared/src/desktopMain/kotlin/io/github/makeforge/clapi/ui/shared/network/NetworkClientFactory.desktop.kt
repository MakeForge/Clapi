package io.github.makeforge.clapi.ui.shared.network

import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.NetworkLogger
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.network.grpc.NettyDynamicGrpcClient
import io.github.makeforge.clapi.core.network.grpc.ProtoParser
import io.github.makeforge.clapi.core.network.grpc.ProtocJarParser
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual object NetworkClientFactory {

    actual fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy,
    ): KtorApiClient {
        val timeoutMs = settings.requestTimeoutSec.toLong() * 1_000L

        val httpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = minOf(timeoutMs, 10_000L)
                socketTimeoutMillis  = timeoutMs
            }

            if (settings.followRedirects) {
                install(HttpRedirect) {
                    checkHttpMethod = false
                    allowHttpsDowngrade = true
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                    explicitNulls = false
                })
            }

            install(WebSockets)

            engine {
                if (settings.proxyEnabled) {
                    val proxyUrl = settings.httpProxy.trim().ifBlank { settings.httpsProxy.trim() }
                    if (proxyUrl.isNotBlank()) {
                        proxy = runCatching { ProxyBuilder.http(Url(proxyUrl)) }.getOrNull()
                    }
                }
            }

            expectSuccess = false
        }

        return KtorApiClient(
            httpClient = httpClient,
            logger = logger,
            retryPolicy = retryPolicy,
            idleTimeoutMs = timeoutMs,
            allowJson5InJsonBodies = settings.allowJson5InJsonBodies,
        )
    }

    actual fun createProtoParser(): ProtoParser = ProtocJarParser()

    actual fun buildGrpcClient(settings: AppSettings): DynamicGrpcClient {
        val timeoutMs = (settings.requestTimeoutSec.toLong() * 1_000L).coerceAtLeast(1_000L)
        return NettyDynamicGrpcClient(
            protoParser = ProtocJarParser(),
            timeoutMs = timeoutMs,
        )
    }
}
