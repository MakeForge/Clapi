package io.github.makeforge.clapi.mcp.server.transport

import io.github.makeforge.clapi.mcp.server.ClapiMcpServer
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

/**
 * Headless CLI Stdio runner for Clapi MCP Server (`--mcp [--dir <path>]`).
 *
 * Captures the original `System.out` exclusively for JSON-RPC 2.0 frame output and immediately
 * redirects `System.setOut(System.err)` so that any diagnostic logs or library output go to `stderr`
 * and never corrupt newline-delimited JSON-RPC framing on `stdout`.
 */
object StdioMcpServerRunner {

    /**
     * Parses CLI arguments for `--dir <path>` or `--dir=<path>`, falling back to
     * `-Dclapi.dir`, `CLAPI_DIR`, or `~/.clapi`.
     */
    fun resolveStorageDirectory(args: Array<String>): String {
        for (i in args.indices) {
            val arg = args[i]
            if (arg == "--dir" && i + 1 < args.size && args[i + 1].isNotBlank()) {
                return args[i + 1].trim()
            }
            if (arg.startsWith("--dir=")) {
                val value = arg.removePrefix("--dir=").trim()
                if (value.isNotBlank()) return value
            }
        }
        return System.getProperty("clapi.dir")?.takeIf { it.isNotBlank() }
            ?: System.getenv("CLAPI_DIR")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.clapi")
    }

    /**
     * Entry point for `clapi --mcp`. Redirects `System.out` to `System.err` before initializing
     * any workspace or storage components.
     */
    fun runFromCli(args: Array<String>, appVersion: String = "1.19.0") {
        val protocolOut: PrintStream = System.out
        val protocolIn: InputStream = System.`in`
        System.setOut(System.err)

        val storageDir = resolveStorageDirectory(args)
        File(storageDir).mkdirs()

        val backend = HeadlessWorkspaceBackend(baseDirectory = storageDir)
        val server = ClapiMcpServer(backend = backend, serverVersion = appVersion)

        runBlocking {
            runStreamLoop(
                input = protocolIn,
                output = protocolOut,
                server = server,
            )
        }
    }

    /**
     * Processes newline-delimited JSON-RPC 2.0 requests from [input] and writes responses to [output].
     */
    suspend fun runStreamLoop(
        input: InputStream,
        output: OutputStream,
        server: ClapiMcpServer,
    ) {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        val writer = PrintWriter(OutputStreamWriter(output, StandardCharsets.UTF_8), false)
        val session = server.getOrCreateSession()

        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val response = server.handleJsonRpc(trimmed, session)
            if (response != null) {
                writer.println(response.toString())
                writer.flush()
            }
        }
    }
}
