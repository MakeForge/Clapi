// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedSampleServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.BottomTab
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.HeaderKind
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.ResponseTab
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interactive Compose Desktop UI E2E tests driving real HTTP requests through [MainScreen]
 * against an embedded `sample-server` instance.
 *
 * Covers FR-005 (CRUD, body types, JSON5, auth/OAuth2/inheritance, response viewer tabs,
 * history, console logs, cancellation, errors, and retries).
 */
class HttpRequestUiE2ETest {

    companion object {
        @JvmField
        @ClassRule
        val server = EmbeddedSampleServerRule()
    }

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    private fun setupStateWithBaseUrlEnv(): io.github.makeforge.clapi.ui.shared.state.AppState {
        return workspace.newAppState(openDefaultTab = true).apply {
            val env = EnvState("Local").apply {
                variables.add(MutableKeyValue("baseUrl", server.baseUrl))
            }
            environments.add(env)
            selectedEnvIndex = 0
        }
    }

    private fun clickSendAndWaitForResponse(tab: io.github.makeforge.clapi.ui.shared.state.RequestTabState) {
        val beforeEpoch = tab.response?.executedAtEpochMillis ?: -1L
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !tab.isLoading && (tab.response?.executedAtEpochMillis ?: -1L) != beforeEpoch
        }
        composeRule.waitForIdle()
    }

    // ── CRUD, ResponseViewer tabs, History & Console ─────────────────────────────────────────

    @Test
    fun http_crud_with_env_var_updates_response_viewer_history_and_console() {
        val state = workspace.newAppState(openDefaultTab = false).apply {
            val env = EnvState("Local").apply {
                variables.add(MutableKeyValue("baseUrl", server.baseUrl))
            }
            environments.add(env)
            selectedEnvIndex = 0
            historyExpanded = true
            bottomPanelExpanded = true
            selectedBottomTab = BottomTab.CONSOLE
            createNewCollection("CRUD Collection")
            addRequestToCollection(collections.first().id)
        }
        val tab = state.activeTab!!
        tab.url = "{{baseUrl}}/api/users"

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. GET /api/users via Send button
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("Alice"))
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
        composeRule.onAllNodesWithText("200", substring = true, useUnmergedTree = true)[0].assertIsDisplayed()

        // History should have recorded the GET request
        assertTrue(state.historyItems.isNotEmpty(), "Expected history item after Send")
        assertTrue(state.consoleLogs.isNotEmpty(), "Expected console log entries after Send")

        // Switch through ResponseViewer tabs (Headers, Cookies, Timing, Raw, Body)
        composeRule.runOnUiThread { tab.responseTab = ResponseTab.HEADERS }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { tab.responseTab = ResponseTab.TIMING }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { tab.responseTab = ResponseTab.RAW }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { tab.responseTab = ResponseTab.BODY }
        composeRule.waitForIdle()

        // 2. GET /api/cookies verifies cookies populated in ResponseDefinition & Cookies tab
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/cookies"
            tab.responseTab = ResponseTab.COOKIES
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(
            tab.response!!.cookies.any { it.key.equals("session", ignoreCase = true) || it.value.contains("session=abc123") },
            "Expected session cookie in response, got: ${tab.response!!.cookies}",
        )

        // 3. POST /api/users via UI method dropdown
        composeRule.onNodeWithTag("method-dropdown", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("POST", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(HttpMethodType.POST, tab.method)

        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/users"
            tab.bodyType = BodyType.JSON
            tab.bodyContent = """{"name":"Charlie","email":"charlie@clapi.dev"}"""
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertTrue(tab.response!!.statusCode in listOf(200, 201), "Unexpected POST status: ${tab.response?.statusCode}")
        assertTrue(tab.response!!.bodyText.contains("Charlie"))

        // 4. PUT & DELETE
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.PUT
            tab.url = "{{baseUrl}}/api/users/1"
            tab.bodyContent = """{"name":"Alice Updated"}"""
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)

        composeRule.runOnUiThread {
            tab.method = HttpMethodType.DELETE
            tab.url = "{{baseUrl}}/api/users/1"
            tab.bodyType = BodyType.NONE
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertTrue(tab.response!!.statusCode in listOf(200, 204))

        // 5. Closing the tab and reopening via recorded History item restores it in the workspace
        val firstHistory = state.historyItems.first()
        composeRule.runOnUiThread {
            state.closeTab(0)
            state.openHistoryItem(firstHistory)
        }
        composeRule.waitForIdle()
        assertEquals(firstHistory.requestId, state.activeTab?.id)
    }

    // ── Body formats (JSON5, Form-Data, URL-Encoded, XML, Raw) ───────────────────────────────

    @Test
    fun http_body_formats_transmit_accurately_from_ui() {
        val state = setupStateWithBaseUrlEnv().apply {
            settings.allowJson5InJsonBodies = true
        }
        val tab = state.activeTab!!
        tab.method = HttpMethodType.POST

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // JSON5 (unquoted key, comment, trailing comma) transmitted as strict JSON
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/json"
            tab.bodyType = BodyType.JSON
            tab.bodyContent = """
                {
                  // json5 comment
                  greeting: 'hello-json5',
                  count: 3,
                }
            """.trimIndent()
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("hello-json5"), "JSON5 body not echoed: ${tab.response?.bodyText}")
        assertFalse(tab.response!!.bodyText.contains("// json5 comment"), "JSON5 comment must be stripped on the wire")

        // URL-Encoded form
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/urlencoded"
            tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
            tab.urlencodedRows.clear()
            tab.urlencodedRows.add(io.github.makeforge.clapi.ui.shared.state.MutableFormDataRow(key = "username", value = "clapi_user"))
            tab.urlencodedRows.add(io.github.makeforge.clapi.ui.shared.state.MutableFormDataRow(key = "role", value = "admin"))
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("clapi_user"))

        // Multipart Form-Data
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/form-data"
            tab.bodyType = BodyType.FORM_DATA
            tab.formRows.clear()
            tab.formRows.add(
                io.github.makeforge.clapi.ui.shared.state.MutableFormDataRow(
                    key = "title",
                    value = "release-check",
                ),
            )
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("release-check"))

        // XML & Raw text
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/raw"
            tab.bodyType = BodyType.XML
            tab.bodyContent = "<ping>xml-ok</ping>"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("xml-ok"))

        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/raw"
            tab.bodyType = BodyType.RAW_TEXT
            tab.bodyContent = "plain-raw-payload"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("plain-raw-payload"))
    }

    // ── Auth: Basic, Bearer, API Key, Live OAuth2 Fetch Tokens, Collection/Folder Inheritance ─

    @Test
    fun http_auth_modes_valid_and_invalid_and_live_oauth2_fetch_tokens() {
        val state = setupStateWithBaseUrlEnv()
        val tab = state.activeTab!!

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Basic Auth: valid -> 200, invalid -> 401
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/auth/basic"
            tab.authType = AuthType.BASIC
            tab.authUsername = "admin"
            tab.authPassword = "password"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)

        composeRule.runOnUiThread { tab.authPassword = "wrong" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(401, tab.response?.statusCode)

        // Bearer Auth: valid -> 200, invalid -> 401
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/auth/bearer"
            tab.authType = AuthType.BEARER
            tab.authToken = "test-token"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)

        composeRule.runOnUiThread { tab.authToken = "bad-token" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(401, tab.response?.statusCode)

        // API Key Auth: valid -> 200, invalid -> 401
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/auth/apikey"
            tab.authType = AuthType.API_KEY
            tab.authApiKey = "X-API-Key"
            tab.authApiValue = "test-api-key"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)

        composeRule.runOnUiThread { tab.authApiValue = "wrong-key" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(401, tab.response?.statusCode)

        // Live OAuth2 Fetch Tokens via AuthEditor UI against sample-server /oauth/token
        composeRule.runOnUiThread {
            tab.selectedEditorTab = io.github.makeforge.clapi.ui.shared.state.RequestEditorTab.AUTH
            tab.authType = AuthType.OAUTH2
            tab.authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
            tab.authOAuth2AccessTokenUrl = "${server.baseUrl}/oauth/token"
            tab.authOAuth2ClientId = "clapi-ui-client"
            tab.authOAuth2ClientSecret = "clapi-ui-secret"
            tab.authOAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BODY
            tab.authOAuth2AccessToken = ""
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth-oauth2-fetch-tokens-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            tab.authOAuth2AccessToken.isNotBlank()
        }
        assertEquals("mcp-oauth-token", tab.authOAuth2AccessToken)

        // Verify {{_oauth2.accessToken}} resolves and sends on the wire
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/echo-headers"
            tab.headers.add(MutableKeyValue("X-OAuth-Check", "{{_oauth2.accessToken}}", kind = HeaderKind.USER))
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(
            tab.response!!.bodyText.contains("mcp-oauth-token"),
            "Expected fetched OAuth2 token echoed in headers, got: ${tab.response?.bodyText}",
        )
    }

    @Test
    fun collection_and_folder_inherited_auth_headers_and_vars_transmit_on_wire() {
        val state = workspace.newAppState(openDefaultTab = false)
        val folder = io.github.makeforge.clapi.ui.shared.state.CollectionNode(
            id = "folder-secured",
            name = "Secured Folder",
            isFolder = true,
            headerEntries = listOf(io.github.makeforge.clapi.core.model.KeyValueEntry("X-Folder-Header", "from-folder")),
        )
        val col = io.github.makeforge.clapi.ui.shared.state.CollectionNode(
            id = "col-inherited",
            name = "Inherited API",
            isFolder = true,
            kind = io.github.makeforge.clapi.core.model.RequestKind.COLLECTION,
            authType = AuthType.BEARER,
            authToken = "test-token",
            variableEntries = listOf(io.github.makeforge.clapi.core.model.KeyValueEntry("colHost", server.baseUrl)),
            headerEntries = listOf(io.github.makeforge.clapi.core.model.KeyValueEntry("X-Collection-Header", "from-col")),
            children = androidx.compose.runtime.mutableStateListOf(folder),
        )
        state.collections.add(col)

        state.addRequestToCollection(folder.id)
        val tab = state.activeTab!!
        tab.authType = AuthType.INHERIT
        tab.url = "{{colHost}}/api/auth/bearer"

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. Inherited Bearer auth passes /api/auth/bearer
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)

        // 2. Inherited collection & folder headers reach /api/echo-headers
        composeRule.runOnUiThread { tab.url = "{{colHost}}/api/echo-headers" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        val body = tab.response!!.bodyText
        assertTrue(body.contains("from-col"), "Missing inherited collection header: $body")
        assertTrue(body.contains("from-folder"), "Missing inherited folder header: $body")
    }

    // ── In-Flight Cancellation, Errors & Retry UI Flow ───────────────────────────────────────

    @Test
    fun in_flight_request_can_be_cancelled_via_stop_button_and_subsequent_send_succeeds() {
        val state = setupStateWithBaseUrlEnv()
        val tab = state.activeTab!!
        tab.url = "{{baseUrl}}/api/slow?ms=5000"

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { tab.isLoading }
        composeRule.onNodeWithTag("stop-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { !tab.isLoading }
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).assertIsDisplayed()

        // Subsequent fast request must succeed cleanly
        composeRule.runOnUiThread { tab.url = "{{baseUrl}}/api/users" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("Alice"))
    }

    @Test
    fun error_statuses_unreachable_host_and_retry_config_complete_cleanly() {
        val state = setupStateWithBaseUrlEnv()
        val tab = state.activeTab!!

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 404 Not Found
        composeRule.runOnUiThread { tab.url = "{{baseUrl}}/api/error/404" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(404, tab.response?.statusCode)

        // 500 Internal Server Error with retry enabled via Retry config dialog
        composeRule.onNodeWithTag("retry-menu-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("retry-config-dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("retry-enabled-checkbox", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("retry-attempts-input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("retry-attempts-input", useUnmergedTree = true).performTextInput("2")
        composeRule.onNodeWithTag("retry-delay-input", useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag("retry-delay-input", useUnmergedTree = true).performTextInput("50")
        composeRule.onNodeWithTag("retry-config-save", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(tab.retryEnabled)

        composeRule.runOnUiThread { tab.url = "{{baseUrl}}/api/error/500" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(500, tab.response?.statusCode)

        // Unreachable port surfaces error cleanly and restores Send button
        val unusedPort = ServerSocket(0).use { it.localPort }
        composeRule.runOnUiThread {
            tab.retryEnabled = false
            tab.url = "http://127.0.0.1:$unusedPort/unreachable"
            tab.response = null
            tab.lastError = null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !tab.isLoading && (tab.lastError != null || tab.response != null)
        }
        assertNotNull(tab.lastError, "Expected lastError for unreachable port")
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).assertIsDisplayed()
    }

    // ── Additional HTTP Methods (PATCH, OPTIONS, HEAD, TRACE, CONNECT) ───────────────────────

    @Test
    fun http_patch_options_head_and_trace_methods_execute_from_ui() {
        val state = setupStateWithBaseUrlEnv()
        val tab = state.activeTab!!

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // PATCH /api/users/1
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.PATCH
            tab.url = "{{baseUrl}}/api/users/1"
            tab.bodyType = BodyType.JSON
            tab.bodyContent = """{"name":"Alice Patched"}"""
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("PATCH"))
        assertTrue(tab.response!!.bodyText.contains("Alice Patched"))

        // OPTIONS /api/users
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.OPTIONS
            tab.url = "{{baseUrl}}/api/users"
            tab.bodyType = BodyType.NONE
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(
            tab.response!!.headers.any { it.key.equals("Allow", ignoreCase = true) && it.value.contains("PATCH") },
            "Expected Allow header from OPTIONS, got: ${tab.response!!.headers}",
        )

        // HEAD /api/users (headers present, body empty)
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.HEAD
            tab.url = "{{baseUrl}}/api/users"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(
            tab.response!!.headers.any { it.key.equals("X-Total-Count", ignoreCase = true) && it.value == "3" },
            "Expected X-Total-Count header from HEAD, got: ${tab.response!!.headers}",
        )
        assertTrue(tab.response!!.bodyText.isBlank(), "HEAD response body should be empty")

        // TRACE /api/trace (echoes custom header)
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.TRACE
            tab.url = "{{baseUrl}}/api/trace"
            tab.headers.add(MutableKeyValue("X-Trace-Probe", "trace-ok-123", kind = HeaderKind.USER))
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("TRACE"))
        assertTrue(tab.response!!.bodyText.contains("trace-ok-123"))
    }

    // ── Additional Body Formats (HTML, JS, BINARY, FILE Multipart, HTTP GraphQL) & JWT/Query API Key

    @Test
    fun http_html_js_binary_file_upload_graphql_body_and_jwt_apikey_query_auth_from_ui() {
        val state = setupStateWithBaseUrlEnv()
        val tab = state.activeTab!!

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. HTML body -> Content-Type text/html
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.POST
            tab.url = "{{baseUrl}}/api/raw"
            tab.bodyType = BodyType.HTML
            tab.bodyContent = "<html><body><h1>Clapi HTML</h1></body></html>"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("text/html"))
        assertTrue(tab.response!!.bodyText.contains("Clapi HTML"))

        // 2. JavaScript body -> Content-Type application/javascript
        composeRule.runOnUiThread {
            tab.bodyType = BodyType.JAVASCRIPT
            tab.bodyContent = "const clapiReady = true;"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("application/javascript"))
        assertTrue(tab.response!!.bodyText.contains("clapiReady"))

        // 3. Binary body (clapi-binary:<filename>\n<base64>) -> /api/upload
        val binaryPayload = byteArrayOf(0x00, 0x01, 0x02, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val binaryBase64 = java.util.Base64.getEncoder().encodeToString(binaryPayload)
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/upload"
            tab.bodyType = BodyType.BINARY
            tab.syncSystemHeaders()
            tab.bodyContent = "clapi-binary:firmware.bin\n$binaryBase64"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("\"mode\": \"raw\"") || tab.response!!.bodyText.contains("\"mode\":\"raw\""), tab.response!!.bodyText)
        assertTrue(tab.response!!.bodyText.contains("firmware.bin"), "Expected X-Clapi-Filename echoed: ${tab.response!!.bodyText}")

        // 4. Multipart FORM_DATA with FormEntryType.FILE attachment -> /api/upload
        val fileBytesBase64 = java.util.Base64.getEncoder().encodeToString("hello multipart file upload".encodeToByteArray())
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/upload"
            tab.bodyType = BodyType.FORM_DATA
            tab.formRows.clear()
            tab.formRows.add(
                io.github.makeforge.clapi.ui.shared.state.MutableFormDataRow(
                    key = "attachment",
                    type = io.github.makeforge.clapi.core.model.FormEntryType.FILE,
                    value = "notes.txt",
                    bytesBase64 = fileBytesBase64,
                ),
            )
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("multipart"), tab.response!!.bodyText)
        assertTrue(tab.response!!.bodyText.contains("notes.txt"), tab.response!!.bodyText)
        assertTrue(tab.response!!.bodyText.contains("hello multipart file upload"), tab.response!!.bodyText)

        // 5. BodyType.GRAPHQL inside standard HTTP tab -> /api/graphql
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/graphql"
            tab.bodyType = BodyType.GRAPHQL
            tab.bodyContent = "mutation UpdateAlias { updateAlias(id: \"7\") { id } }"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("mutation UpdateAlias"), tab.response!!.bodyText)

        // 6. AuthType.JWT valid (200) and invalid (401)
        composeRule.runOnUiThread {
            tab.method = HttpMethodType.GET
            tab.url = "{{baseUrl}}/api/auth/bearer"
            tab.bodyType = BodyType.NONE
            tab.authType = AuthType.JWT
            tab.authToken = "test-token"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)

        composeRule.runOnUiThread { tab.authToken = "invalid-jwt" }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(401, tab.response?.statusCode)

        // 7. AuthType.API_KEY placed in query string -> /api/echo-query
        composeRule.runOnUiThread {
            tab.url = "{{baseUrl}}/api/echo-query"
            tab.authType = AuthType.API_KEY
            tab.authApiKey = "api_key"
            tab.authApiValue = "query-secret-99"
            tab.authApiPlacement = "query"
        }
        composeRule.waitForIdle()
        clickSendAndWaitForResponse(tab)
        assertEquals(200, tab.response?.statusCode)
        assertTrue(tab.response!!.bodyText.contains("query-secret-99"), tab.response!!.bodyText)
        assertTrue(
            tab.response!!.bodyText.contains("\"apiKeyHeader\": \"\"") || tab.response!!.bodyText.contains("\"apiKeyHeader\":\"\""),
            "API key in query placement must not be sent as a header: ${tab.response!!.bodyText}",
        )
    }
}
