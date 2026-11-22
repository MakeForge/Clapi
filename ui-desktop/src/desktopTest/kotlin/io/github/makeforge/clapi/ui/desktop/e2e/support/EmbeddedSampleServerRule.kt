// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e.support

import io.github.makeforge.clapi.server.module
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import org.junit.rules.ExternalResource
import java.net.ServerSocket
import java.net.Socket

/**
 * JUnit [ExternalResource] spinning up the deterministic `sample-server` on an ephemeral localhost
 * port for interactive Compose UI E2E tests.
 */
class EmbeddedSampleServerRule : ExternalResource() {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    var port: Int = 0
        private set

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    val wsUrl: String
        get() = "ws://127.0.0.1:$port/ws"

    val mcpHttpUrl: String
        get() = "$baseUrl/mcp"

    val mcpLegacySseUrl: String
        get() = "$baseUrl/mcp/sse"

    override fun before() {
        port = ServerSocket(0).use { it.localPort }
        server = embeddedServer(Netty, port = port, module = { module() }).also {
            it.start(wait = false)
        }
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            val ready = runCatching {
                Socket("127.0.0.1", port).use { true }
            }.getOrDefault(false)
            if (ready) return
            Thread.sleep(50)
        }
        error("Embedded sample-server failed to accept TCP connections on 127.0.0.1:$port within 5s")
    }

    override fun after() {
        runCatching { server?.stop(300, 1_500) }
        server = null
    }
}
