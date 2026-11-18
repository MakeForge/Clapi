package io.github.makeforge.clapi.mcp.server.prompts

import io.github.makeforge.clapi.core.model.McpContent
import io.github.makeforge.clapi.core.model.McpGetPromptResult
import io.github.makeforge.clapi.core.model.McpPrompt
import io.github.makeforge.clapi.core.model.McpPromptArgument
import io.github.makeforge.clapi.core.model.McpPromptMessage
import io.github.makeforge.clapi.mcp.server.McpPromptProvider
import io.github.makeforge.clapi.mcp.server.backend.ExecutionResultDto
import io.github.makeforge.clapi.mcp.server.backend.McpWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.SanitizedRequestDto
import kotlinx.serialization.json.Json

object McpPromptRegistry : McpPromptProvider {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override val prompts: List<McpPrompt> = listOf(
        McpPrompt(
            name = "debug_api_failure",
            title = "Debug API Failure",
            description = "Analyzes a request's sanitized configuration, latest response status/headers/body preview, failed test assertions, and script console logs to recommend fixes.",
            arguments = listOf(
                McpPromptArgument(
                    name = "requestId",
                    description = "Request UUID or slash path (e.g., 'Collection/Folder/RequestName').",
                    required = true,
                )
            ),
        ),
        McpPrompt(
            name = "generate_api_tests",
            title = "Generate Post-Request API Tests",
            description = "Inspects a request and its latest execution response to generate post-request JavaScript assertions using clapi.test and clapi.expect.",
            arguments = listOf(
                McpPromptArgument(
                    name = "requestId",
                    description = "Request UUID or slash path.",
                    required = true,
                )
            ),
        ),
        McpPrompt(
            name = "curl_to_clapi_request",
            title = "Convert cURL to Clapi Request",
            description = "Converts a raw cURL command into a structured Clapi request inside a target collection and optional folder, extracting sensitive headers/tokens into secret variables.",
            arguments = listOf(
                McpPromptArgument(
                    name = "curlCommand",
                    description = "Raw cURL command string.",
                    required = true,
                ),
                McpPromptArgument(
                    name = "targetCollection",
                    description = "Target collection UUID or name.",
                    required = true,
                ),
                McpPromptArgument(
                    name = "targetFolder",
                    description = "Optional target folder path within the collection.",
                    required = false,
                ),
            ),
        ),
    )

    override suspend fun getPrompt(
        name: String,
        arguments: Map<String, String>,
        backend: McpWorkspaceBackend,
    ): McpGetPromptResult {
        return when (name) {
            "debug_api_failure" -> {
                val requestId = arguments["requestId"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Missing required prompt argument 'requestId'.")
                val req = backend.getRequest(requestId)
                val latest = backend.getLatestResponse(req.id)
                val logs = backend.getConsoleLogs(limit = 30)
                val reqJson = json.encodeToString(SanitizedRequestDto.serializer(), req)
                val latestJson = latest?.let { json.encodeToString(ExecutionResultDto.serializer(), it) }
                    ?: "No cached execution snapshot available. Run `clapi_execute_request` first if live response data is needed."
                val logsText = if (logs.isEmpty()) "No console logs." else logs.joinToString("\n") { "[${it.level}] ${it.message}" }

                McpGetPromptResult(
                    description = "Debug API failure for '${req.slashPath}'",
                    messages = listOf(
                        McpPromptMessage(
                            role = "user",
                            content = McpContent(
                                type = "text",
                                text = """
                                    Please diagnose the API request failure for **${req.slashPath}** (`id=${req.id}`).

                                    ### 1. Sanitized Request Configuration
                                    ```json
                                    $reqJson
                                    ```

                                    ### 2. Latest Execution Snapshot
                                    ```json
                                    $latestJson
                                    ```

                                    ### 3. Recent Script & Execution Console Logs
                                    ```text
                                    $logsText
                                    ```

                                    Identify the root cause (e.g., missing/unresolved variables, wrong HTTP method/headers/body schema, auth misconfiguration, or script assertion bug) and propose an exact fix using `clapi_upsert_request` or `clapi_set_variable`.
                                """.trimIndent()
                            )
                        )
                    )
                )
            }
            "generate_api_tests" -> {
                val requestId = arguments["requestId"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Missing required prompt argument 'requestId'.")
                val req = backend.getRequest(requestId)
                val latest = backend.getLatestResponse(req.id)
                val reqJson = json.encodeToString(SanitizedRequestDto.serializer(), req)
                val latestJson = latest?.let { json.encodeToString(ExecutionResultDto.serializer(), it) }
                    ?: "No cached response yet — execute the request with `clapi_execute_request` to inspect live response fields."

                McpGetPromptResult(
                    description = "Generate post-request test assertions for '${req.slashPath}'",
                    messages = listOf(
                        McpPromptMessage(
                            role = "user",
                            content = McpContent(
                                type = "text",
                                text = """
                                    Write comprehensive post-request JavaScript test assertions for **${req.slashPath}** (`id=${req.id}`) and save them to the request's `testScript` using `clapi_upsert_request`.

                                    ### Clapi Scripting API Reference
                                    - `clapi.test("Assertion name", function() { ... });`
                                    - `clapi.expect(clapi.response.code).to.equal(200);`
                                    - `var body = clapi.response.json();`
                                    - `clapi.expect(body).to.be.an("object");`
                                    - `clapi.expect(clapi.response.responseTime).to.be.below(2000);`

                                    ### Request Definition
                                    ```json
                                    $reqJson
                                    ```

                                    ### Latest Response Snapshot
                                    ```json
                                    $latestJson
                                    ```
                                """.trimIndent()
                            )
                        )
                    )
                )
            }
            "curl_to_clapi_request" -> {
                val curl = arguments["curlCommand"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Missing required prompt argument 'curlCommand'.")
                val targetCol = arguments["targetCollection"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Missing required prompt argument 'targetCollection'.")
                val targetFolder = arguments["targetFolder"]?.trim()?.takeIf { it.isNotEmpty() }
                val parentPath = if (targetFolder != null) "$targetCol/$targetFolder" else targetCol

                McpGetPromptResult(
                    description = "Convert cURL command to Clapi request in '$parentPath'",
                    messages = listOf(
                        McpPromptMessage(
                            role = "user",
                            content = McpContent(
                                type = "text",
                                text = """
                                    Convert the following cURL command into a structured Clapi request inside **$parentPath**:

                                    ```bash
                                    $curl
                                    ```

                                    ### Instructions
                                    1. Ensure the target collection/folder `$parentPath` exists (using `clapi_list_tree`, `clapi_manage_collection`, or `clapi_manage_folder` if needed).
                                    2. If the cURL command contains hardcoded secrets, API keys, or bearer tokens, store them as write-only secret variables (`kind: "secret"`) using `clapi_set_variable` and reference them via `{{variableName}}`.
                                    3. Call `clapi_upsert_request` with `parentId: "$parentPath"` and the parsed HTTP method, URL, query parameters, headers, auth configuration, and body.
                                """.trimIndent()
                            )
                        )
                    )
                )
            }
            else -> throw IllegalArgumentException("Unknown MCP prompt: '$name'")
        }
    }
}
