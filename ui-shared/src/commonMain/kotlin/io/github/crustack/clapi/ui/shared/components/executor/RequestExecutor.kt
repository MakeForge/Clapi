package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.*
import io.github.crustack.clapi.core.network.*
import io.github.crustack.clapi.core.network.dynamicvar.*
import io.github.crustack.clapi.core.network.oauth2.*
import io.github.crustack.clapi.core.network.vault.*
import io.github.crustack.clapi.core.network.websocket.*
import io.github.crustack.clapi.core.scripting.*
import io.github.crustack.clapi.ui.shared.components.*
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.state.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val MAX_SCRIPT_CHAIN_DEPTH = 16

/**
 * Executes a sub-HTTP request from a [clapi.sendRequest()] script call.
 * Uses [client] (built from the current AppSettings) to make the real call.
 */
internal suspend fun executeSubRequest(client: ApiClient, spec: SendRequestSpec): SendRequestResult {
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

fun sendRequest(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
) {
    sendRequestInternal(scope, state, tab, chainDepth = 0, tokenClient = tokenClient)
}

internal fun sendRequestInternal(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    chainDepth: Int,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
) {
    if (tab.kind == RequestKind.GRPC) {
        sendGrpcRequestInternal(scope, state, tab, chainDepth, tokenClient = tokenClient)
        return
    }

    if (tab.kind == RequestKind.WEBSOCKET) {
        sendWebSocketRequestInternal(scope, state, tab)
        return
    }

    if (tab.kind == RequestKind.GRAPHQL) {
        sendGraphQlRequestInternal(scope, state, tab, chainDepth, tokenClient = tokenClient)
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
                val label = if (ancestor.isCollection) "Collection '${ancestor.name}'" else "Folder '${ancestor.name}'"
                preScriptChain.add(label to s)
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
        textsToScan.add(tab.authOAuth2ClientId)
        textsToScan.add(tab.authOAuth2ClientSecret)
        textsToScan.add(tab.authOAuth2Username)
        textsToScan.add(tab.authOAuth2Password)
        textsToScan.add(tab.authOAuth2AccessTokenUrl)
        textsToScan.add(tab.authOAuth2RefreshTokenUrl)
        textsToScan.add(tab.authOAuth2AuthorizationUrl)
        textsToScan.add(tab.authOAuth2RedirectUri)
        textsToScan.add(tab.authOAuth2Scope)
        if (tab.authType == AuthType.INHERIT) {
            val resolvedAuth = state.resolveInheritedAuth(tab)
            if (resolvedAuth != null) {
                resolvedAuth.username?.let { textsToScan.add(it) }
                resolvedAuth.password?.let { textsToScan.add(it) }
                resolvedAuth.token?.let { textsToScan.add(it) }
                resolvedAuth.apiKey?.let { textsToScan.add(it) }
                resolvedAuth.apiValue?.let { textsToScan.add(it) }
                resolvedAuth.oauth2ClientId?.let { textsToScan.add(it) }
                resolvedAuth.oauth2ClientSecret?.let { textsToScan.add(it) }
                resolvedAuth.oauth2Username?.let { textsToScan.add(it) }
                resolvedAuth.oauth2Password?.let { textsToScan.add(it) }
                resolvedAuth.oauth2AccessTokenUrl?.let { textsToScan.add(it) }
                resolvedAuth.oauth2RefreshTokenUrl?.let { textsToScan.add(it) }
                resolvedAuth.oauth2AuthorizationUrl?.let { textsToScan.add(it) }
                resolvedAuth.oauth2RedirectUri?.let { textsToScan.add(it) }
                resolvedAuth.oauth2Scope?.let { textsToScan.add(it) }
            }
        }
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

        val resolvedDynamicVars = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = variableLayersWithSecrets,
            tokenClient = tokenClient,
        ) ?: return@launch

        val effectiveVariableLayers = if (resolvedDynamicVars.isNotEmpty()) {
            listOf(resolvedDynamicVars) + variableLayersWithSecrets
        } else {
            variableLayersWithSecrets
        }

        val hasValidAuth = ensureValidOAuth2Token(
            state = state,
            tab = tab,
            variableLayers = effectiveVariableLayers,
            tokenClient = tokenClient,
        )
        if (!hasValidAuth) {
            tab.isLoading = false
            tab.currentJob = null
            return@launch
        }

        val resolvedBaseForLog = resolveUrlForLog(
            url = effectiveUrl,
            variableLayers = effectiveVariableLayers,
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

            client.execute(request, effectiveVariableLayers).collect { event ->
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
                                val label = if (ancestor.isCollection) "Collection '${ancestor.name}'" else "Folder '${ancestor.name}'"
                                testScriptChain.add(label to s)
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

