package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RabbitMqOperationMode(val value: String, val badge: String) {
    @SerialName("publish")
    PUBLISH("publish", "PUB"),

    @SerialName("subscribe")
    SUBSCRIBE("subscribe", "SUB"),

    @SerialName("rpc")
    RPC("rpc", "RPC");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): RabbitMqOperationMode? =
            entries.firstOrNull {
                it.value.equals(value, ignoreCase = true) ||
                    it.badge.equals(value, ignoreCase = true)
            }
    }
}

@Serializable
enum class RabbitMqExchangeType(val value: String) {
    @SerialName("default")
    DEFAULT("default"),

    @SerialName("direct")
    DIRECT("direct"),

    @SerialName("topic")
    TOPIC("topic"),

    @SerialName("fanout")
    FANOUT("fanout"),

    @SerialName("headers")
    HEADERS("headers");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): RabbitMqExchangeType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class AmqpAckMode(val value: String) {
    @SerialName("auto")
    AUTO("auto"),

    @SerialName("manual")
    MANUAL("manual");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): AmqpAckMode? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class RabbitMqReplyStrategy(val value: String) {
    @SerialName("direct_reply_to")
    DIRECT_REPLY_TO("direct_reply_to"),

    @SerialName("temporary_queue")
    TEMPORARY_QUEUE("temporary_queue"),

    @SerialName("named_queue")
    NAMED_QUEUE("named_queue");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): RabbitMqReplyStrategy? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class RabbitMqConnectionConfig(
    val host: String = "localhost",
    val port: Int? = 5672,
    val vhost: String = "/",
    val useTls: Boolean = false,
) {
    fun effectivePort(): Int = (port ?: if (useTls) 5671 else 5672).coerceIn(1, 65535)
}

@Serializable
data class RabbitMqExchangeConfig(
    val name: String = "",
    val type: RabbitMqExchangeType = RabbitMqExchangeType.DIRECT,
    val durable: Boolean = true,
    val autoDelete: Boolean = false,
)

@Serializable
data class RabbitMqQueueConfig(
    val name: String = "",
    val durable: Boolean = true,
    val exclusive: Boolean = false,
    val autoDelete: Boolean = false,
)

@Serializable
data class AmqpMessageProperties(
    val contentType: String? = "application/json",
    val contentEncoding: String? = null,
    val deliveryMode: Int? = 2,
    val priority: Int? = null,
    val correlationId: String? = null,
    val replyTo: String? = null,
    val expiration: String? = null,
    val messageId: String? = null,
    val type: String? = null,
    val appId: String? = null,
)

@Serializable
data class RabbitMqRequestDetails(
    val mode: RabbitMqOperationMode = RabbitMqOperationMode.PUBLISH,
    val connection: RabbitMqConnectionConfig = RabbitMqConnectionConfig(),
    val exchange: RabbitMqExchangeConfig? = null,
    val queue: RabbitMqQueueConfig? = null,
    val routingKey: String = "",
    val replyQueue: String? = null,
    val headers: List<HttpRequestHeader> = emptyList(),
    val properties: AmqpMessageProperties? = null,
    val message: WebSocketMessagePayload? = null,
    val auth: AuthConfig? = null,
)

@Serializable
data class RabbitMqRequestSettings(
    val connectionTimeoutMs: Long = 10_000L,
    val heartbeatSeconds: Int = 30,
    val publisherConfirms: Boolean = true,
    val mandatory: Boolean = false,
    val declareTopology: Boolean = false,
    val ackMode: AmqpAckMode = AmqpAckMode.AUTO,
    val prefetchCount: Int = 50,
    val maxTimelineMessages: Int = 1_000,
    val rpcTimeoutMs: Long = 15_000L,
    val replyStrategy: RabbitMqReplyStrategy = RabbitMqReplyStrategy.DIRECT_REPLY_TO,
    val useTlsClientCert: Boolean = false,
) {
    fun clampedConnectionTimeoutMs(): Long = connectionTimeoutMs.coerceIn(500L, 120_000L)
    fun clampedHeartbeatSeconds(): Int = heartbeatSeconds.coerceIn(0, 600)
    fun clampedPrefetchCount(): Int = prefetchCount.coerceIn(1, 10_000)
    fun clampedMaxTimelineMessages(): Int = maxTimelineMessages.coerceIn(10, 50_000)
    fun clampedRpcTimeoutMs(): Long = rpcTimeoutMs.coerceIn(100L, 300_000L)
}
