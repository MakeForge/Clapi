package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.core.model.AuthConfig
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.json.Json5
import io.github.crustack.clapi.core.model.FormDataEntry
import io.github.crustack.clapi.core.model.FormEntryType
import io.github.crustack.clapi.core.model.GraphQlBody
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.ResponseMetrics
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.LlmTextAssembler
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.NetworkLogger
import io.github.crustack.clapi.core.network.NoOpNetworkLogger
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.encodeGraphQlEnvelope
import io.github.crustack.clapi.core.network.encodeBase64
import io.github.crustack.clapi.core.network.getEnvironmentVariable
import io.github.crustack.clapi.core.scripting.ClapiScriptEngine
import io.github.crustack.clapi.core.scripting.ScriptContext
import io.github.crustack.clapi.core.scripting.SendRequestResult
import io.github.crustack.clapi.core.scripting.SendRequestSpec
import io.github.crustack.clapi.core.network.vault.SecretDeclaration
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.persistence.TabsRepository
import io.github.crustack.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.clapi.ui.shared.platform.PlatformStorage
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.TestResultEntry
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Issues an HTTP request for [tab] and streams the result back into [tab]'s
 * state properties.  All heavy work runs off the main thread.
 */
private const val BINARY_ATTACHMENT_PREFIX = "clapi-binary:"
private const val MAX_SCRIPT_CHAIN_DEPTH = 16

/**
 * Executes a sub-HTTP request from a [clapi.sendRequest()] script call.
 * Uses [client] (built from the current AppSettings) to make the real call.
 */
