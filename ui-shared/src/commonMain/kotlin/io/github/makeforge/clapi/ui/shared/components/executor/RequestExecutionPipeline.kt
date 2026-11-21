package io.github.makeforge.clapi.ui.shared.components.executor

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.NetworkLogger
import io.github.makeforge.clapi.core.network.NoOpNetworkLogger
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.getEnvironmentVariable
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.vault.SecretResolutionService
import io.github.makeforge.clapi.core.scripting.ClapiScriptEngine
import io.github.makeforge.clapi.core.scripting.ScriptContext
import io.github.makeforge.clapi.ui.shared.network.NetworkClientFactory
import io.github.makeforge.clapi.ui.shared.state.AppSettings
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.LogLevel
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.state.TestResultEntry
import kotlinx.coroutines.CoroutineScope

/**
 * Lazily instantiates an [ApiClient] for `clapi.sendRequest()` only when a script actually
 * invokes a sub-request, and guarantees it is closed when the request lifecycle completes.
 */
internal class LazySubRequestClientHolder(
    private val settings: AppSettings,
    private val clientFactory: (AppSettings, NetworkLogger, RetryPolicy) -> ApiClient,
) : AutoCloseable {
    var instance: ApiClient? = null
        private set

    fun getOrCreate(): ApiClient {
        val current = instance
        if (current != null) return current
        val created = clientFactory(settings, NoOpNetworkLogger, RetryPolicy(maxAttempts = 1))
        instance = created
        return created
    }

    override fun close() {
        runCatching { instance?.close() }
        instance = null
    }
}

/**
 * Builds the [RetryPolicy] configured on [tab].
 * Shared across HTTP and GraphQL request executors so retry behavior is identical.
 */
internal fun buildRetryPolicyForTab(tab: RequestTabState): RetryPolicy {
    return if (tab.retryEnabled) {
        RetryPolicy(
            maxAttempts = tab.retryCount.coerceAtLeast(1),
            baseDelayMs = tab.retryDelayMs.coerceAtLeast(0L),
            maxDelayMs = (tab.retryDelayMs.coerceAtLeast(0L) * 10L)
                .coerceAtLeast(tab.retryDelayMs),
        )
    } else {
        RetryPolicy(
            maxAttempts = 1,
            baseDelayMs = 0L,
            maxDelayMs = 0L,
        )
    }
}

/**
 * Builds the root-to-leaf pre-request script chain (`Collection -> Ancestor Folders -> Request`).
 */
internal fun buildPreScriptChain(
    state: AppState,
    tab: RequestTabState,
): List<Pair<String, String>> {
    val ancestorFolders = state.getAncestorFolders(tab)
    val chain = mutableListOf<Pair<String, String>>()
    for (ancestor in ancestorFolders) {
        val s = ancestor.preRequestScript
        if (!s.isNullOrBlank()) {
            val label = if (ancestor.isCollection) "Collection '${ancestor.name}'" else "Folder '${ancestor.name}'"
            chain.add(label to s)
        }
    }
    if (tab.preRequestScript.isNotBlank()) {
        chain.add("Request '${tab.name}'" to tab.preRequestScript)
    }
    return chain
}

/**
 * Builds the leaf-to-root post-request test script chain (`Request -> Ancestor Folders -> Collection`).
 */
internal fun buildPostScriptChain(
    state: AppState,
    tab: RequestTabState,
): List<Pair<String, String>> {
    val ancestorFolders = state.getAncestorFolders(tab)
    val chain = mutableListOf<Pair<String, String>>()
    if (tab.testScript.isNotBlank()) {
        chain.add("Request '${tab.name}'" to tab.testScript)
    }
    for (ancestor in ancestorFolders.asReversed()) {
        val s = ancestor.testScript
        if (!s.isNullOrBlank()) {
            val label = if (ancestor.isCollection) "Collection '${ancestor.name}'" else "Folder '${ancestor.name}'"
            chain.add(label to s)
        }
    }
    return chain
}

/**
 * Collects all authentication parameter strings from [tab] and any inherited folder/collection auth
 * so secret/dynamic-variable scanners include auth placeholders.
 */
internal fun collectAuthTextsToScan(
    state: AppState,
    tab: RequestTabState,
): List<String> = buildList {
    add(tab.authUsername)
    add(tab.authPassword)
    add(tab.authToken)
    add(tab.authApiKey)
    add(tab.authApiValue)
    add(tab.authOAuth2ClientId)
    add(tab.authOAuth2ClientSecret)
    add(tab.authOAuth2Username)
    add(tab.authOAuth2Password)
    add(tab.authOAuth2AccessTokenUrl)
    add(tab.authOAuth2RefreshTokenUrl)
    add(tab.authOAuth2AuthorizationUrl)
    add(tab.authOAuth2RedirectUri)
    add(tab.authOAuth2Scope)
    if (tab.authType == AuthType.INHERIT) {
        val resolvedAuth = state.resolveInheritedAuth(tab)
        if (resolvedAuth != null) {
            resolvedAuth.username?.let { add(it) }
            resolvedAuth.password?.let { add(it) }
            resolvedAuth.token?.let { add(it) }
            resolvedAuth.apiKey?.let { add(it) }
            resolvedAuth.apiValue?.let { add(it) }
            resolvedAuth.oauth2ClientId?.let { add(it) }
            resolvedAuth.oauth2ClientSecret?.let { add(it) }
            resolvedAuth.oauth2Username?.let { add(it) }
            resolvedAuth.oauth2Password?.let { add(it) }
            resolvedAuth.oauth2AccessTokenUrl?.let { add(it) }
            resolvedAuth.oauth2RefreshTokenUrl?.let { add(it) }
            resolvedAuth.oauth2AuthorizationUrl?.let { add(it) }
            resolvedAuth.oauth2RedirectUri?.let { add(it) }
            resolvedAuth.oauth2Scope?.let { add(it) }
        }
    }
}

