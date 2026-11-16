package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.GraphQlBody
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.json.Json5
import io.github.crustack.clapi.core.network.LlmTextAssembler
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.NetworkLogger
import io.github.crustack.clapi.core.network.NoOpNetworkLogger
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.scripting.ClapiScriptEngine
import io.github.crustack.clapi.core.scripting.ScriptContext
import io.github.crustack.clapi.ui.shared.components.encodeOrderedQuery
import io.github.crustack.clapi.ui.shared.components.parseQueryPairs
import io.github.crustack.clapi.ui.shared.components.splitRequestUrl
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.persistence.TabsRepository
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.SystemHeaderRules
import io.github.crustack.clapi.ui.shared.state.TestResultEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun sendGraphQlRequestInternal(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    chainDepth: Int,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
) {
    if (tab.url.isBlank()) {
        state.log("URL is empty", LogLevel.WARNING)
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

        val urlParts = splitRequestUrl(tab.url.trim())
        var effectiveUrl = urlParts.base
        var effectiveMethod = if (tab.graphQlMethod.equals("GET", ignoreCase = true)) HttpMethodType.GET else HttpMethodType.POST
        val effectiveHeaders = enabledHeadersForSend(tab).toMutableList()
        val effectiveQueryParams = enabledQueryParamsForSend(tab).toMutableList()
        var effectiveQuery = tab.effectiveGraphQlQuery
        var effectiveVariables = tab.effectiveGraphQlVariables
        val requestScopedScriptVars = mutableMapOf<String, String>()

        // ── Pre-request script chain (Root-to-Leaf) ───────────────────────
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
                    method = effectiveMethod.name,
                    variables = buildMap {
                        putAll(flatVars)
                        putAll(requestScopedScriptVars)
                    },
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariables.toMap(),
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

        val textsToScan = mutableListOf<String>()
        textsToScan.add(tab.url)
        textsToScan.add(effectiveUrl)
        textsToScan.add(urlParts.query)
        textsToScan.add(effectiveQuery)
        textsToScan.add(effectiveVariables)
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

        // Build GraphQL request body & query params depending on POST vs GET
        val requestBody: RequestBody
        if (effectiveMethod == HttpMethodType.GET) {
            requestBody = RequestBody(type = BodyType.NONE)
            val resolvedQuery = VariableResolver.resolve(effectiveQuery, effectiveVariableLayers)
            effectiveQueryParams.add(KeyValueEntry("query", resolvedQuery))
            if (effectiveVariables.isNotBlank() && effectiveVariables.trim() != "{}") {
                val resolvedVars = VariableResolver.resolve(effectiveVariables, effectiveVariableLayers)
                val wireVars = if (state.settings.allowJson5InJsonBodies) {
                    Json5.toWireJson(resolvedVars).getOrDefault(resolvedVars)
                } else resolvedVars
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
            val request = RequestDefinition(
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

            val logger = object : NetworkLogger {
                override fun debug(message: String) { state.log(message) }
                override fun info(message: String) { state.log(message) }
                override fun error(message: String, throwable: Throwable?) {
                    state.log(message, LogLevel.ERROR)
                }
            }

            val client = NetworkClientFactory.build(
                settings = state.settings,
                logger = logger,
                retryPolicy = RetryPolicy(maxAttempts = 1),
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

                        // ── Post-request test script chain (Leaf-to-Root) ─────
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
                                url = effectiveUrl,
                                method = effectiveMethod.name,
                                statusCode = resp.statusCode,
                                responseBody = resp.bodyText,
                                responseHeaders = resp.headers.associate { it.key to it.value },
                                responseTimeMs = resp.metrics.responseTimeMs,
                                variables = buildMap {
                                    putAll(flatVars2)
                                    putAll(requestScopedScriptVars)
                                },
                                globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                                collectionVariables = state.collectionVariables.toMap(),
                                requestHeaders = effectiveHeaders.associate { it.key to it.value },
                                requestQueryParams = effectiveQueryParams.associate { it.key to it.value },
                                requestBody = effectiveQuery,
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
    }
    tab.currentJob = job
}

/**
 * Saves [tab] to disk via [TabsRepository] and marks it as clean.
 * [onSaved] is invoked on the main thread after the save completes.
 */