private suspend fun executeSubRequest(client: ApiClient, spec: SendRequestSpec): SendRequestResult {
    val requestDef = RequestDefinition(
        id = "sr-${spec.url.hashCode()}",
        name = "sendRequest",
        method = HttpMethodType.entries.firstOrNull { it.name == spec.method } ?: HttpMethodType.GET,
        url = spec.url,
        headers = spec.headers.map { (k, v) -> KeyValueEntry(k, v) },
        body = spec.body?.let { RequestBody(BodyType.JSON, content = it) } ?: RequestBody(),
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
    var result = SendRequestResult(statusCode = 0, statusText = "No response received")
    client.execute(requestDef).collect { event ->
        when (event) {
            is NetworkEvent.Success -> result = SendRequestResult(
                statusCode = event.response.statusCode,
                statusText = event.response.statusText,
                body = event.response.bodyText,
                headers = event.response.headers.associate { it.key to it.value },
                elapsedMs = event.response.metrics.responseTimeMs,
            )
            is NetworkEvent.Failure -> result = SendRequestResult(
                statusCode = 0,
                statusText = "Error: ${event.error.message}",
            )
            else -> {}
        }
    }
    return result
}

fun sendRequest(scope: CoroutineScope, state: AppState, tab: RequestTabState) {
    sendRequestInternal(scope, state, tab, chainDepth = 0)
}

private fun sendRequestInternal(scope: CoroutineScope, state: AppState, tab: RequestTabState, chainDepth: Int) {
    if (tab.kind == RequestKind.GRPC) {
        sendGrpcRequestInternal(scope, state, tab, chainDepth)
        return
    }

    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
        return
    }

    // H-1: Cancel any existing in-flight request before starting a new one.
    // This prevents concurrent requests racing to update the same tab state.
    tab.currentJob?.cancel()

    val job = scope.launch {
        // Create a fresh script engine per request, wired to the current settings
        // so clapi.sendRequest() sub-calls inherit proxy/timeout configuration.
        val subReqClient = NetworkClientFactory.build(state.settings, NoOpNetworkLogger, RetryPolicy(maxAttempts = 1))
        val scriptEngine = ClapiScriptEngine(
            sendRequestExecutor = { spec -> executeSubRequest(subReqClient, spec) },
        )
        tab.isLoading = true
        tab.response  = null
        tab.lastError = null
        tab.streamChunks.clear()
        tab.liveStreamText = ""
        state.testResults.clear()
        // Strip embedded query string from the URL: tab.params is the single source
        // of truth for query parameters (syncUrlFromParams embeds them for display,
        // but passing both to KtorApiClient would cause each param to be appended
        // twice — once from the parsed URL and once from effectiveQueryParams).
        // Fragment is client-only and must not be glued onto a query value.
        val urlParts = splitRequestUrl(tab.url.trim())
        var effectiveUrl = urlParts.base
        var effectiveMethod = tab.method
        val effectiveHeaders = enabledHeadersForSend(tab).toMutableList()
        // Query parameters are an ordered multi-map: repeated keys such as
        // `tag=kotlin&tag=ktor` are distinct values and must reach the wire.
        // Do not use associate() here because it silently keeps only the last
        // row for each key.
        val effectiveQueryParams = enabledQueryParamsForSend(tab).toMutableList()
        var effectiveBodyContent = tab.bodyContent
        val requestScopedScriptVars = mutableMapOf<String, String>()

        // ── Pre-request script chain (Root-to-Leaf) ───────────────────────
        // T024: Ancestor folder pre-request scripts execute before request pre-request script.
        // T025: // TODO: Cross-Script Variable Isolation: Currently, folder pre-request and request scripts share and mutate the same mutable variable context (environment, global, and request-scoped script variables). In future revisions with sandbox isolation, consider adding lexical scope isolation or copy-on-write variable scoping so parent folder scripts cannot unintentionally clobber variables meant to be private to child requests or sibling folders.
        // T025: // TODO: Script Error Boundary Handling: If a pre-request script in an ancestor folder fails or throws an unhandled exception, currently the error is logged and subsequent scripts continue unless executionSkipRequest was called. Consider adding configurable error boundary strategies (e.g., FAIL_FAST to abort the entire dispatch immediately on script error, vs BEST_EFFORT to continue).
        val ancestorFolders = state.getAncestorFolders(tab)
        val preScriptChain = mutableListOf<Pair<String, String>>()
        for (ancestor in ancestorFolders) {
            val s = ancestor.preRequestScript
            if (!s.isNullOrBlank()) {
                preScriptChain.add("Folder '${ancestor.name}'" to s)
            }
        }
        if (tab.preRequestScript.isNotBlank()) {
            preScriptChain.add("Request '${tab.name}'" to tab.preRequestScript)
        }

        if (preScriptChain.isNotEmpty()) {
            // M-8: Clean up variables injected by the previous run of this script
            // so stale keys don't accumulate across re-sends.
            if (tab.scriptInjectedVarKeys.isNotEmpty()) {
                val env = state.selectedEnvironment
                env?.variables?.removeAll { it.key in tab.scriptInjectedVarKeys && it.kind == io.github.crustack.clapi.ui.shared.state.HeaderKind.USER }
                tab.scriptInjectedVarKeys.clear()
            }

            for ((source, scriptContent) in preScriptChain) {
                val layers = state.activeVariableLayers(tab)
                val flatVars = buildMap<String, String> { layers.asReversed().forEach { putAll(it) } }
                val preCtx = ScriptContext(
                    url       = effectiveUrl,
                    method    = effectiveMethod.name,
                    variables = buildMap {
                        putAll(flatVars)
                        putAll(requestScopedScriptVars)
                    },
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariables.toMap(),
                    requestHeaders = effectiveHeaders.associate { it.key to it.value },
                    // The current script API exposes a map/get-by-name view. Keep its
                    // historical last-value behavior without collapsing the wire list.
                    requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                    requestBody = effectiveBodyContent,
                )

                val preResult = scriptEngine.executePreRequestScript(scriptContent, preCtx, state.settings.scriptPrefix)
                preResult.logs.forEach { state.log(it) }
                if (preResult.error != null) {
                    state.log("⚠ Pre-request script error ($source): ${preResult.error}", LogLevel.ERROR)
                }
                // Merge new variables from the script into the active environment
                if (preResult.newVariables.isNotEmpty()) {
                    // M-8: Track which keys were added by this script run
                    tab.scriptInjectedVarKeys.addAll(preResult.newVariables.keys)
                    state.mergeScriptVariables(preResult.newVariables)
                }
                if (preResult.newRequestVariables.isNotEmpty()) {
                    requestScopedScriptVars.putAll(preResult.newRequestVariables)
                }
                if (preResult.newGlobalVariables.isNotEmpty()) {
                    state.mergeGlobalScriptVariables(preResult.newGlobalVariables)
                }
                if (preResult.newCollectionVariables.isNotEmpty()) {
                    state.mergeCollectionScriptVariables(preResult.newCollectionVariables)
                }
                if (preResult.executionSkipRequest) {
                    state.log("↷ Request skipped by script ($source)", LogLevel.INFO)
                    tab.isLoading = false
                    tab.currentJob = null
                    return@launch
                }
                if (preResult.requestMutations.url != null) {
                    val scriptParts = splitRequestUrl(preResult.requestMutations.url!!)
                    effectiveUrl = scriptParts.base
                    effectiveQueryParams.clear()
                    effectiveQueryParams.addAll(
                        parseQueryPairs(scriptParts.query).map { KeyValueEntry(it.first, it.second) },
                    )
                }
                if (preResult.requestMutations.method != null) {
                    effectiveMethod = HttpMethodType.entries.firstOrNull {
                        it.name.equals(preResult.requestMutations.method, ignoreCase = true)
                    } ?: effectiveMethod
                }
                if (preResult.requestMutations.body != null) {
                    effectiveBodyContent = preResult.requestMutations.body ?: effectiveBodyContent
                }
                if (preResult.requestMutations.headers.isNotEmpty()) {
                    preResult.requestMutations.headers.forEach { (key, value) ->
                        val firstIndex = effectiveHeaders.indexOfFirst { it.key.equals(key, ignoreCase = true) }
                        effectiveHeaders.removeAll { it.key.equals(key, ignoreCase = true) }
                        val replacement = KeyValueEntry(key, value)
                        if (firstIndex >= 0) {
                            effectiveHeaders.add(firstIndex, replacement)
                        } else {
                            effectiveHeaders.add(replacement)
                        }
                    }
                }
                if (preResult.requestMutations.queryParams.isNotEmpty()) {
                    preResult.requestMutations.queryParams.forEach { (key, value) ->
                        // setQueryParam is an upsert-by-name operation. If the original
                        // request contained repeated values for this key, replace the
                        // group with the explicitly scripted value.
                        val firstIndex = effectiveQueryParams.indexOfFirst { it.key == key }
                        effectiveQueryParams.removeAll { it.key == key }
                        val replacement = KeyValueEntry(key, value)
                        if (firstIndex >= 0) {
                            effectiveQueryParams.add(firstIndex, replacement)
                        } else {
                            effectiveQueryParams.add(replacement)
                        }
                    }
                }
            }
        }

        val textsToScan = mutableListOf<String>()
        textsToScan.add(tab.url)
        textsToScan.add(effectiveUrl)
        textsToScan.add(urlParts.query)
        textsToScan.add(effectiveBodyContent)
        effectiveHeaders.forEach {
            textsToScan.add(it.key)
            textsToScan.add(it.value)
        }
        effectiveQueryParams.forEach {
            textsToScan.add(it.key)
            textsToScan.add(it.value)
        }
        textsToScan.add(tab.authUsername)
        textsToScan.add(tab.authPassword)
        textsToScan.add(tab.authToken)
        textsToScan.add(tab.authApiKey)
        textsToScan.add(tab.authApiValue)
        tab.formRows.forEach {
            textsToScan.add(it.key)
            textsToScan.add(it.value)
        }
        tab.urlencodedRows.forEach {
            textsToScan.add(it.key)
            textsToScan.add(it.value)
        }

        val resolvedSecrets = resolveExternalSecretsForTab(state, tab, textsToScan) ?: return@launch
        val variableLayersWithSecrets = if (resolvedSecrets.isNotEmpty()) {
            listOf(resolvedSecrets) + state.activeVariableLayers(tab)
        } else {
            state.activeVariableLayers(tab)
        }

        val resolvedBaseForLog = resolveUrlForLog(
            url = effectiveUrl,
            variableLayers = variableLayersWithSecrets,
            requestScopedVars = requestScopedScriptVars,
        )
        val effectiveUrlForLog = if (effectiveQueryParams.isNotEmpty()) {
            val qs = encodeOrderedQuery(effectiveQueryParams.map { it.key to it.value }, preserveTemplates = true)
            "$resolvedBaseForLog?$qs"
        } else {
            resolvedBaseForLog
        }
        state.logNetworkEvent("→ $effectiveMethod $effectiveUrlForLog")

        try {
            val request = prepareTabRequest(
                tab = tab,
                method = effectiveMethod,
                url = effectiveUrl,
                queryParams = effectiveQueryParams,
                headers = effectiveHeaders,
                bodyContent = effectiveBodyContent,
                state = state,
            ).toRequestDefinition()

            val logger = object : NetworkLogger {
                override fun debug(message: String) { state.log(message) }
                override fun info(message: String)  { state.log(message) }
                override fun error(message: String, throwable: Throwable?) {
                    state.log(message, LogLevel.ERROR)
                }
            }

            val retryPolicy = if (tab.retryEnabled) {
                RetryPolicy(
                    maxAttempts = tab.retryCount.coerceAtLeast(1),
                    baseDelayMs = tab.retryDelayMs.coerceAtLeast(0L),
                    maxDelayMs  = (tab.retryDelayMs.coerceAtLeast(0L) * 10L)
                        .coerceAtLeast(tab.retryDelayMs),
                )
            } else {
                RetryPolicy(
                    maxAttempts = 1,
                    baseDelayMs = 0L,
                    maxDelayMs = 0L,
                )
            }

            val client = NetworkClientFactory.build(
                settings = state.settings,
                logger = logger,
                retryPolicy = retryPolicy,
            )

            client.execute(request, variableLayersWithSecrets).collect { event ->
                when (event) {
                    is NetworkEvent.Started -> {
                        state.recordHistory(
                            requestId = tab.id,
                            method = tab.method,
                            name = tab.name,
                            url = tab.url,
                        )
                        state.logNetworkEvent("Request started", LogLevel.INFO)
                    }
                    is NetworkEvent.RetryScheduled -> {
                        state.logNetworkEvent(
                            "Retry #${event.attempt} in ${event.delayMs}ms – ${event.reason}",
                            LogLevel.WARNING,
                        )
                    }
                    is NetworkEvent.Chunk -> {
                        tab.streamChunks.add(event.raw)
                        tab.liveStreamText = LlmTextAssembler.assemble(tab.streamChunks.toList())
                    }
                    is NetworkEvent.Success -> {
                        tab.response  = event.response
                        tab.lastError = null
                        state.logNetworkEvent(
                            "← ${event.response.statusCode} ${event.response.statusText}" +
                                "  (${event.response.metrics.responseTimeMs}ms," +
                                " ${event.response.metrics.responseSizeBytes}B)",
                            LogLevel.SUCCESS,
                        )
                        // ── Post-request script chain (Leaf-to-Root) ──────
                        // T024: Request test script executes first, followed by ancestor folder test scripts in leaf-to-root order.
                        // T025: // TODO: Test Script Cascading & Error Boundaries: Test scripts execute leaf-to-root (child request first, then ancestor folders). Currently, assertions and logs from all cascading scripts are aggregated into state.testResults and console logs. Future work should provide separate test summary reporting per folder hierarchy level and allow configuring failure behaviors (e.g. aborting parent test scripts if child assertion fails).
                        val testScriptChain = mutableListOf<Pair<String, String>>()
                        if (tab.testScript.isNotBlank()) {
                            testScriptChain.add("Request '${tab.name}'" to tab.testScript)
                        }
                        for (ancestor in ancestorFolders.asReversed()) {
                            val s = ancestor.testScript
                            if (!s.isNullOrBlank()) {
                                testScriptChain.add("Folder '${ancestor.name}'" to s)
                            }
                        }

                        for ((source, scriptContent) in testScriptChain) {
                            val resp = event.response
                            val layers2 = state.activeVariableLayers(tab)
                            val flatVars2 = buildMap<String, String> { layers2.asReversed().forEach { putAll(it) } }
                            val testCtx = ScriptContext(
                                url             = effectiveUrl,
                                method          = effectiveMethod.name,
                                statusCode      = resp.statusCode,
                                responseBody    = resp.bodyText,
                                responseHeaders = resp.headers.associate { it.key to it.value },
                                responseTimeMs  = resp.metrics.responseTimeMs,
                                variables       = buildMap {
                                    putAll(flatVars2)
                                    putAll(requestScopedScriptVars)
                                },
                                globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                                collectionVariables = state.collectionVariables.toMap(),
                                requestHeaders = effectiveHeaders.associate { it.key to it.value },
                                requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                                requestBody = effectiveBodyContent,
                                streamEvents = resp.streamEvents,
                                assembledText = resp.assembledText ?: tab.liveStreamText.ifBlank { null },
                            )
                            val testResult = scriptEngine.executeTestScript(scriptContent, testCtx, state.settings.scriptPrefix)
                            testResult.logs.forEach { state.log(it) }
                            if (testResult.error != null) {
                                state.log("⚠ Post-request script error ($source): ${testResult.error}", LogLevel.ERROR)
                            }
                            testResult.assertions.forEach { a ->
                                state.testResults.add(TestResultEntry(a.name, a.passed, a.message ?: ""))
                                state.log(
                                    "${if (a.passed) "✓" else "✗"} ${a.name}" +
                                        if (!a.passed && a.message != null) " — ${a.message}" else "",
                                    if (a.passed) LogLevel.SUCCESS else LogLevel.ERROR,
                                )
                            }
                            if (testResult.newVariables.isNotEmpty()) {
                                // M-8: Track test-script injected keys alongside pre-request keys
                                tab.scriptInjectedVarKeys.addAll(testResult.newVariables.keys)
                                state.mergeScriptVariables(testResult.newVariables)
                            }
                            if (testResult.newGlobalVariables.isNotEmpty()) {
                                state.mergeGlobalScriptVariables(testResult.newGlobalVariables)
                            }
                            if (testResult.newCollectionVariables.isNotEmpty()) {
                                state.mergeCollectionScriptVariables(testResult.newCollectionVariables)
                            }
                            if (testResult.executionSetNextRequestCalled) {
                                val token = testResult.executionNextRequest
                                if (token == null) {
                                    state.log("↷ Script setNextRequest(null): stopping chain", LogLevel.INFO)
                                    break
                                } else {
                                    if (chainDepth >= MAX_SCRIPT_CHAIN_DEPTH) {
                                        state.log("⚠ Script chain limit reached ($MAX_SCRIPT_CHAIN_DEPTH)", LogLevel.WARNING)
                                    } else {
                                        val target = state.resolveScriptRequestTarget(
                                            token = token,
                                            preferredCollectionId = tab.collectionId,
                                        )
                                        if (target == null) {
                                            state.log("⚠ setNextRequest target not found or ambiguous: $token", LogLevel.WARNING)
                                        } else {
                                            state.log("↷ setNextRequest → ${target.name}", LogLevel.INFO)
                                            state.openRequest(
                                                requestId = target.requestId,
                                                name = target.name,
                                                method = target.method,
                                                url = target.url,
                                            )
                                            val nextTab = state.activeTab
                                            if (nextTab != null) {
                                                sendRequestInternal(scope, state, nextTab, chainDepth + 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is NetworkEvent.Failure -> {
                        tab.lastError = event.error.message ?: "Unknown error"
                        state.logNetworkEvent("✗ ${event.error.message}", LogLevel.ERROR)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                // Request was cancelled by the user – reset loading state cleanly.
                state.log("Request cancelled", LogLevel.WARNING)
            } else {
                tab.lastError = e.message ?: "Unknown error"
                state.log("✗ ${e.message ?: "Unknown error"}", LogLevel.ERROR)
            }
        } finally {
            tab.isLoading = false
            tab.currentJob = null
        }
    }
    // H-1: Store the job so it can be cancelled if Send is clicked again.
    tab.currentJob = job
}

private fun sendGrpcRequestInternal(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    chainDepth: Int,
) {
    if (tab.url.isBlank()) {
        tab.lastError = "URL is empty"
        state.log("URL is empty", LogLevel.WARNING)
        return
    }
    if (tab.protoFilePath.isNullOrBlank()) {
        tab.lastError = "No proto file selected"
        state.log("No proto file selected", LogLevel.WARNING)
        return
    }
    if (tab.grpcMethod.isNullOrBlank()) {
        tab.lastError = "No gRPC method selected"
        state.log("No gRPC method selected", LogLevel.WARNING)
        return
    }

    tab.currentJob?.cancel()

    val job = scope.launch {
        val subReqClient = NetworkClientFactory.build(state.settings, NoOpNetworkLogger, RetryPolicy(maxAttempts = 1))
        val scriptEngine = ClapiScriptEngine(
            sendRequestExecutor = { spec -> executeSubRequest(subReqClient, spec) },
        )
        tab.isLoading = true
        tab.response = null
        tab.lastError = null
        tab.streamChunks.clear()
        tab.liveStreamText = ""
        state.testResults.clear()

        var effectiveUrl = tab.url.trim()
        val effectiveMetadata = tab.grpcMetadata
            .filter { it.enabled && it.key.isNotBlank() }
            .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
            .toMutableList()
        var effectiveMessageBody = tab.grpcMessageBody
        val requestScopedScriptVars = mutableMapOf<String, String>()

        val ancestorFolders = state.getAncestorFolders(tab)
        val preScriptChain = mutableListOf<Pair<String, String>>()
        for (ancestor in ancestorFolders) {
            val s = ancestor.preRequestScript
            if (!s.isNullOrBlank()) {
                preScriptChain.add("Folder '${ancestor.name}'" to s)
            }
        }
        if (tab.preRequestScript.isNotBlank()) {
            preScriptChain.add("Request '${tab.name}'" to tab.preRequestScript)
        }

        if (preScriptChain.isNotEmpty()) {
            if (tab.scriptInjectedVarKeys.isNotEmpty()) {
                val env = state.selectedEnvironment
                env?.variables?.removeAll { it.key in tab.scriptInjectedVarKeys && it.kind == HeaderKind.USER }
                tab.scriptInjectedVarKeys.clear()
            }

            for ((source, scriptContent) in preScriptChain) {
                val layers = state.activeVariableLayers(tab)
                val flatVars = buildMap<String, String> { layers.asReversed().forEach { putAll(it) } }
                val preCtx = ScriptContext(
                    url = effectiveUrl,
                    method = tab.grpcMethod.orEmpty(),
                    variables = buildMap {
                        putAll(flatVars)
                        putAll(requestScopedScriptVars)
                    },
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariables.toMap(),
                    requestHeaders = effectiveMetadata.associate { it.key to it.value },
                    requestQueryParams = emptyMap(),
                    requestBody = effectiveMessageBody,
                )

                val preResult = scriptEngine.executePreRequestScript(scriptContent, preCtx, state.settings.scriptPrefix)
                preResult.logs.forEach { state.log(it) }
                if (preResult.error != null) {
                    state.log("⚠ Pre-request script error ($source): ${preResult.error}", LogLevel.ERROR)
                }
                if (preResult.newVariables.isNotEmpty()) {
                    tab.scriptInjectedVarKeys.addAll(preResult.newVariables.keys)
                    state.mergeScriptVariables(preResult.newVariables)
                }
                if (preResult.newRequestVariables.isNotEmpty()) {
                    requestScopedScriptVars.putAll(preResult.newRequestVariables)
                }
                if (preResult.newGlobalVariables.isNotEmpty()) {
                    state.mergeGlobalScriptVariables(preResult.newGlobalVariables)
                }
                if (preResult.newCollectionVariables.isNotEmpty()) {
                    state.mergeCollectionScriptVariables(preResult.newCollectionVariables)
                }
                if (preResult.executionSkipRequest) {
                    state.log("↷ Request skipped by script ($source)", LogLevel.INFO)
                    tab.isLoading = false
                    tab.currentJob = null
                    return@launch
                }
                if (preResult.requestMutations.url != null) {
                    effectiveUrl = preResult.requestMutations.url!!.trim()
                }
                if (preResult.requestMutations.body != null) {
                    effectiveMessageBody = preResult.requestMutations.body ?: effectiveMessageBody
                }
                if (preResult.requestMutations.headers.isNotEmpty()) {
                    preResult.requestMutations.headers.forEach { (key, value) ->
                        val firstIndex = effectiveMetadata.indexOfFirst { it.key.equals(key, ignoreCase = true) }
                        effectiveMetadata.removeAll { it.key.equals(key, ignoreCase = true) }
                        val replacement = KeyValueEntry(key, value)
                        if (firstIndex >= 0) {
                            effectiveMetadata.add(firstIndex, replacement)
                        } else {
                            effectiveMetadata.add(replacement)
                        }
                    }
                }
            }
        }

        val textsToScan = mutableListOf<String>()
        textsToScan.add(effectiveUrl)
        textsToScan.add(effectiveMessageBody)
        effectiveMetadata.forEach {
            textsToScan.add(it.key)
            textsToScan.add(it.value)
        }
        tab.protoFilePath?.let { textsToScan.add(it) }
        tab.grpcMethod?.let { textsToScan.add(it) }

        val resolvedSecrets = resolveExternalSecretsForTab(state, tab, textsToScan) ?: return@launch
        val mergedScopedVars = if (resolvedSecrets.isNotEmpty()) {
            requestScopedScriptVars + resolvedSecrets
        } else {
            requestScopedScriptVars
        }

        val grpcCallRequest = resolveGrpcCallRequest(
            tab = tab,
            state = state,
            requestScopedVars = mergedScopedVars,
            urlOverride = effectiveUrl,
            metadataOverride = effectiveMetadata,
            bodyOverride = effectiveMessageBody,
        )

        state.recordHistory(
            requestId = tab.id,
            method = tab.method,
            name = tab.name,
            url = tab.url,
        )
        state.logNetworkEvent("→ gRPC ${grpcCallRequest.fullMethodName} ${grpcCallRequest.host}:${grpcCallRequest.port}")

        val grpcClient = NetworkClientFactory.buildGrpcClient(state.settings)
        try {
            val grpcResp = grpcClient.executeUnary(grpcCallRequest)
            val bodyText = grpcResp.responseJson ?: buildString {
                append("{\n")
                append("  \"status\": \"${grpcResp.statusDescription}\",\n")
                append("  \"code\": ${grpcResp.statusCode},\n")
                append("  \"message\": \"${(grpcResp.errorMessage ?: "").replace("\"", "\\\"")}\"\n")
                append("}")
            }
            val bodyBytes = bodyText.encodeToByteArray().size.toLong()
            val responseDef = ResponseDefinition(
                requestId = tab.id,
                statusCode = grpcResp.statusCode,
                statusText = grpcResp.statusDescription,
                headers = grpcResp.responseMetadata.map { (k, v) -> KeyValueEntry(k, v) },
                cookies = emptyList(),
                bodyText = bodyText,
                contentType = "application/json",
                executedAtEpochMillis = currentTimeMillis(),
                metrics = ResponseMetrics(
                    statusCode = grpcResp.statusCode,
                    responseTimeMs = grpcResp.durationMs,
                    responseSizeBytes = bodyBytes,
                    serverMs = grpcResp.durationMs,
                ),
            )
            tab.response = responseDef
            if (grpcResp.statusCode == 0) {
                tab.lastError = null
                state.logNetworkEvent(
                    "← 0 OK (${grpcResp.durationMs}ms, ${bodyBytes}B)",
                    LogLevel.SUCCESS,
                )
            } else {
                tab.lastError = "${grpcResp.statusCode} ${grpcResp.statusDescription}: ${grpcResp.errorMessage ?: "gRPC call failed"}"
                state.logNetworkEvent(
                    "✗ ${grpcResp.statusCode} ${grpcResp.statusDescription} — ${grpcResp.errorMessage ?: ""}",
                    LogLevel.ERROR,
                )
            }

            // ── Post-request script chain (Leaf-to-Root) ──────────────
            val testScriptChain = mutableListOf<Pair<String, String>>()
            if (tab.testScript.isNotBlank()) {
                testScriptChain.add("Request '${tab.name}'" to tab.testScript)
            }
            for (ancestor in ancestorFolders.asReversed()) {
                val s = ancestor.testScript
                if (!s.isNullOrBlank()) {
                    testScriptChain.add("Folder '${ancestor.name}'" to s)
                }
            }

            for ((source, scriptContent) in testScriptChain) {
                val layers2 = state.activeVariableLayers(tab)
                val flatVars2 = buildMap<String, String> { layers2.asReversed().forEach { putAll(it) } }
                val testCtx = ScriptContext(
                    url = effectiveUrl,
                    method = grpcCallRequest.fullMethodName,
                    statusCode = responseDef.statusCode,
                    responseBody = responseDef.bodyText,
                    responseHeaders = responseDef.headers.associate { it.key to it.value },
                    responseTimeMs = responseDef.metrics.responseTimeMs,
                    variables = buildMap {
                        putAll(flatVars2)
                        putAll(requestScopedScriptVars)
                    },
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariables.toMap(),
                    requestHeaders = grpcCallRequest.metadata.toMap(),
                    requestQueryParams = emptyMap(),
                    requestBody = grpcCallRequest.jsonPayload,
                )
                val testResult = scriptEngine.executeTestScript(scriptContent, testCtx, state.settings.scriptPrefix)
                testResult.logs.forEach { state.log(it) }
                if (testResult.error != null) {
                    state.log("⚠ Post-request script error ($source): ${testResult.error}", LogLevel.ERROR)
                }
                testResult.assertions.forEach { a ->
                    state.testResults.add(TestResultEntry(a.name, a.passed, a.message ?: ""))
                    state.log(
                        "${if (a.passed) "✓" else "✗"} ${a.name}" +
                            if (!a.passed && a.message != null) " — ${a.message}" else "",
                        if (a.passed) LogLevel.SUCCESS else LogLevel.ERROR,
                    )
                }
                if (testResult.newVariables.isNotEmpty()) {
                    tab.scriptInjectedVarKeys.addAll(testResult.newVariables.keys)
                    state.mergeScriptVariables(testResult.newVariables)
                }
                if (testResult.newGlobalVariables.isNotEmpty()) {
                    state.mergeGlobalScriptVariables(testResult.newGlobalVariables)
                }
                if (testResult.newCollectionVariables.isNotEmpty()) {
                    state.mergeCollectionScriptVariables(testResult.newCollectionVariables)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                state.log("Request cancelled", LogLevel.WARNING)
            } else {
                tab.lastError = e.message ?: "Unknown error"
                state.log("✗ ${e.message ?: "Unknown error"}", LogLevel.ERROR)
            }
        } finally {
            runCatching { grpcClient.shutdown() }
            tab.isLoading = false
            tab.currentJob = null
        }
    }
    tab.currentJob = job
}

/**
 * Saves [tab] to disk via [TabsRepository] and marks it as clean.
 * [onSaved] is invoked on the main thread after the save completes.
 */
fun saveRequest(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    onSaved: (() -> Unit)? = null,
) {
    tab.syncSystemHeaders()
    scope.launch {
        val checkpoint = tab.captureSaveCheckpoint()
        val collectionSynced = tab.collectionName == null || state.syncTabToCollectionNode(tab)
        // The clean marker must be part of the persisted tab snapshot. Previously
        // TabsRepository.save ran first, so an immediate reload restored a dirty tab.
        tab.markSaved()
        val tabsSaved = withContext(ioDispatcher) { TabsRepository.save(state) }
        val workspaceSaved = if ((tab.isFolderTab || tab.isCollectionTab || tab.collectionName != null) && collectionSynced) {
            withContext(ioDispatcher) { WorkspaceRepository.save(state) }
        } else {
            collectionSynced
        }
        val ok = tabsSaved && workspaceSaved
        if (ok) {
            state.log("✓ Request saved: ${tab.name}", LogLevel.SUCCESS)
            onSaved?.invoke()
        } else {
            tab.restoreSaveCheckpoint(checkpoint.copy(isDirty = true))
            // Best effort: if tabs were written before workspace failed, put the
            // original dirty marker back on disk as well as in memory.
            withContext(ioDispatcher) { TabsRepository.save(state) }
            state.log("✗ Failed to save request: ${tab.name}", LogLevel.ERROR)
            state.showError(
                title = "Save failed",
                message = "The request could not be saved. Please try again.",
            )
        }
    }
}

// ── Internal helpers ────────────────────────────────────────────

/**
 * Canonical tab-to-request projection shared by Send and every Copy As format.
 * Script mutations are supplied as overrides by Send; Copy uses the tab defaults.
 */
data class PreparedTabRequest(
    val id: String,
    val name: String,
    val method: HttpMethodType,
    val url: String,
    val fragment: String,
    val queryParams: List<KeyValueEntry>,
    val headers: List<KeyValueEntry>,
    val auth: AuthConfig,
    val body: RequestBody,
) {
    fun toRequestDefinition(): RequestDefinition = RequestDefinition(
        id = id,
        name = name,
        method = method,
        url = url,
        queryParams = queryParams.filter { it.enabled && it.key.isNotBlank() },
        headers = headers.filter { it.enabled && it.key.isNotBlank() },
        auth = auth,
        body = body,
        createdAtEpochMillis = currentTimeMillis(),
        updatedAtEpochMillis = currentTimeMillis(),
    )
}

fun prepareTabRequest(
    tab: RequestTabState,
    method: HttpMethodType = tab.method,
    url: String = tab.url.trim(),
    queryParams: List<KeyValueEntry> = enabledQueryParamsForSend(tab),
    headers: List<KeyValueEntry> = enabledHeadersForSend(tab),
    bodyContent: String = tab.bodyContent,
    state: AppState? = null,
): PreparedTabRequest {
    val parts = splitRequestUrl(url)
    return PreparedTabRequest(
        id = tab.id,
        name = tab.name,
        method = method,
        url = parts.base,
        fragment = parts.fragment,
        queryParams = queryParams,
        headers = headers,
        auth = buildAuthConfig(tab, state),
        body = buildRequestBody(tab, bodyContent),
    )
}

fun buildAuthConfig(tab: RequestTabState, state: AppState? = null): AuthConfig {
    val effectiveAuthType: AuthType
    val effectiveParams: Map<String, String>
    val effectivePlacement: String?
    if (tab.authType == AuthType.INHERIT && state != null) {
        val resolved = state.resolveInheritedAuth(tab)
        if (resolved != null && resolved.authType != AuthType.NONE && resolved.authType != AuthType.INHERIT) {
            effectiveAuthType = resolved.authType
            effectiveParams = when (resolved.authType) {
                AuthType.BASIC -> mapOf("username" to (resolved.username ?: ""), "password" to (resolved.password ?: ""))
                AuthType.BEARER, AuthType.JWT -> mapOf("token" to (resolved.token ?: ""))
                AuthType.API_KEY -> mapOf("key" to (resolved.apiKey ?: ""), "value" to (resolved.apiValue ?: ""))
                else -> emptyMap()
            }
            effectivePlacement = if (resolved.authType == AuthType.API_KEY) {
                io.github.crustack.clapi.ui.shared.state.normalizeApiKeyPlacement(resolved.apiPlacement)
            } else null
        } else {
            effectiveAuthType = AuthType.NONE
            effectiveParams = emptyMap()
            effectivePlacement = null
        }
    } else {
        effectiveAuthType = tab.authType
        effectiveParams = when (tab.authType) {
            AuthType.BASIC   -> mapOf("username" to tab.authUsername, "password" to tab.authPassword)
            AuthType.BEARER  -> mapOf("token" to tab.authToken)
            AuthType.JWT     -> mapOf("token" to tab.authToken)
            AuthType.API_KEY -> mapOf("key" to tab.authApiKey, "value" to tab.authApiValue)
            else             -> emptyMap()
        }
        effectivePlacement = if (tab.authType == AuthType.API_KEY) {
            io.github.crustack.clapi.ui.shared.state.normalizeApiKeyPlacement(tab.authApiPlacement)
        } else null
    }
    return AuthConfig(
        type = effectiveAuthType,
        params = effectiveParams,
        placement = effectivePlacement,
    )
}

/**
 * Builds a [RequestBody] from [tab] state, using structured form rows when available.
 * Falls back to parsing [effectiveBodyContent] for older/simpler body types.
 */
fun buildRequestBody(tab: RequestTabState, effectiveBodyContent: String): RequestBody {
    return when (tab.bodyType) {
        BodyType.FORM_DATA -> {
            val rows = tab.formRows.filter { it.enabled }
            val formDataEntries = rows.map {
                FormDataEntry(it.key, it.type, it.value, it.description, it.enabled, it.bytesBase64)
            }
            val formEntries = rows.map { KeyValueEntry(it.key, it.value) }
            RequestBody(
                type = BodyType.FORM_DATA,
                formEntries = formEntries,
                formDataEntries = formDataEntries,
            )
        }
        BodyType.X_WWW_FORM_URLENCODED -> {
            val rows = tab.urlencodedRows.filter { it.enabled }
            val formEntries = if (rows.isNotEmpty()) {
                rows.map { KeyValueEntry(it.key, it.value) }
            } else {
                // Backward compat: parse from raw bodyContent
                effectiveBodyContent.split("&", "\n").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) KeyValueEntry(part.substring(0, idx).trim(), part.substring(idx + 1).trim())
                    else null
                }
            }
            RequestBody(type = BodyType.X_WWW_FORM_URLENCODED, formEntries = formEntries)
        }
        BodyType.GRAPHQL -> RequestBody(type = BodyType.GRAPHQL, graphQl = GraphQlBody(query = effectiveBodyContent))
        else -> buildRequestBody(tab.bodyType, effectiveBodyContent)
    }
}

/**
 * Builds a [RequestBody] for the given body type and raw content string.
 * For FORM_DATA and X_WWW_FORM_URLENCODED, parses [content] as key=value pairs
 * separated by & or newlines and populates [RequestBody.formEntries].
 */
fun buildRequestBody(bodyType: BodyType, content: String): RequestBody {
    val rawContent = content.ifBlank { null }
    val binaryAttachment = parseBinaryAttachment(content)
    val formEntries = when (bodyType) {
        BodyType.FORM_DATA, BodyType.X_WWW_FORM_URLENCODED -> {
            content.split("&", "\n").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) KeyValueEntry(
                    key = part.substring(0, idx).trim(),
                    value = part.substring(idx + 1).trim(),
                ) else null
            }
        }
        else -> emptyList()
    }
    return RequestBody(
        type = bodyType,
        content = if (bodyType == BodyType.GRAPHQL) null else rawContent,
        formEntries = formEntries,
        graphQl = if (bodyType == BodyType.GRAPHQL) GraphQlBody(query = content) else null,
        binaryName = binaryAttachment?.first,
        binaryBytesBase64 = binaryAttachment?.second,
    )
}

private fun parseBinaryAttachment(content: String): Pair<String, String>? {
    if (!content.startsWith(BINARY_ATTACHMENT_PREFIX)) return null
    val separator = content.indexOf('\n')
    if (separator <= BINARY_ATTACHMENT_PREFIX.length || separator >= content.length - 1) return null
    val fileName = content.substring(BINARY_ATTACHMENT_PREFIX.length, separator).trim()
    val base64 = content.substring(separator + 1).trim()
    if (fileName.isBlank() || base64.isBlank()) return null
    return fileName to base64
}

/** Builds a cURL command string for the given tab, resolving {{vars}} from variable layers. */
fun buildCurlCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val parts = mutableListOf("curl", "-X ${request.method.name}")

    preparedHeaderList(request, variableLayers).forEach { (key, value) ->
        parts += "-H ${shellQuote("$key: $value")}"
    }
    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        body.multipart.forEach { (key, value) -> parts += "--form-string ${shellQuote("$key=$value")}" }
    } else if (body.content != null) {
        parts += "--data-binary ${shellQuote(body.content)}"
    }

    // Build URL with inline query params
    val resolvedUrl = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    parts += shellQuote(resolvedUrl)
    return parts.joinToString(" \\\n  ")
}

fun enabledHeadersForSend(tab: RequestTabState): List<KeyValueEntry> {
    val enabled = tab.headers.filter { it.enabled && it.key.isNotBlank() }
    val userHeaders = enabled.filter { it.kind != HeaderKind.INHERITED }
    val inheritedHeaders = enabled.filter { it.kind == HeaderKind.INHERITED }
    val userKeys = userHeaders.map { it.key.lowercase() }.toSet()
    val activeInherited = inheritedHeaders.filter { it.key.lowercase() !in userKeys }
    return (userHeaders + activeInherited).map { KeyValueEntry(it.key, it.value, secret = it.secret) }
}

fun enabledQueryParamsForSend(tab: RequestTabState): List<KeyValueEntry> {
    val enabled = tab.params.filter { it.enabled && it.key.isNotBlank() }
    val userParams = enabled.filter { it.kind != HeaderKind.INHERITED }
    val inheritedParams = enabled.filter { it.kind == HeaderKind.INHERITED }
    val userKeys = userParams.map { it.key.lowercase() }.toSet()
    val activeInherited = inheritedParams.filter { it.key.lowercase() !in userKeys }
    return (userParams + activeInherited).map { KeyValueEntry(it.key, it.value, secret = it.secret) }
}

internal fun resolveUrlForLog(
    url: String,
    variableLayers: List<Map<String, String>>,
    requestScopedVars: Map<String, String> = emptyMap(),
): String {
    val layers = if (requestScopedVars.isNotEmpty()) {
        listOf(requestScopedVars) + variableLayers
    } else {
        variableLayers
    }
    return VariableResolver.resolve(url, layers, removeUnresolved = false)
}

/** cURL command with raw (unresolved) {{variables}} preserved – useful for sharing templates. */
fun buildCurlCommandRaw(tab: RequestTabState, allowJson5: Boolean = true): String =
    buildCurlCommand(tab, emptyList(), allowJson5)

/** Python `requests` snippet resolving {{vars}}. */
fun buildPythonCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val sb = StringBuilder()
    sb.appendLine("import requests")
    sb.appendLine()

    val headers = preparedHeaderList(request, variableLayers)
    if (headers.isNotEmpty()) {
        sb.appendLine("headers = {")
        headers.forEach { (k, v) -> sb.appendLine("    ${pyStr(k)}: ${pyStr(v)},") }
        sb.appendLine("}")
        sb.appendLine()
    }

    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    val method = request.method.name.uppercase()

    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        sb.appendLine("files = [")
        body.multipart.forEach { (key, value) ->
            sb.appendLine("    (${pyStr(key)}, (None, ${pyStr(value)})),")
        }
        sb.appendLine("]")
        sb.appendLine()
    } else if (body.content != null) {
        sb.appendLine("data = ${pyStr(body.content)}")
        sb.appendLine()
    }
    sb.append("response = requests.request(${pyStr(method)}, ${pyStr(url)}")
    if (headers.isNotEmpty()) sb.append(", headers=headers")
    if (body.multipart != null) sb.append(", files=files")
    else if (body.content != null) sb.append(", data=data")
    sb.appendLine(")")
    sb.appendLine("print(response.status_code, response.text)")
    return sb.toString().trimEnd()
}

/** HTTPie CLI snippet resolving {{vars}}. */
fun buildHTTPieCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val parts = mutableListOf("http", tab.method.name)
    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    parts += shellQuote(url)

    preparedHeaderList(request, variableLayers, removeUnresolved = true).forEach { (k, v) -> parts += shellQuote("$k:$v") }

    if (request.body.type != BodyType.NONE && tab.bodyContent.isNotBlank()) {
        parts += "--raw"
        parts += shellQuote(prepareCopyBody(request.body, variableLayers, allowJson5).content.orEmpty())
    }

    return parts.joinToString(" \\\n  ")
}

