package io.github.makeforge.clapi.core.network.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultSaslConfig
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.ReturnListener
import com.rabbitmq.client.ShutdownListener
import io.github.makeforge.clapi.core.model.AmqpAckStatus
import io.github.makeforge.clapi.core.model.MessageDirection
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqQueueConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqReplyStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

actual fun createDefaultRabbitMqClient(): RabbitMqClient = DesktopRabbitMqClient()

class DesktopRabbitMqClient : RabbitMqClient {

    override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult =
        withContext(Dispatchers.IO) {
            val startNanos = System.nanoTime()
            val effectiveExchange = effectiveExchangeName(request.exchange)
            val effectiveRoutingKey = request.routingKey
            var connection: Connection? = null
            var channel: Channel? = null
            try {
                val factory = buildConnectionFactory(
                    connection = request.connection,
                    auth = request.auth,
                    connectionTimeoutMs = request.connectionTimeoutMs,
                    heartbeatSeconds = request.heartbeatSeconds,
                )
                connection = factory.newConnection("clapi-publish")
                channel = connection.createChannel()

                if (request.declareTopology) {
                    declareTopologyIfNeeded(channel, request.exchange, request.queue, effectiveRoutingKey)
                }

                val returnedRef = AtomicReference<ReturnedInfo?>(null)
                if (request.mandatory) {
                    channel.addReturnListener(ReturnListener { replyCode, replyText, _, _, _, _ ->
                        returnedRef.set(ReturnedInfo(replyCode, replyText))
                    })
                }

                if (request.publisherConfirms) {
                    channel.confirmSelect()
                }

                val basicProps = toBasicProperties(request.properties, request.headers)
                channel.basicPublish(
                    effectiveExchange,
                    effectiveRoutingKey,
                    request.mandatory,
                    basicProps,
                    request.bodyBytes,
                )

                var confirmed = false
                if (request.publisherConfirms) {
                    confirmed = channel.waitForConfirms(request.connectionTimeoutMs.coerceIn(1_000L, 60_000L))
                } else if (request.mandatory) {
                    // Brief yield so any immediate basic.return frame arrives before close
                    Thread.sleep(50)
                }

                val returned = returnedRef.get()
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                RabbitMqPublishResult(
                    confirmed = confirmed,
                    returnedUnroutable = returned != null,
                    returnReplyCode = returned?.replyCode,
                    returnReplyText = returned?.replyText,
                    exchange = effectiveExchange,
                    routingKey = effectiveRoutingKey,
                    byteLength = request.bodyBytes.size.toLong(),
                    elapsedMs = elapsedMs,
                    errorMessage = if (request.publisherConfirms && !confirmed && returned == null) {
                        "Broker did not confirm message publication"
                    } else {
                        null
                    },
                )
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                RabbitMqPublishResult(
                    confirmed = false,
                    exchange = effectiveExchange,
                    routingKey = effectiveRoutingKey,
                    byteLength = request.bodyBytes.size.toLong(),
                    elapsedMs = elapsedMs,
                    errorMessage = formatBrokerError(e),
                )
            } finally {
                runCatching { channel?.close() }
                runCatching { connection?.close() }
            }
        }

