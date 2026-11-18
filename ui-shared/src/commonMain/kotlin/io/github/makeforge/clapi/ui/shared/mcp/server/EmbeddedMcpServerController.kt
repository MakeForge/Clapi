package io.github.makeforge.clapi.ui.shared.mcp.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.makeforge.clapi.mcp.server.ClapiMcpServer
import io.github.makeforge.clapi.mcp.server.transport.KtorMcpServerHost
import io.github.makeforge.clapi.ui.shared.build.GeneratedBuildInfo
import io.github.makeforge.clapi.ui.shared.state.AppState

/**
 * Manages the lifecycle of the embedded Ktor HTTP MCP Server inside Clapi Desktop.
 */
class EmbeddedMcpServerController(
    private val appState: AppState,
) {
    val backend: AppStateWorkspaceBackend = AppStateWorkspaceBackend(appState)
    val mcpServer: ClapiMcpServer = ClapiMcpServer(
        backend = backend,
        serverVersion = GeneratedBuildInfo.APP_VERSION,
    )

    private var host: KtorMcpServerHost? = null

    var isRunning by mutableStateOf(false)
        private set

    var activePort by mutableStateOf(3333)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    val endpointUrl: String
        get() = "http://127.0.0.1:$activePort/mcp"

    @Synchronized
    fun start(port: Int = appState.settings.mcpServerPort): Boolean {
        stop()
        val safePort = port.coerceIn(0, 65535)
        return try {
            val newHost = KtorMcpServerHost(
                mcpServer = mcpServer,
                host = "127.0.0.1",
                requestedPort = safePort,
            )
            newHost.start()
            host = newHost
            activePort = newHost.actualPort
            isRunning = true
            lastError = null
            true
        } catch (e: Exception) {
            host = null
            isRunning = false
            lastError = e.message ?: "Failed to bind MCP server on 127.0.0.1:$safePort"
            false
        }
    }

    @Synchronized
    fun stop() {
        val current = host
        host = null
        isRunning = false
        current?.stop()
    }

    @Synchronized
    fun applySettings() {
        if (appState.settings.mcpServerEnabled) {
            if (!isRunning || activePort != appState.settings.mcpServerPort) {
                start(appState.settings.mcpServerPort)
            }
        } else {
            if (isRunning) {
                stop()
            }
        }
    }
}
