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

    private const val MAX_POOL_SIZE = 8

    internal data class NetworkClientPoolKey(
        val timeoutMs: Long,
        val followRedirects: Boolean,
        val maxRedirects: Int,
        val proxyEnabled: Boolean,
        val httpProxy: String,
        val httpsProxy: String,
    )

    private val clientPoolLock = Any()
    private val httpClientPool = linkedMapOf<NetworkClientPoolKey, HttpClient>()

    actual fun build(
        settings: AppSettings,
        logger: NetworkLogger,
        retryPolicy: RetryPolicy,
    ): KtorApiClient {
        val timeoutMs = (settings.requestTimeoutMsOverride ?: (settings.requestTimeoutSec.toLong() * 1_000L))
            .coerceAtLeast(1_000L)
        val effectiveFollowRedirects = settings.followRedirects && settings.maxRedirects > 0
        val poolKey = NetworkClientPoolKey(
            timeoutMs = timeoutMs,
            followRedirects = effectiveFollowRedirects,
            maxRedirects = settings.maxRedirects,
            proxyEnabled = settings.proxyEnabled,
            httpProxy = settings.httpProxy.trim(),
            httpsProxy = settings.httpsProxy.trim(),
        )

        val httpClient = synchronized(clientPoolLock) {
            val cached = httpClientPool.remove(poolKey)
            if (cached != null) {
                httpClientPool[poolKey] = cached
                cached
            } else {
                if (httpClientPool.size >= MAX_POOL_SIZE) {
                    val oldestKey = httpClientPool.keys.firstOrNull()
                    if (oldestKey != null) {
                        httpClientPool.remove(oldestKey)?.let { evicted ->
                            runCatching { evicted.close() }
                        }
                    }
                }
                createConfiguredHttpClient(poolKey).also { created ->
                    httpClientPool[poolKey] = created
                }
            }
        }

        return KtorApiClient(
            httpClient = httpClient,
            logger = logger,
            retryPolicy = retryPolicy,
            idleTimeoutMs = timeoutMs,
            allowJson5InJsonBodies = settings.allowJson5InJsonBodies,
            closeHttpClientOnClose = false,
        )
    }

    private fun createConfiguredHttpClient(key: NetworkClientPoolKey): HttpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = key.timeoutMs
                connectTimeoutMillis = minOf(key.timeoutMs, 10_000L)
                socketTimeoutMillis  = key.timeoutMs
            }

            if (key.followRedirects) {
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
                if (key.proxyEnabled) {
                    val proxyUrl = key.httpProxy.ifBlank { key.httpsProxy }
                    if (proxyUrl.isNotBlank()) {
                        proxy = runCatching { ProxyBuilder.http(Url(proxyUrl)) }.getOrNull()
                    }
                }
            }

            expectSuccess = false
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
