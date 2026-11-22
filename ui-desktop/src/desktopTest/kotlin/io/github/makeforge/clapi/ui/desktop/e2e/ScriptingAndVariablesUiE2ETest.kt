// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.desktop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.ui.desktop.e2e.support.EmbeddedSampleServerRule
import io.github.makeforge.clapi.ui.desktop.e2e.support.IsolatedWorkspaceRule
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.CrashReportDialog
import io.github.makeforge.clapi.ui.shared.components.executor.SecretLogRedactor
import io.github.makeforge.clapi.ui.shared.components.executor.buildCurlCommand
import io.github.makeforge.clapi.ui.shared.state.BottomTab
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.HeaderKind
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interactive Compose Desktop UI E2E tests for pre/post-request scripting, test assertion results,
 * console logs, dynamic variables, variable boundary conditions, and secret redaction across UI surfaces.
 *
 * Covers FR-006 (US2), FR-014 & FR-015 (US5).
 */
class ScriptingAndVariablesUiE2ETest {

    companion object {
        @JvmField
        @ClassRule
        val server = EmbeddedSampleServerRule()
    }

    @get:Rule
    val workspace = IsolatedWorkspaceRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pre_and_post_scripts_mutate_wire_request_update_env_and_render_test_results_and_console() {
        val env = EnvState("ScriptEnv").apply {
            variables.add(MutableKeyValue("baseUrl", server.baseUrl))
        }
        val state = workspace.newAppState(openDefaultTab = true).apply {
            environments.add(env)
            selectedEnvIndex = 0
            bottomPanelExpanded = true
            selectedBottomTab = BottomTab.TEST_RESULTS
        }
        val tab = state.activeTab!!
        tab.url = "{{baseUrl}}/api/echo-headers"
        tab.preRequestScript = """
            clapi.request.setHeader("X-Script-Injected", "from-pre-script");
            clapi.environment.set("computedByScript", "env-val-99");
            clapi.console.log("pre-script-marker");
        """.trimIndent()
        tab.testScript = """
            clapi.console.log("post-script-marker");
            clapi.test("status is 200", function() {
                clapi.expect(clapi.response.code).to.equal(200);
            });
            clapi.test("intentional failure", function() {
                clapi.expect(clapi.response.code).to.equal(201);
            });
        """.trimIndent()

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !tab.isLoading && tab.response != null && tab.testResults.size == 2
        }
        composeRule.waitForIdle()

        // Pre-request mutated header reached server
        assertEquals(200, tab.response?.statusCode)
        assertTrue(
            tab.response!!.bodyText.contains("from-pre-script"),
            "Expected X-Script-Injected header on wire, got: ${tab.response?.bodyText}",
        )

        // Pre-request mutated environment variable updated in UI state
        assertEquals("env-val-99", env.variables.firstOrNull { it.key == "computedByScript" }?.value)

