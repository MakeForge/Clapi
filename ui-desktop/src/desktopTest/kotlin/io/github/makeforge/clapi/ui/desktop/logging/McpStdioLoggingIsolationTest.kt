package io.github.makeforge.clapi.ui.desktop.logging

import io.github.makeforge.clapi.core.logging.AppLogging
import io.github.makeforge.clapi.mcp.server.transport.StdioMcpServerRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpStdioLoggingIsolationTest {

    private val originalOut = System.out
    private val originalErr = System.err
    private val capturedOut = ByteArrayOutputStream()
    private val capturedErr = ByteArrayOutputStream()

    @Before
    fun setup() {
        System.setOut(PrintStream(capturedOut))
        System.setErr(PrintStream(capturedErr))
    }

    @After
    fun tearDown() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun testStdioMcpModeDoesNotPolluteStdoutWithLogs() {
        // Run log configurator as in main()
        val tempDir = System.getProperty("java.io.tmpdir") + "/clapi-mcp-log-test"
        LogDirectoryConfigurator.configure(arrayOf("--mcp", "--dir", tempDir))

        // In MCP mode, System.out is captured for JSON-RPC and System.setOut(System.err) is called
        val protocolOut = System.out
        System.setOut(System.err)

        val logger = AppLogging.logger("McpIsolationTest")
        val logMarker = "MCP_DIAGNOSTIC_LOG_MESSAGE_999"
        logger.info { logMarker }

        // Protocol writes to protocolOut
        val jsonRpcMarker = """{"jsonrpc":"2.0","result":{},"id":1}"""
        protocolOut.println(jsonRpcMarker)

        val outString = capturedOut.toString(Charsets.UTF_8.name())
        assertTrue(outString.contains(jsonRpcMarker), "Stdout must contain protocol JSON-RPC frames")
        assertFalse(outString.contains(logMarker), "Stdout must NEVER contain diagnostic log text")
    }
}
