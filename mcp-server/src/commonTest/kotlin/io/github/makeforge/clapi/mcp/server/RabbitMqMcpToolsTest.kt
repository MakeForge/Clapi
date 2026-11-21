package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.execution.RequestExecutionService
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqClient
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqPublishRequest
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqRpcRequest
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqSubscribeRequest
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqSubscriptionSession
import io.github.makeforge.clapi.mcp.server.backend.ExecuteRequestInput
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.REDACTED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.RabbitMqConnectionDto
import io.github.makeforge.clapi.mcp.server.backend.RabbitMqExchangeDto
import io.github.makeforge.clapi.mcp.server.backend.RabbitMqRequestConfigDto
import io.github.makeforge.clapi.mcp.server.backend.RunTestSuiteInput
import io.github.makeforge.clapi.mcp.server.backend.SanitizedAuthDto
import io.github.makeforge.clapi.mcp.server.backend.SetVariableInput
import io.github.makeforge.clapi.mcp.server.backend.SubscribeRabbitMqInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RabbitMqMcpToolsTest {

    private val noopApiClient = object : ApiClient {
        override fun execute(request: RequestDefinition, variableLayers: List<Map<String, String>>): Flow<NetworkEvent> = emptyFlow()
    }

    private fun fakeRabbitClient(secretToEcho: String): RabbitMqClient = object : RabbitMqClient {
        override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult =
            RabbitMqPublishResult(
                confirmed = true,
                exchange = request.exchange.name,
                routingKey = request.routingKey,
                byteLength = request.bodyBytes.size.toLong(),
                elapsedMs = 7L,
            )

        override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession =
            object : RabbitMqSubscriptionSession {
                override val status: StateFlow<RabbitMqConnectionStatus> = MutableStateFlow(RabbitMqConnectionStatus.CONNECTED)
                override val errorMessage: StateFlow<String?> = MutableStateFlow(null)
                override val messages: Flow<RabbitMqDeliveryMessage> = flowOf(
                    RabbitMqDeliveryMessage(
                        id = "m1",
                        timestampEpochMillis = 1_000L,
                        exchange = "orders.events",
                        routingKey = "order.created",
                        headers = mapOf("Authorization" to "Bearer $secretToEcho", "x-note" to "ok"),
                        payloadText = """{"echo":"$secretToEcho","ok":true}""",
                        byteLength = 35L,
                    ),
                    RabbitMqDeliveryMessage(
                        id = "m2",
                        timestampEpochMillis = 1_001L,
                        exchange = "orders.events",
                        routingKey = "order.updated",
                        payloadText = """{"ok":true}""",
                        byteLength = 11L,
                    ),
                )
                override suspend fun ack(deliveryTag: Long) = Unit
                override suspend fun nack(deliveryTag: Long, requeue: Boolean) = Unit
                override suspend fun close() = Unit
            }

        override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult =
            RabbitMqRpcResult(
                correlationId = "corr-123",
                replyTo = "amq.rabbitmq.reply-to",
                reply = RabbitMqDeliveryMessage(
                    id = "r1",
                    timestampEpochMillis = 2_000L,
                    headers = mapOf("x-auth-token" to secretToEcho),
                    payloadText = """{"total":42,"leaked":"$secretToEcho"}""",
                    byteLength = 40L,
                ),
                elapsedMs = 11L,
            )
    }

    @Test
    fun authorsExecutesSubscribesAndRedactsSecretsAcrossMcpTools() = runTest {
        val secret = "super-secret-rmq-pass-999"
        val execService = RequestExecutionService(apiClient = noopApiClient, rabbitMqClient = fakeRabbitClient(secret))
        val backend = HeadlessWorkspaceBackend(apiClient = noopApiClient, executionService = execService)
        val server = ClapiMcpServer(backend = backend)

        assertTrue(server.allTools().any { it.name == "clapi_subscribe_rabbitmq" })

        backend.manageCollection(
            ManageCollectionInput(
                action = "create",
                name = "RabbitMQ API",
                auth = SanitizedAuthDto(type = "BASIC", username = "clapi", password = secret),
            )
        )
        backend.setVariable(
            SetVariableInput(scope = "global", key = "rmqSecret", kind = "secret", value = secret)
        )

        val pubReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "RabbitMQ API",
                name = "Publish Order",
                kind = "rabbitmq",
                bodyContent = """{"orderId":"1"}""",
                auth = SanitizedAuthDto(type = "INHERIT"),
                rabbitmq = RabbitMqRequestConfigDto(
                    mode = "publish",
                    connection = RabbitMqConnectionDto(host = "localhost", port = 5672),
                    exchange = RabbitMqExchangeDto(name = "orders.events", type = "topic"),
                    routingKey = "order.created",
                ),
            )
        )

        val subReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "RabbitMQ API",
                name = "Subscribe Orders",
                kind = "rabbitmq",
                auth = SanitizedAuthDto(type = "INHERIT"),
                rabbitmq = RabbitMqRequestConfigDto(
                    mode = "subscribe",
                    connection = RabbitMqConnectionDto(host = "localhost", port = 5672),
                    exchange = RabbitMqExchangeDto(name = "orders.events", type = "topic"),
                    routingKey = "order.#",
                ),
            )
        )

        val rpcReq = backend.upsertRequest(
            UpsertRequestInput(
                parentId = "RabbitMQ API",
                name = "Calc RPC",
                kind = "rabbitmq",
                bodyContent = """{"qty":2}""",
                auth = SanitizedAuthDto(type = "INHERIT"),
                testScript = """
                    clapi.test("RPC total is 42", function() {
                        clapi.expect(clapi.response.code).to.equal(200);
                    });
                """.trimIndent(),
                rabbitmq = RabbitMqRequestConfigDto(
                    mode = "rpc",
                    connection = RabbitMqConnectionDto(host = "localhost", port = 5672),
                    routingKey = "orders.rpc.calc",
                ),
            )
        )

        // Verify tree badges
        val tree = backend.listTree()
        val children = tree.single().children
        assertEquals(listOf("PUB", "SUB", "RPC"), children.map { it.method })

        // Verify Publish execution
        val pubExec = backend.executeRequest(ExecuteRequestInput(requestId = pubReq.id))
        assertEquals(200, pubExec.statusCode)
        assertEquals("CONFIRMED", pubExec.statusText)

        // Verify RPC execution + zero-exfiltration redaction + assertions
        val rpcExec = backend.executeRequest(ExecuteRequestInput(requestId = rpcReq.id))
        assertEquals(200, rpcExec.statusCode)
        assertFalse(rpcExec.bodyPreview.contains(secret), "Secret must not appear in RPC bodyPreview")
        assertTrue(rpcExec.bodyPreview.contains(REDACTED_SECRET_PLACEHOLDER))
        assertEquals(1, rpcExec.assertions.size)
        assertTrue(rpcExec.assertions.single().passed)

        // Verify Subscribe rejected by executeRequest with guidance
        val ex = assertFailsWith<IllegalArgumentException> {
            backend.executeRequest(ExecuteRequestInput(requestId = subReq.id))
        }
        assertTrue(ex.message!!.contains("clapi_subscribe_rabbitmq"))

        // Verify bounded clapi_subscribe_rabbitmq + redaction
        val subRes = backend.subscribeRabbitMq(
            SubscribeRabbitMqInput(requestId = subReq.id, maxMessages = 1, timeoutMs = 1_000L)
        )
        assertEquals(1, subRes.messageCount)
        assertTrue(subRes.truncated)
        val firstMsg = subRes.messages.single()
        assertFalse(firstMsg.bodyPreview.contains(secret))
        assertTrue(firstMsg.bodyPreview.contains(REDACTED_SECRET_PLACEHOLDER))
        assertEquals("Bearer $MASKED_SECRET_PLACEHOLDER", firstMsg.headers["Authorization"])

        // Verify test suite runs PUB & RPC and skips SUB
        val suite = backend.runTestSuite(RunTestSuiteInput(targetId = "RabbitMQ API"))
        assertEquals(3, suite.totalRequests)
        assertEquals(3, suite.passedRequests)
        val skippedSub = suite.results.first { it.name == "Subscribe Orders" }
        assertTrue(skippedSub.error!!.contains("SKIPPED"))
    }
}
