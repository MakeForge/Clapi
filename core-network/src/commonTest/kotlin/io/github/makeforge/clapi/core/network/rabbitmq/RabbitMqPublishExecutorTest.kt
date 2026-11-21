package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.AmqpMessageProperties
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RabbitMqPublishExecutorTest {

    private class CapturingClient(
        private val publishResponse: (RabbitMqPublishRequest) -> RabbitMqPublishResult,
    ) : RabbitMqClient {
        var lastPublish: RabbitMqPublishRequest? = null
        override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult {
            lastPublish = request
            return publishResponse(request)
        }
        override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession =
            error("Not used")
        override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult =
            error("Not used")
    }

    @Test
    fun interpolatesVariablesSelectsActiveVariantAndInheritsAuth() = runTest {
        val fake = CapturingClient { req ->
            RabbitMqPublishResult(
                confirmed = true,
                exchange = req.exchange.name,
                routingKey = req.routingKey,
                byteLength = req.bodyBytes.size.toLong(),
                elapsedMs = 12L,
            )
        }
        val executor = RabbitMqRequestExecutor(fake)

        val item = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Pub"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.PUBLISH,
                connection = RabbitMqConnectionConfig(host = "{{host}}", port = 5672, vhost = "/{{tenant}}"),
                exchange = RabbitMqExchangeConfig(name = "ex.{{tenant}}", type = RabbitMqExchangeType.TOPIC),
                routingKey = "order.{{event}}",
                headers = listOf(
                    HttpRequestHeader(name = "x-env", value = "{{tenant}}"),
                    HttpRequestHeader(name = "x-disabled", value = "ignored", disabled = true),
                ),
                properties = AmqpMessageProperties(contentType = "application/json", appId = "app-{{tenant}}"),
                message = WebSocketMessagePayload.Variants(
                    listOf(
                        WebSocketMessageVariant("First", selected = false, WebSocketMessage("json", """{"v":1}""")),
                        WebSocketMessageVariant("Second", selected = true, WebSocketMessage("json", """{"id":"{{orderId}}"}""")),
                    )
                ),
                auth = AuthInherit,
            ),
            settings = RabbitMqRequestSettings(publisherConfirms = true, mandatory = true, declareTopology = true),
        )

        val result = executor.publish(
            item = item,
            variableLayers = listOf(
                mapOf("host" to "rmq.internal", "tenant" to "eu", "event" to "created", "orderId" to "ord-42", "pass" to "pw")
            ),
            ancestorAuthChain = listOf(AuthBasic(username = "svc", password = "{{pass}}")),
        )

        assertTrue(result.isSuccess)
        assertTrue(result.confirmed)
        val req = fake.lastPublish!!
        assertEquals("rmq.internal", req.connection.host)
        assertEquals("/eu", req.connection.vhost)
        assertEquals("ex.eu", req.exchange.name)
        assertEquals("order.created", req.routingKey)
        assertEquals(mapOf("x-env" to "eu"), req.headers)
        assertEquals("app-eu", req.properties.appId)
        assertEquals("""{"id":"ord-42"}""", req.bodyBytes.decodeToString())
        val auth = assertIs<RabbitMqAuthCredentials.Plain>(req.auth)
        assertEquals("svc", auth.username)
        assertEquals("pw", auth.password)
    }

    @Test
    fun rejectsBlankHostBeforeBrokerCall() = runTest {
        val fake = CapturingClient { error("Should not be called") }
        val executor = RabbitMqRequestExecutor(fake)
        val item = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Invalid"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.PUBLISH,
                connection = RabbitMqConnectionConfig(host = "   "),
            ),
        )
        val result = executor.publish(item)
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("host", ignoreCase = true))
    }

    @Test
    fun reportsMandatoryUnroutableReturn() = runTest {
        val fake = CapturingClient { req ->
            RabbitMqPublishResult(
                confirmed = true,
                returnedUnroutable = true,
                returnReplyCode = 312,
                returnReplyText = "NO_ROUTE",
                exchange = req.exchange.name,
                routingKey = req.routingKey,
            )
        }
        val executor = RabbitMqRequestExecutor(fake)
        val item = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Unroutable"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.PUBLISH,
                connection = RabbitMqConnectionConfig(host = "localhost"),
                routingKey = "no.such.queue",
            ),
            settings = RabbitMqRequestSettings(mandatory = true),
        )
        val result = executor.publish(item)
        assertFalse(result.isSuccess)
        assertTrue(result.returnedUnroutable)
        assertEquals(312, result.returnReplyCode)
        assertEquals("NO_ROUTE", result.returnReplyText)
    }
}
