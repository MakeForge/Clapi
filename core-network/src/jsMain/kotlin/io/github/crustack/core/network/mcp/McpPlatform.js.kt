package io.github.crustack.core.network.mcp

import io.github.crustack.core.model.McpConnectionConfig
import kotlin.random.Random

actual val mcpStdioSupported: Boolean = false

actual fun createStdioTransport(config: McpConnectionConfig): McpTransport =
    throw UnsupportedOperationException("MCP stdio is not supported in the browser")

actual fun mcpSecureRandomBytes(size: Int): ByteArray = Random.Default.nextBytes(size)

actual val mcpInteractiveOAuthSupported: Boolean = false

actual suspend fun mcpOpenAuthorizeUrlAndAwaitCode(authorizeUrl: String, redirectPort: Int): String =
    throw UnsupportedOperationException("Paste the redirected URL / authorization code to complete OAuth in the browser")