    override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession =
        withContext(Dispatchers.IO) {
            val statusFlow = MutableStateFlow(RabbitMqConnectionStatus.CONNECTING)
            val errorFlow = MutableStateFlow<String?>(null)
            val messagesFlow = MutableSharedFlow<RabbitMqDeliveryMessage>(
                replay = 0,
                extraBufferCapacity = 512,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

            var connection: Connection? = null
            var channel: Channel? = null
            var consumerTag: String? = null

            try {
                val factory = buildConnectionFactory(
                    connection = request.connection,
                    auth = request.auth,
                    connectionTimeoutMs = request.connectionTimeoutMs,
                    heartbeatSeconds = request.heartbeatSeconds,
                )
                connection = factory.newConnection("clapi-subscribe")
                channel = connection.createChannel()
                channel.basicQos(request.prefetchCount.coerceIn(1, 10_000))

                val exchangeName = request.exchange?.let { effectiveExchangeName(it) }.orEmpty()
                if (request.declareTopology && request.exchange != null && exchangeName.isNotBlank()) {
                    channel.exchangeDeclare(
                        exchangeName,
                        request.exchange.type.value,
                        request.exchange.durable,
                        request.exchange.autoDelete,
                        null,
                    )
                }

                val targetQueue: String = if (request.queue.name.isBlank()) {
                    val declared = channel.queueDeclare("", false, true, true, null).queue
                    if (exchangeName.isNotBlank()) {
                        channel.queueBind(declared, exchangeName, request.routingKey)
                    }
                    declared
                } else {
                    if (request.declareTopology) {
                        channel.queueDeclare(
                            request.queue.name,
                            request.queue.durable,
                            request.queue.exclusive,
                            request.queue.autoDelete,
                            null,
                        )
                        if (exchangeName.isNotBlank()) {
                            channel.queueBind(request.queue.name, exchangeName, request.routingKey)
                        }
                    }
                    request.queue.name
                }

                val autoAck = request.ackMode == AmqpAckMode.AUTO
                val deliverCallback = DeliverCallback { _, delivery ->
                    val msg = deliveryToMessage(
                        delivery = delivery,
                        ackStatus = if (autoAck) AmqpAckStatus.AUTO_ACKED else AmqpAckStatus.PENDING,
                    )
                    messagesFlow.tryEmit(msg)
                }
                val cancelCallback = CancelCallback {
                    statusFlow.value = RabbitMqConnectionStatus.DISCONNECTED
                }

                connection.addShutdownListener(ShutdownListener { cause ->
                    if (!cause.isInitiatedByApplication) {
                        errorFlow.value = cause.message ?: "RabbitMQ connection closed unexpectedly"
                        statusFlow.value = RabbitMqConnectionStatus.ERROR
                    } else if (statusFlow.value != RabbitMqConnectionStatus.DISCONNECTED) {
                        statusFlow.value = RabbitMqConnectionStatus.DISCONNECTED
                    }
                })

                consumerTag = channel.basicConsume(targetQueue, autoAck, deliverCallback, cancelCallback)
                statusFlow.value = RabbitMqConnectionStatus.CONNECTED

                DesktopSubscriptionSession(
                    connection = connection,
                    channel = channel,
                    consumerTag = consumerTag,
                    statusFlow = statusFlow,
                    errorFlow = errorFlow,
                    messagesFlow = messagesFlow,
                )
            } catch (e: Exception) {
                runCatching { channel?.close() }
                runCatching { connection?.close() }
                val message = formatBrokerError(e)
                errorFlow.value = message
                statusFlow.value = RabbitMqConnectionStatus.ERROR
                DesktopSubscriptionSession(
                    connection = null,
                    channel = null,
                    consumerTag = null,
                    statusFlow = statusFlow,
                    errorFlow = errorFlow,
                    messagesFlow = messagesFlow,
                )
            }
        }

    override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult =
        withContext(Dispatchers.IO) {
            val startNanos = System.nanoTime()
            val correlationId = request.properties.correlationId?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            val effectiveExchange = effectiveExchangeName(request.exchange)
            val effectiveRoutingKey = request.routingKey
            var connection: Connection? = null
            var channel: Channel? = null
            var consumerTag: String? = null
            var resolvedReplyTo = ""

            try {
                if (request.replyStrategy == RabbitMqReplyStrategy.NAMED_QUEUE && request.replyQueue.isNullOrBlank()) {
                    return@withContext RabbitMqRpcResult(
                        correlationId = correlationId,
                        replyTo = "",
                        elapsedMs = 0L,
                        errorMessage = "Named reply queue strategy requires a non-blank replyQueue",
                    )
                }

                val factory = buildConnectionFactory(
                    connection = request.connection,
                    auth = request.auth,
                    connectionTimeoutMs = request.connectionTimeoutMs,
                    heartbeatSeconds = request.heartbeatSeconds,
                )
                connection = factory.newConnection("clapi-rpc")
                channel = connection.createChannel()

                if (request.declareTopology) {
                    declareTopologyIfNeeded(channel, request.exchange, request.queue, effectiveRoutingKey)
                }

                val replyDeferred = CompletableDeferred<RabbitMqDeliveryMessage>()
                connection.addShutdownListener(ShutdownListener { cause ->
                    if (!cause.isInitiatedByApplication && !replyDeferred.isCompleted) {
                        replyDeferred.completeExceptionally(
                            IllegalStateException(cause.message ?: "RabbitMQ connection lost during RPC call")
                        )
                    }
                })

                resolvedReplyTo = when (request.replyStrategy) {
                    RabbitMqReplyStrategy.DIRECT_REPLY_TO -> "amq.rabbitmq.reply-to"
                    RabbitMqReplyStrategy.TEMPORARY_QUEUE -> channel.queueDeclare("", false, true, true, null).queue
                    RabbitMqReplyStrategy.NAMED_QUEUE -> {
                        val named = request.replyQueue!!.trim()
                        if (request.declareTopology) {
                            channel.queueDeclare(named, true, false, false, null)
                        }
                        named
                    }
                }

                val isNamedQueue = request.replyStrategy == RabbitMqReplyStrategy.NAMED_QUEUE
                val autoAck = !isNamedQueue
                val localChannel = channel
                val deliverCallback = DeliverCallback { _, delivery ->
                    val incomingCorrId = delivery.properties?.correlationId
                    if (incomingCorrId == correlationId) {
                        if (!autoAck) {
                            runCatching { localChannel.basicAck(delivery.envelope.deliveryTag, false) }
                        }
                        replyDeferred.complete(deliveryToMessage(delivery, AmqpAckStatus.AUTO_ACKED))
                    } else if (!autoAck) {
                        // Requeue unrelated messages on shared named reply queue
                        runCatching { localChannel.basicNack(delivery.envelope.deliveryTag, false, true) }
                    }
                }
                val cancelCallback = CancelCallback {
                    if (!replyDeferred.isCompleted) {
                        replyDeferred.completeExceptionally(IllegalStateException("RPC reply consumer cancelled by broker"))
                    }
                }

                consumerTag = channel.basicConsume(resolvedReplyTo, autoAck, deliverCallback, cancelCallback)

                val effectiveReplyToProp = request.properties.replyTo?.takeIf { it.isNotBlank() } ?: resolvedReplyTo
                val rpcProps = request.properties.copy(
                    correlationId = correlationId,
                    replyTo = effectiveReplyToProp,
                )
                val basicProps = toBasicProperties(rpcProps, request.headers)
                channel.basicPublish(
                    effectiveExchange,
                    effectiveRoutingKey,
                    false,
                    basicProps,
                    request.bodyBytes,
                )

                val replyMsg = withTimeout(request.rpcTimeoutMs.coerceIn(100L, 300_000L)) {
                    replyDeferred.await()
                }
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                RabbitMqRpcResult(
                    correlationId = correlationId,
                    replyTo = effectiveReplyToProp,
                    reply = replyMsg,
                    timedOut = false,
                    elapsedMs = elapsedMs,
                )
            } catch (_: TimeoutCancellationException) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                RabbitMqRpcResult(
                    correlationId = correlationId,
                    replyTo = resolvedReplyTo,
                    timedOut = true,
                    elapsedMs = elapsedMs,
                    errorMessage = "RPC reply timed out after ${request.rpcTimeoutMs} ms (exchange='$effectiveExchange', routingKey='$effectiveRoutingKey')",
                )
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                RabbitMqRpcResult(
                    correlationId = correlationId,
                    replyTo = resolvedReplyTo,
                    timedOut = false,
                    elapsedMs = elapsedMs,
                    errorMessage = formatBrokerError(e),
                )
            } finally {
                if (consumerTag != null) {
                    runCatching { channel?.basicCancel(consumerTag) }
                }
                runCatching { channel?.close() }
                runCatching { connection?.close() }
            }
        }

