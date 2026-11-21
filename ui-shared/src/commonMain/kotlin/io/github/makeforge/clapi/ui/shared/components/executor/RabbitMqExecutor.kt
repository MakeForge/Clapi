package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.AmqpAckStatus
import io.github.makeforge.clapi.core.model.AuthConfig
import io.github.makeforge.clapi.core.model.RabbitMqConnectionStatus
import io.github.makeforge.clapi.core.model.RabbitMqPublishResult
import io.github.makeforge.clapi.core.model.RabbitMqRpcResult
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.RabbitMqRequestItem
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.network.rabbitmq.RabbitMqRequestExecutor
import io.github.makeforge.clapi.core.scripting.ClapiScriptEngine
import io.github.makeforge.clapi.core.scripting.ScriptContext
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.ConsoleEntry
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val sharedRabbitMqExecutor = RabbitMqRequestExecutor()
private val sharedRabbitMqScriptEngine = ClapiScriptEngine()

private fun buildTabRabbitMqItem(tab: RequestTabState): RabbitMqRequestItem {
    val msgPayload = if (tab.webSocketMessageVariants.isNotEmpty()) {
        WebSocketMessagePayload.Variants(tab.webSocketMessageVariants.toList())
    } else {
        WebSocketMessagePayload.Single(
            WebSocketMessage(type = tab.effectiveWebSocketFormat, data = tab.webSocketOutgoingMessage)
        )
    }
    val openAuth: io.github.makeforge.clapi.core.model.opencollection.AuthConfig? = when (tab.authType) {
        io.github.makeforge.clapi.core.model.AuthType.INHERIT -> AuthInherit
        io.github.makeforge.clapi.core.model.AuthType.BASIC -> AuthBasic(
            username = tab.authUsername,
            password = tab.authPassword,
        )
        io.github.makeforge.clapi.core.model.AuthType.BEARER,
        io.github.makeforge.clapi.core.model.AuthType.JWT -> AuthBearer(token = tab.authToken)
        io.github.makeforge.clapi.core.model.AuthType.OAUTH2 -> AuthOAuth2()
        else -> null
    }
    return RabbitMqRequestItem(
        info = RabbitMqRequestInfo(name = tab.name),
        rabbitmq = RabbitMqRequestDetails(
            mode = tab.rabbitMqMode,
            connection = tab.rabbitMqConnection,
            exchange = tab.rabbitMqExchange,
            queue = tab.rabbitMqQueue,
            routingKey = tab.rabbitMqRoutingKey,
            replyQueue = tab.rabbitMqReplyQueue.ifBlank { null },
            headers = tab.headers.map {
                HttpRequestHeader(name = it.key, value = it.value, disabled = if (it.enabled) null else true)
            },
            properties = tab.rabbitMqProperties,
            message = msgPayload,
            auth = openAuth,
        ),
        settings = tab.rabbitMqSettings,
        docs = tab.rabbitMqDocs.ifBlank { null },
    )
}

private fun resolveAncestorAuthChain(state: AppState, tab: RequestTabState): List<io.github.makeforge.clapi.core.model.opencollection.AuthConfig?> {
    val inherited = state.resolveInheritedAuth(tab) ?: return emptyList()
    val mapped: io.github.makeforge.clapi.core.model.opencollection.AuthConfig? = when (inherited.authType) {
        io.github.makeforge.clapi.core.model.AuthType.BASIC -> AuthBasic(
            username = inherited.username.orEmpty(),
            password = inherited.password.orEmpty(),
        )
        io.github.makeforge.clapi.core.model.AuthType.BEARER,
        io.github.makeforge.clapi.core.model.AuthType.JWT -> AuthBearer(
            token = inherited.token.orEmpty(),
        )
        io.github.makeforge.clapi.core.model.AuthType.OAUTH2 -> AuthOAuth2()
        else -> null
    }
    return listOf(mapped)
}

private fun buildVariableLayers(state: AppState): List<Map<String, String>> {
    val layers = mutableListOf<Map<String, String>>()
    val envMap = state.selectedEnvironment?.toVariableMap(state.environments).orEmpty()
    if (envMap.isNotEmpty()) layers.add(envMap)
    val globalMap = state.globalVariables.filter { it.enabled }.associate { it.key to it.value }
    if (globalMap.isNotEmpty()) layers.add(globalMap)
    return layers
}

