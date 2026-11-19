package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.AmqpAckStatus
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqQueueConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RabbitMqSubscribeExecutorTest {

    private class FakeSession : RabbitMqSubscriptionSession {
        val statusMutable = MutableStateFlow(RabbitMqConnectionStatus.CONNECTED)
        val errorMutable = MutableStateFlow<String?>(null)
        val messagesMutable = MutableSharedFlow<RabbitMqDeliveryMessage>(replay = 1, extraBufferCapacity = 16)
        val ackedTags = mutableListOf<Long>()
        val nackedTags = mutableListOf<Pair<Long, Boolean>>()
        var closed = false

        override val status: StateFlow<RabbitMqConnectionStatus> = statusMutable
        override val errorMessage: StateFlow<String?> = errorMutable
        override val messages: Flow<RabbitMqDeliveryMessage> = messagesMutable
        override suspend fun ack(deliveryTag: Long) { ackedTags += deliveryTag }
        override suspend fun nack(deliveryTag: Long, requeue: Boolean) { nackedTags += deliveryTag to requeue }
        override suspend fun close() {
            closed = true
            statusMutable.value = RabbitMqConnectionStatus.DISCONNECTED
        }
    }

    @Test
    fun subscribesWithManualAckAndClampedPrefetch() = runTest {
        val fakeSession = FakeSession()
        var captured: RabbitMqSubscribeRequest? = null
        val client = object : RabbitMqClient {
            override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult = error("unused")
            override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult = error("unused")
            override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession {
                captured = request
                return fakeSession
            }
        }

        val item = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Sub"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.SUBSCRIBE,
                connection = RabbitMqConnectionConfig(host = "{{host}}", port = 5672),
                exchange = RabbitMqExchangeConfig(name = "events", type = RabbitMqExchangeType.TOPIC),
                queue = RabbitMqQueueConfig(name = "q.{{env}}"),
                routingKey = "order.#",
            ),
            settings = RabbitMqRequestSettings(ackMode = AmqpAckMode.MANUAL, prefetchCount = 99_999),
        )

        val session = RabbitMqRequestExecutor(client).subscribe(
            item = item,
            variableLayers = listOf(mapOf("host" to "broker.local", "env" to "dev")),
        )

        assertEquals("broker.local", captured?.connection?.host)
        assertEquals("q.dev", captured?.queue?.name)
        assertEquals(AmqpAckMode.MANUAL, captured?.ackMode)
        assertEquals(10_000, captured?.prefetchCount) // clamped to max 10_000

        var received: RabbitMqDeliveryMessage? = null
        val job = launch { received = session.messages.first() }
        fakeSession.messagesMutable.emit(
            RabbitMqDeliveryMessage(
                id = "m1",
                timestampEpochMillis = 1000L,
                deliveryTag = 7L,
                exchange = "events",
                routingKey = "order.created",
                payloadText = """{"id":1}""",
                ackStatus = AmqpAckStatus.PENDING,
            )
        )
        job.join()

        assertEquals(7L, received?.deliveryTag)
        session.ack(7L)
        session.nack(8L, requeue = true)
        session.close()

        assertEquals(listOf(7L), fakeSession.ackedTags)
        assertEquals(listOf(8L to true), fakeSession.nackedTags)
        assertTrue(fakeSession.closed)
        assertEquals(RabbitMqConnectionStatus.DISCONNECTED, session.status.value)
    }
}