    private fun buildConnectionFactory(
        connection: RabbitMqConnectionConfig,
        auth: RabbitMqAuthCredentials,
        connectionTimeoutMs: Long,
        heartbeatSeconds: Int,
    ): ConnectionFactory {
        val factory = ConnectionFactory()
        val rawHost = connection.host.trim()
        require(rawHost.isNotBlank()) { "RabbitMQ host must not be blank" }

        var useTls = connection.useTls
        if (rawHost.startsWith("amqp://", ignoreCase = true) || rawHost.startsWith("amqps://", ignoreCase = true)) {
            val uri = URI(rawHost)
            factory.host = uri.host ?: "localhost"
            factory.port = if (uri.port > 0) uri.port else connection.effectivePort()
            val pathVhost = uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() }
            factory.virtualHost = pathVhost ?: connection.vhost.ifBlank { "/" }
            if (uri.scheme.equals("amqps", ignoreCase = true)) {
                useTls = true
            }
        } else {
            val colonIdx = rawHost.lastIndexOf(':')
            if (colonIdx > 0 && rawHost.indexOf(':') == colonIdx && rawHost.substring(colonIdx + 1).toIntOrNull() != null) {
                factory.host = rawHost.substring(0, colonIdx)
                factory.port = rawHost.substring(colonIdx + 1).toInt().coerceIn(1, 65535)
            } else {
                factory.host = rawHost
                factory.port = connection.effectivePort()
            }
            factory.virtualHost = connection.vhost.ifBlank { "/" }
        }

