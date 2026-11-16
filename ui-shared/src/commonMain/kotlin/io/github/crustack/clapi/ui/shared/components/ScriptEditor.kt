package io.github.crustack.clapi.ui.shared.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.crustack.clapi.ui.shared.theme.ClapiColors

/**
 * A multi-line script editor used for Pre-request and Post-request script tabs.
 * Now powered by the shared [CodeEditor] with JavaScript syntax highlighting,
 * search, formatting, word wrap, and copy-to-clipboard.
 *
 * @param script         current script content
 * @param onScriptChanged callback for changes
 * @param title          human-readable label (e.g. "Pre-request Script")
 * @param showCursorPosition whether to show the shared editor position indicator
 */
@kotlinx.serialization.ExperimentalSerializationApi
@Composable
fun ScriptEditor(
    script: String,
    onScriptChanged: (String) -> Unit,
    title: String,
    showCursorPosition: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = ClapiColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        CodeEditor(
            text = script,
            onTextChange = onScriptChanged,
            language = SyntaxLanguage.JAVASCRIPT,
            modifier = Modifier.fillMaxSize(),
            showToolbar = true,
            enableFolding = false,
            enableSearch = true,
            enableFormat = true,
            enableWordWrap = true,
            enableCopy = true,
            enableDownload = false,
            showCursorPosition = showCursorPosition,
            placeholder = if (title.contains("Pre-request", ignoreCase = true))
                PRE_REQUEST_HINT else TEST_HINT,
            testTagPrefix = "script-editor",
        )
    }
}

// ── Script API reference hints ──────────────────────────────────

private val PRE_REQUEST_HINT = """
// Pre-request script — runs BEFORE the HTTP request is sent.
// Default namespace prefix: "clapi" (change in Settings › Scripts).
//
// Variable scopes:
//   clapi.environment.set("key", "value")     // environment variable
//   clapi.environment.get("key")
//   clapi.globals.set("key", "value")          // global variable
//   clapi.collectionVariables.set("key", "v")  // collection variable
//
// Mutate the outgoing request:
//   clapi.request.headers.add("X-Trace", "id")
//   clapi.request.headers.upsert("X-Key", clapi.environment.get("key"))
//   request.setQueryParam("debug", "true")      // low-level aliases also work
//   request.setMethod("POST")
//   request.setUrl("https://other-host.example.com")
//
// Logging:
//   console.log("msg", value)
//   clapi.console.log("msg")
//
// Example:
//   clapi.environment.set("ts", Date.now().toString())
//   clapi.request.headers.add("X-Timestamp", clapi.environment.get("ts"))
//   console.log("Sending", clapi.request.method, clapi.request.url)
""".trim()

private val TEST_HINT = """
// Post-request script — runs AFTER the response is received.
// Default namespace prefix: "clapi" (change in Settings › Scripts).
//
// Test blocks:
//   clapi.test("name", () => {
//     clapi.expect(clapi.response.code).to.equal(200)
//   })
//
// Response accessors:
//   clapi.response.code          // HTTP status int
//   clapi.response.responseTime  // ms
//   clapi.response.size          // bytes
//   clapi.response.text()        // body as string
//   clapi.response.json()        // parsed JSON (use .field for paths)
//   clapi.response.headers.get("Content-Type")
//
// Assertions:
//   .to.equal(v)  .to.notEqual(v)  .to.include("s")  .to.match("regex")
//   .to.be.above(n)  .to.be.below(n)  .to.be.oneOf([a,b])
//   .to.exist  .to.be.ok  .to.be.null  .to.be.empty
//
// Chain to next request:
//   clapi.environment.set("token", clapi.response.json().token)
//
// Example:
//   clapi.test("Status is 200", () => {
//     clapi.expect(clapi.response.code).to.equal(200)
//   })
//   clapi.test("Has token", () => {
//     clapi.expect(clapi.response.json().token).to.exist
//     clapi.environment.set("token", clapi.response.json().token)
//   })
//   clapi.test("Fast response", () => {
//     clapi.expect(clapi.response.responseTime).to.be.below(500)
//   })
""".trim()
