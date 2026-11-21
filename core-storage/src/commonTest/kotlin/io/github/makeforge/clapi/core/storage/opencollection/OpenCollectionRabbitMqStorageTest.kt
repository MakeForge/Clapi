package io.github.makeforge.clapi.core.storage.opencollection

import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenCollectionRabbitMqStorageTest {

    private fun sampleDocument(): OpenCollectionDocument = OpenCollectionDocument(
        info = CollectionInfo(name = "RabbitMQ Collection"),
        items = listOf(
            RabbitMqRequestItem(
                info = RabbitMqRequestInfo(name = "Publish Order", tags = listOf("amqp")),
                rabbitmq = RabbitMqRequestDetails(
                    mode = RabbitMqOperationMode.PUBLISH,
                    connection = RabbitMqConnectionConfig(host = "localhost", port = 5672, vhost = "/", useTls = false),
                    exchange = RabbitMqExchangeConfig(name = "orders.events", type = RabbitMqExchangeType.TOPIC),
                    routingKey = "order.created",
                    headers = listOf(HttpRequestHeader(name = "x-trace", value = "abc")),
                    properties = AmqpMessageProperties(contentType = "application/json", deliveryMode = 2),
                    message = WebSocketMessagePayload.Single(WebSocketMessage(type = "json", data = """{"ok":true}""")),
                    auth = AuthInherit,
                ),
                settings = RabbitMqRequestSettings(
                    publisherConfirms = true,
                    mandatory = true,
                    declareTopology = true,
                    ackMode = AmqpAckMode.AUTO,
                    replyStrategy = RabbitMqReplyStrategy.DIRECT_REPLY_TO,
                ),
                docs = "RabbitMQ publish docs",
            ),
            RabbitMqRequestItem(
                info = RabbitMqRequestInfo(name = "Subscribe Orders"),
                rabbitmq = RabbitMqRequestDetails(
                    mode = RabbitMqOperationMode.SUBSCRIBE,
                    connection = RabbitMqConnectionConfig(host = "localhost", port = 5672),
                    exchange = RabbitMqExchangeConfig(name = "orders.events", type = RabbitMqExchangeType.TOPIC),
                    queue = RabbitMqQueueConfig(name = "orders.q", durable = true),
                    routingKey = "order.#",
                ),
                settings = RabbitMqRequestSettings(ackMode = AmqpAckMode.MANUAL, prefetchCount = 20),
            ),
        ),
    )

    @Test
    fun jsonSchemaValidationAndRoundTripPasses() = runTest {
        val validator = OpenCollectionSchemaValidator()
        val doc = sampleDocument()
        val rawJson = OpenCollectionSerializer.serializeJson(doc)
        val issues = validator.validate(rawJson)
        assertTrue(issues.isEmpty(), "Expected no JSON schema issues, got: $issues")

        val driver = BundledCollectionStorageDriver(validator)
        val location = StorageLocation.MemoryKey("rabbitmq-test")
        driver.write(location, doc)
        val loaded = driver.read(location)

        assertEquals(2, loaded.items.size)
        val pub = assertIs<RabbitMqRequestItem>(loaded.items[0])
        assertEquals("Publish Order", pub.info.name)
        assertEquals(RabbitMqOperationMode.PUBLISH, pub.rabbitmq.mode)
        assertEquals("order.created", pub.rabbitmq.routingKey)
        assertEquals(AuthInherit, pub.rabbitmq.auth)
    }

    @Test
    fun yamlSchemaValidationAndRoundTripPasses() {
        val validator = OpenCollectionSchemaValidator()
        val doc = sampleDocument()
        val rawYaml = OpenCollectionSerializer.serializeYaml(doc)
        val issues = validator.validateYaml(rawYaml)
        assertTrue(issues.isEmpty(), "Expected no YAML schema issues, got: $issues\nYAML:\n$rawYaml")

        val parsed = OpenCollectionSerializer.deserializeYaml(rawYaml)
        assertEquals(2, parsed.items.size)
        val sub = assertIs<RabbitMqRequestItem>(parsed.items[1])
        assertEquals(RabbitMqOperationMode.SUBSCRIBE, sub.rabbitmq.mode)
        assertEquals(AmqpAckMode.MANUAL, sub.settings?.ackMode)
        assertEquals("orders.q", sub.rabbitmq.queue?.name)
    }
}
