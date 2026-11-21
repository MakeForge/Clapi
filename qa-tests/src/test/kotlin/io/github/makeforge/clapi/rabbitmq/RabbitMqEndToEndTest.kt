package io.github.makeforge.clapi.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqQueueConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqReplyStrategy
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.network.rabbitmq.DesktopRabbitMqClient
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqRequestExecutor
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSchemaValidator
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RabbitMqEndToEndTest {

    private val host = System.getenv("RABBITMQ_HOST") ?: "localhost"
    private val port = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672
    private val user = System.getenv("RABBITMQ_USER") ?: "clapi"
    private val pass = System.getenv("RABBITMQ_PASS") ?: "clapi-secret"

    private val executor = RabbitMqRequestExecutor(DesktopRabbitMqClient())

    @Before
    fun assumeBrokerReachable() {
        val reachable = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 1500)
                true
            }
        }.getOrDefault(false)
        Assume.assumeTrue("Skipping RabbitMqEndToEndTest: RabbitMQ not reachable at $host:$port", reachable)
    }

    @Test
    fun publishAndSubscribeOverTopicExchangeWithInheritedAuth() = runBlocking {
        val exName = "qa.orders.${UUID.randomUUID()}"
        val subItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Subscribe"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.SUBSCRIBE,
                connection = RabbitMqConnectionConfig(host = host, port = port, vhost = "/"),
                exchange = RabbitMqExchangeConfig(name = exName, type = RabbitMqExchangeType.TOPIC, durable = false, autoDelete = true),
                queue = RabbitMqQueueConfig(name = "", durable = false, exclusive = true, autoDelete = true),
                routingKey = "order.#",
                auth = AuthInherit,
            ),
            settings = RabbitMqRequestSettings(declareTopology = true, ackMode = AmqpAckMode.MANUAL),
        )
        val pubItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Publish"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.PUBLISH,
                connection = RabbitMqConnectionConfig(host = host, port = port, vhost = "/"),
                exchange = RabbitMqExchangeConfig(name = exName, type = RabbitMqExchangeType.TOPIC, durable = false, autoDelete = true),
                routingKey = "order.{{action}}",
                headers = listOf(HttpRequestHeader(name = "x-qa", value = "yes")),
                properties = AmqpMessageProperties(contentType = "application/json", deliveryMode = 1),
                message = WebSocketMessagePayload.Single(WebSocketMessage("json", """{"orderId":"{{id}}"}""")),
                auth = AuthInherit,
            ),
            settings = RabbitMqRequestSettings(declareTopology = true, publisherConfirms = true),
        )

        val inheritedAuth = listOf(AuthBasic(username = user, password = pass))
        val session = executor.subscribe(subItem, ancestorAuthChain = inheritedAuth)
        try {
            var receivedText: String? = null
            var receivedHeader: String? = null
            var deliveryTag: Long? = null
            val collector = launch {
                val msg = withTimeout(5_000L) { session.messages.first() }
                receivedText = msg.payloadText
                receivedHeader = msg.headers["x-qa"]
                deliveryTag = msg.deliveryTag
            }

            delay(150)
            val pubResult = executor.publish(
                item = pubItem,
                variableLayers = listOf(mapOf("action" to "created", "id" to "ord-777")),
                ancestorAuthChain = inheritedAuth,
            )
            assertTrue(pubResult.isSuccess, "Publish failed: ${pubResult.errorMessage}")
            assertTrue(pubResult.confirmed)

            collector.join()
            assertEquals("""{"orderId":"ord-777"}""", receivedText)
            assertEquals("yes", receivedHeader)
            assertNotNull(deliveryTag)
            session.ack(deliveryTag!!)
        } finally {
            session.close()
        }
    }

    @Test
    fun rpcOverRabbitMqDirectReplyToAndTimeout() = runBlocking {
        val rpcQueue = "qa.rpc.${UUID.randomUUID()}"
        val factory = ConnectionFactory().apply {
            host = this@RabbitMqEndToEndTest.host
            port = this@RabbitMqEndToEndTest.port
            username = user
            password = pass
        }

        factory.newConnection("qa-rpc-responder").use { conn ->
            conn.createChannel().use { ch ->
                ch.queueDeclare(rpcQueue, true, false, true, null)
                val deliver = DeliverCallback { _, delivery ->
                    val replyTo = delivery.properties.replyTo
                    val corrId = delivery.properties.correlationId
                    val replyProps = AMQP.BasicProperties.Builder()
                        .correlationId(corrId)
                        .contentType("application/json")
                        .build()
                    val body = """{"echo":${delivery.body.decodeToString()},"status":"ok"}""".toByteArray()
                    ch.basicPublish("", replyTo, replyProps, body)
                    ch.basicAck(delivery.envelope.deliveryTag, false)
                }
                val tag = ch.basicConsume(rpcQueue, false, deliver) { _ -> }
                try {
                    val rpcItem = RabbitMqRequestItem(
                        info = RabbitMqRequestInfo(name = "RPC Calc"),
                        rabbitmq = RabbitMqRequestDetails(
                            mode = RabbitMqOperationMode.RPC,
                            connection = RabbitMqConnectionConfig(host = host, port = port),
                            exchange = RabbitMqExchangeConfig(name = "", type = RabbitMqExchangeType.DEFAULT),
                            routingKey = rpcQueue,
                            message = WebSocketMessagePayload.Single(WebSocketMessage("json", """{"n":21}""")),
                            auth = AuthBasic(username = user, password = pass),
                        ),
                        settings = RabbitMqRequestSettings(
                            rpcTimeoutMs = 5_000L,
                            replyStrategy = RabbitMqReplyStrategy.DIRECT_REPLY_TO,
                        ),
                    )
                    val res = executor.rpc(rpcItem)
                    assertTrue(res.isSuccess, "RPC failed: ${res.errorMessage}")
                    assertEquals("amq.rabbitmq.reply-to", res.replyTo)
                    assertEquals("""{"echo":{"n":21},"status":"ok"}""", res.reply?.payloadText)

                    // OpenCollection round-trip check
                    val doc = OpenCollectionDocument(info = CollectionInfo(name = "QA"), items = listOf(rpcItem))
                    val yaml = OpenCollectionSerializer.serializeYaml(doc)
                    assertTrue(OpenCollectionSchemaValidator().validateYaml(yaml).isEmpty())
                } finally {
                    runCatching { ch.basicCancel(tag) }
                }
            }
        }

        // Verify timeout when no responder listens
        val timeoutItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "RPC Timeout"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.RPC,
                connection = RabbitMqConnectionConfig(host = host, port = port),
                routingKey = "qa.rpc.nobody.${UUID.randomUUID()}",
                auth = AuthBasic(username = user, password = pass),
            ),
            settings = RabbitMqRequestSettings(rpcTimeoutMs = 400L),
        )
        val timeoutRes = executor.rpc(timeoutItem)
        assertFalse(timeoutRes.isSuccess)
        assertTrue(timeoutRes.timedOut)
    }
}