        factory.connectionTimeout = connectionTimeoutMs.coerceIn(500L, 120_000L).toInt()
        factory.requestedHeartbeat = heartbeatSeconds.coerceIn(0, 600)
        factory.isAutomaticRecoveryEnabled = false

        when (auth) {
            is RabbitMqAuthCredentials.None -> {
                // Uses RabbitMQ default guest/guest unless broker allows anonymous
            }
            is RabbitMqAuthCredentials.Plain -> {
                factory.username = auth.username
                factory.password = auth.password
            }
            is RabbitMqAuthCredentials.OAuth2Bearer -> {
                factory.username = auth.username
                factory.password = auth.token
            }
            is RabbitMqAuthCredentials.ExternalTls -> {
                useTls = true
                factory.saslConfig = DefaultSaslConfig.EXTERNAL
                val sslContext = buildTlsContext(auth)
                if (sslContext != null) {
                    factory.useSslProtocol(sslContext)
                } else {
                    factory.useSslProtocol()
                }
            }
        }

        if (useTls && auth !is RabbitMqAuthCredentials.ExternalTls) {
            factory.useSslProtocol()
        }

        return factory
    }

    private fun buildTlsContext(auth: RabbitMqAuthCredentials.ExternalTls): SSLContext? {
        if (auth.certPath.isNullOrBlank() && auth.caCertPath.isNullOrBlank()) return null
        val keyManagers = if (!auth.certPath.isNullOrBlank() && File(auth.certPath).exists()) {
            val ks = KeyStore.getInstance("PKCS12")
            val pass = auth.passphrase?.toCharArray() ?: CharArray(0)
            FileInputStream(auth.certPath).use { ks.load(it, pass) }
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(ks, pass)
            }.keyManagers
        } else {
            null
        }
        val trustManagers = if (!auth.caCertPath.isNullOrBlank() && File(auth.caCertPath).exists()) {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = FileInputStream(auth.caCertPath).use { cf.generateCertificate(it) }
            val ts = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("ca", cert)
            }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(ts)
            }.trustManagers
        } else {
            null
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, trustManagers, null)
        }
    }

    private fun effectiveExchangeName(exchange: RabbitMqExchangeConfig): String =
        if (exchange.type == RabbitMqExchangeType.DEFAULT) "" else exchange.name.trim()

    private fun declareTopologyIfNeeded(
        channel: Channel,
        exchange: RabbitMqExchangeConfig,
        queue: RabbitMqQueueConfig?,
        routingKey: String,
    ) {
        val exchangeName = effectiveExchangeName(exchange)
        if (exchangeName.isNotBlank()) {
            channel.exchangeDeclare(
                exchangeName,
                exchange.type.value,
                exchange.durable,
                exchange.autoDelete,
                null,
            )
        }
        if (queue != null && queue.name.isNotBlank()) {
            channel.queueDeclare(
                queue.name,
                queue.durable,
                queue.exclusive,
                queue.autoDelete,
                null,
            )
            if (exchangeName.isNotBlank()) {
                channel.queueBind(queue.name, exchangeName, routingKey)
            }
        }
    }

    private fun toBasicProperties(
        props: AmqpMessageProperties,
        headers: Map<String, String>,
    ): AMQP.BasicProperties {
        val builder = AMQP.BasicProperties.Builder()
        props.contentType?.takeIf { it.isNotBlank() }?.let { builder.contentType(it) }
        props.contentEncoding?.takeIf { it.isNotBlank() }?.let { builder.contentEncoding(it) }
        props.deliveryMode?.let { builder.deliveryMode(it) }
        props.priority?.let { builder.priority(it) }
        props.correlationId?.takeIf { it.isNotBlank() }?.let { builder.correlationId(it) }
        props.replyTo?.takeIf { it.isNotBlank() }?.let { builder.replyTo(it) }
        props.expiration?.takeIf { it.isNotBlank() }?.let { builder.expiration(it) }
        props.messageId?.takeIf { it.isNotBlank() }?.let { builder.messageId(it) }
        props.type?.takeIf { it.isNotBlank() }?.let { builder.type(it) }
        props.appId?.takeIf { it.isNotBlank() }?.let { builder.appId(it) }
        if (headers.isNotEmpty()) {
            builder.headers(headers.mapValues { it.value as Any })
        }
        return builder.build()
    }

    private fun deliveryToMessage(
        delivery: Delivery,
        ackStatus: AmqpAckStatus,
    ): RabbitMqDeliveryMessage {
        val body = delivery.body ?: ByteArray(0)
        val decodedUtf8 = decodeUtf8OrNull(body)
        val isBinary = decodedUtf8 == null
        val props = delivery.properties
        val headerMap = props?.headers?.entries?.associate { (k, v) ->
            k to (v?.toString() ?: "")
        } ?: emptyMap()

        return RabbitMqDeliveryMessage(
            id = UUID.randomUUID().toString(),
            timestampEpochMillis = System.currentTimeMillis(),
            direction = MessageDirection.RECEIVED,
            deliveryTag = delivery.envelope?.deliveryTag,
            exchange = delivery.envelope?.exchange ?: "",
            routingKey = delivery.envelope?.routingKey ?: "",
            contentType = props?.contentType,
            headers = headerMap,
            properties = AmqpMessageProperties(
                contentType = props?.contentType,
                contentEncoding = props?.contentEncoding,
                deliveryMode = props?.deliveryMode,
                priority = props?.priority,
                correlationId = props?.correlationId,
                replyTo = props?.replyTo,
                expiration = props?.expiration,
                messageId = props?.messageId,
                type = props?.type,
                appId = props?.appId,
            ),
            payloadText = decodedUtf8,
            payloadBytesBase64 = if (isBinary) Base64.getEncoder().encodeToString(body) else null,
            isBinary = isBinary,
            byteLength = body.size.toLong(),
            ackStatus = ackStatus,
        )
    }

    private fun decodeUtf8OrNull(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            if (decoded.any { it.code < 0x09 || (it.code in 0x0E..0x1F) }) null else decoded
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun formatBrokerError(e: Exception): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "RabbitMQ error"
        return when {
            msg.contains("ACCESS_REFUSED", ignoreCase = true) -> "Authentication failed: $msg"
            msg.contains("NOT_ALLOWED", ignoreCase = true) -> "Virtual host or permission denied: $msg"
            msg.contains("Connection refused", ignoreCase = true) -> "Broker unreachable (connection refused): $msg"
            msg.contains("SSL", ignoreCase = true) || msg.contains("TLS", ignoreCase = true) -> "TLS handshake failed: $msg"
            else -> msg
        }
    }

    private data class ReturnedInfo(val replyCode: Int, val replyText: String)

    private class DesktopSubscriptionSession(
        private val connection: Connection?,
        private val channel: Channel?,
        private val consumerTag: String?,
        private val statusFlow: MutableStateFlow<RabbitMqConnectionStatus>,
        private val errorFlow: MutableStateFlow<String?>,
        private val messagesFlow: MutableSharedFlow<RabbitMqDeliveryMessage>,
    ) : RabbitMqSubscriptionSession {

        override val status: StateFlow<RabbitMqConnectionStatus> = statusFlow.asStateFlow()
        override val errorMessage: StateFlow<String?> = errorFlow.asStateFlow()
        override val messages: Flow<RabbitMqDeliveryMessage> = messagesFlow.asSharedFlow()

        override suspend fun ack(deliveryTag: Long) {
            withContext(Dispatchers.IO) {
                channel?.basicAck(deliveryTag, false)
            }
        }

        override suspend fun nack(deliveryTag: Long, requeue: Boolean) {
            withContext(Dispatchers.IO) {
                channel?.basicNack(deliveryTag, false, requeue)
            }
        }

        override suspend fun close() {
            withContext(Dispatchers.IO) {
                statusFlow.value = RabbitMqConnectionStatus.DISCONNECTING
                if (consumerTag != null) {
                    runCatching { channel?.basicCancel(consumerTag) }
                }
                runCatching { channel?.close() }
                runCatching { connection?.close() }
                statusFlow.value = RabbitMqConnectionStatus.DISCONNECTED
            }
        }
    }
}
