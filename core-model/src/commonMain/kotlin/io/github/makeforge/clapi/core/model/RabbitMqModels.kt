package io.github.makeforge.clapi.core.model

import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
import kotlinx.serialization.Serializable

@Serializable
enum class RabbitMqConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

@Serializable
enum class AmqpAckStatus {
    AUTO_ACKED,
    PENDING,
    ACKED,
    NACKED,
    REQUEUED,
}

@Serializable
data class RabbitMqMessageAssertion(
    val name: String,
    val passed: Boolean,
    val message: String = "",
)

@Serializable
data class RabbitMqDeliveryMessage(
    val id: String,
    val timestampEpochMillis: Long,
    val direction: MessageDirection = MessageDirection.RECEIVED,
    val deliveryTag: Long? = null,
    val exchange: String = "",
    val routingKey: String = "",
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val properties: AmqpMessageProperties = AmqpMessageProperties(),
    val payloadText: String? = null,
    val payloadBytesBase64: String? = null,
    val isBinary: Boolean = false,
    val byteLength: Long = 0L,
    val ackStatus: AmqpAckStatus = AmqpAckStatus.AUTO_ACKED,
    val assertionResults: List<RabbitMqMessageAssertion> = emptyList(),
)

@Serializable
data class RabbitMqPublishResult(
    val confirmed: Boolean = false,
    val returnedUnroutable: Boolean = false,
    val returnReplyCode: Int? = null,
    val returnReplyText: String? = null,
    val exchange: String = "",
    val routingKey: String = "",
    val byteLength: Long = 0L,
    val elapsedMs: Long = 0L,
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean get() = errorMessage == null && !returnedUnroutable
}

@Serializable
data class RabbitMqRpcResult(
    val correlationId: String,
    val replyTo: String = "",
    val reply: RabbitMqDeliveryMessage? = null,
    val timedOut: Boolean = false,
    val elapsedMs: Long = 0L,
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean get() = errorMessage == null && !timedOut && reply != null
}