fun executeRabbitMqPublishTab(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    if (tab.rabbitMqIsExecuting) return
    scope.launch {
        tab.rabbitMqIsExecuting = true
        tab.rabbitMqError = null
        try {
            val layers = buildVariableLayers(state).toMutableList()
            if (tab.preRequestScript.isNotBlank()) {
                val envVars = state.selectedEnvironment?.toVariableMap(state.environments).orEmpty().toMutableMap()
                val res = sharedRabbitMqScriptEngine.executePreRequestScript(
                    tab.preRequestScript,
                    ScriptContext(url = tab.rabbitMqConnection.host, method = "PUB", variables = envVars),
                )
                res.logs.forEach { state.consoleLogs.add(ConsoleEntry(message = it, level = LogLevel.INFO)) }
                if (res.newVariables.isNotEmpty()) layers.add(0, res.newVariables)
            }
            val item = buildTabRabbitMqItem(tab)
            val result: RabbitMqPublishResult = sharedRabbitMqExecutor.publish(
                item = item,
                variableLayers = layers,
                ancestorAuthChain = resolveAncestorAuthChain(state, tab),
                resolvedOAuth2Token = tab.authOAuth2AccessToken.ifBlank { null },
            )
            tab.rabbitMqLastPublishResult = result
            if (!result.isSuccess) {
                tab.rabbitMqError = result.errorMessage
                    ?: if (result.returnedUnroutable) "Unroutable (${result.returnReplyCode}): ${result.returnReplyText}" else "Publish failed"
            }
            if (tab.testScript.isNotBlank()) {
                val body = """{"confirmed":${result.confirmed},"exchange":"${result.exchange}","routingKey":"${result.routingKey}"}"""
                val testRes = sharedRabbitMqScriptEngine.executeTestScript(
                    tab.testScript,
                    ScriptContext(
                        url = tab.rabbitMqConnection.host,
                        method = "PUB",
                        statusCode = if (result.isSuccess) 200 else 500,
                        responseBody = body,
                        responseTimeMs = result.elapsedMs,
                    ),
                )
                testRes.logs.forEach { state.consoleLogs.add(ConsoleEntry(message = it, level = LogLevel.INFO)) }
                tab.testResults.clear()
                tab.testResults.addAll(
                    testRes.assertions.map { a ->
                        io.github.makeforge.clapi.ui.shared.state.TestResultEntry(
                            name = a.name,
                            passed = a.passed,
                            message = a.message.orEmpty(),
                        )
                    }
                )
            }
        } catch (e: Exception) {
            tab.rabbitMqError = e.message ?: "RabbitMQ publish error"
        } finally {
            tab.rabbitMqIsExecuting = false
        }
    }
}

fun executeRabbitMqRpcTab(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    if (tab.rabbitMqIsExecuting) return
    scope.launch {
        tab.rabbitMqIsExecuting = true
        tab.rabbitMqError = null
        try {
            val layers = buildVariableLayers(state).toMutableList()
            if (tab.preRequestScript.isNotBlank()) {
                val envVars = state.selectedEnvironment?.toVariableMap(state.environments).orEmpty().toMutableMap()
                val res = sharedRabbitMqScriptEngine.executePreRequestScript(
                    tab.preRequestScript,
                    ScriptContext(url = tab.rabbitMqConnection.host, method = "RPC", variables = envVars),
                )
                res.logs.forEach { state.consoleLogs.add(ConsoleEntry(message = it, level = LogLevel.INFO)) }
                if (res.newVariables.isNotEmpty()) layers.add(0, res.newVariables)
            }
            val item = buildTabRabbitMqItem(tab)
            val result: RabbitMqRpcResult = sharedRabbitMqExecutor.rpc(
                item = item,
                variableLayers = layers,
                ancestorAuthChain = resolveAncestorAuthChain(state, tab),
                resolvedOAuth2Token = tab.authOAuth2AccessToken.ifBlank { null },
            )
            tab.rabbitMqLastRpcResult = result
            if (!result.isSuccess) {
                tab.rabbitMqError = result.errorMessage ?: "RPC call failed"
            }
            if (tab.testScript.isNotBlank()) {
                val testRes = sharedRabbitMqScriptEngine.executeTestScript(
                    tab.testScript,
                    ScriptContext(
                        url = tab.rabbitMqConnection.host,
                        method = "RPC",
                        statusCode = if (result.isSuccess) 200 else if (result.timedOut) 504 else 500,
                        responseBody = result.reply?.payloadText.orEmpty(),
                        responseHeaders = result.reply?.headers.orEmpty(),
                        responseTimeMs = result.elapsedMs,
                    ),
                )
                testRes.logs.forEach { state.consoleLogs.add(ConsoleEntry(message = it, level = LogLevel.INFO)) }
                tab.testResults.clear()
                tab.testResults.addAll(
                    testRes.assertions.map { a ->
                        io.github.makeforge.clapi.ui.shared.state.TestResultEntry(
                            name = a.name,
                            passed = a.passed,
                            message = a.message.orEmpty(),
                        )
                    }
                )
            }
        } catch (e: Exception) {
            tab.rabbitMqError = e.message ?: "RabbitMQ RPC error"
        } finally {
            tab.rabbitMqIsExecuting = false
        }
    }
}

