package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.GraphQlBody
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestBody
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.json.Json5
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.LlmTextAssembler
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.NetworkLogger
import io.github.makeforge.clapi.core.network.NoOpNetworkLogger
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.execution.extractTemplateStrings
import io.github.makeforge.clapi.core.network.getEnvironmentVariable
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.core.scripting.ClapiScriptEngine
import io.github.makeforge.clapi.core.scripting.ScriptContext
import io.github.makeforge.clapi.ui.shared.components.encodeOrderedQuery
import io.github.makeforge.clapi.ui.shared.components.parseQueryPairs
import io.github.makeforge.clapi.ui.shared.components.splitRequestUrl
import io.github.makeforge.clapi.ui.shared.network.NetworkClientFactory
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.HeaderKind
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.SystemHeaderRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun sendGraphQlRequestInternal(
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
        tab.response = null
        tab.lastError = null
        tab.streamChunks.clear()
        tab.liveStreamText = ""
        state.testResults.clear()

        val urlParts = splitRequestUrl(tab.url.trim())
        var effectiveUrl = urlParts.base
        var effectiveMethod = if (tab.graphQlMethod.equals("GET", ignoreCase = true)) HttpMethodType.GET else HttpMethodType.POST
        val effectiveHeaders = enabledHeadersForSend(tab).toMutableList()
        val effectiveQueryParams = enabledQueryParamsForSend(tab).toMutableList()
        var effectiveQuery = tab.effectiveGraphQlQuery
        var effectiveVariables = tab.effectiveGraphQlVariables
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
                    url = effectiveUrl,
                    method = effectiveMethod.name,
                    variables = flatVars,
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariablesFor(tab),
                    requestHeaders = effectiveHeaders.associate { it.key to it.value },
                    requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                    requestBody = effectiveQuery,
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
                    effectiveQuery = preResult.requestMutations.body ?: effectiveQuery
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

        // Build GraphQL request body & query params depending on POST vs GET
        // Note: For GET, do NOT pre-resolve variables here because transport (KtorApiClient.buildRequest)
        // resolves queryParams against effectiveVariableLayers once. Pre-resolving here caused double
        // interpolation when a variable value contained literal "{{nested}}" text (BUG-06b).
        val requestBody: RequestBody
        if (effectiveMethod == HttpMethodType.GET) {
            requestBody = RequestBody(type = BodyType.NONE)
            effectiveQueryParams.add(KeyValueEntry("query", effectiveQuery))
            if (effectiveVariables.isNotBlank() && effectiveVariables.trim() != "{}") {
                val wireVars = if (state.settings.allowJson5InJsonBodies && !effectiveVariables.contains("{{")) {
                    Json5.toWireJson(effectiveVariables).getOrDefault(effectiveVariables)
                } else {
                    effectiveVariables
                }
                effectiveQueryParams.add(KeyValueEntry("variables", wireVars))
            }
        } else {
            val hasContentType = effectiveHeaders.any { it.key.equals(SystemHeaderRules.CONTENT_TYPE, ignoreCase = true) }
            if (!hasContentType) {
                effectiveHeaders.add(KeyValueEntry(SystemHeaderRules.CONTENT_TYPE, "application/json"))
            }
            val gqlBody = GraphQlBody(
                query = effectiveQuery,
                variablesJson = effectiveVariables.takeIf { it.isNotBlank() && it.trim() != "{}" },
            )
            requestBody = RequestBody(
                type = BodyType.GRAPHQL,
                graphQl = gqlBody,
            )
        }

        val initialRequest = RequestDefinition(
            id = tab.id,
            name = tab.name,
            method = effectiveMethod,
            url = effectiveUrl,
            queryParams = effectiveQueryParams.filter { it.enabled && it.key.isNotBlank() },
            headers = effectiveHeaders.filter { it.enabled && it.key.isNotBlank() },
            auth = buildAuthConfig(tab, state),
            body = requestBody,
            createdAtEpochMillis = currentTimeMillis(),
            updatedAtEpochMillis = currentTimeMillis(),
        )

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
            val request = initialRequest.copy(auth = buildAuthConfig(tab, state))

            val logger = object : NetworkLogger {
                override fun debug(message: String) { state.log(message) }
                override fun info(message: String) { state.log(message) }
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
                            method = effectiveMethod,
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
                        tab.response = event.response
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
                            requestBody = effectiveQuery,
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
                        val message = event.error.message ?: "Unknown error"
                        tab.lastError = message
                        state.logNetworkEvent("✗ Network error: $message", LogLevel.ERROR)
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val message = e.message ?: "Request failed"
            tab.lastError = message
            state.logNetworkEvent("✗ Error: $message", LogLevel.ERROR)
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
