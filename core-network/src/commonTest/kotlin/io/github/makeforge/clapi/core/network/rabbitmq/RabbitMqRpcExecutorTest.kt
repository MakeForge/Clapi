package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqReplyStrategy
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RabbitMqRpcExecutorTest {

    @Test
    fun rejectsNamedQueueStrategyWhenReplyQueueBlank() = runTest {
        val client = object : RabbitMqClient {
            override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult = error("unused")
            override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession = error("unused")
            override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult = error("should not be called")
        }
        val item = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "RPC"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.RPC,
                connection = RabbitMqConnectionConfig(host = "localhost"),
                routingKey = "rpc.calc",
                replyQueue = "   ",
            ),
            settings = RabbitMqRequestSettings(replyStrategy = RabbitMqReplyStrategy.NAMED_QUEUE),
        )
        val res = RabbitMqRequestExecutor(client).rpc(item)
        assertFalse(res.isSuccess)
        assertTrue(res.errorMessage!!.contains("replyQueue", ignoreCase = true))
    }

    @Test
    fun clampsRpcTimeoutAndReturnsCorrelatedReply() = runTest {
        var captured: RabbitMqRpcRequest? = null
        val client = object : RabbitMqClient {
            override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult = error("unused")
            override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession = error("unused")
            override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult {
                captured = request
                return RabbitMqRpcResult(
                    correlationId = "corr-1",
                    replyTo = "amq.rabbitmq.reply-to",
                    reply = RabbitMqDeliveryMessage(
                        id = "r1",
                        timestampEpochMillis = 2000L,
                        payloadText = """{"total":99}""",
                    ),
                    elapsedMs = 15L,
                )
            }
        }
        val item = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "RPC Calc"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.RPC,
                connection = RabbitMqConnectionConfig(host = "localhost"),
                routingKey = "rpc.{{op}}",
                message = WebSocketMessagePayload.Single(WebSocketMessage("json", """{"x":1}""")),
            ),
            settings = RabbitMqRequestSettings(
                rpcTimeoutMs = 50L, // below min 100ms
                replyStrategy = RabbitMqReplyStrategy.DIRECT_REPLY_TO,
            ),
        )
        val res = RabbitMqRequestExecutor(client).rpc(
            item = item,
            variableLayers = listOf(mapOf("op" to "sum")),
        )
        assertTrue(res.isSuccess)
        assertEquals("rpc.sum", captured?.routingKey)
        assertEquals(100L, captured?.rpcTimeoutMs)
        assertEquals("""{"total":99}""", res.reply?.payloadText)
    }
}
