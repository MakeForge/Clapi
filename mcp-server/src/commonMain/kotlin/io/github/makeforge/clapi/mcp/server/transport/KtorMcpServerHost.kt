package io.github.makeforge.clapi.mcp.server.transport

import io.github.makeforge.clapi.mcp.server.ClapiMcpServer
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Embedded Ktor Netty host for Clapi MCP Server bound strictly to `127.0.0.1`.
 *
 * Exposes:
 * - `POST /mcp`, `GET /mcp`, `DELETE /mcp` (Streamable HTTP `2025-06-18` with `Mcp-Session-Id`)
 * - `GET /mcp/sse`, `POST /mcp/messages` (Legacy HTTP+SSE `2024-11-05`)
 */
class KtorMcpServerHost(
    val mcpServer: ClapiMcpServer,
    val host: String = "127.0.0.1",
    val requestedPort: Int = 3333,
) {
    private var engine: EmbeddedServer<*, *>? = null

    @Volatile
    var actualPort: Int = requestedPort
        private set

    val isRunning: Boolean
        get() = engine != null

    val endpointUrl: String
        get() = "http://$host:$actualPort/mcp"

    @Synchronized
    fun start() {
        if (engine != null) return
        val srv = embeddedServer(Netty, host = host, port = requestedPort) {
            routing {
                installClapiMcpRoutes(mcpServer)
            }
        }
        srv.start(wait = false)
        val resolved = runCatching {
            runBlocking { srv.engine.resolvedConnectors().firstOrNull()?.port }
        }.getOrNull()
        actualPort = resolved ?: requestedPort
        engine = srv
    }

    @Synchronized
    fun stop(gracePeriodMillis: Long = 200, timeoutMillis: Long = 1000) {
        val current = engine ?: return
        engine = null
        runCatching {
            current.stop(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
        }
    }
}

fun Route.installClapiMcpRoutes(mcpServer: ClapiMcpServer) {
    val eventIds = AtomicInteger(1)
    val sseChannels = ConcurrentHashMap<String, Channel<String>>()
    val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    fun ApplicationCall.applyMcpCors() {
        response.header("Access-Control-Allow-Origin", "*")
        response.header("Access-Control-Allow-Headers", "*")
        response.header("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS")
        response.header("Access-Control-Expose-Headers", "Mcp-Session-Id")
    }

    fun formatSseFrame(data: String, id: Int, event: String = "message"): String = buildString {
        append("id: ").append(id).append('\n')
        if (event != "message") append("event: ").append(event).append('\n')
        append("data: ").append(data).append("\n\n")
    }

    options("/mcp") {
        call.applyMcpCors()
        call.respond(HttpStatusCode.NoContent)
    }
    options("/mcp/sse") {
        call.applyMcpCors()
        call.respond(HttpStatusCode.NoContent)
    }
    options("/mcp/messages") {
        call.applyMcpCors()
        call.respond(HttpStatusCode.NoContent)
    }

    post("/mcp") {
        call.applyMcpCors()
        val sessionHeader = call.request.header("Mcp-Session-Id")
        if (!sessionHeader.isNullOrBlank() && !mcpServer.sessions.containsKey(sessionHeader)) {
            call.respond(HttpStatusCode.NotFound, "Unknown MCP session")
            return@post
        }
        val body = call.receiveText()
        val session = mcpServer.getOrCreateSession(sessionHeader)
        val responseObj = mcpServer.handleJsonRpc(body, session)

        val method = runCatching {
            (lenientJson.parseToJsonElement(body) as? JsonObject)?.get("method")
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
        }.getOrNull()

        if (method == "initialize" && responseObj?.get("result") != null) {
            call.response.header("Mcp-Session-Id", session.id)
        }

        if (responseObj == null) {
            call.respond(HttpStatusCode.Accepted)
        } else {
            call.respondText(responseObj.toString(), ContentType.Application.Json)
        }
    }

    get("/mcp") {
        call.applyMcpCors()
        val sessionHeader = call.request.header("Mcp-Session-Id")
        val session = sessionHeader?.let { mcpServer.sessions[it] }
        if (session == null) {
            call.respond(HttpStatusCode.MethodNotAllowed, "GET SSE requires active Mcp-Session-Id")
            return@get
        }
        val channel = sseChannels.getOrPut(session.id) { Channel(Channel.UNLIMITED) }
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
            for (frame in channel) {
                write(frame)
                flush()
            }
        }
    }

    delete("/mcp") {
        call.applyMcpCors()
        val sessionHeader = call.request.header("Mcp-Session-Id")
        if (sessionHeader != null) {
            mcpServer.sessions.remove(sessionHeader)
            sseChannels.remove(sessionHeader)?.close()
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // Legacy 2024-11-05 HTTP+SSE transport
    get("/mcp/sse") {
        call.applyMcpCors()
        val session = mcpServer.getOrCreateSession(null)
        val channel = Channel<String>(Channel.UNLIMITED)
        sseChannels[session.id] = channel
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
            write("event: endpoint\ndata: /mcp/messages?sessionId=${session.id}\n\n")
            flush()
            for (frame in channel) {
                write(frame)
                flush()
            }
        }
    }

    post("/mcp/messages") {
        call.applyMcpCors()
        val sid = call.request.queryParameters["sessionId"]
        val session = mcpServer.getOrCreateSession(sid)
        val channel = sseChannels[session.id]
        val responseObj = mcpServer.handleJsonRpc(call.receiveText(), session)
        if (responseObj != null) {
            channel?.send(formatSseFrame(responseObj.toString(), eventIds.getAndIncrement()))
        }
        call.respond(HttpStatusCode.Accepted)
    }
}