fun startRabbitMqSubscriptionTab(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    scope.launch {
        tab.rabbitMqActiveSession?.close()
        tab.rabbitMqActiveSession = null
        tab.rabbitMqError = null
        tab.rabbitMqConnectionStatus = RabbitMqConnectionStatus.CONNECTING

        val item = buildTabRabbitMqItem(tab)
        val session = sharedRabbitMqExecutor.subscribe(
            item = item,
            variableLayers = buildVariableLayers(state),
            ancestorAuthChain = resolveAncestorAuthChain(state, tab),
            resolvedOAuth2Token = tab.authOAuth2AccessToken.ifBlank { null },
        )
        tab.rabbitMqActiveSession = session

        launch {
            session.status.collect { st ->
                tab.rabbitMqConnectionStatus = st
            }
        }
        launch {
            session.errorMessage.collect { err ->
                if (err != null) tab.rabbitMqError = err
            }
        }
        launch {
            val cap = tab.rabbitMqSettings.clampedMaxTimelineMessages()
            session.messages.collect { rawMsg ->
                val enrichedMsg = if (tab.testScript.isNotBlank()) {
                    val envVars = state.selectedEnvironment?.toVariableMap(state.environments).orEmpty().toMutableMap()
                    val scriptRes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        sharedRabbitMqScriptEngine.executeTestScript(
                            tab.testScript,
                            ScriptContext(
                                url = "${tab.rabbitMqConnection.host}/${rawMsg.exchange}/${rawMsg.routingKey}",
                                method = "SUB",
                                statusCode = 200,
                                responseBody = rawMsg.payloadText.orEmpty(),
                                responseSizeBytes = rawMsg.byteLength,
                                responseHeaders = rawMsg.headers,
                                variables = envVars,
                            ),
                        )
                    }
                    scriptRes.logs.forEach { state.consoleLogs.add(ConsoleEntry(message = it, level = LogLevel.INFO)) }
                    scriptRes.error?.let { err ->
                        state.consoleLogs.add(ConsoleEntry(message = "[RabbitMQ On-Message Script Error] $err", level = LogLevel.ERROR))
                    }
                    if (scriptRes.newVariables.isNotEmpty()) {
                        state.selectedEnvironment?.let { env ->
                            scriptRes.newVariables.forEach { (k, v) ->
                                val existing = env.variables.indexOfFirst { it.key == k }
                                if (existing >= 0) {
                                    env.variables[existing].value = v
                                } else {
                                    env.variables.add(io.github.makeforge.clapi.ui.shared.state.MutableKeyValue(k, v))
                                }
                            }
                        }
                    }
                    val mappedAssertions = scriptRes.assertions.map { a ->
                        io.github.makeforge.clapi.core.model.RabbitMqMessageAssertion(
                            name = a.name,
                            passed = a.passed,
                            message = a.message.orEmpty(),
                        )
                    }
                    tab.testResults.clear()
                    tab.testResults.addAll(
                        mappedAssertions.map { a ->
                            io.github.makeforge.clapi.ui.shared.state.TestResultEntry(a.name, a.passed, a.message)
                        }
                    )
                    rawMsg.copy(assertionResults = mappedAssertions)
                } else {
                    rawMsg
                }
                tab.rabbitMqMessages.add(enrichedMsg)
                while (tab.rabbitMqMessages.size > cap) {
                    tab.rabbitMqMessages.removeAt(0)
                }
            }
        }
    }
}

fun stopRabbitMqSubscriptionTab(scope: CoroutineScope, tab: RequestTabState) {
    scope.launch {
        tab.rabbitMqActiveSession?.close()
        tab.rabbitMqActiveSession = null
        tab.rabbitMqConnectionStatus = RabbitMqConnectionStatus.DISCONNECTED
    }
}

fun ackRabbitMqMessage(scope: CoroutineScope, tab: RequestTabState, deliveryTag: Long) {
    scope.launch {
        runCatching { tab.rabbitMqActiveSession?.ack(deliveryTag) }
        val idx = tab.rabbitMqMessages.indexOfFirst { it.deliveryTag == deliveryTag }
        if (idx >= 0) {
            tab.rabbitMqMessages[idx] = tab.rabbitMqMessages[idx].copy(ackStatus = AmqpAckStatus.ACKED)
        }
    }
}

fun nackRabbitMqMessage(scope: CoroutineScope, tab: RequestTabState, deliveryTag: Long, requeue: Boolean) {
    scope.launch {
        runCatching { tab.rabbitMqActiveSession?.nack(deliveryTag, requeue) }
        val idx = tab.rabbitMqMessages.indexOfFirst { it.deliveryTag == deliveryTag }
        if (idx >= 0) {
            tab.rabbitMqMessages[idx] = tab.rabbitMqMessages[idx].copy(
                ackStatus = if (requeue) AmqpAckStatus.REQUEUED else AmqpAckStatus.NACKED
            )
        }
    }
}
