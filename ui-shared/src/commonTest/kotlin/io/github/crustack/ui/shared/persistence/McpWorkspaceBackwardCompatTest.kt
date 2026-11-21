package io.github.crustack.ui.shared.persistence

import io.github.crustack.core.model.RequestKind
import io.github.crustack.ui.shared.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpWorkspaceBackwardCompatTest {
    @Test
    fun pre_mcp_workspace_loads_as_http() {
        val json = """
            {"type":"clapiWorkspace","version":"1.0",
             "collections":[{"type":"clapiCollection","version":"1.0","name":"Old",
               "folders":[],"requests":[{"name":"Ping","method":"GET","url":"http://localhost/ping"}]}],
             "environments":[]}
        """.trimIndent()
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importWorkspaceFromString(state, json)
        val req = state.collections.first().children.first { !it.isFolder }
        assertEquals(RequestKind.HTTP, req.kind)
        assertEquals("Ping", req.name)
    }

    @Test
    fun mcp_request_imports_auth_and_headers_into_mcp_config() {
        val json = """
            {"type":"clapiWorkspace","version":"1.0",
             "collections":[{"type":"clapiCollection","version":"1.0","name":"Mcp",
               "folders":[],"requests":[{
                 "name":"Authed MCP",
                 "kind":"MCP",
                 "method":"POST",
                 "url":"http://localhost/mcp/authed",
                 "mcpTransport":"STREAMABLE_HTTP",
                 "auth":{"type":"BEARER","token":"clapi-mcp-token"},
                 "headers":[{"key":"X-Api-Key","value":"clapi-key"}]
               }]}],
             "environments":[]}
        """.trimIndent()
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importWorkspaceFromString(state, json)
        val req = state.collections.first().children.first { !it.isFolder }
        assertEquals(RequestKind.MCP, req.kind)
        assertEquals("BEARER", req.authType?.name)
        assertEquals("clapi-mcp-token", req.authToken)
        val mcp = req.mcpConfig
        assertTrue(mcp != null)
        assertEquals("clapi-mcp-token", mcp!!.auth.params["token"])
        assertEquals("clapi-key", mcp.headers.single { it.key == "X-Api-Key" }.value)
    }

    @Test
    fun mcp_client_fields_round_trip() {
        val json = """
            {"type":"clapiWorkspace","version":"1.0",
             "collections":[{"type":"clapiCollection","version":"1.0","name":"Mcp",
               "folders":[],"requests":[{
                 "name":"Client MCP",
                 "kind":"MCP",
                 "method":"POST",
                 "url":"http://localhost/mcp",
                 "mcpTransport":"STREAMABLE_HTTP",
                 "mcpSamplingMode":"MANUAL",
                 "mcpSamplingForwardUrl":"http://localhost:8080/v1/chat/completions",
                 "mcpSamplingForwardToken":"llm-test-key",
                 "mcpSamplingMaxTokens":128,
                 "mcpAutoRespondElicitation":false,
                 "mcpRoots":[{"uri":"file:///tmp/clapi","name":"tmp"}]
               }]}],
             "environments":[]}
        """.trimIndent()
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importWorkspaceFromString(state, json)
        val req = state.collections.first().children.first { !it.isFolder }
        val mcp = req.mcpConfig!!
        assertEquals(io.github.crustack.core.model.McpSamplingMode.MANUAL, mcp.samplingMode)
        assertEquals("http://localhost:8080/v1/chat/completions", mcp.samplingForwardUrl)
        assertEquals("llm-test-key", mcp.samplingForwardToken)
        assertEquals(128, mcp.samplingMaxTokens)
        assertEquals(false, mcp.autoRespondElicitation)
        assertEquals("file:///tmp/clapi", mcp.roots.single().uri)
        assertEquals("tmp", mcp.roots.single().name)
        val exported = ImportExportRepository.exportWorkspaceToString(state)
        assertTrue(exported.contains("MANUAL"))
        assertTrue(exported.contains("file:///tmp/clapi"))
        assertTrue(exported.contains("mcpSamplingForwardUrl"))
        assertTrue(exported.contains("mcpSamplingForwardToken"))
        assertTrue(exported.contains("mcpSamplingMaxTokens"))
        assertTrue(exported.contains("mcpAutoRespondElicitation"))
    }
}
