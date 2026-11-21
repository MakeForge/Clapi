package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.ResponseMetrics
import io.github.crustack.clapi.core.network.NoOpNetworkLogger
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.scripting.ClapiScriptEngine
import io.github.crustack.clapi.core.scripting.ScriptContext
import io.github.crustack.clapi.ui.shared.components.resolveGrpcCallRequest
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.TestResultEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun sendGrpcRequestInternal(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    chainDepth: Int,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
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

        textsToScan.add(tab.authOAuth2AccessTokenUrl)
        textsToScan.add(tab.authOAuth2RefreshTokenUrl)
        textsToScan.add(tab.authOAuth2ClientId)
        textsToScan.add(tab.authOAuth2ClientSecret)
        textsToScan.add(tab.authOAuth2Username)
        textsToScan.add(tab.authOAuth2Password)
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
        val baseVariableLayers = if (resolvedSecrets.isNotEmpty()) {
            listOf(resolvedSecrets) + state.activeVariableLayers(tab)
        } else {
            state.activeVariableLayers(tab)
        }

        val resolvedDynamicVars = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = baseVariableLayers,
            tokenClient = tokenClient,
        ) ?: return@launch

        val mergedScopedVars = if (resolvedSecrets.isNotEmpty() || resolvedDynamicVars.isNotEmpty()) {
            requestScopedScriptVars + resolvedSecrets + resolvedDynamicVars
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
                    val label = if (ancestor.isCollection) "Collection '${ancestor.name}'" else "Folder '${ancestor.name}'"
                    testScriptChain.add(label to s)
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

