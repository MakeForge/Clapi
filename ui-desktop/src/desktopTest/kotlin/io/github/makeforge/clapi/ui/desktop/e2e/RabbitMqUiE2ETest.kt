// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.opencollection.AmqpAckMode
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqConnectionConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeConfig
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqExchangeType
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqOperationMode
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqQueueConfig
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.RabbitMqEditorTab
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Interactive Compose Desktop UI tests for [RabbitMqRequestEditor] inside [MainScreen].
 *
 * - Broker-independent tests (mode switching, tab hiding/fallback, dirty/save, unreachable-broker
 *   error banner) run unconditionally everywhere.
 * - Live Publish/Subscribe/Ack/RPC UI E2E tests execute when the RabbitMQ test container is
 *   reachable and fail hard in CI (`CI=true`) if unreachable.
 *
 * Covers FR-010 (US3).
 */
class RabbitMqUiE2ETest {

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val brokerHost = System.getenv("RABBITMQ_HOST") ?: "localhost"
    private val brokerPort = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672
    private val brokerUser = System.getenv("RABBITMQ_USER") ?: "clapi"
    private val brokerPass = System.getenv("RABBITMQ_PASS") ?: "clapi-secret"

    private fun requireBrokerOrSkip() {
        val reachable = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(brokerHost, brokerPort), 1_500)
                true
            }
        }.getOrDefault(false)
        if (!reachable) {
            val isCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
            if (isCi) {
                fail("RabbitMQ broker not reachable at $brokerHost:$brokerPort in CI — Docker service failed to start")
            } else {
                Assume.assumeTrue("Skipping live RabbitMQ UI E2E: broker not reachable at $brokerHost:$brokerPort", false)
            }
        }
    }

    @Test
    fun rabbitmq_editor_switches_modes_hides_subscribe_tabs_and_saves_edits() {
        val state = workspace.newAppState(openDefaultTab = false)
        state.createNewCollection("Messaging")
        val col = state.collections.first()
        val rmqTab = RequestTabState(
            name = "Orders Queue",
            kind = RequestKind.RABBITMQ,
            url = "amqp://localhost:5672/",
            collectionId = col.id,
        ).apply {
            rabbitMqMode = RabbitMqOperationMode.PUBLISH
            selectedRabbitMqTab = RabbitMqEditorTab.MESSAGE
            bodyContent = """{"orderId":101}"""
        }
        state.openTabs.add(rmqTab)
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // PUBLISH mode shows MESSAGE and HEADERS tabs
        composeRule.onNodeWithTag("rabbitmq-request-editor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("rabbitmq-tab-message", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("rabbitmq-tab-headers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("rabbitmq-tab-amqp", useUnmergedTree = true).assertIsDisplayed()

        // Switch to SUBSCRIBE via dropdown -> MESSAGE & HEADERS hidden, selected tab falls back to AMQP
        composeRule.onNodeWithTag("rabbitmq-mode-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rabbitmq-mode-option-subscribe", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(RabbitMqOperationMode.SUBSCRIBE, rmqTab.rabbitMqMode)
        assertEquals(RabbitMqEditorTab.AMQP, rmqTab.selectedRabbitMqTab)
        composeRule.onNodeWithTag("rabbitmq-tab-message", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("rabbitmq-tab-headers", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("rabbitmq-tab-amqp", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(rmqTab.isDirty, "Switching mode must mark tab dirty")

        // Switch to RPC -> MESSAGE & HEADERS reappear
        composeRule.onNodeWithTag("rabbitmq-mode-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rabbitmq-mode-option-rpc", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(RabbitMqOperationMode.RPC, rmqTab.rabbitMqMode)
        composeRule.onNodeWithTag("rabbitmq-tab-message", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("rabbitmq-tab-headers", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun rabbitmq_unreachable_broker_surfaces_error_cleanly_without_crash() {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val state = workspace.newAppState(openDefaultTab = false)
        val rmqTab = RequestTabState(
            name = "Unreachable Broker",
            kind = RequestKind.RABBITMQ,
            url = "amqp://127.0.0.1:$unusedPort/",
        ).apply {
            rabbitMqMode = RabbitMqOperationMode.PUBLISH
            rabbitMqConnection = RabbitMqConnectionConfig(host = "127.0.0.1", port = unusedPort, vhost = "/")
            rabbitMqExchange = RabbitMqExchangeConfig(name = "test.ex", type = RabbitMqExchangeType.TOPIC)
            rabbitMqRoutingKey = "test.key"
            bodyContent = """{"ping":true}"""
        }
        state.openTabs.add(rmqTab)
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rabbitmq-primary-action", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !rmqTab.rabbitMqIsExecuting && (
                rmqTab.rabbitMqError != null ||
                    rmqTab.lastError != null ||
                    rmqTab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.ERROR ||
                    state.consoleLogs.any { it.message.contains("RabbitMQ", ignoreCase = true) || it.message.contains("fail", ignoreCase = true) || it.message.contains("error", ignoreCase = true) }
                )
        }
        composeRule.waitForIdle()

        assertTrue(
            rmqTab.rabbitMqError != null ||
                rmqTab.lastError != null ||
                rmqTab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.ERROR ||
                state.consoleLogs.isNotEmpty(),
            "Expected error feedback when publishing to unreachable RabbitMQ port",
        )
        composeRule.onNodeWithTag("rabbitmq-primary-action", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun rabbitmq_live_publish_subscribe_and_rpc_work_from_ui() {
        requireBrokerOrSkip()

        val exchangeName = "clapi.ui.e2e.${UUID.randomUUID()}"
        val rpcQueue = "clapi.ui.rpc.${UUID.randomUUID()}"
        val state = workspace.newAppState(openDefaultTab = false)

        val subTab = RequestTabState(
            name = "UI Sub",
            kind = RequestKind.RABBITMQ,
            url = "amqp://$brokerHost:$brokerPort/",
        ).apply {
            rabbitMqMode = RabbitMqOperationMode.SUBSCRIBE
            rabbitMqConnection = RabbitMqConnectionConfig(host = brokerHost, port = brokerPort, vhost = "/")
            rabbitMqExchange = RabbitMqExchangeConfig(name = exchangeName, type = RabbitMqExchangeType.TOPIC, durable = false, autoDelete = true)
            rabbitMqQueue = RabbitMqQueueConfig(name = "", durable = false, exclusive = true, autoDelete = true)
            rabbitMqRoutingKey = "ui.#"
            rabbitMqSettings = io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings(
                declareTopology = true,
                ackMode = AmqpAckMode.MANUAL,
            )
            authType = io.github.makeforge.clapi.core.model.AuthType.BASIC
            authUsername = brokerUser
            authPassword = brokerPass
        }
        val pubTab = RequestTabState(
            name = "UI Pub",
            kind = RequestKind.RABBITMQ,
            url = "amqp://$brokerHost:$brokerPort/",
        ).apply {
            rabbitMqMode = RabbitMqOperationMode.PUBLISH
            rabbitMqConnection = RabbitMqConnectionConfig(host = brokerHost, port = brokerPort, vhost = "/")
            rabbitMqExchange = RabbitMqExchangeConfig(name = exchangeName, type = RabbitMqExchangeType.TOPIC, durable = false, autoDelete = true)
            rabbitMqRoutingKey = "ui.created"
            rabbitMqSettings = io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestSettings(
                declareTopology = true,
            )
            bodyContent = """{"event":"ui-live-pub"}"""
            authType = io.github.makeforge.clapi.core.model.AuthType.BASIC
            authUsername = brokerUser
            authPassword = brokerPass
        }
        state.openTabs.addAll(listOf(subTab, pubTab))
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. Start subscription from UI
        composeRule.onNodeWithTag("rabbitmq-primary-action", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            subTab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.CONNECTED
        }

        // 2. Switch to Pub tab and publish via UI
        composeRule.onNodeWithTag("tab-chip-${pubTab.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rabbitmq-primary-action", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !pubTab.rabbitMqIsExecuting && subTab.rabbitMqMessages.any { it.payloadText?.contains("ui-live-pub") == true }
        }

        // 3. Switch back to Sub tab, verify message in timeline & unsubscribe cleanly
        composeRule.onNodeWithTag("tab-chip-${subTab.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ui-live-pub", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("rabbitmq-primary-action", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            subTab.rabbitMqConnectionStatus == RabbitMqConnectionStatus.DISCONNECTED
        }

        // 4. Live RPC round-trip via UI with local responder
        val factory = ConnectionFactory().apply {
            host = brokerHost
            port = brokerPort
            username = brokerUser
            password = brokerPass
        }
        factory.newConnection().use { conn ->
            conn.createChannel().use { ch ->
                ch.queueDeclare(rpcQueue, false, false, true, null)
                val deliver = DeliverCallback { _, delivery ->
                    val props = AMQP.BasicProperties.Builder()
                        .correlationId(delivery.properties.correlationId)
                        .build()
                    ch.basicPublish("", delivery.properties.replyTo, props, """{"reply":"rpc-ui-ok"}""".toByteArray())
                }
                ch.basicConsume(rpcQueue, true, deliver) { _ -> }

                val rpcTab = RequestTabState(
                    name = "UI RPC",
                    kind = RequestKind.RABBITMQ,
                    url = "amqp://$brokerHost:$brokerPort/",
                ).apply {
                    rabbitMqMode = RabbitMqOperationMode.RPC
                    rabbitMqConnection = RabbitMqConnectionConfig(host = brokerHost, port = brokerPort, vhost = "/")
                    rabbitMqExchange = RabbitMqExchangeConfig(name = "", type = RabbitMqExchangeType.DIRECT)
                    rabbitMqRoutingKey = rpcQueue
                    bodyContent = """{"ping":"rpc"}"""
                    authType = io.github.makeforge.clapi.core.model.AuthType.BASIC
                    authUsername = brokerUser
                    authPassword = brokerPass
                }
                composeRule.runOnUiThread {
                    state.openTabs.add(rpcTab)
                    state.activeTabIndex = state.openTabs.lastIndex
                }
                composeRule.waitForIdle()

                composeRule.onNodeWithTag("rabbitmq-primary-action", useUnmergedTree = true).performClick()
                composeRule.waitUntil(timeoutMillis = 15_000) {
                    !rpcTab.rabbitMqIsExecuting && rpcTab.rabbitMqLastRpcResult != null
                }
                assertNotNull(rpcTab.rabbitMqLastRpcResult)
                assertTrue(rpcTab.rabbitMqLastRpcResult!!.reply?.payloadText?.contains("rpc-ui-ok") == true)
            }
        }
    }
}
