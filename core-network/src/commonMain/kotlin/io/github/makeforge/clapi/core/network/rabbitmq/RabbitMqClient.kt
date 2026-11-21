package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqQueueConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqReplyStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface RabbitMqAuthCredentials {
    data object None : RabbitMqAuthCredentials
    data class Plain(val username: String, val password: String) : RabbitMqAuthCredentials
    data class OAuth2Bearer(val token: String, val username: String = "") : RabbitMqAuthCredentials
    data class ExternalTls(
        val certPath: String? = null,
        val keyPath: String? = null,
        val passphrase: String? = null,
        val caCertPath: String? = null,
    ) : RabbitMqAuthCredentials
}

data class RabbitMqPublishRequest(
    val connection: RabbitMqConnectionConfig,
    val auth: RabbitMqAuthCredentials = RabbitMqAuthCredentials.None,
    val exchange: RabbitMqExchangeConfig = RabbitMqExchangeConfig(),
    val queue: RabbitMqQueueConfig? = null,
    val routingKey: String = "",
    val headers: Map<String, String> = emptyMap(),
    val properties: AmqpMessageProperties = AmqpMessageProperties(),
    val bodyBytes: ByteArray = ByteArray(0),
    val connectionTimeoutMs: Long = 10_000L,
    val heartbeatSeconds: Int = 30,
    val publisherConfirms: Boolean = true,
    val mandatory: Boolean = false,
    val declareTopology: Boolean = false,
)

data class RabbitMqSubscribeRequest(
    val connection: RabbitMqConnectionConfig,
    val auth: RabbitMqAuthCredentials = RabbitMqAuthCredentials.None,
    val exchange: RabbitMqExchangeConfig? = null,
    val queue: RabbitMqQueueConfig = RabbitMqQueueConfig(),
    val routingKey: String = "",
    val connectionTimeoutMs: Long = 10_000L,
    val heartbeatSeconds: Int = 30,
    val declareTopology: Boolean = false,
    val ackMode: AmqpAckMode = AmqpAckMode.AUTO,
    val prefetchCount: Int = 50,
)

data class RabbitMqRpcRequest(
    val connection: RabbitMqConnectionConfig,
    val auth: RabbitMqAuthCredentials = RabbitMqAuthCredentials.None,
    val exchange: RabbitMqExchangeConfig = RabbitMqExchangeConfig(),
    val queue: RabbitMqQueueConfig? = null,
    val routingKey: String = "",
    val replyQueue: String? = null,
    val replyStrategy: RabbitMqReplyStrategy = RabbitMqReplyStrategy.DIRECT_REPLY_TO,
    val headers: Map<String, String> = emptyMap(),
    val properties: AmqpMessageProperties = AmqpMessageProperties(),
    val bodyBytes: ByteArray = ByteArray(0),
    val connectionTimeoutMs: Long = 10_000L,
    val heartbeatSeconds: Int = 30,
    val declareTopology: Boolean = false,
    val rpcTimeoutMs: Long = 15_000L,
)

interface RabbitMqSubscriptionSession {
    val status: StateFlow<RabbitMqConnectionStatus>
    val errorMessage: StateFlow<String?>
    val messages: Flow<RabbitMqDeliveryMessage>
    suspend fun ack(deliveryTag: Long)
    suspend fun nack(deliveryTag: Long, requeue: Boolean = false)
    suspend fun close()
}

interface RabbitMqClient {
    suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult
    suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession
    suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult
}

expect fun createDefaultRabbitMqClient(): RabbitMqClient