/**
 * Pass 1 secret resolution before pre-request scripts run:
 * Scans [preScriptChain] for references to enabled `secret: true` environment variables or `{{_secret.*}}`
 * placeholders so pre-request scripts see resolved secret values in `ScriptContext.variables` instead of `""`.
 * Returns `null` if secret resolution failed and halted the request.
 */
internal suspend fun resolvePreScriptSecretsForTab(
    state: AppState,
    tab: RequestTabState,
    preScriptChain: List<Pair<String, String>>,
    secretResolutionService: SecretResolutionService = SecretResolutionService(),
    processEnvProvider: (String) -> String? = ::getEnvironmentVariable,
): Map<String, String>? {
    if (preScriptChain.isEmpty()) return emptyMap()
    val secretVarNames = state.selectedEnvironment?.variables
        ?.filter { it.enabled && it.secret && it.key.isNotBlank() }
        ?.map { it.key.trim() }
        .orEmpty()
    val scriptTexts = preScriptChain.map { it.second }
    val syntheticTokens = mutableListOf<String>()
    syntheticTokens.addAll(scriptTexts)
    for (secName in secretVarNames) {
        if (scriptTexts.any { it.contains(secName) }) {
            syntheticTokens.add("{{$secName}}")
        }
    }
    return resolveExternalSecretsForTab(
        state = state,
        tab = tab,
        textsToScan = syntheticTokens,
        secretResolutionService = secretResolutionService,
        processEnvProvider = processEnvProvider,
    )
}

/**
 * Assembles the final ordered variable layers for transport and log resolution in strict precedence order:
 * 1. `requestScopedScriptVars` (`clapi.variables.set`) — Layer 0 (Highest priority)
 * 2. `resolvedDynamicVars` (`{{$...}}`, `{{_auth.*}}`) — Layer 1
 * 3. `resolvedSecrets` (`{{_secret.*}}`, resolved `secret: true` env vars) — Layer 2
 * 4. `state.activeVariableLayers(tab)` (`Environment -> Folders leaf-to-root -> Active Collection -> Global`)
 */
internal fun assembleEffectiveVariableLayers(
    state: AppState,
    tab: RequestTabState,
    requestScopedScriptVars: Map<String, String>,
    resolvedDynamicVars: Map<String, String>,
    resolvedSecrets: Map<String, String>,
): List<Map<String, String>> = buildList {
    if (requestScopedScriptVars.isNotEmpty()) {
        add(requestScopedScriptVars.toMap())
    }
    if (resolvedDynamicVars.isNotEmpty()) {
        add(resolvedDynamicVars)
    }
    if (resolvedSecrets.isNotEmpty()) {
        add(resolvedSecrets)
    }
    addAll(state.activeVariableLayers(tab))
}

/**
 * Executes the leaf-to-root post-request test script chain, merges variable mutations
 * (with collection variables isolated to [tab]'s root collection), and executes `setNextRequest`
 * chaining across HTTP, GraphQL, and gRPC requests up to [MAX_SCRIPT_CHAIN_DEPTH].
 */
internal suspend fun executePostRequestScriptChain(
    scope: CoroutineScope,
    state: AppState,
    tab: RequestTabState,
    chainDepth: Int,
    scriptEngine: ClapiScriptEngine,
    testScriptChain: List<Pair<String, String>>,
    effectiveUrl: String,
    effectiveMethodName: String,
    statusCode: Int,
    responseBody: String,
    responseHeaders: Map<String, String>,
    responseTimeMs: Long,
    requestHeaders: Map<String, String>,
    requestQueryParams: Map<String, String>,
    requestBody: String,
    requestScopedScriptVars: Map<String, String>,
    resolvedSecrets: Map<String, String>,
    streamEvents: List<String> = emptyList(),
    assembledText: String? = null,
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
    for ((source, scriptContent) in testScriptChain) {
        val layers2 = state.activeVariableLayers(tab)
        val flatVars2 = buildMap<String, String> {
            layers2.asReversed().forEach { putAll(it) }
            putAll(resolvedSecrets)
            putAll(requestScopedScriptVars)
        }
        val testCtx = ScriptContext(
            url = effectiveUrl,
            method = effectiveMethodName,
            statusCode = statusCode,
            responseBody = responseBody,
            responseHeaders = responseHeaders,
            responseTimeMs = responseTimeMs,
            variables = flatVars2,
            globalVariables = state.globalVariables.filter { it.enabled }.associate { it.key to it.value },
            collectionVariables = state.collectionVariablesFor(tab),
            requestHeaders = requestHeaders,
            requestQueryParams = requestQueryParams,
            requestBody = requestBody,
            streamEvents = streamEvents,
            assembledText = assembledText,
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
            state.mergeCollectionScriptVariables(testResult.newCollectionVariables, tab)
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
                        preferredCollectionId = tab.collectionId ?: state.resolveRootCollectionNode(tab)?.id,
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
                            sendRequestInternal(
                                scope = scope,
                                state = state,
                                tab = nextTab,
                                chainDepth = chainDepth + 1,
                                tokenClient = tokenClient,
                                clientFactory = clientFactory,
                                grpcClientFactory = grpcClientFactory,
                                secretResolutionService = secretResolutionService,
                                processEnvProvider = processEnvProvider,
                            )
                        }
                    }
                }
            }
        }
    }
}
