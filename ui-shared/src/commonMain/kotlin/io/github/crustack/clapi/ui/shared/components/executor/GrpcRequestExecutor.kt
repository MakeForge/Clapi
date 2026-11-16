package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.ResponseMetrics
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkLogger
import io.github.crustack.clapi.core.network.NoOpNetworkLogger
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.getEnvironmentVariable
import io.github.crustack.clapi.core.network.grpc.DynamicGrpcClient
import io.github.crustack.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.core.scripting.ClapiScriptEngine
import io.github.crustack.clapi.core.scripting.ScriptContext
import io.github.crustack.clapi.ui.shared.components.resolveGrpcCallRequest
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.state.AppSettings
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun sendGrpcRequestInternal(
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
        val subReqHolder = LazySubRequestClientHolder(state.settings, clientFactory)
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

        var effectiveUrl = tab.url.trim()
        val effectiveMetadata = tab.grpcMetadata
            .filter { it.enabled && it.key.isNotBlank() }
            .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
            .toMutableList()
        var effectiveMessageBody = tab.grpcMessageBody
        val requestScopedScriptVars = mutableMapOf<String, String>()

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
                    method = tab.grpcMethod.orEmpty(),
                    variables = flatVars,
                    globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
                    collectionVariables = state.collectionVariablesFor(tab),
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
                    state.mergeCollectionScriptVariables(preResult.newCollectionVariables, tab)
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

        val textsToScan = buildList {
            add(effectiveUrl)
            add(effectiveMessageBody)
            effectiveMetadata.forEach {
                add(it.key)
                add(it.value)
            }
            tab.protoFilePath?.let { add(it) }
            tab.grpcMethod?.let { add(it) }
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

        val baseVariableLayers = buildList {
            if (requestScopedScriptVars.isNotEmpty()) add(requestScopedScriptVars.toMap())
            if (resolvedSecrets.isNotEmpty()) add(resolvedSecrets)
            addAll(state.activeVariableLayers(tab))
        }

        val resolvedDynamicVars = resolveDynamicVariablesForTab(
            state = state,
            tab = tab,
            textsToScan = textsToScan,
            variableLayers = baseVariableLayers,
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

        // Layer 0 priority: requestScopedScriptVars overrides resolvedDynamicVars and resolvedSecrets
        val mergedScopedVars = buildMap {
            putAll(resolvedSecrets)
            putAll(resolvedDynamicVars)
            putAll(requestScopedScriptVars)
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

        val grpcClient = grpcClientFactory(state.settings)
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

            val testScriptChain = buildPostScriptChain(state, tab)
            executePostRequestScriptChain(
                scope = scope,
                state = state,
                tab = tab,
                chainDepth = chainDepth,
                scriptEngine = scriptEngine,
                testScriptChain = testScriptChain,
                effectiveUrl = effectiveUrl,
                effectiveMethodName = grpcCallRequest.fullMethodName,
                statusCode = responseDef.statusCode,
                responseBody = responseDef.bodyText,
                responseHeaders = responseDef.headers.associate { it.key to it.value },
                responseTimeMs = responseDef.metrics.responseTimeMs,
                requestHeaders = grpcCallRequest.metadata.toMap(),
                requestQueryParams = emptyMap(),
                requestBody = grpcCallRequest.jsonPayload,
                requestScopedScriptVars = requestScopedScriptVars,
                resolvedSecrets = resolvedSecrets,
                tokenClient = tokenClient,
                clientFactory = clientFactory,
                grpcClientFactory = grpcClientFactory,
                secretResolutionService = secretResolutionService,
                processEnvProvider = processEnvProvider,
            )
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
        } finally {
            subReqHolder.close()
        }
    }
    tab.currentJob = job
}