        // Post-request test results rendered in BottomPanel Test Results tab
        assertEquals(1, tab.testResults.count { it.passed })
        assertEquals(1, tab.testResults.count { !it.passed })
        composeRule.onNodeWithTag("test-results", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("status is 200", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("intentional failure", substring = true, useUnmergedTree = true).assertIsDisplayed()

        // Switch to Console tab and verify script console logs are rendered
        composeRule.onNodeWithText("Console", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("console-output", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(state.consoleLogs.any { it.message.contains("pre-script-marker") })
        assertTrue(state.consoleLogs.any { it.message.contains("post-script-marker") })
    }

    @Test
    fun failing_script_reports_error_and_restores_send_button() {
        val env = EnvState("ScriptErrEnv").apply {
            variables.add(MutableKeyValue("baseUrl", server.baseUrl))
        }
        val state = workspace.newAppState(openDefaultTab = true).apply {
            environments.add(env)
            selectedEnvIndex = 0
            bottomPanelExpanded = true
            selectedBottomTab = BottomTab.CONSOLE
        }
        val tab = state.activeTab!!
        tab.url = "{{baseUrl}}/api/users"
        tab.preRequestScript = """throw new Error("boom-from-pre-script");"""

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !tab.isLoading && (
                state.consoleLogs.any { it.message.contains("boom-from-pre-script") } ||
                    tab.lastError?.contains("boom-from-pre-script") == true ||
                    tab.response != null
                )
        }
        composeRule.waitForIdle()

        assertFalse(tab.isLoading)
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            state.consoleLogs.any { it.message.contains("boom-from-pre-script") } ||
                tab.lastError?.contains("boom-from-pre-script") == true,
            "Expected script error surfaced in console or tab lastError; logs=${state.consoleLogs.map { it.message }}",
        )
    }

    // ── US5 (FR-014 & FR-015): Secret Redaction, Typed Dynamic Vars & Circular Var Boundaries ─

    @Test
    fun secrets_from_active_inherited_and_global_scopes_never_leak_into_logs_history_or_crash_reports() {
        val parentSecret = "PARENT_SUPER_SECRET_98765"
        val childSecret = "CHILD_SUPER_SECRET_54321"
        val globalSecret = "GLOBAL_SUPER_SECRET_11223"
        val externalSecret = "EXTERNAL_VAULT_SECRET_99999"

        val parentEnv = EnvState("ParentSec").apply {
            variables.add(MutableKeyValue("baseUrl", server.baseUrl))
            variables.add(MutableKeyValue("parentKey", parentSecret, secret = true))
        }
        val childEnv = EnvState("ChildSec", extends = "ParentSec").apply {
            variables.add(MutableKeyValue("childKey", childSecret, secret = true))
        }
        val state = workspace.newAppState(openDefaultTab = true).apply {
            environments.addAll(listOf(parentEnv, childEnv))
            selectedEnvIndex = 1
            globalVariables.add(MutableKeyValue("globalKey", globalSecret, secret = true))
        }
        val tab = state.activeTab!!
        // Use inherited parent secret + global secret on live wire (selected-env `secret=true` without
        // an external vault/process-env provider intentionally requires external secret resolution)
        tab.url = "{{baseUrl}}/api/echo-query?p={{parentKey}}&g={{globalKey}}"
        tab.headers.add(MutableKeyValue("X-Secret-Header", "{{globalKey}}", kind = HeaderKind.USER))

        // Verify SecretLogRedactor collects all 4 secret scopes
        val collected = SecretLogRedactor.collectSecretValues(
            state = state,
            tab = tab,
            resolvedSecrets = mapOf("vaultToken" to externalSecret),
        )
        assertTrue(parentSecret in collected, "Parent inherited env secret must be collected")
        assertTrue(childSecret in collected, "Active env secret must be collected")
        assertTrue(globalSecret in collected, "Global secret must be collected")
        assertTrue(externalSecret in collected, "External resolved secret must be collected")

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { !tab.isLoading && tab.response != null }
        composeRule.waitForIdle()

        val allSecrets = listOf(parentSecret, childSecret, globalSecret, externalSecret)

        // 1. Console logs must NOT contain plaintext secrets
        val consoleDump = state.consoleLogs.joinToString("\n") { it.message }
        allSecrets.forEach { secret ->
            assertFalse(consoleDump.contains(secret), "Plaintext secret leaked into Console logs: $consoleDump")
        }

        // 2. Diagnostics logs (networkEventLogs + consoleLogs) must NOT contain plaintext secrets
        val diagDump = (state.diagnostics.consoleLogs + state.diagnostics.networkEventLogs)
            .joinToString("\n") { it.message }
        allSecrets.forEach { secret ->
            assertFalse(diagDump.contains(secret), "Plaintext secret leaked into Diagnostics logs: $diagDump")
        }

        // 3. History items must NOT persist plaintext secrets (URL keeps template or redacted)
        val historyDump = state.historyItems.joinToString("\n") { "${it.name} ${it.url}" }
        allSecrets.forEach { secret ->
            assertFalse(historyDump.contains(secret), "Plaintext secret leaked into History: $historyDump")
        }

        // 4. Raw cURL command keeps {{var}} placeholders without leaking plaintext secrets
        val rawCurl = buildCurlCommand(tab, variableLayers = emptyList(), allowJson5 = true, state = state)
        allSecrets.forEach { secret ->
            assertFalse(rawCurl.contains(secret), "Plaintext secret leaked into raw cURL: $rawCurl")
        }
    }

    @Test
    fun crash_report_dialog_redacts_secrets_in_copied_details_and_github_bug_url() {
        val leakedToken = "TOP_SECRET_CRASH_TOKEN_4242"
        val ex = IllegalStateException("Request failed with token=$leakedToken")
        var copied: String? = null
        var bugUrl: String? = null

        composeRule.setContent {
            CrashReportDialog(
                throwable = ex,
                appVersion = "1.0.0",
                operatingSystem = "Linux",
                onDismiss = {},
                onCopyDetails = { copied = it },
                onReportBug = { bugUrl = it },
                secretValues = setOf(leakedToken),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crash-dialog-copy-button").performClick()
        composeRule.onNodeWithTag("crash-dialog-report-button").performClick()
        composeRule.waitForIdle()

        assertNotNull(copied)
        assertNotNull(bugUrl)
        assertFalse(copied!!.contains(leakedToken), "Copied crash details leaked secret: $copied")
        assertFalse(bugUrl!!.contains(leakedToken), "GitHub bug report URL leaked secret: $bugUrl")
        assertTrue(copied!!.contains(SecretLogRedactor.MASK))
    }

    @Test
    fun typed_dynamic_variables_json5_toggle_and_circular_variables_behave_deterministically() {
        val env = EnvState("BoundaryEnv").apply {
            variables.add(MutableKeyValue("baseUrl", server.baseUrl))
            variables.add(MutableKeyValue("loopA", "{{loopB}}"))
            variables.add(MutableKeyValue("loopB", "{{loopA}}"))
            variables.add(
                MutableKeyValue(
                    key = "dynNum",
                    value = "",
                    isDynamic = true,
                    variableType = VariableType.NUMBER,
                    dynamicScript = "return 42;",
                ),
            )
            variables.add(
                MutableKeyValue(
                    key = "dynBool",
                    value = "",
                    isDynamic = true,
                    variableType = VariableType.BOOLEAN,
                    dynamicScript = "return true;",
                ),
            )
        }
        val state = workspace.newAppState(openDefaultTab = true).apply {
            environments.add(env)
            selectedEnvIndex = 0
            settings.allowJson5InJsonBodies = false
        }
        val tab = state.activeTab!!
        tab.method = HttpMethodType.POST
        tab.url = "{{baseUrl}}/api/json?c={{loopA}}&missing={{neverDefined}}&broken={{unclosed"
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"count": {{dynNum}}, "active": {{dynBool}}}"""

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // 1. Typed dynamic variables send native JSON types even with JSON5 disabled, and circular/unresolved vars don't hang
        composeRule.onNodeWithTag("send-button", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) { !tab.isLoading && tab.response != null }
        assertEquals(200, tab.response?.statusCode)
        val echoed = tab.response!!.bodyText
        assertTrue(echoed.contains("42"), "Expected typed number 42 in echoed body: $echoed")
        assertTrue(echoed.contains("true"), "Expected typed boolean true in echoed body: $echoed")
    }
}