/** PowerShell `Invoke-WebRequest` snippet resolving {{vars}}. */
fun buildPowerShellCommand(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
    state: AppState? = null,
): String {
    val request = prepareTabRequest(tab, state = state)
    val sb = StringBuilder()
    val url = buildUrlWithParams(request, variableLayers, removeUnresolved = true)
    val headers = preparedHeaderList(request, variableLayers)

    if (headers.isNotEmpty()) {
        sb.appendLine("\$headers = @{")
        headers.forEach { (k, v) -> sb.appendLine("    '${k.replace("'", "''")}'='${v.replace("'", "''")}'") }
        sb.appendLine("}")
        sb.appendLine()
    }

    sb.append("Invoke-WebRequest -Uri '${url.replace("'", "''")}' -Method ${request.method.name}")
    if (headers.isNotEmpty()) sb.append(" -Headers \$headers")

    val body = prepareCopyBody(request.body, variableLayers, allowJson5)
    if (body.multipart != null) {
        val fields = body.multipart.joinToString("; ") { (key, value) ->
            "'${key.replace("'", "''")}'='${value.replace("'", "''")}'"
        }
        sb.append(" -Form @{$fields}")
    } else if (body.content != null) {
        sb.append(" -Body '${body.content.replace("'", "''")}'")
    }

    return sb.toString().trimEnd()
}

// ── Curl / format helpers ────────────────────────────────────────────

private fun buildUrlWithParams(
    request: PreparedTabRequest,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = false,
): String {
    val queryEntries = request.queryParams.filter { it.enabled && it.key.isNotBlank() }.map { p ->
        VariableResolver.resolve(p.key, variableLayers, removeUnresolved) to
            VariableResolver.resolve(p.value, variableLayers, removeUnresolved)
    }.toMutableList()
    val auth = request.auth
    if (auth.type == AuthType.API_KEY && effectiveApiKeyPlacement(auth) == "query") {
        val key = VariableResolver.resolve(auth.params["key"].orEmpty(), variableLayers, removeUnresolved)
        if (key.isNotBlank()) {
            queryEntries += key to VariableResolver.resolve(
                auth.params["value"].orEmpty(), variableLayers, removeUnresolved,
            )
        }
    }
    // Strip any embedded query string from resolvedBase: tab.params is the single
    // source of truth for query parameters (kept in sync by syncParamsFromUrl /
    // syncUrlFromParams). Appending to a URL that already contains those params
    // would duplicate every parameter in the final request URL.
    val qs = encodeOrderedQuery(queryEntries, preserveTemplates = !removeUnresolved)
    val resolvedBase = VariableResolver.resolve(request.url, variableLayers, removeUnresolved)
    val resolvedFragment = VariableResolver.resolve(request.fragment, variableLayers, removeUnresolved)
    return joinRequestUrl(resolvedBase, qs, resolvedFragment)
}

private data class PreparedCopyBody(
    val content: String? = null,
    val multipart: List<Pair<String, String>>? = null,
)

private fun prepareCopyBody(
    body: RequestBody,
    variableLayers: List<Map<String, String>>,
    allowJson5: Boolean,
): PreparedCopyBody {
    fun resolve(value: String) = VariableResolver.resolve(value, variableLayers, removeUnresolved = true)
    return when (body.type) {
        BodyType.NONE -> PreparedCopyBody()
        BodyType.BINARY -> error("Embedded binary has no reusable file path")
        BodyType.FORM_DATA -> {
            require(body.formDataEntries.none { it.type == FormEntryType.FILE }) {
                "Embedded multipart file has no reusable file path"
            }
            val entries = if (body.formDataEntries.isNotEmpty()) {
                body.formDataEntries.map { it.key to resolve(it.value) }
            } else body.formEntries.map { it.key to resolve(it.value) }
            PreparedCopyBody(multipart = entries)
        }
        BodyType.X_WWW_FORM_URLENCODED -> PreparedCopyBody(content =
            encodeOrderedQuery(body.formEntries.filter { it.enabled }.map { it.key to resolve(it.value) }))
        BodyType.GRAPHQL -> PreparedCopyBody(content =
            encodeGraphQlEnvelope(body.graphQl ?: GraphQlBody(), variableLayers, allowJson5))
        else -> PreparedCopyBody(content = body.content?.let { content ->
            if (allowJson5 && body.type == BodyType.JSON && content.isNotBlank()) {
                Json5.toWireJson(resolve(content)).getOrDefault(resolve(content))
            } else resolve(content)
        })
    }
}

internal fun copyHeaderList(
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    omitMultipartContentType: Boolean = true,
): List<Pair<String, String>> = preparedHeaderList(
    prepareTabRequest(tab), variableLayers, removeUnresolved = true,
    omitMultipartContentType = omitMultipartContentType,
)

private fun preparedHeaderList(
    request: PreparedTabRequest,
    variableLayers: List<Map<String, String>>,
    removeUnresolved: Boolean = true,
    omitMultipartContentType: Boolean = true,
): List<Pair<String, String>> {
    fun resolve(s: String) = VariableResolver.resolve(s, variableLayers, removeUnresolved)
    val list = mutableListOf<Pair<String, String>>()
    request.headers.filter { it.enabled && it.key.isNotBlank() }
        .forEach { list += resolve(it.key) to resolve(it.value) }
    val auth = request.auth
    when (auth.type) {
        AuthType.BASIC -> {
            val encoded = "${resolve(auth.params["username"].orEmpty())}:${resolve(auth.params["password"].orEmpty())}"
                .encodeToByteArray().encodeBase64()
            list += "Authorization" to "Basic $encoded"
        }
        AuthType.BEARER, AuthType.JWT -> {
            val token = resolve(auth.params["token"].orEmpty()).trim()
            if (token.isNotEmpty()) list += "Authorization" to "Bearer $token"
        }
        AuthType.API_KEY -> {
            if (effectiveApiKeyPlacement(auth) != "query") {
                val key = resolve(auth.params["key"].orEmpty())
                if (key.isNotBlank()) list += key to resolve(auth.params["value"].orEmpty())
            }
        }
        else -> Unit
    }
    return list.filterNot { (key, _) ->
        omitMultipartContentType && request.body.type == BodyType.FORM_DATA &&
            key.equals("Content-Type", ignoreCase = true)
    }
}

internal fun effectiveApiKeyPlacement(auth: AuthConfig): String =
    io.github.crustack.clapi.ui.shared.state.normalizeApiKeyPlacement(auth.placement ?: auth.params["placement"])

private fun pyStr(s: String): String = "\"" + s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t") + "\""

internal fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

internal suspend fun resolveExternalSecretsForTab(
    state: AppState,
    tab: RequestTabState,
    textsToScan: Iterable<String>,
    secretResolutionService: SecretResolutionService = SecretResolutionService(),
    processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
): Map<String, String>? {
    val secretVariableNames = state.selectedEnvironment?.variables
        ?.filter { it.enabled && it.secret && it.key.isNotBlank() }
        ?.map { it.key.trim() }
        ?.toSet()
        ?: emptySet()

    val declarations = state.selectedEnvironment?.externalSecrets?.map {
        SecretDeclaration(
            name = it.name.trim(),
            path = it.path.trim(),
            enabled = it.enabled,
        )
    } ?: emptyList()

    return try {
        secretResolutionService.resolveAllSecrets(
            texts = textsToScan,
            declarations = declarations,
            secretVariableNames = secretVariableNames,
            vaultUrl = state.settings.vaultServerUrl,
            vaultToken = state.settings.vaultToken.ifBlank {
                PlatformStorage.getSecretString("clapi.settings.vaultToken").orEmpty()
            },
            mountPath = state.settings.vaultMountPath.ifBlank { "secret" },
            namespace = state.settings.vaultNamespace.ifBlank { null },
            processEnvProvider = processEnvProvider,
        )
    } catch (e: Exception) {
        val errorMsg = e.message ?: "Failed to resolve external secrets"
        tab.lastError = errorMsg
        tab.isLoading = false
        tab.currentJob = null
        state.log("Secret resolution error: $errorMsg", LogLevel.ERROR)
        null
    }
}
