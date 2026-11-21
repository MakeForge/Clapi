package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RabbitMqSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun roundTripPublishSubscribeAndRpcItems() {
        val publishItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Publish Order", tags = listOf("events")),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.PUBLISH,
                connection = RabbitMqConnectionConfig(host = "{{rmqHost}}", port = 5672, vhost = "/orders", useTls = false),
                exchange = RabbitMqExchangeConfig(name = "orders.events", type = RabbitMqExchangeType.TOPIC, durable = true),
                routingKey = "order.created",
                headers = listOf(HttpRequestHeader(name = "x-source", value = "clapi")),
                properties = AmqpMessageProperties(contentType = "application/json", deliveryMode = 2),
                message = WebSocketMessagePayload.Variants(
                    listOf(
                        WebSocketMessageVariant(
                            title = "Default Order",
                            selected = true,
                            message = WebSocketMessage(type = "json", data = """{"orderId":"123"}"""),
                        )
                    )
                ),
                auth = AuthInherit,
            ),
            settings = RabbitMqRequestSettings(publisherConfirms = true, mandatory = true, declareTopology = true),
            docs = "Publishes order events",
        )

        val subscribeItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Subscribe Orders"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.SUBSCRIBE,
                connection = RabbitMqConnectionConfig(host = "localhost", port = 5672),
                exchange = RabbitMqExchangeConfig(name = "orders.events", type = RabbitMqExchangeType.TOPIC),
                queue = RabbitMqQueueConfig(name = "", durable = false, exclusive = true, autoDelete = true),
                routingKey = "order.#",
                auth = AuthBasic(username = "clapi", password = "{{rmqPass}}"),
            ),
            settings = RabbitMqRequestSettings(ackMode = AmqpAckMode.MANUAL, prefetchCount = 25, maxTimelineMessages = 500),
        )

        val rpcItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Calculate Total RPC"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.RPC,
                connection = RabbitMqConnectionConfig(host = "localhost", port = 5671, useTls = true),
                exchange = RabbitMqExchangeConfig(name = "", type = RabbitMqExchangeType.DEFAULT),
                routingKey = "orders.rpc.calc",
                replyQueue = "orders.rpc.replies",
                message = WebSocketMessagePayload.Single(WebSocketMessage(type = "json", data = """{"qty":2}""")),
                auth = AuthBearer(token = "{{jwtToken}}"),
            ),
            settings = RabbitMqRequestSettings(
                rpcTimeoutMs = 5_000L,
                replyStrategy = RabbitMqReplyStrategy.NAMED_QUEUE,
                useTlsClientCert = false,
            ),
        )

        val doc = OpenCollectionDocument(
            info = CollectionInfo(name = "RabbitMQ Suite"),
            items = listOf(publishItem, subscribeItem, rpcItem),
        )

        val encoded = json.encodeToString(OpenCollectionDocument.serializer(), doc)
        val decoded = json.decodeFromString(OpenCollectionDocument.serializer(), encoded)

        assertEquals(3, decoded.items.size)
        val decPub = assertIs<RabbitMqRequestItem>(decoded.items[0])
        assertEquals(RabbitMqOperationMode.PUBLISH, decPub.rabbitmq.mode)
        assertEquals("orders.events", decPub.rabbitmq.exchange?.name)
        assertEquals(RabbitMqExchangeType.TOPIC, decPub.rabbitmq.exchange?.type)
        assertEquals(AuthInherit, decPub.rabbitmq.auth)
        assertEquals(true, decPub.settings?.mandatory)

        val decSub = assertIs<RabbitMqRequestItem>(decoded.items[1])
        assertEquals(RabbitMqOperationMode.SUBSCRIBE, decSub.rabbitmq.mode)
        assertEquals(AmqpAckMode.MANUAL, decSub.settings?.ackMode)
        assertEquals(25, decSub.settings?.prefetchCount)

        val decRpc = assertIs<RabbitMqRequestItem>(decoded.items[2])
        assertEquals(RabbitMqOperationMode.RPC, decRpc.rabbitmq.mode)
        assertEquals(RabbitMqReplyStrategy.NAMED_QUEUE, decRpc.settings?.replyStrategy)
        assertEquals("orders.rpc.replies", decRpc.rabbitmq.replyQueue)
    }
}
