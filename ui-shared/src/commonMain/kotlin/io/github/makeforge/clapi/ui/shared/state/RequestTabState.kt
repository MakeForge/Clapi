package io.github.makeforge.clapi.ui.shared.state

import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyVariant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.WebSocketLogMessage
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.network.ConnectionState
import io.github.makeforge.clapi.core.network.grpc.ProtoMethodInfo
import io.github.makeforge.clapi.editor.core.Json5EditorSupport
import io.github.makeforge.clapi.editor.core.LanguageMode
import io.github.makeforge.clapi.editor.ui.EditorViewModel
import io.github.makeforge.clapi.editor.ui.SyntaxHighlighterRegistry
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis
import io.github.makeforge.clapi.ui.shared.platform.generateUuid
import kotlinx.coroutines.Job

// ── Per-tab state (one per open request tab) ────────────────────

class RequestTabState(
    val id: String = generateUuid(),
    name: String = "Untitled",
    method: HttpMethodType = HttpMethodType.GET,
    url: String = "",
    collectionName: String? = null,
    collectionId: String? = null,
    folderPath: List<String> = emptyList(),
    requestRef: String? = null,
    kind: RequestKind = RequestKind.HTTP,
    collectionSummary: String? = null,
    collectionVersion: String? = null,
) {
    val requestId: String get() = id

    var name     by mutableStateOf(name)
    var requestRef by mutableStateOf(requestRef)
    var method   by mutableStateOf(method)
    var url      by mutableStateOf(url)
    var isDirty  by mutableStateOf(false)
    /** Cheap observable used by autosave; every user-visible mutation advances it. */
    var persistenceRevision by mutableLongStateOf(0L)
        private set
    var lastSavedTimestamp by mutableStateOf<Long?>(null)
    private var savedSnapshot by mutableStateOf("")

    var collectionName by mutableStateOf(collectionName)
    var collectionId by mutableStateOf(collectionId)
    var folderPath by mutableStateOf(folderPath)

    var selectedEditorTab by mutableStateOf(RequestEditorTab.PARAMS)
    var collectionSnapshotProvider by mutableStateOf<(() -> String)?>(null)

    val params  = mutableStateListOf<MutableKeyValue>()
    val variables = mutableStateListOf<MutableKeyValue>()
    val headers = mutableStateListOf(
        MutableKeyValue(SystemHeaderRules.CONTENT_TYPE, "application/json", kind = HeaderKind.SYSTEM, keyLocked = true),
        MutableKeyValue(SystemHeaderRules.ACCEPT, "application/json", kind = HeaderKind.SYSTEM, keyLocked = true),
        MutableKeyValue(SystemHeaderRules.USER_AGENT, "Clapi/1.0", kind = HeaderKind.SYSTEM, keyLocked = true),
    )

    var bodyType    by mutableStateOf(BodyType.NONE)

    /**
     * Remembers the last-selected RAW sub-type (JSON, XML, HTML, JS, Text)
     * so that switching away from RAW and back preserves the user's choice.
     */
    var lastRawSubtype by mutableStateOf(BodyType.JSON)

    /**
     * All body contents keyed by [BodyType].
     * Switching [bodyType] automatically resolves to the correct content.
     */
    val bodyContents = mutableStateMapOf<BodyType, String>()

    /** Content for the currently active body type. Gets/sets [bodyContents]\[[bodyType]]. */
    var bodyContent: String
        get() = bodyContents[bodyType] ?: ""
        set(value) { bodyContents[bodyType] = value }

    /** Structured rows for FORM_DATA body type (key/type/value/description). */
    val formRows = mutableStateListOf<MutableFormDataRow>()
    /** Structured rows for X_WWW_FORM_URLENCODED body type (key/value/description). */
    val urlencodedRows = mutableStateListOf<MutableFormDataRow>()

    var authType by mutableStateOf(AuthType.NONE)
    var authUsername  by mutableStateOf("")
    var authPassword  by mutableStateOf("")
    var authToken     by mutableStateOf("")
    var authApiKey    by mutableStateOf("")
    var authApiValue  by mutableStateOf("")
    var authApiPlacement by mutableStateOf("header")
    var authOAuth2Flow by mutableStateOf(OAuth2Flow.CLIENT_CREDENTIALS)
    var authOAuth2AccessTokenUrl by mutableStateOf("")
    var authOAuth2RefreshTokenUrl by mutableStateOf("")
    var authOAuth2AuthorizationUrl by mutableStateOf("")
    var authOAuth2RedirectUri by mutableStateOf("")
    var authOAuth2Scope by mutableStateOf("")
    var authOAuth2ClientId by mutableStateOf("")
    var authOAuth2ClientSecret by mutableStateOf("")
    var authOAuth2CredentialsPlacement by mutableStateOf(OAuth2CredentialsPlacement.BASIC_AUTH_HEADER)
    var authOAuth2Username by mutableStateOf("")
    var authOAuth2Password by mutableStateOf("")
    var authOAuth2AccessToken by mutableStateOf("")
    var authOAuth2RefreshToken by mutableStateOf("")
    var authOAuth2IdToken by mutableStateOf("")
    var authOAuth2ExpiresAtEpochMs by mutableStateOf<Long?>(null)
    var authOAuth2IsFetching by mutableStateOf(false)
    var authOAuth2FetchError by mutableStateOf<String?>(null)

    var preRequestScript by mutableStateOf("")
    var testScript       by mutableStateOf("")
    var kind by mutableStateOf(kind)
    val isFolderTab: Boolean get() = kind == RequestKind.FOLDER
    val isCollectionTab: Boolean get() = kind == RequestKind.COLLECTION
    var collectionSummary by mutableStateOf(collectionSummary)
    var collectionVersion by mutableStateOf(collectionVersion)
    var storageType by mutableStateOf(StorageType.LOCAL)
    var gitRemoteUrl by mutableStateOf("")
    var gitBranch by mutableStateOf("main")
    var storageError by mutableStateOf<String?>(null)
    var isSavingStorage by mutableStateOf(false)
    val collectionProtoFiles = mutableStateListOf<ProtoFileItemState>()
    val collectionImportPaths = mutableStateListOf<ProtoImportPathState>()
    var mcpConfig by mutableStateOf(McpConnectionConfig())

    // ── gRPC state ──────────────────────────────────────────────────────────
    var protoFilePath by mutableStateOf<String?>(null)
    var protoSyncToGit by mutableStateOf(false)
    var grpcMethod by mutableStateOf<String?>(null)
    var grpcMethodType by mutableStateOf("unary")
    var availableMethods by mutableStateOf<List<ProtoMethodInfo>>(emptyList())
    var protoLoadError by mutableStateOf<String?>(null)
    var isProtoLoading by mutableStateOf(false)
    val grpcMetadata = mutableStateListOf<MutableKeyValue>()
    var selectedGrpcTab by mutableStateOf(GrpcEditorTab.MESSAGE)

    // ── WebSocket state ──────────────────────────────────────────────────────
    var selectedWebSocketTab by mutableStateOf(WebSocketEditorTab.MESSAGE)
    var webSocketTimeout by mutableStateOf<String?>("inherit")
    var webSocketKeepAliveInterval by mutableStateOf<String?>("inherit")
    var webSocketDocs by mutableStateOf("")
    var webSocketConnectionState by mutableStateOf(ConnectionState.DISCONNECTED)
    val webSocketLogMessages = mutableStateListOf<WebSocketLogMessage>()
    val webSocketMessageVariants = mutableStateListOf<WebSocketMessageVariant>()
    var activeWebSocketVariantIndex by mutableStateOf(0)
    var webSocketMessageFormat by mutableStateOf(WebSocketMessageType.TEXT)
    var webSocketOutgoingPayload by mutableStateOf("")

    // ── GraphQL state ────────────────────────────────────────────────────────
    var selectedGraphQLTab by mutableStateOf(GraphQLEditorTab.BODY)
    var graphQlMethod by mutableStateOf("POST")
    var graphQlQuery by mutableStateOf("query {\n  \n}")
    var graphQlVariables by mutableStateOf("{\n  \n}")
    val graphQlVariants = mutableStateListOf<GraphQLBodyVariant>()
    var activeGraphQLVariantIndex by mutableStateOf(0)
    var graphQlDocs by mutableStateOf("")
    var graphQlTimeout by mutableStateOf<String?>("inherit")
    var graphQlEncodeUrl by mutableStateOf<String?>("inherit")
    var graphQlFollowRedirects by mutableStateOf<String?>("inherit")
    var graphQlForwardAuthHeader by mutableStateOf<String?>("inherit")
    var graphQlMaxRedirects by mutableStateOf<String?>("inherit")

    val activeGraphQLVariant: GraphQLBodyVariant?
        get() = graphQlVariants.getOrNull(activeGraphQLVariantIndex)

    var effectiveGraphQlQuery: String
        get() = activeGraphQLVariant?.body?.query ?: graphQlQuery
        set(value) {
            val variant = activeGraphQLVariant
            if (variant != null && activeGraphQLVariantIndex in graphQlVariants.indices) {
                graphQlVariants[activeGraphQLVariantIndex] = variant.copy(
                    body = variant.body.copy(query = value)
                )
            } else {
                graphQlQuery = value
            }
        }

    var effectiveGraphQlVariables: String
        get() = activeGraphQLVariant?.body?.variables ?: graphQlVariables
        set(value) {
            val variant = activeGraphQLVariant
            if (variant != null && activeGraphQLVariantIndex in graphQlVariants.indices) {
                graphQlVariants[activeGraphQLVariantIndex] = variant.copy(
                    body = variant.body.copy(variables = value)
                )
            } else {
                graphQlVariables = value
            }
        }

    val activeWebSocketVariant: WebSocketMessageVariant?
        get() = webSocketMessageVariants.getOrNull(activeWebSocketVariantIndex)

    var webSocketOutgoingMessage: String
        get() = activeWebSocketVariant?.message?.data ?: webSocketOutgoingPayload
        set(value) {
            val variant = activeWebSocketVariant
            if (variant != null && activeWebSocketVariantIndex in webSocketMessageVariants.indices) {
                webSocketMessageVariants[activeWebSocketVariantIndex] = variant.copy(
                    message = variant.message.copy(data = value)
                )
            } else {
                webSocketOutgoingPayload = value
            }
        }

    var effectiveWebSocketFormat: WebSocketMessageType
        get() = activeWebSocketVariant?.message?.type ?: webSocketMessageFormat
        set(value) {
            val variant = activeWebSocketVariant
            if (variant != null && activeWebSocketVariantIndex in webSocketMessageVariants.indices) {
                webSocketMessageVariants[activeWebSocketVariantIndex] = variant.copy(
                    message = variant.message.copy(type = value)
                )
            } else {
                webSocketMessageFormat = value
            }
        }

    /** JSON message payload for gRPC requests, backed by bodyContents[BodyType.JSON]. */
    var grpcMessageBody: String
        get() = bodyContents[BodyType.JSON] ?: ""
        set(value) {
            bodyType = BodyType.JSON
            bodyContents[BodyType.JSON] = value
        }

    var retryEnabled by mutableStateOf(false)
    var retryCount by mutableStateOf(1)
    var retryDelayMs by mutableStateOf(250L)

    /**
     * Tracks the set of variable keys injected by the pre-request script.
     * Cleared and repopulated on every send so stale script-set variables
     * are removed before the script re-executes (fixes M-8).
     */
    val scriptInjectedVarKeys: MutableSet<String> = mutableSetOf()

    /**
     * The coroutine Job for the currently in-flight HTTP request.
     * Cancelling this job aborts the request (fixes H-1 race condition).
     */
    var currentJob: Job? = null

    // ── Per-tab EditorViewModel cache (survives editor-tab switches) ─────────
    // Keyed by BodyType so JSON/XML/etc. each keep independent undo stacks.
    // Not part of Compose state — not serialized, not tracked for dirty checking.
    private val bodyViewModelCache = HashMap<BodyType, EditorViewModel>()
    private var json5EnabledForCachedJsonVm: Boolean? = null

    fun getOrCreateBodyViewModel(
        bodyType: BodyType,
        initialText: String,
        languageMode: LanguageMode,
        allowJson5: Boolean = true,
    ): EditorViewModel {
        if (!SyntaxHighlighterRegistry.hasHighlighter(LanguageMode.PLAIN_TEXT)) {
            SyntaxHighlighterRegistry.registerBuiltinHighlighters()
        }
        if (languageMode == LanguageMode.JSON) {
            val cached = bodyViewModelCache[bodyType]
            if (cached != null && json5EnabledForCachedJsonVm != null && json5EnabledForCachedJsonVm != allowJson5) {
                cached.dispose()
                bodyViewModelCache.remove(bodyType)
            }
            json5EnabledForCachedJsonVm = allowJson5
        }
        return bodyViewModelCache.getOrPut(bodyType) {
            val provider = if (allowJson5 && languageMode == LanguageMode.JSON) Json5EditorSupport else null
            EditorViewModel(initialText, languageMode, provider)
        }
    }

    /** Disposes all cached body EditorViewModels. Call before removing this tab. */
    fun disposeBodyViewModels() {
        bodyViewModelCache.values.forEach { it.dispose() }
        bodyViewModelCache.clear()
    }

    // ── URL undo stack (per-tab, plain strings, no Compose state) ───────────
    val urlUndoStack: ArrayDeque<String> = ArrayDeque(32)

    // response associated with this tab
    var response    by mutableStateOf<ResponseDefinition?>(null)
    var responseTab by mutableStateOf(ResponseTab.BODY)
    var isLoading   by mutableStateOf(false)

    // last error message (displayed in response panel when response is null + error)
    var lastError   by mutableStateOf<String?>(null)

    /** Raw SSE/NDJSON payloads collected while a streaming response is in flight. */
    val streamChunks = mutableStateListOf<String>()

    /** Concatenated LLM token text shown live while streaming. */
    var liveStreamText by mutableStateOf("")

    /** Per-tab post-request script assertion results. */
    val testResults = mutableStateListOf<TestResultEntry>()

    fun appendStreamChunk(rawChunk: String) {
        if (streamChunks.size >= MAX_STREAM_CHUNKS) {
            streamChunks.removeRange(0, streamChunks.size - MAX_STREAM_CHUNKS + 1)
        }
        streamChunks.add(rawChunk)
    }

    fun appendWebSocketLogMessage(message: WebSocketLogMessage) {
        if (webSocketLogMessages.size >= MAX_WEBSOCKET_LOGS) {
            webSocketLogMessages.removeRange(0, webSocketLogMessages.size - MAX_WEBSOCKET_LOGS + 1)
        }
        webSocketLogMessages.add(message)
    }

    companion object {
        const val MAX_WEBSOCKET_LOGS = 1_000
        const val MAX_STREAM_CHUNKS = 5_000
    }

    init {
        if (kind == RequestKind.FOLDER || kind == RequestKind.COLLECTION) {
            headers.clear()
            selectedEditorTab = RequestEditorTab.VARIABLES
        } else if (kind == RequestKind.GRPC) {
            headers.clear()
            bodyType = BodyType.JSON
        } else if (kind == RequestKind.WEBSOCKET) {
            headers.clear()
            if (this.url.isBlank()) {
                this.url = "ws://"
            }
        } else if (kind == RequestKind.GRAPHQL) {
            headers.clear()
            if (this.url.isBlank()) {
                this.url = "https://"
            }
        }
        savedSnapshot = currentSnapshotForDirtyTracking()
    }

    private fun totalBodyLength(): Int = bodyContents.values.sumOf { it.length }

    private fun currentSnapshot(): String {
        if (kind == RequestKind.COLLECTION) {
            return listOf(
                name,
                kind.name,
                collectionSummary.orEmpty(),
                collectionVersion.orEmpty(),
                storageType.name,
                gitRemoteUrl,
                gitBranch,
                collectionSnapshotProvider?.invoke().orEmpty(),
            ).joinToString("#")
        }

        val paramsSnapshot = params
            .filter { it.kind != HeaderKind.INHERITED }
            .joinToString(";") { p -> "${p.key}|${p.value}|${p.enabled}|${p.secret}" }
        val variablesSnapshot = variables
            .filter { it.kind != HeaderKind.INHERITED }
            .joinToString(";") { v -> "${v.key}|${v.value}|${v.enabled}|${v.secret}" }
        val headersSnapshot = headers
            .filter { it.kind != HeaderKind.INHERITED }
            .joinToString(";") { h -> "${h.key}|${h.value}|${h.enabled}|${h.secret}|${h.kind}|${h.keyLocked}" }
        val formRowsSnapshot = formRows.joinToString(";") { r -> "${r.key}|${r.type.name}|${r.value}|${r.description}|${r.enabled}|${r.bytesBase64}" }
        val urlencodedRowsSnapshot = urlencodedRows.joinToString(";") { r -> "${r.key}|${r.value}|${r.description}|${r.enabled}" }
        val grpcMetadataSnapshot = grpcMetadata
            .filter { it.kind != HeaderKind.INHERITED }
            .joinToString(";") { m -> "${m.key}|${m.value}|${m.enabled}|${m.secret}" }
        val webSocketVariantsSnapshot = webSocketMessageVariants
            .joinToString(";") { v -> "${v.title}|${v.selected}|${v.message.type.value}|${v.message.data}" }
        val allBodyContentsSnapshot = bodyContents.entries
            .sortedBy { entry -> entry.key.name }
            .joinToString(";") { entry -> "${entry.key.name}=${entry.value}" }
        return listOf(
            name,
            method.name,
            url,
            bodyType.name,
            lastRawSubtype.name,
            allBodyContentsSnapshot,
            authType.name,
            authUsername,
            authPassword,
            authToken,
            authApiKey,
            authApiValue,
            authApiPlacement,
            authOAuth2Flow.name,
            authOAuth2AccessTokenUrl,
            authOAuth2RefreshTokenUrl,
            authOAuth2AuthorizationUrl,
            authOAuth2RedirectUri,
            authOAuth2Scope,
            authOAuth2ClientId,
            authOAuth2ClientSecret,
            authOAuth2CredentialsPlacement.name,
            authOAuth2Username,
            authOAuth2Password,
            preRequestScript,
            testScript,
            retryEnabled.toString(),
            retryCount.toString(),
            retryDelayMs.toString(),
            paramsSnapshot,
            variablesSnapshot,
            headersSnapshot,
            formRowsSnapshot,
            urlencodedRowsSnapshot,
            kind.name,
            mcpClientFingerprint(),
            protoFilePath.orEmpty(),
            protoSyncToGit.toString(),
            grpcMethod.orEmpty(),
            grpcMethodType,
            grpcMetadataSnapshot,
            collectionSummary.orEmpty(),
            collectionVersion.orEmpty(),
            collectionProtoFiles.joinToString(";") { "${it.path}:${it.syncToGit}" },
            collectionImportPaths.joinToString(";") { "${it.path}:${it.enabled}:${it.syncToGit}" },
            webSocketTimeout.orEmpty(),
            webSocketKeepAliveInterval.orEmpty(),
            webSocketDocs,
            webSocketOutgoingPayload,
            webSocketMessageFormat.value,
            webSocketVariantsSnapshot,
            graphQlMethod,
            effectiveGraphQlQuery,
            effectiveGraphQlVariables,
            graphQlVariants.joinToString(";") { "${it.title}|${it.selected}|${it.body.query}|${it.body.variables}" },
            graphQlTimeout.orEmpty(),
            graphQlEncodeUrl.orEmpty(),
            graphQlFollowRedirects.orEmpty(),
            graphQlForwardAuthHeader.orEmpty(),
            graphQlMaxRedirects.orEmpty(),
            graphQlDocs,
        ).joinToString("#")
    }

    /** Compact fingerprint of Client-tab MCP settings for dirty tracking and auto-save. */
    fun mcpClientFingerprint(): String {
        val headers = mcpConfig.headers.joinToString(";") {
            "${it.key}|${it.value}|${it.enabled}|${it.secret}"
        }
        val auth = mcpConfig.auth.params.entries.sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value}" }
        val oauth = mcpConfig.oauth?.let {
            listOf(
                it.authServerUrl, it.clientId, it.clientSecret, it.scopes.joinToString(","),
                it.redirectPort, it.redirectUri, it.useDcr, it.useDiscovery, it.grantType,
                it.accessToken, it.refreshToken, it.tokenType, it.expiresAtEpochMillis, it.resource,
            ).joinToString("|")
        }.orEmpty()
        val args = mcpConfig.args.joinToString("\u001f")
        val env = mcpConfig.env.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
        val roots = mcpConfig.roots.joinToString(";") { "${it.uri}|${it.name.orEmpty()}" }
        return listOf(
            mcpConfig.url,
            mcpConfig.transport.name,
            mcpConfig.httpMode.name,
            mcpConfig.command,
            args,
            env,
            mcpConfig.workingDir.orEmpty(),
            headers,
            mcpConfig.auth.type.name,
            normalizeApiKeyPlacement(mcpConfig.auth.placement ?: mcpConfig.auth.params["placement"]),
            auth,
            oauth,
            mcpConfig.samplingMode.name,
            mcpConfig.samplingForwardUrl.orEmpty(),
            mcpConfig.samplingForwardToken.orEmpty(),
            mcpConfig.samplingMaxTokens?.toString().orEmpty(),
            mcpConfig.autoRespondElicitation.toString(),
            roots,
        ).joinToString("#")
    }

    fun currentSnapshotForPersistence(): String = currentSnapshot()

    fun savedSnapshotForPersistence(): String = savedSnapshot

    internal data class SaveCheckpoint(
        val savedSnapshot: String,
        val isDirty: Boolean,
        val lastSavedTimestamp: Long?,
    )

    internal fun captureSaveCheckpoint(): SaveCheckpoint =
        SaveCheckpoint(savedSnapshot, isDirty, lastSavedTimestamp)

    internal fun restoreSaveCheckpoint(checkpoint: SaveCheckpoint) {
        savedSnapshot = checkpoint.savedSnapshot
        isDirty = checkpoint.isDirty
        lastSavedTimestamp = checkpoint.lastSavedTimestamp
    }

    fun restoreSavedSnapshot(snapshot: String?, legacyDirtyFlag: Boolean = false) {
        savedSnapshot = when {
            snapshot != null -> snapshot
            legacyDirtyFlag -> "__legacy-dirty__"
            else -> currentSnapshotForDirtyTracking()
        }
        recomputeDirty()
    }

    fun recomputeDirty() {
        isDirty = currentSnapshotForDirtyTracking() != savedSnapshot
    }

    private fun currentSnapshotForDirtyTracking(): String {
        val totalBodyLength = totalBodyLength()
        if (totalBodyLength <= 100_000) return currentSnapshot()

        // Lightweight snapshot for huge payloads: avoids allocating multi-MB strings
        // on the UI thread while still tracking meaningful state transitions.
        val bodyLengthFingerprint = bodyContents.entries
            .sortedBy { it.key.name }
            .joinToString(";") { "${it.key.name}:${it.value.length}" }
        val paramsCount = params.size
        val variablesCount = variables.size
        val headersCount = headers.size
        val formCount = formRows.size
        val urlEncodedCount = urlencodedRows.size
        return listOf(
            "LARGE",
            name,
            method.name,
            url,
            bodyType.name,
            lastRawSubtype.name,
            bodyLengthFingerprint,
            authType.name,
            retryEnabled.toString(),
            retryCount.toString(),
            retryDelayMs.toString(),
            paramsCount.toString(),
            variablesCount.toString(),
            headersCount.toString(),
            formCount.toString(),
            urlEncodedCount.toString(),
        ).joinToString("#")
    }

    fun markDirty() {
        persistenceRevision++
        // For large body content (> 100 KB total across all body types),
        // skip the expensive currentSnapshot() call which serialises the
        // full body into a string — this would be O(n) for multi-MB
        // payloads and blocks the UI thread.  In that case, any edit is
        // unconditionally dirty.  For normal-sized content, use exact
        // snapshot comparison to correctly detect reverts to saved state.
        val totalBodyLength = totalBodyLength()
        if (totalBodyLength > 100_000) {
            isDirty = true
        } else {
            recomputeDirty()
        }
    }

    fun markSaved() {
        savedSnapshot = currentSnapshotForDirtyTracking()
        isDirty = false
        lastSavedTimestamp = currentTimeMillis()
    }

    /**
     * After a rename, replaces only the name prefix of [savedSnapshot] so that
     * dirty tracking still reflects unsaved body/URL changes.
     *
     * Without this fix, renaming a tab permanently corrupts [savedSnapshot] with the
     * old name → subsequent [recomputeDirty] always returns `true`, even when the user
     * reverts every other edit back to the original values.
     *
     * [oldName] is the name the tab had immediately before the rename.
     */
    fun reanchorSavedSnapshotAfterRename(oldName: String) {
        // Snapshot format: name#method#url#…  (components joined with '#')
        // We need to replace the leading name component without touching the rest.
        val sep = '#'
        val prefixWithSep = "$oldName$sep"
        savedSnapshot = when {
            savedSnapshot.startsWith(prefixWithSep) ->
                "$name$sep${savedSnapshot.removePrefix(prefixWithSep)}"
            savedSnapshot == oldName ->
                name
            !isDirty ->
                // Edge case: name itself contained '#' so the prefix didn't match.
                // The tab is clean so a full reanchor is safe.
                currentSnapshotForDirtyTracking()
            else ->
                // Dirty tab and couldn't parse. Leave savedSnapshot unchanged.
                // Cmd+S (markSaved) will fully re-anchor on the next explicit save.
                savedSnapshot
        }
        recomputeDirty()
    }

    fun syncSystemHeaders() {
        if (kind == RequestKind.FOLDER || kind == RequestKind.GRPC || kind == RequestKind.COLLECTION || kind == RequestKind.WEBSOCKET || kind == RequestKind.GRAPHQL) return
        // Content-Type always reflects the current body type (derived value).
        upsertSystemHeader(SystemHeaderRules.CONTENT_TYPE, SystemHeaderRules.defaultContentTypeFor(bodyType), overwriteIfExists = true)
        // Accept and User-Agent are only inserted when missing so that user
        // overrides to these values are preserved across body-type changes and
        // request sends.
        upsertSystemHeader(SystemHeaderRules.ACCEPT, "application/json", overwriteIfExists = false)
        upsertSystemHeader(SystemHeaderRules.USER_AGENT, "Clapi/1.0", overwriteIfExists = false)
    }

    private fun upsertSystemHeader(key: String, value: String, overwriteIfExists: Boolean = true) {
        val existing = headers.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        if (existing >= 0) {
            if (overwriteIfExists) {
                headers[existing].key = key
                headers[existing].value = value
            }
            headers[existing].kind = HeaderKind.SYSTEM
            headers[existing].keyLocked = true
            return
        }
        headers.add(
            MutableKeyValue(
                key = key,
                value = value,
                enabled = true,
                kind = HeaderKind.SYSTEM,
                keyLocked = true,
            )
        )
    }
}
