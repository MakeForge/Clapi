package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.RabbitMqDeliveryMessage
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.RequestDefaults
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.execution.RequestExecutionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RabbitMqScriptingAndSuiteTest {

    private val noopApiClient = object : ApiClient {
        override fun execute(request: RequestDefinition, variableLayers: List<Map<String, String>>): Flow<NetworkEvent> = emptyFlow()
    }

    @Test
    fun requestExecutionServiceResolvesAncestorAuthForPublishAndRpc() = runTest {
        var capturedAuth: RabbitMqAuthCredentials? = null
        val client = object : RabbitMqClient {
            override suspend fun publish(request: RabbitMqPublishRequest): RabbitMqPublishResult {
                capturedAuth = request.auth
                return RabbitMqPublishResult(confirmed = true, routingKey = request.routingKey, elapsedMs = 3L)
            }
            override suspend fun subscribe(request: RabbitMqSubscribeRequest): RabbitMqSubscriptionSession = error("unused")
            override suspend fun rpc(request: RabbitMqRpcRequest): RabbitMqRpcResult {
                capturedAuth = request.auth
                return RabbitMqRpcResult(
                    correlationId = "c1",
                    replyTo = "amq.rabbitmq.reply-to",
                    reply = RabbitMqDeliveryMessage(id = "r1", timestampEpochMillis = 1L, payloadText = """{"ok":true}"""),
                    elapsedMs = 5L,
                )
            }
        }

        val service = RequestExecutionService(apiClient = noopApiClient, rabbitMqClient = client)
        val colDoc = OpenCollectionDocument(
            info = CollectionInfo(name = "Col"),
            request = RequestDefaults(auth = AuthBasic(username = "colUser", password = "colPass")),
        )
        val folder = FolderItem(info = io.github.makeforge.clapi.core.model.opencollection.FolderInfo(name = "F1"))
        val pubItem = RabbitMqRequestItem(
            info = RabbitMqRequestInfo(name = "Pub"),
            rabbitmq = RabbitMqRequestDetails(
                mode = RabbitMqOperationMode.PUBLISH,
                connection = RabbitMqConnectionConfig(host = "localhost"),
                routingKey = "k1",
                auth = AuthInherit,
            ),
        )
        val pubRes = service.executeRabbitMqPublish(pubItem, listOf(folder), colDoc)
        assertTrue(pubRes.isSuccess)
        val plain = assertIs<RabbitMqAuthCredentials.Plain>(capturedAuth)
        assertEquals("colUser", plain.username)
        assertEquals("colPass", plain.password)

        val rpcItem = pubItem.copy(
            info = RabbitMqRequestInfo(name = "Rpc"),
            rabbitmq = pubItem.rabbitmq.copy(mode = RabbitMqOperationMode.RPC),
        )
        val rpcRes = service.executeRabbitMqRpc(rpcItem, listOf(folder), colDoc)
        assertTrue(rpcRes.isSuccess)
        assertEquals("""{"ok":true}""", rpcRes.reply?.payloadText)
    }
}
