package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.*
import io.github.crustack.clapi.core.network.*
import io.github.crustack.clapi.core.network.dynamicvar.*
import io.github.crustack.clapi.core.network.execution.extractTemplateStrings
import io.github.crustack.clapi.core.network.grpc.DynamicGrpcClient
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
    clientFactory: (AppSettings, NetworkLogger, RetryPolicy) -> ApiClient = { s, l, r ->
        NetworkClientFactory.build(s, l, r)
    },
    grpcClientFactory: (AppSettings) -> DynamicGrpcClient = { s ->
        NetworkClientFactory.buildGrpcClient(s)
    },
    secretResolutionService: SecretResolutionService = SecretResolutionService(),
    processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
) {
    sendRequestInternal(
        scope = scope,
        state = state,
        tab = tab,
        chainDepth = 0,
        tokenClient = tokenClient,
        clientFactory = clientFactory,
        grpcClientFactory = grpcClientFactory,
        secretResolutionService = secretResolutionService,
        processEnvProvider = processEnvProvider,
    )
}

internal fun sendRequestInternal(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    chainDepth: Int,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
    clientFactory: (AppSettings, NetworkLogger, RetryPolicy) -> ApiClient = { s, l, r ->
        NetworkClientFactory.build(s, l, r)
    },
    grpcClientFactory: (AppSettings) -> DynamicGrpcClient = { s ->
        NetworkClientFactory.buildGrpcClient(s)
    },
    secretResolutionService: SecretResolutionService = SecretResolutionService(),
    processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
) {
    if (tab.kind == RequestKind.GRPC) {
        sendGrpcRequestInternal(
            scope = scope,
            state = state,
            tab = tab,
            chainDepth = chainDepth,
            tokenClient = tokenClient,
            clientFactory = clientFactory,
            grpcClientFactory = grpcClientFactory,
            secretResolutionService = secretResolutionService,
            processEnvProvider = processEnvProvider,
        )
        return
    }

    if (tab.kind == RequestKind.WEBSOCKET) {
        sendWebSocketRequestInternal(scope, state, tab)
        return
    }

    if (tab.kind == RequestKind.GRAPHQL) {
        sendGraphQlRequestInternal(
            scope = scope,
            state = state,
            tab = tab,
            chainDepth = chainDepth,
            tokenClient = tokenClient,
            clientFactory = clientFactory,
            grpcClientFactory = grpcClientFactory,
            secretResolutionService = secretResolutionService,
            processEnvProvider = processEnvProvider,
        )
        return
    }

    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
        return
    }

    tab.currentJob?.cancel()

    val job = scope.launch {
        val subReqHolder = LazySubRequestClientHolder(state.settings, clientFactory)
        var client: ApiClient? = null
        try {
            val scriptEngine = ClapiScriptEngine(
                sendRequestExecutor = { spec -> executeSubRequest(subReqHolder.getOrCreate(), spec) },
            )
        tab.isLoading = true
        tab.response  = null
        tab.lastError = null
        tab.streamChunks.clear()
        tab.liveStreamText = ""
        state.testResults.clear()

        val urlParts = splitRequestUrl(tab.url.trim())
        var effectiveUrl = urlParts.base
        var effectiveMethod = tab.method
        val effectiveHeaders = enabledHeadersForSend(tab).toMutableList()
        val effectiveQueryParams = enabledQueryParamsForSend(tab).toMutableList()
        var effectiveBodyContent = tab.bodyContent
        val requestScopedScriptVars = mutableMapOf<String, String>()

        // ── Pre-request script chain (Root-to-Leaf) ───────────────────────
        val preScriptChain = buildPreScriptChain(state, tab)

        if (preScriptChain.isNotEmpty()) {
            if (tab.scriptInjectedVarKeys.isNotEmpty()) {
                val env = state.selectedEnvironment
                env?.variables?.removeAll { it.key in tab.scriptInjectedVarKeys && it.kind == HeaderKind.USER }
                tab.scriptInjectedVarKeys.clear()
            }

            // Pass 1: Resolve any secret:true environment variables or {{_secret.*}} tokens
            // referenced in the pre-request scripts so ScriptContext.variables has resolved values.
            val preScriptSecrets = resolvePreScriptSecretsForTab(
                state = state,
                tab = tab,
                preScriptChain = preScriptChain,
                secretResolutionService = secretResolutionService,
                processEnvProvider = processEnvProvider,
            ) ?: return@launch

            for ((source, scriptContent) in preScriptChain) {
                val layers = state.activeVariableLayers(tab)
                val flatVars = buildMap<String, String> {
                    layers.asReversed().forEach { putAll(it) }
                    putAll(preScriptSecrets)
                    putAll(requestScopedScriptVars)
                }
                val preCtx = ScriptContext(
                    url       = effectiveUrl,
                    method    = effectiveMethod.name,
                    variables = flatVars,
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariablesFor(tab),
                    requestHeaders = effectiveHeaders.associate { it.key to it.value },
                    requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                    requestBody = effectiveBodyContent,
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
                    state.mergeCollectionScriptVariables(preResult.newCollectionVariables, tab)
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

        val initialRequest = prepareTabRequest(
            tab = tab,
            method = effectiveMethod,
            url = effectiveUrl,
            queryParams = effectiveQueryParams,
            headers = effectiveHeaders,
            bodyContent = effectiveBodyContent,
            state = state,
        ).toRequestDefinition()

        // Pass 2: Scan all fields of the post-script prepared request, auth config, and script vars
        val textsToScan = buildList {
            add(tab.url)
            add(urlParts.query)
            addAll(initialRequest.extractTemplateStrings())
            addAll(collectAuthTextsToScan(state, tab))
            addAll(requestScopedScriptVars.values)
        }

        val resolvedSecrets = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            secretResolutionService = secretResolutionService,
            processEnvProvider = processEnvProvider,
        ) ?: return@launch

        val variableLayersWithSecrets = buildList {
            if (requestScopedScriptVars.isNotEmpty()) add(requestScopedScriptVars.toMap())
            if (resolvedSecrets.isNotEmpty()) add(resolvedSecrets)
            addAll(state.activeVariableLayers(tab))
        }

        val resolvedDynamicVars = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = variableLayersWithSecrets,
            tokenClient = tokenClient,
        ) ?: return@launch

        val effectiveVariableLayers = assembleEffectiveVariableLayers(
            state = state,
            tab = tab,
            requestScopedScriptVars = requestScopedScriptVars,
            resolvedDynamicVars = resolvedDynamicVars,
            resolvedSecrets = resolvedSecrets,
        )

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
        )
        val effectiveUrlForLog = if (effectiveQueryParams.isNotEmpty()) {
            val qs = encodeOrderedQuery(effectiveQueryParams.map { it.key to it.value }, preserveTemplates = true)
            "$resolvedBaseForLog?$qs"
        } else {
            resolvedBaseForLog
        }
        state.logNetworkEvent("→ $effectiveMethod $effectiveUrlForLog")

        try {
            // Rebuild request after ensureValidOAuth2Token so any newly refreshed OAuth2 accessToken is included
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

            val retryPolicy = buildRetryPolicyForTab(tab)

            client = clientFactory(
                state.settings,
                logger,
                retryPolicy,
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
                        val testScriptChain = buildPostScriptChain(state, tab)
                        val resp = event.response
                        executePostRequestScriptChain(
                            scope = scope,
                            state = state,
                            tab = tab,
                            chainDepth = chainDepth,
                            scriptEngine = scriptEngine,
                            testScriptChain = testScriptChain,
                            effectiveUrl = effectiveUrl,
                            effectiveMethodName = effectiveMethod.name,
                            statusCode = resp.statusCode,
                            responseBody = resp.bodyText,
                            responseHeaders = resp.headers.associate { it.key to it.value },
                            responseTimeMs = resp.metrics.responseTimeMs,
                            requestHeaders = effectiveHeaders.associate { it.key to it.value },
                            requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                            requestBody = effectiveBodyContent,
                            requestScopedScriptVars = requestScopedScriptVars,
                            resolvedSecrets = resolvedSecrets,
                            streamEvents = resp.streamEvents,
                            assembledText = resp.assembledText ?: tab.liveStreamText.ifBlank { null },
                            tokenClient = tokenClient,
                            clientFactory = clientFactory,
                            grpcClientFactory = grpcClientFactory,
                            secretResolutionService = secretResolutionService,
                            processEnvProvider = processEnvProvider,
                        )
                    }
                    is NetworkEvent.Failure -> {
                        tab.lastError = event.error.message ?: "Unknown error"
                        state.logNetworkEvent("✗ ${event.error.message}", LogLevel.ERROR)
                    }
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
                tab.isLoading = false
                tab.currentJob = null
            }
        } finally {
            runCatching { client?.close() }
            subReqHolder.close()
        }
    }
    tab.currentJob = job
}
