package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
import io.github.makeforge.clapi.core.model.opencollection.AuthConfig
import io.github.makeforge.clapi.core.model.opencollection.ClientCertificate
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqQueueConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqReplyStrategy
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.scripting.RequestContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Orchestrates variable interpolation, auth inheritance resolution, and RabbitMQ client execution
 * for Publish, Subscribe, and RPC operations on [RabbitMqRequestItem].
 */
class RabbitMqRequestExecutor(
    private val client: RabbitMqClient = createDefaultRabbitMqClient(),
) {

    suspend fun publish(
        item: RabbitMqRequestItem,
        variableLayers: List<Map<String, String>> = emptyList(),
        ancestorAuthChain: List<AuthConfig?> = emptyList(),
        clientCertificates: List<ClientCertificate> = emptyList(),
        userDynamicVariables: List<DynamicVariable> = emptyList(),
        requestContext: RequestContext? = null,
        resolvedOAuth2Token: String? = null,
    ): RabbitMqPublishResult {
        val details = item.rabbitmq
        val settings = item.settings ?: RabbitMqRequestSettings()

        val resolve: (String) -> String = { raw ->
            VariableResolver.resolve(
                value = raw,
                variableLayers = variableLayers,
                userDynamicVariables = userDynamicVariables,
                requestContext = requestContext,
            )
        }

        val resolvedConn = resolveConnection(details.connection, resolve)
        if (resolvedConn.host.isBlank()) {
            return RabbitMqPublishResult(
                confirmed = false,
                errorMessage = "RabbitMQ host must not be blank",
            )
        }

        val resolvedExchange = resolveExchange(details.exchange ?: RabbitMqExchangeConfig(), resolve)
        val resolvedQueue = details.queue?.let { resolveQueue(it, resolve) }
        val resolvedRoutingKey = resolve(details.routingKey)
        val resolvedHeaders = resolveHeaders(details.headers, resolve)
        val resolvedProps = resolveProperties(details.properties ?: AmqpMessageProperties(), resolve)
        val bodyBytes = resolvePayloadBytes(details.message, resolve)
        val auth = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = details.auth,
            ancestorAuthChain = ancestorAuthChain,
            variableLayers = variableLayers,
            useTlsClientCert = settings.useTlsClientCert,
            clientCertificates = clientCertificates,
            resolvedOAuth2Token = resolvedOAuth2Token,
        )

        return client.publish(
            RabbitMqPublishRequest(
                connection = resolvedConn,
                auth = auth,
                exchange = resolvedExchange,
                queue = resolvedQueue,
                routingKey = resolvedRoutingKey,
                headers = resolvedHeaders,
                properties = resolvedProps,
                bodyBytes = bodyBytes,
                connectionTimeoutMs = settings.clampedConnectionTimeoutMs(),
                heartbeatSeconds = settings.clampedHeartbeatSeconds(),
                publisherConfirms = settings.publisherConfirms,
                mandatory = settings.mandatory,
                declareTopology = settings.declareTopology,
            )
        )
    }

    suspend fun subscribe(
        item: RabbitMqRequestItem,
        variableLayers: List<Map<String, String>> = emptyList(),
        ancestorAuthChain: List<AuthConfig?> = emptyList(),
        clientCertificates: List<ClientCertificate> = emptyList(),
        userDynamicVariables: List<DynamicVariable> = emptyList(),
        requestContext: RequestContext? = null,
        resolvedOAuth2Token: String? = null,
    ): RabbitMqSubscriptionSession {
        val details = item.rabbitmq
        val settings = item.settings ?: RabbitMqRequestSettings()

        val resolve: (String) -> String = { raw ->
            VariableResolver.resolve(
                value = raw,
                variableLayers = variableLayers,
                userDynamicVariables = userDynamicVariables,
                requestContext = requestContext,
            )
        }

        val resolvedConn = resolveConnection(details.connection, resolve)
        val resolvedExchange = details.exchange?.let { resolveExchange(it, resolve) }
        val resolvedQueue = resolveQueue(details.queue ?: RabbitMqQueueConfig(), resolve)
        val resolvedRoutingKey = resolve(details.routingKey)
        val auth = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = details.auth,
            ancestorAuthChain = ancestorAuthChain,
            variableLayers = variableLayers,
            useTlsClientCert = settings.useTlsClientCert,
            clientCertificates = clientCertificates,
            resolvedOAuth2Token = resolvedOAuth2Token,
        )

        return client.subscribe(
            RabbitMqSubscribeRequest(
                connection = resolvedConn,
                auth = auth,
                exchange = resolvedExchange,
                queue = resolvedQueue,
                routingKey = resolvedRoutingKey,
                connectionTimeoutMs = settings.clampedConnectionTimeoutMs(),
                heartbeatSeconds = settings.clampedHeartbeatSeconds(),
                declareTopology = settings.declareTopology,
                ackMode = settings.ackMode,
                prefetchCount = settings.clampedPrefetchCount(),
            )
        )
    }

    suspend fun rpc(
        item: RabbitMqRequestItem,
        variableLayers: List<Map<String, String>> = emptyList(),
        ancestorAuthChain: List<AuthConfig?> = emptyList(),
        clientCertificates: List<ClientCertificate> = emptyList(),
        userDynamicVariables: List<DynamicVariable> = emptyList(),
        requestContext: RequestContext? = null,
        resolvedOAuth2Token: String? = null,
    ): RabbitMqRpcResult {
        val details = item.rabbitmq
        val settings = item.settings ?: RabbitMqRequestSettings()

        val resolve: (String) -> String = { raw ->
            VariableResolver.resolve(
                value = raw,
                variableLayers = variableLayers,
                userDynamicVariables = userDynamicVariables,
                requestContext = requestContext,
            )
        }

        val resolvedConn = resolveConnection(details.connection, resolve)
        if (resolvedConn.host.isBlank()) {
            return RabbitMqRpcResult(
                correlationId = "",
                errorMessage = "RabbitMQ host must not be blank",
            )
        }

        val resolvedReplyQueue = details.replyQueue?.let(resolve)?.takeIf { it.isNotBlank() }
        if (settings.replyStrategy == RabbitMqReplyStrategy.NAMED_QUEUE && resolvedReplyQueue.isNullOrBlank()) {
            return RabbitMqRpcResult(
                correlationId = "",
                errorMessage = "Named reply queue strategy requires a non-blank replyQueue",
            )
        }

        val resolvedExchange = resolveExchange(details.exchange ?: RabbitMqExchangeConfig(), resolve)
        val resolvedQueue = details.queue?.let { resolveQueue(it, resolve) }
        val resolvedRoutingKey = resolve(details.routingKey)
        val resolvedHeaders = resolveHeaders(details.headers, resolve)
        val resolvedProps = resolveProperties(details.properties ?: AmqpMessageProperties(), resolve)
        val bodyBytes = resolvePayloadBytes(details.message, resolve)
        val auth = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = details.auth,
            ancestorAuthChain = ancestorAuthChain,
            variableLayers = variableLayers,
            useTlsClientCert = settings.useTlsClientCert,
            clientCertificates = clientCertificates,
            resolvedOAuth2Token = resolvedOAuth2Token,
        )

        return client.rpc(
            RabbitMqRpcRequest(
                connection = resolvedConn,
                auth = auth,
                exchange = resolvedExchange,
                queue = resolvedQueue,
                routingKey = resolvedRoutingKey,
                replyQueue = resolvedReplyQueue,
                replyStrategy = settings.replyStrategy,
                headers = resolvedHeaders,
                properties = resolvedProps,
                bodyBytes = bodyBytes,
                connectionTimeoutMs = settings.clampedConnectionTimeoutMs(),
                heartbeatSeconds = settings.clampedHeartbeatSeconds(),
                declareTopology = settings.declareTopology,
                rpcTimeoutMs = settings.clampedRpcTimeoutMs(),
            )
        )
    }

    private fun resolveConnection(
        conn: RabbitMqConnectionConfig,
        resolve: (String) -> String,
    ): RabbitMqConnectionConfig = conn.copy(
        host = resolve(conn.host).trim(),
        vhost = resolve(conn.vhost).ifBlank { "/" },
    )

    private fun resolveExchange(
        ex: RabbitMqExchangeConfig,
        resolve: (String) -> String,
    ): RabbitMqExchangeConfig = ex.copy(name = resolve(ex.name).trim())

    private fun resolveQueue(
        q: RabbitMqQueueConfig,
        resolve: (String) -> String,
    ): RabbitMqQueueConfig = q.copy(name = resolve(q.name).trim())

    private fun resolveHeaders(
        headers: List<io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader>,
        resolve: (String) -> String,
    ): Map<String, String> = headers
        .filter { it.disabled != true && it.name.isNotBlank() }
        .associate { resolve(it.name) to resolve(it.value) }

    private fun resolveProperties(
        props: AmqpMessageProperties,
        resolve: (String) -> String,
    ): AmqpMessageProperties = props.copy(
        contentType = props.contentType?.let(resolve),
        contentEncoding = props.contentEncoding?.let(resolve),
        correlationId = props.correlationId?.let(resolve),
        replyTo = props.replyTo?.let(resolve),
        expiration = props.expiration?.let(resolve),
        messageId = props.messageId?.let(resolve),
        type = props.type?.let(resolve),
        appId = props.appId?.let(resolve),
    )

    @OptIn(ExperimentalEncodingApi::class)
    private fun resolvePayloadBytes(
        payload: WebSocketMessagePayload?,
        resolve: (String) -> String,
    ): ByteArray {
        val activeMsg = when (payload) {
            null -> return ByteArray(0)
            is WebSocketMessagePayload.Single -> payload.message
            is WebSocketMessagePayload.Variants -> {
                payload.variants.firstOrNull { it.selected == true }?.message
                    ?: payload.variants.firstOrNull()?.message
                    ?: return ByteArray(0)
            }
        }
        val resolvedData = resolve(activeMsg.data)
        return if (activeMsg.type == io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType.BINARY) {
            runCatching { Base64.decode(resolvedData.trim()) }.getOrElse { resolvedData.encodeToByteArray() }
        } else {
            resolvedData.encodeToByteArray()
        }
    }
}
