package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.network.ConnectionState
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.encodeBase64
import io.github.makeforge.clapi.core.network.websocket.WebSocketClientManager
import io.github.makeforge.clapi.core.network.websocket.WebSocketConnectConfig
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun connectWebSocketTab(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
) {
    if (tab.url.isBlank()) {
        tab.lastError = "URL is empty"
        state.log("WebSocket URL is empty", LogLevel.WARNING)
        return
    }

    val layers = state.activeVariableLayers(tab)
    var resolvedUrl = VariableResolver.resolve(tab.url.trim(), layers, removeUnresolved = true)
    if (!resolvedUrl.startsWith("ws://", ignoreCase = true) && !resolvedUrl.startsWith("wss://", ignoreCase = true)) {
        if (resolvedUrl.startsWith("http://", ignoreCase = true)) {
            resolvedUrl = "ws://" + resolvedUrl.substring(7)
        } else if (resolvedUrl.startsWith("https://", ignoreCase = true)) {
            resolvedUrl = "wss://" + resolvedUrl.substring(8)
        } else {
            resolvedUrl = "ws://" + resolvedUrl
        }
    }

    val headersMap = mutableMapOf<String, String>()
    tab.headers
        .filter { it.enabled && it.key.isNotBlank() }
        .forEach {
            val k = VariableResolver.resolve(it.key, layers, removeUnresolved = true)
            val v = VariableResolver.resolve(it.value, layers, removeUnresolved = true)
            headersMap[k] = v
        }

    val auth = buildAuthConfig(tab, state)
    when (auth.type) {
        AuthType.BEARER, AuthType.JWT -> {
            val token = VariableResolver.resolve(auth.params["token"].orEmpty(), layers, removeUnresolved = true)
            if (token.isNotBlank()) {
                headersMap["Authorization"] = "Bearer $token"
            }
        }
        AuthType.BASIC -> {
            val u = VariableResolver.resolve(auth.params["username"].orEmpty(), layers, removeUnresolved = true)
            val p = VariableResolver.resolve(auth.params["password"].orEmpty(), layers, removeUnresolved = true)
            if (u.isNotBlank() || p.isNotBlank()) {
                headersMap["Authorization"] = "Basic " + "$u:$p".encodeToByteArray().encodeBase64()
            }
        }
        AuthType.API_KEY -> {
            val k = VariableResolver.resolve(auth.params["key"].orEmpty(), layers, removeUnresolved = true)
            val v = VariableResolver.resolve(auth.params["value"].orEmpty(), layers, removeUnresolved = true)
            if (k.isNotBlank()) {
                if (auth.placement.equals("query", ignoreCase = true)) {
                    val delimiter = if (resolvedUrl.contains("?")) "&" else "?"
                    resolvedUrl = "$resolvedUrl$delimiter$k=$v"
                } else {
                    headersMap[k] = v
                }
            }
        }
        else -> {}
    }

    val timeoutMs = tab.webSocketTimeout?.takeIf { it != "inherit" }?.toLongOrNull()
    val pingMs = tab.webSocketKeepAliveInterval?.takeIf { it != "inherit" }?.toLongOrNull()

    val config = WebSocketConnectConfig(
        url = resolvedUrl,
        headers = headersMap,
        timeoutMs = timeoutMs,
        pingIntervalMs = pingMs,
    )

    state.log("Connecting to WebSocket: $resolvedUrl", LogLevel.INFO)
    WebSocketClientManager.default.connect(tab.id, config)
}

fun disconnectWebSocketTab(tab: RequestTabState) {
    WebSocketClientManager.default.disconnect(tab.id)
}

fun sendWebSocketMessage(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
) {
    val rawPayload = tab.webSocketOutgoingMessage
    val format = tab.effectiveWebSocketFormat
    val layers = state.activeVariableLayers(tab)
    val resolvedPayload = VariableResolver.resolve(rawPayload, layers, removeUnresolved = true)

    scope.launch {
        try {
            WebSocketClientManager.default.sendMessage(tab.id, resolvedPayload, format)
        } catch (e: Exception) {
            state.log("Failed to send WebSocket message: ${e.message}", LogLevel.ERROR)
        }
    }
}

internal fun sendWebSocketRequestInternal(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
) {
    if (tab.webSocketConnectionState == ConnectionState.CONNECTED) {
        sendWebSocketMessage(scope, state, tab)
    } else {
        connectWebSocketTab(scope, state, tab)
    }
}

