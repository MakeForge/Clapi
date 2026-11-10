package io.github.crustack.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import io.github.crustack.clapi.core.model.storage.GitCredentialsConfig
import io.github.crustack.clapi.core.model.storage.GitStorageConfig
import io.github.crustack.clapi.core.model.storage.StorageExtension
import io.github.crustack.clapi.core.model.storage.StorageType
import io.github.crustack.clapi.core.storage.git.CollectionGitStatus
import io.github.crustack.clapi.core.storage.git.GitRepositoryManager
import io.github.crustack.clapi.core.storage.git.ProtoPathResolver
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.FormEntryType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.McpConnectionConfig
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.network.grpc.ProtoMethodInfo
import io.github.crustack.clapi.editor.core.Json5EditorSupport
import io.github.crustack.clapi.editor.core.LanguageMode
import io.github.crustack.clapi.editor.ui.EditorViewModel
import io.github.crustack.clapi.editor.ui.SyntaxHighlighterRegistry
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.opencollection.ClientCertificate
import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.crustack.clapi.core.network.keepassxc.DefaultKeePassXcClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import io.github.crustack.clapi.ui.shared.mcp.McpSessionState
import io.github.crustack.clapi.ui.shared.platform.generateUuid
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.components.syncParamsFromUrl
import io.github.crustack.clapi.core.network.ConnectionState
import io.github.crustack.clapi.core.network.websocket.WebSocketClientManager
import io.github.crustack.clapi.core.model.WebSocketLogMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessage
import io.github.crustack.clapi.ui.shared.persistence.ImportExportNaming

import io.github.crustack.clapi.ui.shared.i18n.AppLanguage

// ── Enumerations ────────────────────────────────────────────────

enum class RequestEditorTab(val label: String) {
    PARAMS("Params"), HEADERS("Headers"), BODY("Body"),
    AUTH("Auth"), PRE_REQUEST("Pre-request"), TESTS("Post-request"),
    VARIABLES("Variables")
}

enum class GrpcEditorTab(val label: String) {
    MESSAGE("Message"), HEADERS("Headers"), AUTH("Auth"), SCRIPTS("Scripts")
}

enum class WebSocketEditorTab(val label: String) {
    MESSAGE("Message"),
    HEADERS("Headers"),
    AUTH("Auth"),
    SETTINGS("Settings"),
    SCRIPTS("Scripts"),
    VARIABLES("Variables"),
    DOCS("Docs")
}

enum class ResponseTab(val label: String) {
    BODY("Body"), HEADERS("Headers"), COOKIES("Cookies"), TIMING("Timing"), RAW("Raw")
}

enum class BottomTab(val label: String) {
    CONSOLE("Console"), TEST_RESULTS("Test Results"), LOGS("Logs")
}

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

enum class AppTheme { DARK, LIGHT, SYSTEM }
enum class ResponseLayout { RIGHT, BOTTOM }

/** Top-level workspace mode — determines which main panel is shown. */
enum class WorkspaceMode(val label: String) {
    HTTP("HTTP"),
    REALTIME("Realtime"),
    GRAPHQL("GraphQL"),
}

enum class HeaderKind { SYSTEM, USER, INHERITED }

fun normalizeApiKeyPlacement(value: String?): String =
    if (value.equals("query", ignoreCase = true)) "query" else "header"

// ── Data holders ────────────────────────────────────────────────

data class ConsoleEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = currentTimeMillis(),
)

data class TestResultEntry(val name: String, val passed: Boolean, val message: String = "")

data class ResolvedFolderAuth(
    val folderName: String,
    val authType: AuthType,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val apiKey: String? = null,
    val apiValue: String? = null,
    val apiPlacement: String? = null,
    val oauth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val oauth2AccessTokenUrl: String? = null,
    val oauth2RefreshTokenUrl: String? = null,
    val oauth2AuthorizationUrl: String? = null,
    val oauth2RedirectUri: String? = null,
    val oauth2Scope: String? = null,
    val oauth2ClientId: String? = null,
    val oauth2ClientSecret: String? = null,
    val oauth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
    val oauth2Username: String? = null,
    val oauth2Password: String? = null,
)

data class HistoryItem(
    val requestId: String,
    val method: HttpMethodType,
    val name: String,
    val url: String,
    val timestamp: Long,
    val collectionId: String? = null,
    val folderPath: List<String> = emptyList(),
)

data class CollectionNode(
    val id: String,
    val name: String,
    val isFolder: Boolean = false,
    val method: HttpMethodType? = null,
    val url: String? = null,
    val children: MutableList<CollectionNode> = mutableStateListOf(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    // Request configuration populated from collection import
    val userHeaders: List<Pair<String, String>> = emptyList(),
    /** Authoritative ordered rows. Null means this is a legacy collection. */
    val queryEntries: List<KeyValueEntry>? = null,
    /** Authoritative ordered rows. Null means [userHeaders] is the fallback. */
    val headerEntries: List<KeyValueEntry>? = null,
    val bodyType: BodyType? = null,
    val bodyContent: String? = null,
    /** Per-type raw body contents keyed by [BodyType.name]. */
    val bodyContents: Map<String, String> = emptyMap(),
    /** Structured form-data rows (FORM_DATA body type). */
    val formDataEntries: List<FormDataEntryState> = emptyList(),
    /** Structured urlencoded rows (X_WWW_FORM_URLENCODED body type). */
    val urlencodedEntries: List<FormDataEntryState> = emptyList(),
    val authType: AuthType? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
    val authApiPlacement: String? = null,
    val authOAuth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val authOAuth2AccessTokenUrl: String? = null,
    val authOAuth2RefreshTokenUrl: String? = null,
    val authOAuth2AuthorizationUrl: String? = null,
    val authOAuth2RedirectUri: String? = null,
    val authOAuth2Scope: String? = null,
    val authOAuth2ClientId: String? = null,
    val authOAuth2ClientSecret: String? = null,
    val authOAuth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
    val authOAuth2Username: String? = null,
    val authOAuth2Password: String? = null,
    val requestRef: String? = null,
    val kind: RequestKind = RequestKind.HTTP,
    val mcpConfig: McpConnectionConfig? = null,
    val variableEntries: List<KeyValueEntry>? = null,
    val protoFilePath: String? = null,
    val protoSyncToGit: Boolean = protoFilePath?.let { ProtoPathResolver.isRelativeGitProtoPath(it) } ?: false,
    val grpcMethod: String? = null,
    val grpcMethodType: String? = "unary",
    val summary: String? = null,
    val version: String? = null,
    val protoFiles: List<ProtoFileItemState> = emptyList(),
    val importPaths: List<ProtoImportPathState> = emptyList(),
    val storageExtension: StorageExtension? = null,
    val webSocketVariants: List<WebSocketMessageVariant> = emptyList(),
    val webSocketTimeout: String? = null,
    val webSocketKeepAliveInterval: String? = null,
    val webSocketDocs: String? = null,
    val webSocketMessageFormat: WebSocketMessageType? = null,
    val graphQlVariants: List<io.github.crustack.clapi.core.model.opencollection.GraphQLBodyVariant> = emptyList(),
    val graphQlSettings: io.github.crustack.clapi.core.model.opencollection.GraphQLRequestSettings? = null,
    val graphQlDocs: String? = null,
) {
    val isCollection: Boolean get() = kind == RequestKind.COLLECTION
    val storageType: StorageType get() = storageExtension?.type ?: StorageType.LOCAL
    val gitRemoteUrl: String get() = storageExtension?.git?.remoteUrl.orEmpty()
    val gitBranch: String get() = storageExtension?.git?.branch ?: "main"
}

/**
 * Immutable snapshot of a form-data/urlencoded row — used in [CollectionNode]
 * and import/export serialization.
 */
data class FormDataEntryState(
    val key: String = "",
    val type: FormEntryType = FormEntryType.TEXT,
    val value: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val bytesBase64: String = "",
)

/**
 * Mutable row for structured form-data and x-www-form-urlencoded editors.
 * [type] distinguishes text-value from file-attachment rows.
 */
class MutableFormDataRow(
    key: String = "",
    type: FormEntryType = FormEntryType.TEXT,
    value: String = "",
    description: String = "",
    enabled: Boolean = true,
    bytesBase64: String = "",
    val uid: String = generateUuid(),
) {
    var key         by mutableStateOf(key)
    var type        by mutableStateOf(type)
    var value       by mutableStateOf(value)
    var description by mutableStateOf(description)
    var enabled     by mutableStateOf(enabled)
    var bytesBase64 by mutableStateOf(bytesBase64)
}

/** Mutable key-value pair used in param / header / variable editors. */
class MutableKeyValue(
    key: String = "",
    value: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    kind: HeaderKind = HeaderKind.USER,
    keyLocked: Boolean = false,
    /** Stable unique identifier used as LazyColumn item key (fixes M-7 index-key bug). */
    val uid: String = generateUuid(),
) {
    var key     by mutableStateOf(key)
    var value   by mutableStateOf(value)
    var enabled by mutableStateOf(enabled)
    var secret  by mutableStateOf(secret)
    var kind    by mutableStateOf(kind)
    var keyLocked by mutableStateOf(keyLocked)
}

object SystemHeaderRules {
    const val CONTENT_TYPE = "Content-Type"
    const val ACCEPT = "Accept"
    const val USER_AGENT = "User-Agent"

    val nonDeletableKeys: Set<String> = setOf(CONTENT_TYPE, ACCEPT, USER_AGENT)

    fun isSystemHeader(key: String): Boolean = key in nonDeletableKeys

    fun defaultContentTypeFor(bodyType: BodyType): String = when (bodyType) {
        BodyType.JSON       -> "application/json"
        BodyType.XML        -> "application/xml"
        BodyType.HTML       -> "text/html"
        BodyType.JAVASCRIPT -> "application/javascript"
        BodyType.FORM_DATA  -> "multipart/form-data"
        BodyType.X_WWW_FORM_URLENCODED -> "application/x-www-form-urlencoded"
        BodyType.GRAPHQL    -> "application/json"
        BodyType.RAW_TEXT   -> "text/plain"
        BodyType.BINARY     -> "application/octet-stream"
        BodyType.NONE       -> "application/json"
    }
}

const val SSE_ACCEPT_MEDIA_TYPE = "text/event-stream"

fun isSseAccept(key: String, value: String, enabled: Boolean = true): Boolean =
    enabled &&
        key.equals(SystemHeaderRules.ACCEPT, ignoreCase = true) &&
        value.contains(SSE_ACCEPT_MEDIA_TYPE, ignoreCase = true)

fun RequestTabState.hasSseAccept(): Boolean =
    headers.any { isSseAccept(it.key, it.value, it.enabled) }

fun CollectionNode.hasSseAccept(): Boolean =
    userHeaders.any { isSseAccept(it.first, it.second) }

// ── Environment model ───────────────────────────────────────────

class MutableExternalSecret(
    name: String = "",
    path: String = "",
    description: String = "",
    enabled: Boolean = true,
    url: String = "",
    targetField: String = "password",
    customFilters: Map<String, String> = emptyMap(),
) {
    var name by mutableStateOf(name)
    var path by mutableStateOf(path)
    var description by mutableStateOf(description)
    var enabled by mutableStateOf(enabled)
    var url by mutableStateOf(url)
    var targetField by mutableStateOf(targetField)
    val customFilters = mutableStateMapOf<String, String>().also { it.putAll(customFilters) }
    val customFilterList = mutableStateListOf<MutableKeyValue>().also { list ->
        customFilters.forEach { (k, v) -> list.add(MutableKeyValue(key = k, value = v)) }
    }

    fun syncCustomFiltersFromList() {
        customFilters.clear()
        customFilterList.forEach { kv ->
            if (kv.key.isNotBlank()) {
                customFilters[kv.key] = kv.value
            }
        }
    }
}

class ProtoFileItemState(
    path: String,
    syncToGit: Boolean = ProtoPathResolver.isRelativeGitProtoPath(path),
) {
    var path by mutableStateOf(path)
    var syncToGit by mutableStateOf(syncToGit)
    val fileName: String get() = path.substringAfterLast(java.io.File.separatorChar).substringAfterLast('/')
    val directory: String get() = path.substringBeforeLast(java.io.File.separatorChar, "").substringBeforeLast('/', "")
}

class ProtoImportPathState(
    path: String,
    enabled: Boolean = true,
    syncToGit: Boolean = ProtoPathResolver.isRelativeGitProtoPath(path),
) {
    var path by mutableStateOf(path)
    var enabled by mutableStateOf(enabled)
    var syncToGit by mutableStateOf(syncToGit)
    val fileName: String get() = path.substringAfterLast(java.io.File.separatorChar).substringAfterLast('/')
    val directory: String get() = path.substringBeforeLast(java.io.File.separatorChar, "").substringBeforeLast('/', "")
}

class EnvState(
    name: String,
    variables: List<MutableKeyValue> = emptyList(),
    var color: String? = null,
    var description: Description? = null,
    var clientCertificates: List<ClientCertificate> = emptyList(),
    var extends: String? = null,
    var dotEnvFilePath: String? = null,
    externalSecretsProvider: String = "hashicorp-vault-server",
    externalSecrets: List<MutableExternalSecret> = emptyList(),
) {
    var name by mutableStateOf(name)
    val variables = mutableStateListOf<MutableKeyValue>().also { it.addAll(variables) }
    var externalSecretsProvider by mutableStateOf(externalSecretsProvider)
    val externalSecrets = mutableStateListOf<MutableExternalSecret>().also { it.addAll(externalSecrets) }

    /** Produce a flat variable map for resolution (e.g. {{baseUrl}} → value). */
    fun toVariableMap(): Map<String, String> =
        variables.filter { it.enabled }.associate { it.key to it.value }
}

// ── Settings model ──────────────────────────────────────────────

const val DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP = 720f
const val DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP = 560f

class AppSettings {
    // General
    var autoSaveRequests     by mutableStateOf(false)
    var confirmBeforeDelete  by mutableStateOf(true)
    var defaultTimeoutSec    by mutableStateOf(30)
    var responseLayout       by mutableStateOf(ResponseLayout.RIGHT)

    // Theme
    var theme by mutableStateOf(AppTheme.DARK)

    // Language (i18n)
    var language by mutableStateOf(AppLanguage.EN)

    // Network
    var requestTimeoutSec    by mutableStateOf(30)
    var followRedirects      by mutableStateOf(true)

    // Sidebar
    var collectionsExpanded  by mutableStateOf(false)
    var environmentsExpanded by mutableStateOf(false)

    // Proxy
    var httpProxy            by mutableStateOf("")
    var httpsProxy           by mutableStateOf("")
    var proxyEnabled         by mutableStateOf(false)

    // Scripting
    /** Namespace prefix for scripts (default "clapi"). Change to e.g. "api". */
    var scriptPrefix         by mutableStateOf("clapi")

    // Environment
    /** Name of the last selected environment; restored on app launch. Empty = first env. */
    var selectedEnvName      by mutableStateOf("")

    /** When true (default), JSON bodies accept JSON5; Send converts to strict JSON. */
    var allowJson5InJsonBodies by mutableStateOf(true)

    /** Shows the compact line and column indicator in request and response editors. */
    var showEditorPositionIndicator by mutableStateOf(false)

    /** Preferred manual size for the environment editor. Viewport clamping is not persisted. */
    var environmentDialogWidthDp by mutableStateOf(DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP)
    var environmentDialogHeightDp by mutableStateOf(DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP)

    // Secrets Manager (HashiCorp Vault)
    var vaultProviderType    by mutableStateOf("hashicorp-vault-server")
    var vaultServerUrl       by mutableStateOf("")
    var vaultMountPath       by mutableStateOf("secret")
    var vaultNamespace       by mutableStateOf("")
    var vaultToken           by mutableStateOf("")
}

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
        if (kind == RequestKind.FOLDER || kind == RequestKind.GRPC || kind == RequestKind.COLLECTION) return
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

// ── Global application state ────────────────────────────────────

class AppState(openDefaultTab: Boolean = true, withDemoData: Boolean = false) {
    val dynamicVariableRegistry: DynamicVariableRegistry = DynamicVariableRegistry()

    // ── workspace mode ──
    var workspaceMode by mutableStateOf(WorkspaceMode.HTTP)

    // ── sidebar ────────
    var sidebarExpanded      by mutableStateOf(true)
    var sidebarWidth         by mutableStateOf(260f)
    var historyExpanded      by mutableStateOf(false)
    var collectionsExpanded  by mutableStateOf(withDemoData)
    var environmentsExpanded by mutableStateOf(withDemoData)
    var sidebarSearchQuery   by mutableStateOf("")
    var selectedCollectionId by mutableStateOf<String?>(null)
    var selectedRequestId    by mutableStateOf<String?>(null)
    var sidebarScrollToRequestId by mutableStateOf<String?>(null)
    var collectionsRevision  by mutableStateOf(0)
    var historyRevision      by mutableStateOf(0)

    val selectedFolderIds = mutableStateListOf<String>()
    val selectedRequestIds = mutableStateListOf<String>()
    var lastSelectedSidebarItemId by mutableStateOf<String?>(null)

    fun clearSidebarSelection() {
        selectedFolderIds.clear()
        selectedRequestIds.clear()
        lastSelectedSidebarItemId = null
    }

    fun selectSidebarFolder(
        folderId: String,
        isCtrlPressed: Boolean = false,
        isShiftPressed: Boolean = false,
        visibleFolders: List<String> = emptyList(),
    ) {
        selectedRequestIds.clear()
        if (isCtrlPressed) {
            if (folderId in selectedFolderIds) {
                selectedFolderIds.remove(folderId)
                if (lastSelectedSidebarItemId == folderId) {
                    lastSelectedSidebarItemId = selectedFolderIds.lastOrNull()
                }
            } else {
                selectedFolderIds.add(folderId)
                lastSelectedSidebarItemId = folderId
            }
        } else if (isShiftPressed) {
            val anchor = lastSelectedSidebarItemId
            val anchorIdx = if (anchor != null) visibleFolders.indexOf(anchor) else -1
            val targetIdx = visibleFolders.indexOf(folderId)
            if (anchorIdx >= 0 && targetIdx >= 0) {
                val range = minOf(anchorIdx, targetIdx)..maxOf(anchorIdx, targetIdx)
                val toSelect = visibleFolders.slice(range)
                selectedFolderIds.clear()
                selectedFolderIds.addAll(toSelect)
            } else {
                selectedFolderIds.clear()
                selectedFolderIds.add(folderId)
                lastSelectedSidebarItemId = folderId
            }
        } else {
            selectedFolderIds.clear()
            selectedFolderIds.add(folderId)
            lastSelectedSidebarItemId = folderId
        }
    }

    fun selectSidebarRequest(
        requestId: String,
        isCtrlPressed: Boolean = false,
        isShiftPressed: Boolean = false,
        visibleRequests: List<String> = emptyList(),
    ) {
        selectedFolderIds.clear()
        if (isCtrlPressed) {
            if (requestId in selectedRequestIds) {
                selectedRequestIds.remove(requestId)
                if (lastSelectedSidebarItemId == requestId) {
                    lastSelectedSidebarItemId = selectedRequestIds.lastOrNull()
                }
            } else {
                selectedRequestIds.add(requestId)
                lastSelectedSidebarItemId = requestId
            }
        } else if (isShiftPressed) {
            val anchor = lastSelectedSidebarItemId
            val anchorIdx = if (anchor != null) visibleRequests.indexOf(anchor) else -1
            val targetIdx = visibleRequests.indexOf(requestId)
            if (anchorIdx >= 0 && targetIdx >= 0) {
                val range = minOf(anchorIdx, targetIdx)..maxOf(anchorIdx, targetIdx)
                val toSelect = visibleRequests.slice(range)
                selectedRequestIds.clear()
                selectedRequestIds.addAll(toSelect)
            } else {
                selectedRequestIds.clear()
                selectedRequestIds.add(requestId)
                lastSelectedSidebarItemId = requestId
            }
        } else {
            selectedRequestIds.clear()
            selectedRequestIds.add(requestId)
            lastSelectedSidebarItemId = requestId
        }
    }

    /** Per-folder expanded state. Absent key → expanded (true) by default. */
    val collectionExpandedState = mutableStateMapOf<String, Boolean>()

    // ── split fractions ─
    var requestResponseSplit by mutableStateOf(0.50f)
    var mainVerticalSplit    by mutableStateOf(0.73f)   // main area / bottom panel

    // ── tabs ───────────
    val openTabs       = mutableStateListOf<RequestTabState>().also {
        if (openDefaultTab) it.add(RequestTabState())
    }
    var activeTabIndex by mutableStateOf(if (openDefaultTab) 0 else -1)
    val activeTab: RequestTabState? get() = openTabs.getOrNull(activeTabIndex)

    // ── MCP sessions ──
    // Long-lived, keyed by tab id so an MCP connection (and its loaded tools/
    // resources) survives tab switches. Disposed only when the tab is closed.
    /** Application-lifetime scope for background work that must outlive individual composables. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mcpSessions = mutableMapOf<String, McpSessionState>()
    private var disposalJob: Job? = null

    /** Returns the persistent MCP session for [tabId], creating it on first use. */
    fun getOrCreateMcpSession(tabId: String): McpSessionState {
        check(disposalJob == null) { "AppState is disposed" }
        return mcpSessions.getOrPut(tabId) {
            McpSessionState(appScope, onConsole = { message, level ->
                logNetworkEvent(message, level, echoToConsole = false)
            })
        }
    }

    /** Disconnects and forgets the MCP session for [tabId] (called on tab close). */
    fun disposeMcpSession(tabId: String) {
        mcpSessions.remove(tabId)?.let { session -> appScope.launch { session.disconnect() } }
    }

    /**
     * Releases editor caches, active requests, MCP transports, and application work.
     * Idempotent: repeated callers receive the same completion [Job].
     */
    fun dispose(): Job {
        disposalJob?.let { return it }
        openTabs.forEach { tab ->
            tab.currentJob?.cancel()
            tab.disposeBodyViewModels()
        }
        val sessions = mcpSessions.values.toList()
        mcpSessions.clear()
        val cleanup = appScope.launch {
            sessions.forEach { session -> runCatching { session.disconnect() } }
        }
        disposalJob = cleanup
        cleanup.invokeOnCompletion { appScope.cancel() }
        return cleanup
    }

    /** Awaitable shutdown path used by desktop exit and lifecycle tests. */
    suspend fun disposeAndAwait() {
        dispose().join()
    }

    // ── bottom panel ──
    var selectedBottomTab    by mutableStateOf(BottomTab.CONSOLE)
    var bottomPanelExpanded  by mutableStateOf(false)
    var bottomPanelHeight    by mutableStateOf(200f)
    val consoleLogs  = mutableStateListOf<ConsoleEntry>()
    /** Structured network-event log shown in the Logs tab (fixes M-3). */
    val networkEventLogs = mutableStateListOf<ConsoleEntry>()
    val testResults  = mutableStateListOf<TestResultEntry>()

    // ── dialogs / overlays ──
    var showSettingsDialog   by mutableStateOf(false)
    var showHelpDialog       by mutableStateOf(false)
    var showEnvEditDialog    by mutableStateOf(false)
    var editingEnvIndex      by mutableStateOf(-1)      // index into environments
    var showImportSpecificationDialog by mutableStateOf(false)
    var showExportCollectionsDialog   by mutableStateOf(false)
    var showGitSyncDialog    by mutableStateOf(false)

    // ── Git synchronization ──
    var gitRepositoryManager: GitRepositoryManager = GitRepositoryManager()
    val gitStatusesByRepoDir = mutableStateMapOf<String, CollectionGitStatus>()

    val activeCollectionNode: CollectionNode?
        get() {
            val tab = activeTab
            if (tab != null) {
                if (tab.isCollectionTab) {
                    collections.firstOrNull { it.id == tab.id || it.id == tab.collectionId }?.let { return it }
                }
                tab.collectionId?.let { cid ->
                    collections.firstOrNull { it.id == cid }?.let { return it }
                }
                val rootId = rootCollectionIdContaining(tab.id)
                if (rootId != null) {
                    collections.firstOrNull { it.id == rootId }?.let { return it }
                }
                tab.collectionName?.let { cname ->
                    collections.firstOrNull { it.name == cname }?.let { return it }
                }
            }
            selectedCollectionId?.let { sid ->
                collections.firstOrNull { it.id == sid }?.let { return it }
                val rootId = rootCollectionIdContaining(sid)
                if (rootId != null) {
                    collections.firstOrNull { it.id == rootId }?.let { return it }
                }
            }
            selectedRequestId?.let { rid ->
                val rootId = rootCollectionIdContaining(rid)
                if (rootId != null) {
                    collections.firstOrNull { it.id == rootId }?.let { return it }
                }
            }
            return collections.firstOrNull { it.storageType == StorageType.GIT } ?: collections.firstOrNull()
        }

    val activeGitCollectionNode: CollectionNode?
        get() {
            val active = activeCollectionNode
            if (active != null && active.storageType == StorageType.GIT && active.gitRemoteUrl.isNotBlank()) {
                return active
            }
            return null
        }

    val activeGitStatus: CollectionGitStatus?
        get() {
            val col = activeGitCollectionNode ?: return null
            val repoDir = gitRepositoryManager.getRepositoryDirectory(col.gitRemoteUrl)
            return gitStatusesByRepoDir[repoDir] ?: CollectionGitStatus(branch = col.gitBranch, isClean = true)
        }

    fun getCollectionGitDirectory(tab: RequestTabState? = activeTab): String? {
        val colNode = if (tab != null) {
            tab.collectionId?.let { cid -> collections.firstOrNull { it.id == cid } }
                ?: rootCollectionIdContaining(tab.id)?.let { rid -> collections.firstOrNull { it.id == rid } }
                ?: tab.collectionName?.let { cname -> collections.firstOrNull { it.name == cname } }
                ?: activeCollectionNode
        } else {
            activeCollectionNode
        }
        if (colNode == null || colNode.storageType != StorageType.GIT || colNode.gitRemoteUrl.isBlank()) {
            return null
        }
        return gitRepositoryManager.getCollectionDirectory(colNode.gitRemoteUrl, colNode.name)
    }

    suspend fun refreshGitStatusForCollection(collection: CollectionNode): CollectionGitStatus? {
        if (collection.storageType != StorageType.GIT || collection.gitRemoteUrl.isBlank()) return null
        val repoDir = gitRepositoryManager.getRepositoryDirectory(collection.gitRemoteUrl)
        val status = runCatching {
            gitRepositoryManager.checkStatus(repoDir)
        }.getOrElse { e ->
            CollectionGitStatus(branch = collection.gitBranch, isClean = false, lastError = e.message)
        }
        val effectiveStatus = if (status.branch.isBlank()) status.copy(branch = collection.gitBranch) else status
        gitStatusesByRepoDir[repoDir] = effectiveStatus
        return effectiveStatus
    }

    fun refreshActiveGitStatusAsync() {
        val col = activeGitCollectionNode ?: return
        appScope.launch {
            refreshGitStatusForCollection(col)
        }
    }

    fun refreshAllGitStatusesAsync() {
        val gitCols = collections.filter { it.storageType == StorageType.GIT && it.gitRemoteUrl.isNotBlank() }
        if (gitCols.isEmpty()) return
        appScope.launch {
            gitCols.distinctBy { it.gitRemoteUrl }.forEach { col ->
                refreshGitStatusForCollection(col)
            }
        }
    }

    // ── import/export operation dialog ──
    var showOperationDialog  by mutableStateOf(false)
    var operationTitle       by mutableStateOf("")
    var operationMessage     by mutableStateOf("")
    private var operationJob: Job? = null

    // ── error dialog ──
    var showErrorDialog      by mutableStateOf(false)
    var errorDialogTitle     by mutableStateOf("Operation failed")
    var errorDialogMessage   by mutableStateOf("")

    // ── confirm-delete dialog ──
    var showConfirmDialog      by mutableStateOf(false)
    var confirmDialogTitle     by mutableStateOf("")
    var confirmDialogMessage   by mutableStateOf("")
    var pendingConfirmAction: (() -> Unit)? = null

    /** Show a [ConfirmDeleteDialog] and run [action] if the user confirms. */
    fun showConfirm(title: String, message: String, action: () -> Unit) {
        confirmDialogTitle   = title
        confirmDialogMessage = message
        pendingConfirmAction = action
        showConfirmDialog    = true
    }
    fun resolveConfirm(confirmed: Boolean) {
        showConfirmDialog = false
        if (confirmed) pendingConfirmAction?.invoke()
        pendingConfirmAction = null
    }

    fun startOperation(title: String, message: String, job: Job) {
        operationTitle = title
        operationMessage = message
        operationJob = job
        showOperationDialog = true
    }

    fun finishOperation() {
        showOperationDialog = false
        operationJob = null
    }

    fun cancelOperation() {
        operationJob?.cancel()
        finishOperation()
        log("Operation canceled", LogLevel.WARNING)
    }

    fun showError(title: String, message: String) {
        errorDialogTitle = title
        errorDialogMessage = message
        showErrorDialog = true
    }

    fun dismissError() {
        showErrorDialog = false
    }

    // ── environment ──
    private var _selectedEnvIndex by mutableStateOf(0)
    var selectedEnvIndex: Int
        get() = _selectedEnvIndex
        set(value) {
            _selectedEnvIndex = value
            val current = environments.getOrNull(value)
            val hasKeePassXc = current?.externalSecretsProvider.equals("keepassxc", ignoreCase = true) &&
                    current?.externalSecrets?.any { it.enabled } == true
            if (!hasKeePassXc) {
                appScope.launch {
                    DefaultKeePassXcClientManager.Shared.onEnvironmentSwitched(false)
                }
            }
        }
    val environments = mutableStateListOf<EnvState>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.environments())
        }
    }

    // ── global variables (lowest priority, overridden by environment + local) ──
    val globalVariables = mutableStateListOf<MutableKeyValue>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.globalVariables())
        }
    }
    val collectionVariables = mutableStateMapOf<String, String>()
    var showGlobalVariablesDialog by mutableStateOf(false)

    // ── collection protobuf ──
    val collectionProtoFiles = mutableStateListOf<ProtoFileItemState>()
    val collectionImportPaths = mutableStateListOf<ProtoImportPathState>()

    val selectedEnvironment: EnvState? get() = environments.getOrNull(selectedEnvIndex)

    /**
     * Variable layers for the active environment (used in request variable resolution).
     * Resolution priority (first match wins): Environment → Folder (leaf-to-root) → Collection → Global.
     * Environment variables override folder, collection, and global variables.
     */
    fun activeVariableLayers(tab: RequestTabState? = activeTab): List<Map<String, String>> {
        val layers = mutableListOf<Map<String, String>>()
        layers.add(selectedEnvironment?.toVariableMap().orEmpty())

        if (tab != null) {
            val ancestors = getAncestorFolders(tab).asReversed()
            for (folder in ancestors) {
                val openFolderTab = openTabs.firstOrNull { it.id == folder.id && it.isFolderTab }
                val fVars = if (openFolderTab != null) {
                    openFolderTab.variables
                        .filter { it.enabled && it.key.isNotBlank() }
                        .associate { it.key to it.value }
                } else {
                    folder.variableEntries
                        ?.filter { it.enabled && it.key.isNotBlank() }
                        ?.associate { it.key to it.value }
                        .orEmpty()
                }
                if (fVars.isNotEmpty()) {
                    layers.add(fVars)
                }
            }
            if (tab.isFolderTab) {
                val tabVars = tab.variables
                    .filter { it.enabled && it.key.isNotBlank() }
                    .associate { it.key to it.value }
                if (tabVars.isNotEmpty()) {
                    layers.add(tabVars)
                }
            }
        }

        if (collectionVariables.isNotEmpty()) {
            layers.add(collectionVariables.toMap())
        }
        val globals = globalVariables.filter { it.enabled }.associate { it.key to it.value }
        if (globals.isNotEmpty()) {
            layers.add(globals)
        }
        return layers
    }

    /**
     * Merges variables set by a pre-request or post-request script into the active environment.
     * Existing variables with the same key are updated; new keys are appended.
     */
    fun mergeScriptVariables(vars: Map<String, String>) {
        val env = selectedEnvironment ?: return
        mergeInto(env.variables, vars)
    }

    fun mergeGlobalScriptVariables(vars: Map<String, String>) {
        mergeInto(globalVariables, vars)
    }

    fun mergeCollectionScriptVariables(vars: Map<String, String>) {
        vars.forEach { (key, value) ->
            collectionVariables[key] = value
        }
    }

    private fun mergeInto(target: MutableList<MutableKeyValue>, vars: Map<String, String>) {
        vars.forEach { (key, value) ->
            val existing = target.firstOrNull { it.key == key }
            if (existing != null) existing.value = value
            else target.add(MutableKeyValue(key = key, value = value))
        }
    }

    // ── settings ────────
    val settings = AppSettings()

    // ── history data ──
    val historyItems = mutableStateListOf<HistoryItem>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.historyItems())
        }
    }

    val collections = mutableStateListOf<CollectionNode>().also {
        if (withDemoData) {
            it.addAll(AppStateDemoData.collections())
        }
    }

    init {
        ensureRequestRefsInitialized()
        syncHistoryWithCollections()
    }

    private data class RequestReference(
        val requestId: String,
        val requestRef: String,
        val collectionId: String?,
        val folderPath: List<String>,
        val name: String,
        val method: HttpMethodType,
        val url: String,
    )

    data class ScriptRequestTarget(
        val requestId: String,
        val name: String,
        val method: HttpMethodType,
        val url: String,
    )

    // ── actions ──────────────────────────────────────────────────

    fun addTab(
        requestId: String = generateUuid(),
        name: String = "Untitled ${openTabs.size + 1}",
        method: HttpMethodType = HttpMethodType.GET,
        url: String = "",
    ) {
        openTabs.indexOfFirst { it.id == requestId }.takeIf { it >= 0 }?.let { existingIdx ->
            activeTabIndex = existingIdx
            selectedRequestId = requestId
            return
        }
        var cName: String? = null
        var cId: String? = null
        val fPath = mutableListOf<String>()
        var found = false
        for (c in collections) {
            if (findPathRecursive(c, requestId, fPath)) {
                cName = c.name
                cId = c.id
                found = true
                break
            }
        }
        val pathWithoutNode = if (found && fPath.isNotEmpty()) fPath.dropLast(1) else emptyList()
        val node = findNodeById(collections, requestId)
        val tab = RequestTabState(
            id = requestId,
            requestRef = node?.requestRef ?: node?.id,
            name = name,
            method = method,
            url = url,
            collectionName = cName,
            collectionId = cId,
            folderPath = pathWithoutNode,
        )
        if (node?.queryEntries != null) {
            tab.params.addAll(node.queryEntries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret) })
        } else if (url.contains('?')) {
            syncParamsFromUrl(tab, url)
        }
        node?.preRequestScript?.takeIf { it.isNotBlank() }?.let { tab.preRequestScript = it }
        node?.testScript?.takeIf { it.isNotBlank() }?.let { tab.testScript = it }
        // Populate body, headers, and auth from collection node
        node?.bodyType?.let { tab.bodyType = it; tab.syncSystemHeaders() }
        node?.bodyContent?.takeIf { it.isNotBlank() }?.let { tab.bodyContent = it }
        node?.bodyContents?.forEach { (typeName, content) ->
            runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { tab.bodyContents[it] = content }
        }
        // Populate structured form rows from collection node
        if (!node?.formDataEntries.isNullOrEmpty()) {
            tab.formRows.clear()
            node!!.formDataEntries.forEach { e ->
                tab.formRows.add(MutableFormDataRow(e.key, e.type, e.value, e.description, e.enabled, e.bytesBase64))
            }
        }
        if (!node?.urlencodedEntries.isNullOrEmpty()) {
            tab.urlencodedRows.clear()
            node!!.urlencodedEntries.forEach { e ->
                tab.urlencodedRows.add(MutableFormDataRow(e.key, e.type, e.value, e.description, e.enabled))
            }
        }
        node?.authType?.let { tab.authType = it }
        node?.authUsername?.takeIf { it.isNotBlank() }?.let { tab.authUsername = it }
        node?.authPassword?.takeIf { it.isNotBlank() }?.let { tab.authPassword = it }
        node?.authToken?.takeIf { it.isNotBlank() }?.let { tab.authToken = it }
        node?.authApiKey?.takeIf { it.isNotBlank() }?.let { tab.authApiKey = it }
        node?.authApiValue?.takeIf { it.isNotBlank() }?.let { tab.authApiValue = it }
        tab.authApiPlacement = normalizeApiKeyPlacement(node?.authApiPlacement)
        node?.authOAuth2Flow?.let { tab.authOAuth2Flow = it }
        node?.authOAuth2AccessTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AccessTokenUrl = it }
        node?.authOAuth2RefreshTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RefreshTokenUrl = it }
        node?.authOAuth2AuthorizationUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AuthorizationUrl = it }
        node?.authOAuth2RedirectUri?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RedirectUri = it }
        node?.authOAuth2Scope?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Scope = it }
        node?.authOAuth2ClientId?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientId = it }
        node?.authOAuth2ClientSecret?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientSecret = it }
        node?.authOAuth2CredentialsPlacement?.let { tab.authOAuth2CredentialsPlacement = it }
        node?.authOAuth2Username?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Username = it }
        node?.authOAuth2Password?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Password = it }
        node?.headerEntries?.let { entries ->
            tab.headers.clear()
            tab.headers.addAll(entries.map { entry ->
                val system = SystemHeaderRules.isSystemHeader(entry.key)
                MutableKeyValue(entry.key, entry.value, entry.enabled, entry.secret,
                    kind = if (system) HeaderKind.SYSTEM else HeaderKind.USER, keyLocked = system)
            })
        } ?: node?.userHeaders?.forEach { (k, v) ->
            val existing = tab.headers.find { h -> h.key.equals(k, ignoreCase = true) }
            if (existing != null) existing.value = v
            else tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
        }
        tab.kind = node?.kind ?: RequestKind.HTTP
        node?.mcpConfig?.let { tab.mcpConfig = it }
        if (tab.kind == RequestKind.MCP && tab.url.isBlank()) {
            tab.url = tab.mcpConfig.url
        }
        if (tab.kind == RequestKind.GRPC) {
            tab.headers.clear()
            tab.bodyType = BodyType.JSON
            tab.protoFilePath = node?.protoFilePath
            tab.protoSyncToGit = node?.protoSyncToGit ?: (node?.protoFilePath?.let { ProtoPathResolver.isRelativeGitProtoPath(it) } ?: false)
            tab.grpcMethod = node?.grpcMethod
            tab.grpcMethodType = node?.grpcMethodType ?: "unary"
            node?.bodyContent?.takeIf { it.isNotBlank() }?.let { tab.grpcMessageBody = it }
            tab.grpcMetadata.clear()
            node?.headerEntries?.let { entries ->
                tab.grpcMetadata.addAll(
                    entries.map { entry ->
                        MutableKeyValue(entry.key, entry.value, entry.enabled, entry.secret, kind = HeaderKind.USER)
                    }
                )
            } ?: node?.userHeaders?.forEach { (k, v) ->
                tab.grpcMetadata.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
            }
        }
        if (tab.kind == RequestKind.WEBSOCKET) {
            tab.headers.clear()
            node?.headerEntries?.let { entries ->
                tab.headers.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret, kind = HeaderKind.USER) })
            } ?: node?.userHeaders?.forEach { (k, v) ->
                tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
            }
            if (!node?.webSocketVariants.isNullOrEmpty()) {
                tab.webSocketMessageVariants.clear()
                tab.webSocketMessageVariants.addAll(node!!.webSocketVariants)
                val sel = tab.webSocketMessageVariants.indexOfFirst { it.selected == true }
                tab.activeWebSocketVariantIndex = if (sel >= 0) sel else 0
            }
            node?.webSocketTimeout?.let { tab.webSocketTimeout = it }
            node?.webSocketKeepAliveInterval?.let { tab.webSocketKeepAliveInterval = it }
            node?.webSocketDocs?.let { tab.webSocketDocs = it }
            node?.webSocketMessageFormat?.let { tab.webSocketMessageFormat = it }
            if (node?.bodyContent != null && tab.webSocketMessageVariants.isEmpty()) {
                tab.webSocketOutgoingPayload = node.bodyContent
            }
        }
        if (tab.kind != RequestKind.FOLDER && tab.kind != RequestKind.GRPC && tab.kind != RequestKind.WEBSOCKET) {
            applyInheritedDefaults(tab)
        }
        // Re-anchor the saved snapshot after all fields (including system headers
        // injected by syncSystemHeaders above) have been populated. Without this,
        // the snapshot captured in RequestTabState.init{} pre-dates the system
        // headers, so recomputeDirty() is always true immediately after opening.
        tab.restoreSavedSnapshot(null)
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
        selectedRequestId = requestId
    }

    /**
     * Opens or focuses a dedicated settings tab for the folder with [folderId].
     */
    fun openFolder(folderId: String) {
        val folderNode = findNodeById(collections, folderId) ?: return
        if (!folderNode.isFolder) return

        val existingIdx = openTabs.indexOfFirst { it.id == folderId && it.kind == RequestKind.FOLDER }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            selectedCollectionId = folderId
            return
        }

        var cName: String? = null
        var cId: String? = null
        val fPath = mutableListOf<String>()
        var found = false
        for (c in collections) {
            if (findPathRecursive(c, folderId, fPath)) {
                cName = c.name
                cId = c.id
                found = true
                break
            }
        }
        val pathWithoutNode = if (found && fPath.isNotEmpty()) fPath.dropLast(1) else emptyList()

        val tab = RequestTabState(
            id = folderId,
            name = folderNode.name,
            kind = RequestKind.FOLDER,
            collectionName = cName,
            collectionId = cId,
            folderPath = pathWithoutNode,
        )
        tab.selectedEditorTab = RequestEditorTab.VARIABLES

        folderNode.variableEntries?.let { entries ->
            tab.variables.clear()
            tab.variables.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret) })
        }
        folderNode.headerEntries?.let { entries ->
            tab.headers.clear()
            tab.headers.addAll(entries.map { MutableKeyValue(it.key, it.value, it.enabled, it.secret, kind = HeaderKind.USER) })
        } ?: folderNode.userHeaders.forEach { (k, v) ->
            tab.headers.add(MutableKeyValue(k, v, kind = HeaderKind.USER))
        }
        folderNode.authType?.let { tab.authType = it }
        folderNode.authUsername?.takeIf { it.isNotBlank() }?.let { tab.authUsername = it }
        folderNode.authPassword?.takeIf { it.isNotBlank() }?.let { tab.authPassword = it }
        folderNode.authToken?.takeIf { it.isNotBlank() }?.let { tab.authToken = it }
        folderNode.authApiKey?.takeIf { it.isNotBlank() }?.let { tab.authApiKey = it }
        folderNode.authApiValue?.takeIf { it.isNotBlank() }?.let { tab.authApiValue = it }
        tab.authApiPlacement = normalizeApiKeyPlacement(folderNode.authApiPlacement)
        folderNode.authOAuth2Flow.let { tab.authOAuth2Flow = it }
        folderNode.authOAuth2AccessTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AccessTokenUrl = it }
        folderNode.authOAuth2RefreshTokenUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RefreshTokenUrl = it }
        folderNode.authOAuth2AuthorizationUrl?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2AuthorizationUrl = it }
        folderNode.authOAuth2RedirectUri?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2RedirectUri = it }
        folderNode.authOAuth2Scope?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Scope = it }
        folderNode.authOAuth2ClientId?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientId = it }
        folderNode.authOAuth2ClientSecret?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2ClientSecret = it }
        folderNode.authOAuth2CredentialsPlacement.let { tab.authOAuth2CredentialsPlacement = it }
        folderNode.authOAuth2Username?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Username = it }
        folderNode.authOAuth2Password?.takeIf { it.isNotBlank() }?.let { tab.authOAuth2Password = it }
        folderNode.preRequestScript?.takeIf { it.isNotBlank() }?.let { tab.preRequestScript = it }
        folderNode.testScript?.takeIf { it.isNotBlank() }?.let { tab.testScript = it }

        applyInheritedDefaults(tab)
        tab.restoreSavedSnapshot(null)
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
        selectedCollectionId = folderId
    }

    fun collectionProtobufSnapshot(): String {
        val protos = collectionProtoFiles.joinToString(";") { "${it.path}:${it.syncToGit}" }
        val imports = collectionImportPaths.joinToString(";") { "${it.path}:${it.enabled}:${it.syncToGit}" }
        return "$protos#$imports"
    }

    /**
     * Opens or focuses a dedicated settings tab for the top-level collection.
     */
    fun openCollection(collectionId: String? = null) {
        val target = if (collectionId != null) {
            collections.firstOrNull { it.id == collectionId }
        } else {
            collections.firstOrNull { it.isCollection } ?: collections.firstOrNull()
        }

        if (target == null) {
            val existingIdx = openTabs.indexOfFirst { it.kind == RequestKind.COLLECTION }
            if (existingIdx >= 0) {
                activeTabIndex = existingIdx
                return
            }
            val fallbackTab = RequestTabState(
                id = "collection_settings",
                name = "Collection",
                kind = RequestKind.COLLECTION,
            )
            fallbackTab.collectionSnapshotProvider = { collectionProtobufSnapshot() }
            fallbackTab.markSaved()
            openTabs.add(fallbackTab)
            activeTabIndex = openTabs.size - 1
            return
        }

        selectedCollectionId = target.id
        val existingIdx = openTabs.indexOfFirst {
            it.kind == RequestKind.COLLECTION && (it.id == target.id || it.collectionId == target.id || it.id == "collection_${target.id}")
        }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            return
        }

        val tab = RequestTabState(
            id = target.id,
            name = target.name,
            kind = RequestKind.COLLECTION,
            collectionId = target.id,
            collectionName = target.name,
            collectionSummary = target.summary,
            collectionVersion = target.version,
        )

        // Populate request defaults
        if (target.variableEntries != null) {
            tab.variables.clear()
            tab.variables.addAll(target.variableEntries.map {
                MutableKeyValue(key = it.key, value = it.value, enabled = it.enabled, secret = it.secret)
            })
        }
        if (target.headerEntries != null) {
            tab.headers.clear()
            tab.headers.addAll(target.headerEntries.map {
                MutableKeyValue(key = it.key, value = it.value, enabled = it.enabled, secret = it.secret, kind = HeaderKind.USER)
            })
        }
        tab.authType = target.authType ?: AuthType.NONE
        tab.authUsername = target.authUsername ?: ""
        tab.authPassword = target.authPassword ?: ""
        tab.authToken = target.authToken ?: ""
        tab.authApiKey = target.authApiKey ?: ""
        tab.authApiValue = target.authApiValue ?: ""
        tab.authApiPlacement = target.authApiPlacement ?: "header"
        tab.authOAuth2Flow = target.authOAuth2Flow
        tab.authOAuth2AccessTokenUrl = target.authOAuth2AccessTokenUrl ?: ""
        tab.authOAuth2RefreshTokenUrl = target.authOAuth2RefreshTokenUrl ?: ""
        tab.authOAuth2AuthorizationUrl = target.authOAuth2AuthorizationUrl ?: ""
        tab.authOAuth2RedirectUri = target.authOAuth2RedirectUri ?: ""
        tab.authOAuth2Scope = target.authOAuth2Scope ?: ""
        tab.authOAuth2ClientId = target.authOAuth2ClientId ?: ""
        tab.authOAuth2ClientSecret = target.authOAuth2ClientSecret ?: ""
        tab.authOAuth2CredentialsPlacement = target.authOAuth2CredentialsPlacement
        tab.authOAuth2Username = target.authOAuth2Username ?: ""
        tab.authOAuth2Password = target.authOAuth2Password ?: ""
        tab.preRequestScript = target.preRequestScript ?: ""
        tab.testScript = target.testScript ?: ""

        // Populate collection-level protobuf
        if (target.protoFiles.isNotEmpty() || collectionProtoFiles.isEmpty()) {
            collectionProtoFiles.clear()
            collectionProtoFiles.addAll(target.protoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
        }
        if (target.importPaths.isNotEmpty() || collectionImportPaths.isEmpty()) {
            collectionImportPaths.clear()
            collectionImportPaths.addAll(target.importPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })
        }
        tab.collectionProtoFiles.clear()
        tab.collectionProtoFiles.addAll(collectionProtoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
        tab.collectionImportPaths.clear()
        tab.collectionImportPaths.addAll(collectionImportPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })

        // Populate storage settings
        tab.storageType = target.storageType
        tab.gitRemoteUrl = target.gitRemoteUrl
        tab.gitBranch = target.gitBranch

        tab.collectionSnapshotProvider = { collectionProtobufSnapshot() }
        tab.markSaved()
        openTabs.add(tab)
        activeTabIndex = openTabs.size - 1
    }

    fun createNewCollection(initialName: String = "New Collection"): CollectionNode {
        val uniqueName = disambiguateCollectionName(initialName)
        val node = CollectionNode(
            id = generateUuid(),
            name = uniqueName,
            isFolder = true,
            kind = RequestKind.COLLECTION,
            children = mutableStateListOf(),
        )
        collections.add(node)
        notifyCollectionsChanged()
        return node
    }

    fun deleteCollection(collectionId: String): CollectionNode? {
        val index = collections.indexOfFirst { it.id == collectionId }
        if (index < 0) return null
        val removed = collections.removeAt(index)
        fun collectChildIds(node: CollectionNode): List<String> {
            val ids = mutableListOf(node.id, "collection_${node.id}")
            node.children.forEach { ids.addAll(collectChildIds(it)) }
            return ids
        }
        closeTabsByIds(collectChildIds(removed))
        notifyCollectionsChanged()
        return removed
    }

    fun disambiguateCollectionName(name: String, excludeCollectionId: String? = null): String {
        val existingNames = collections
            .filter { it.id != excludeCollectionId }
            .map { it.name }
            .toSet()
        return ImportExportNaming.generateUniqueCollectionName(name, existingNames)
    }

    fun renameCollection(collectionId: String, desiredName: String): String {
        val trimmed = desiredName.trim()
        val disambiguated = disambiguateCollectionName(trimmed, collectionId)
        val idx = collections.indexOfFirst { it.id == collectionId }
        if (idx >= 0) {
            val oldName = collections[idx].name
            collections[idx] = collections[idx].copy(name = disambiguated)
            openTabs.filter { (it.id == collectionId || it.collectionId == collectionId) && it.isCollectionTab }.forEach { tab ->
                tab.name = disambiguated
                tab.collectionName = disambiguated
                tab.reanchorSavedSnapshotAfterRename(oldName)
            }
            updateTabsCollectionName(oldName, disambiguated)
            notifyCollectionsChanged()
        }
        return disambiguated
    }

    fun getAncestorFolders(tab: RequestTabState): List<CollectionNode> {
        if (tab.isCollectionTab) return emptyList()
        val chain = mutableListOf<CollectionNode>()
        for (root in collections) {
            if (findAncestorChain(root, tab.id, chain)) {
                return chain
            }
        }
        if (tab.collectionId != null || tab.collectionName != null) {
            val root = collections.firstOrNull { it.id == tab.collectionId }
                ?: collections.firstOrNull { it.name == tab.collectionName }
            if (root != null) {
                val fallbackChain = mutableListOf<CollectionNode>()
                fallbackChain.add(root)
                var cursor: CollectionNode = root
                for (folderName in tab.folderPath) {
                    val next = cursor.children.firstOrNull { it.isFolder && it.name == folderName } ?: break
                    fallbackChain.add(next)
                    cursor = next
                }
                return if (tab.isFolderTab && fallbackChain.lastOrNull()?.id == tab.id) {
                    fallbackChain.dropLast(1)
                } else {
                    fallbackChain
                }
            }
        }
        return emptyList()
    }

    private fun findAncestorChain(
        node: CollectionNode,
        targetId: String,
        currentChain: MutableList<CollectionNode>,
    ): Boolean {
        if (node.id == targetId) {
            return true
        }
        if (node.isFolder) {
            currentChain.add(node)
            for (child in node.children) {
                if (findAncestorChain(child, targetId, currentChain)) {
                    return true
                }
            }
            currentChain.removeAt(currentChain.size - 1)
        }
        return false
    }

    fun resolveInheritedAuth(tab: RequestTabState): ResolvedFolderAuth? {
        val ancestors = getAncestorFolders(tab).asReversed()
        if (ancestors.isEmpty() && tab.collectionName == null && tab.collectionId == null) {
            return null
        }
        for (folder in ancestors) {
            val type = folder.authType
            if (type != null && type != AuthType.NONE && type != AuthType.INHERIT) {
                return ResolvedFolderAuth(
                    folderName = folder.name,
                    authType = type,
                    username = folder.authUsername,
                    password = folder.authPassword,
                    token = folder.authToken,
                    apiKey = folder.authApiKey,
                    apiValue = folder.authApiValue,
                    apiPlacement = folder.authApiPlacement,
                    oauth2Flow = folder.authOAuth2Flow,
                    oauth2AccessTokenUrl = folder.authOAuth2AccessTokenUrl,
                    oauth2RefreshTokenUrl = folder.authOAuth2RefreshTokenUrl,
                    oauth2AuthorizationUrl = folder.authOAuth2AuthorizationUrl,
                    oauth2RedirectUri = folder.authOAuth2RedirectUri,
                    oauth2Scope = folder.authOAuth2Scope,
                    oauth2ClientId = folder.authOAuth2ClientId,
                    oauth2ClientSecret = folder.authOAuth2ClientSecret,
                    oauth2CredentialsPlacement = folder.authOAuth2CredentialsPlacement,
                    oauth2Username = folder.authOAuth2Username,
                    oauth2Password = folder.authOAuth2Password,
                )
            }
        }
        val directParentName = ancestors.firstOrNull()?.name ?: tab.collectionName.orEmpty()
        return ResolvedFolderAuth(
            folderName = directParentName,
            authType = AuthType.NONE,
        )
    }

    fun applyInheritedDefaults(tab: RequestTabState) {
        if (tab.isCollectionTab) return
        val ancestors = getAncestorFolders(tab)
        if (ancestors.isEmpty()) {
            tab.headers.removeAll { it.kind == HeaderKind.INHERITED }
            tab.variables.removeAll { it.kind == HeaderKind.INHERITED }
            tab.params.removeAll { it.kind == HeaderKind.INHERITED }
            return
        }

        // 1. Cascade headers (root-to-leaf: closer ancestor overrides distant ancestor)
        val resolvedHeaders = mutableMapOf<String, KeyValueEntry>()
        for (ancestor in ancestors) {
            val entries = ancestor.headerEntries ?: ancestor.userHeaders.map { KeyValueEntry(it.first, it.second) }
            for (entry in entries) {
                if (entry.enabled && entry.key.isNotBlank()) {
                    resolvedHeaders[entry.key.lowercase()] = entry
                }
            }
        }

        tab.headers.removeAll { it.kind == HeaderKind.INHERITED }

        for (entry in resolvedHeaders.values) {
            val alreadyExists = tab.headers.any { it.key.equals(entry.key, ignoreCase = true) }
            if (!alreadyExists) {
                tab.headers.add(
                    MutableKeyValue(
                        key = entry.key,
                        value = entry.value,
                        enabled = true,
                        kind = HeaderKind.INHERITED,
                        keyLocked = true,
                    )
                )
            }
        }

        // 2. Cascade variables (root-to-leaf: closer ancestor overrides distant ancestor)
        val resolvedVariables = mutableMapOf<String, KeyValueEntry>()
        for (ancestor in ancestors) {
            val entries = ancestor.variableEntries.orEmpty()
            for (entry in entries) {
                if (entry.enabled && entry.key.isNotBlank()) {
                    resolvedVariables[entry.key.lowercase()] = entry
                }
            }
        }

        tab.variables.removeAll { it.kind == HeaderKind.INHERITED }

        for (entry in resolvedVariables.values) {
            val alreadyExists = tab.variables.any { it.key.equals(entry.key, ignoreCase = true) }
            if (!alreadyExists) {
                tab.variables.add(
                    MutableKeyValue(
                        key = entry.key,
                        value = entry.value,
                        enabled = true,
                        secret = entry.secret,
                        kind = HeaderKind.INHERITED,
                        keyLocked = true,
                    )
                )
            }
        }

        // Folder settings no longer define query parameters; ensure inherited query params are cleared.
        tab.params.removeAll { it.kind == HeaderKind.INHERITED }
    }

    fun refreshInheritedDefaultsForOpenTabs() {
        for (openTab in openTabs) {
            if (!openTab.isCollectionTab) {
                applyInheritedDefaults(openTab)
            }
        }
    }

    private fun findPathRecursive(node: CollectionNode, targetId: String, path: MutableList<String>): Boolean {
        if (node.id == targetId) return true
        if (node.isFolder) {
            path.add(node.name)
            for (child in node.children) {
                if (findPathRecursive(child, targetId, path)) return true
            }
            path.removeAt(path.size - 1)
        }
        return false
    }

    private fun findNodeById(nodes: List<CollectionNode>, id: String): CollectionNode? {
        for (node in nodes) {
            if (node.id == id) return node
            if (node.isFolder) findNodeById(node.children, id)?.let { return it }
        }
        return null
    }

    /** Folder anywhere in the tree (root or nested). */
    fun findFolder(id: String): CollectionNode? =
        findNodeById(collections, id)?.takeIf { it.isFolder }

    private fun rootCollectionIdContaining(nodeId: String): String? {
        for (root in collections) {
            if (root.id == nodeId) return root.id
            if (findNodeById(listOf(root), nodeId) != null) return root.id
        }
        return null
    }

    private fun findRequestBySignature(
        nodes: List<CollectionNode>,
        name: String,
        method: HttpMethodType,
        url: String,
    ): CollectionNode? {
        for (node in nodes) {
            if (!node.isFolder && node.name == name && node.method == method && node.url == url) return node
            if (node.isFolder) findRequestBySignature(node.children, name, method, url)?.let { return it }
        }
        return null
    }

    private fun findRequestBySignatureInSubtree(node: CollectionNode, tab: RequestTabState): CollectionNode? {
        node.children.forEach { child ->
            if (!child.isFolder && child.name == tab.name && child.method == tab.method && child.url == tab.url) {
                return child
            }
            if (child.isFolder) {
                findRequestBySignatureInSubtree(child, tab)?.let { return it }
            }
        }
        return null
    }

    private fun findRequestByNameInSubtree(node: CollectionNode, tab: RequestTabState): CollectionNode? {
        node.children.forEach { child ->
            if (!child.isFolder && child.name == tab.name) return child
            if (child.isFolder) {
                findRequestByNameInSubtree(child, tab)?.let { return it }
            }
        }
        return null
    }

    private fun resolveFolderPathScope(root: CollectionNode, folderPath: List<String>): CollectionNode {
        var cursor = root
        for (folderName in folderPath) {
            val next = cursor.children.firstOrNull { it.isFolder && it.name == folderName } ?: break
            cursor = next
        }
        return cursor
    }

    private fun resolveSidebarRequestId(requestId: String): String {
        val tab = openTabs.firstOrNull { it.id == requestId }
        if (tab == null) {
            if (findNodeById(collections, requestId) != null) return requestId
            return requestId
        }

        val references = buildRequestReferenceMap()
        val byRequestRef = tab.requestRef?.let { ref ->
            references.values.firstOrNull { it.requestRef == ref }
        }
        if (byRequestRef != null) return byRequestRef.requestId

        if (references.containsKey(requestId) && tabMatchesRequestScope(tab, requestId, references)) {
            return requestId
        }

        val collectionId = tab.collectionId
        if (collectionId != null) {
            val root = collections.firstOrNull { it.isFolder && it.id == collectionId }
            if (root != null) {
                val scope = resolveFolderPathScope(root, tab.folderPath)
                val candidate = findRequestBySignatureInSubtree(scope, tab)
                    ?: findRequestByNameInSubtree(scope, tab)
                    ?: findRequestBySignatureInSubtree(root, tab)
                    ?: findRequestByNameInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
        }

        val collectionName = tab.collectionName
        if (collectionName != null) {
            val roots = collections.filter { it.isFolder && it.name == collectionName }
            val candidateFolders = mutableListOf<CollectionNode>()
            roots.forEach { root ->
                candidateFolders.add(resolveFolderPathScope(root, tab.folderPath))
            }

            candidateFolders.forEach { folder ->
                val candidate = findRequestBySignatureInSubtree(folder, tab)
                if (candidate != null) return candidate.id
            }
            candidateFolders.forEach { folder ->
                val candidate = findRequestByNameInSubtree(folder, tab)
                if (candidate != null) return candidate.id
            }

            roots.forEach { root ->
                val candidate = findRequestBySignatureInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
            roots.forEach { root ->
                val candidate = findRequestByNameInSubtree(root, tab)
                if (candidate != null) return candidate.id
            }
        }

        val bySignature = findRequestBySignature(
            nodes = collections,
            name = tab.name,
            method = tab.method,
            url = tab.url,
        )
        if (bySignature != null) return bySignature.id

        if (references.containsKey(requestId)) return requestId

        return requestId
    }

    private fun tabMatchesRequestScope(
        tab: RequestTabState,
        requestId: String,
        references: Map<String, RequestReference>,
    ): Boolean {
        val reference = references[requestId] ?: return false
        tab.requestRef?.let { if (reference.requestRef != it) return false }

        // Collection ID is the most reliable discriminator: use it exclusively when available.
        // We intentionally do NOT also check collectionName here — names can be stale after
        // a collection rename between sessions while the ID stays correct.
        val tabCollectionId = tab.collectionId
        if (tabCollectionId != null) {
            return tabCollectionId == reference.collectionId
        }

        // No collection ID available — fall back to name + URL to prevent a stale tab id from
        // matching a same-named request that has a different URL (classic restored-tab problem).
        val tabCollectionName = tab.collectionName
        if (tabCollectionName != null) {
            val referenceCollectionName = reference.collectionId?.let { id ->
                collections.firstOrNull { it.isFolder && it.id == id }?.name
            }
            if (referenceCollectionName != null && referenceCollectionName != tabCollectionName) return false
        }
        if (tab.folderPath.isNotEmpty() && !pathStartsWith(reference.folderPath, tab.folderPath)) return false
        // URL match: guard against stale ids in same-named collections.
        if (reference.url.isNotEmpty() && reference.url != tab.url) return false
        return true
    }

    private fun pathStartsWith(path: List<String>, prefix: List<String>): Boolean {
        if (prefix.size > path.size) return false
        prefix.indices.forEach { index ->
            if (path[index] != prefix[index]) return false
        }
        return true
    }

    fun closeTab(index: Int) {
        if (index !in openTabs.indices) return
        val tab = openTabs[index]
        tab.disposeBodyViewModels()
        disposeMcpSession(tab.id)
        if (tab.kind == RequestKind.WEBSOCKET) {
            WebSocketClientManager.default.disconnect(tab.id)
        }
        openTabs.removeAt(index)
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    /**
     * Closes every open tab whose id is in [ids].
     * Safe to call with ids that have no matching open tab (silently ignored).
     * Adjusts [activeTabIndex] correctly even when multiple tabs are removed.
     */
    fun closeTabsByIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        // Remove in reverse-index order to keep earlier indices valid.
        openTabs.indices.reversed()
            .filter { openTabs[it].id in idSet }
            .forEach { idx ->
                val tab = openTabs[idx]
                tab.disposeBodyViewModels()
                disposeMcpSession(tab.id)
                if (tab.kind == RequestKind.WEBSOCKET) {
                    WebSocketClientManager.default.disconnect(tab.id)
                }
                openTabs.removeAt(idx)
            }
        if (openTabs.isEmpty()) {
            activeTabIndex = -1
        } else if (activeTabIndex >= openTabs.size) {
            activeTabIndex = openTabs.size - 1
        }
        selectedRequestId = activeTab?.id
    }

    /** Updates [RequestTabState.collectionName] for every open tab that still has [oldName].
     *  Called after a root collection is renamed so [resolveSidebarRequestId] keeps working. */
    fun updateTabsCollectionName(oldName: String, newName: String) {
        openTabs.forEach { tab ->
            if (tab.collectionName == oldName) tab.collectionName = newName
        }
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in openTabs.indices || toIndex !in openTabs.indices || fromIndex == toIndex) return
        val tab = openTabs.removeAt(fromIndex)
        openTabs.add(toIndex, tab)
        activeTabIndex = toIndex
        selectedRequestId = tab.id
    }

    /**
     * Focuses / activates the next open tab, or wraps around to the first tab if
     * the current tab is the last one.
     */
    fun selectNextTab() {
        if (openTabs.isEmpty()) return
        val nextIndex = if (activeTabIndex + 1 < openTabs.size) activeTabIndex + 1 else 0
        activeTabIndex = nextIndex
        selectedRequestId = activeTab?.id
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        consoleLogs.add(0, ConsoleEntry(message, level))
    }

    /**
     * Appends a network-level event to the structured Logs tab.
     * REST traffic also echoes to Console ([echoToConsole] default true). MCP
     * summaries pass false so Console stays for scripts and app messages.
     */
    fun logNetworkEvent(message: String, level: LogLevel = LogLevel.INFO, echoToConsole: Boolean = true) {
        val entry = ConsoleEntry(message, level)
        networkEventLogs.add(0, entry)
        if (echoToConsole) consoleLogs.add(0, entry)
    }

    fun notifyCollectionsChanged() {
        // Bump revision for the autosave snapshotFlow.
        // Children lists are now SnapshotStateList (mutableStateListOf), so Compose
        // automatically observes add/remove on them — no .copy() trick needed.
        ensureRequestRefsInitialized()
        syncOpenTabsWithCollections()
        syncHistoryWithCollections()
        collectionsRevision++
    }

    /** Collapse every folder in the collection tree. */
    fun collapseAllCollections() {
        for (id in allFolderIds(collections)) collectionExpandedState[id] = false
    }

    /** Expand every folder in the collection tree. */
    fun expandAllCollections() {
        for (id in allFolderIds(collections)) collectionExpandedState[id] = true
    }

    private fun allFolderIds(nodes: List<CollectionNode>): List<String> =
        nodes.flatMap { node ->
            if (node.isFolder) listOf(node.id) + allFolderIds(node.children)
            else emptyList()
        }

    /** Add a new request node inside the given collection (folder). Opens it as a tab. */
    fun addRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = "",
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = "")
    }

    /** Add a new gRPC request node inside the given collection (folder). Opens it as a tab. */
    fun addGrpcRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = "",
            kind = RequestKind.GRPC,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = "")
    }

    /** Add a new WebSocket request node inside the given collection (folder). Opens it as a tab. */
    fun addWebSocketRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = "ws://",
            kind = RequestKind.WEBSOCKET,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = "ws://")
    }

    /** Add a new GraphQL request node inside the given collection (folder). Opens it as a tab. */
    fun addGraphQLRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New Request", siblingNames)
        val requestId = generateUuid()
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://",
            kind = RequestKind.GRAPHQL,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = "https://")
    }

    fun addMcpConnectionToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New MCP Connection", siblingNames)
        val requestId = generateUuid()
        val mcp = McpConnectionConfig(url = "{{mcpBaseUrl}}")
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.POST,
            url = mcp.url,
            kind = RequestKind.MCP,
            mcpConfig = mcp,
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.POST, url = mcp.url)
    }

    fun addSseRequestToCollection(collectionId: String) {
        val folder = findFolder(collectionId) ?: return
        val siblingNames = folder.children.map { it.name }.toSet()
        val name = generateUniqueName("New SSE Request", siblingNames)
        val requestId = generateUuid()
        val url = "{{baseUrl}}/sse"
        val node = CollectionNode(
            id = requestId,
            requestRef = generateUuid(),
            name = name,
            isFolder = false,
            method = HttpMethodType.GET,
            url = url,
            userHeaders = listOf(SystemHeaderRules.ACCEPT to SSE_ACCEPT_MEDIA_TYPE),
        )
        folder.children.add(node)
        notifyCollectionsChanged()
        selectedCollectionId = rootCollectionIdContaining(collectionId) ?: collectionId
        selectedRequestId = requestId
        addTab(requestId = requestId, name = name, method = HttpMethodType.GET, url = url)
    }

    /** Create a request in the selected collection, ensuring a default collection exists when needed. */
    fun addTabInSelectedCollection() {
        val collId = selectedCollectionId
        val folder = if (collId != null) findFolder(collId) else null
        if (folder != null) {
            addRequestToCollection(folder.id)
        } else {
            val firstFolder = collections.firstOrNull { it.isFolder }
            if (firstFolder != null) {
                addRequestToCollection(firstFolder.id)
            } else {
                val defaultFolder = ensureDefaultCollectionFolder()
                addRequestToCollection(defaultFolder.id)
            }
        }
    }

    fun pruneEmptyGlobalVariables() {
        globalVariables.removeAll { it.key.isBlank() && it.value.isBlank() }
    }

    fun pruneEmptyVariablesForEnvironment(index: Int) {
        val env = environments.getOrNull(index) ?: return
        env.variables.removeAll { it.key.isBlank() && it.value.isBlank() }
    }

    fun renameRequestEverywhere(requestId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        openTabs.filter { it.id == requestId }.forEach { tab ->
            val oldName = tab.name
            tab.name = trimmed
            // Re-anchor the name component of savedSnapshot so that dirty tracking
            // reflects only non-name unsaved changes after a rename from the sidebar
            // or tab chip. Without this, reverting a URL edit after a rename always
            // appears dirty because savedSnapshot still contains the old name.
            tab.reanchorSavedSnapshotAfterRename(oldName)
        }
        renameRequestNodeById(collections, requestId, trimmed)
        notifyCollectionsChanged()
    }

    fun revealRequestInSidebar(requestId: String): Boolean {
        val resolvedRequestId = resolveSidebarRequestId(requestId)
        val targetNode = findNodeById(collections, resolvedRequestId)
        if (targetNode == null) {
            showError("Item not found", "Item no longer exists in collections.")
            return false
        }
        sidebarExpanded = true
        sidebarSearchQuery = ""

        if (targetNode.isFolder) {
            selectedCollectionId = targetNode.id
        } else {
            selectedRequestId = resolvedRequestId
        }

        // Also switch the active tab to this request so the sidebar
        // selection stays in sync with the tab bar (the LaunchedEffect
        // in MainScreen sets selectedRequestId = activeTab?.id).
        val tabIdx = openTabs.indexOfFirst { it.id == requestId || it.id == resolvedRequestId }
        if (tabIdx >= 0) activeTabIndex = tabIdx

        if (expandAncestorsForRequest(collections, resolvedRequestId)) {
            // Clear first, then set, so duplicate-name tab switches can retrigger
            // scroll even when the same request id is emitted consecutively.
            sidebarScrollToRequestId = null
            sidebarScrollToRequestId = resolvedRequestId
        }
        return true
    }

    fun recordHistory(
        requestId: String,
        method: HttpMethodType,
        name: String,
        url: String,
        aliasRequestIds: Set<String> = emptySet(),
    ) {
        val references = buildRequestReferenceMap()
        val resolvedRequestId = resolveSidebarRequestId(requestId)
        val reference = references[resolvedRequestId]
            ?: references.values.firstOrNull { it.name == name && it.method == method && it.url == url }
            ?: return

        val idsToReplace = (aliasRequestIds + requestId + resolvedRequestId + reference.requestId)
            .filter { it.isNotBlank() }
            .toSet()
        if (idsToReplace.isNotEmpty()) {
            historyItems.removeAll { it.requestId in idsToReplace }
        }
        val item = HistoryItem(
            requestId = reference.requestId,
            method = reference.method,
            name = reference.name,
            url = reference.url,
            timestamp = currentTimeMillis(),
            collectionId = reference.collectionId,
            folderPath = reference.folderPath,
        )
        historyItems.add(0, item)
        historyRevision++
    }

    fun openHistoryItem(item: HistoryItem) {
        val resolvedRequestId = resolveHistoryRequestId(item)
        val target = findNodeById(collections, resolvedRequestId)
        if (target != null && !target.isFolder) {
            openRequest(requestId = resolvedRequestId, name = target.name, method = target.method ?: item.method, url = target.url ?: item.url)
            selectedRequestId = resolvedRequestId
        } else {
            addTab(
                requestId = generateUuid(),
                name = item.name,
                method = item.method,
                url = item.url,
            )
            showError("Request not found", "Request no longer exists in collections. Opened a temporary tab.")
        }
    }

    private fun resolveHistoryRequestId(item: HistoryItem): String {
        if (findNodeById(collections, item.requestId) != null) return item.requestId
        val bySignature = findRequestBySignature(
            nodes = collections,
            name = item.name,
            method = item.method,
            url = item.url,
        )
        return bySignature?.id ?: item.requestId
    }

    fun goToCollectionFromHistory(item: HistoryItem): Boolean {
        val resolvedRequestId = resolveHistoryRequestId(item)
        val revealed = revealRequestInSidebar(resolvedRequestId)
        if (!revealed) return false

        if (openTabs.none { it.id == resolvedRequestId }) {
            val target = findNodeById(collections, resolvedRequestId)
            if (target != null && !target.isFolder) {
                openRequest(
                    requestId = target.id,
                    name = target.name,
                    method = target.method ?: item.method,
                    url = target.url ?: item.url,
                )
            }
        }
        return true
    }

    fun clearHistory() {
        if (historyItems.isEmpty()) return
        historyItems.clear()
        historyRevision++
    }

    /**
     * Resolves a script-provided next-request token to an existing request
     * across collections.
     *
     * Matching order:
     * 1) exact request id
     * 2) exact requestRef
     * 3) exact request name (preferring [preferredCollectionId] when provided)
     */
    fun resolveScriptRequestTarget(token: String, preferredCollectionId: String? = null): ScriptRequestTarget? {
        val normalized = token.trim()
        if (normalized.isEmpty()) return null
        val refs = buildRequestReferenceMap().values.toList()

        refs.firstOrNull { it.requestId == normalized }?.let {
            return ScriptRequestTarget(it.requestId, it.name, it.method, it.url)
        }
        refs.firstOrNull { it.requestRef == normalized }?.let {
            return ScriptRequestTarget(it.requestId, it.name, it.method, it.url)
        }

        val byName = refs.filter { it.name == normalized }
        if (byName.isEmpty()) return null

        val preferred = preferredCollectionId?.let { cid -> byName.filter { it.collectionId == cid } }.orEmpty()
        val winner = when {
            preferred.size == 1 -> preferred.first()
            byName.size == 1 -> byName.first()
            else -> null
        } ?: return null

        return ScriptRequestTarget(winner.requestId, winner.name, winner.method, winner.url)
    }

    fun removeHistoryItem(requestId: String) {
        val removed = historyItems.removeAll { it.requestId == requestId }
        if (removed) {
            historyRevision++
        }
    }

    fun replaceHistoryItems(items: List<HistoryItem>) {
        historyItems.clear()
        historyItems.addAll(items)
        historyRevision++
    }

    /**
     * Syncs the sidebar to the current active tab: sets [selectedRequestId],
     * expands ancestor folders, and sets the scroll target.
     * Called on startup after tab + workspace restoration, and whenever the
     * active tab changes.
     */
    fun syncSidebarToActiveTab() {
        val tab = activeTab ?: return
        val resolvedRequestId = resolveSidebarRequestId(tab.id)
        val wasAlreadySelected = selectedRequestId == resolvedRequestId
        selectedRequestId = resolvedRequestId
        val rootColId = if (tab.isCollectionTab) {
            tab.collectionId ?: tab.id
        } else {
            tab.collectionId ?: rootCollectionIdContaining(resolvedRequestId)
        }
        if (rootColId != null) {
            selectedCollectionId = rootColId
        }
        if (!wasAlreadySelected && expandAncestorsForRequest(collections, resolvedRequestId)) {
            // Reset signal first so repeated targets still trigger row scrolling.
            sidebarScrollToRequestId = null
            sidebarScrollToRequestId = resolvedRequestId
        }
    }

    private fun buildRequestReferenceMap(): Map<String, RequestReference> {
        val references = linkedMapOf<String, RequestReference>()
        collectRequestReferences(
            nodes = collections,
            currentCollectionId = null,
            currentFolderPath = emptyList(),
            target = references,
        )
        return references
    }

    private fun collectRequestReferences(
        nodes: List<CollectionNode>,
        currentCollectionId: String?,
        currentFolderPath: List<String>,
        target: MutableMap<String, RequestReference>,
    ) {
        for (node in nodes) {
            if (node.isFolder) {
                val nextCollectionId = currentCollectionId ?: node.id
                val nextFolderPath = if (currentCollectionId == null) {
                    currentFolderPath
                } else {
                    currentFolderPath + node.name
                }
                collectRequestReferences(node.children, nextCollectionId, nextFolderPath, target)
            } else {
                val method = node.method ?: continue
                val url = node.url ?: continue
                val requestRef = node.requestRef ?: node.id
                target[node.id] = RequestReference(
                    requestId = node.id,
                    requestRef = requestRef,
                    collectionId = currentCollectionId,
                    folderPath = currentFolderPath,
                    name = node.name,
                    method = method,
                    url = url,
                )
            }
        }
    }

    private fun syncHistoryWithCollections() {
        val references = buildRequestReferenceMap()
        var changed = false
        val synced = historyItems.mapNotNull { item ->
            val ref = references[item.requestId] ?: run {
                changed = true
                return@mapNotNull null
            }
            val updated = item.copy(
                method = ref.method,
                name = ref.name,
                url = ref.url,
                collectionId = ref.collectionId,
                folderPath = ref.folderPath,
            )
            if (updated != item) changed = true
            updated
        }
        if (changed) {
            historyItems.clear()
            historyItems.addAll(synced)
            historyRevision++
        }
    }

    private fun syncOpenTabsWithCollections() {
        val references = buildRequestReferenceMap()
        val rootNamesById = collections
            .filter { it.isFolder }
            .associate { it.id to it.name }

        openTabs.forEach { tab ->
            val reference = tab.requestRef?.let { ref ->
                references.values.firstOrNull { it.requestRef == ref }
            } ?: references[tab.id] ?: return@forEach
            tab.requestRef = reference.requestRef
            tab.collectionId = reference.collectionId
            tab.collectionName = reference.collectionId?.let { rootNamesById[it] }
            tab.folderPath = reference.folderPath
        }
    }

    private fun ensureRequestRefsInitialized() {
        ensureRequestRefsInitializedRecursive(collections)
    }

    private fun ensureRequestRefsInitializedRecursive(nodes: MutableList<CollectionNode>) {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (node.isFolder) {
                if (node.children.isNotEmpty()) ensureRequestRefsInitializedRecursive(node.children)
            } else if (node.requestRef == null) {
                nodes[index] = node.copy(requestRef = generateUuid())
            }
        }
    }

    private fun renameRequestNodeById(nodes: MutableList<CollectionNode>, requestId: String, newName: String): Boolean {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if (node.id == requestId) {
                nodes[index] = node.copy(name = newName)
                return true
            }
            if (node.isFolder && node.children.isNotEmpty()) {
                if (renameRequestNodeById(node.children, requestId, newName)) return true
            }
        }
        return false
    }

    /**
     * Writes the current tab editing state back into the matching [CollectionNode] in the
     * collections tree so that workspace export reflects the latest saved content.
     *
     * Must be called from the main thread (it mutates Compose snapshot state).
     * Returns `true` if the node was found and updated.
     */
    fun syncTabToCollectionNode(tab: RequestTabState): Boolean {
        val updated = syncTabToNodeRecursive(collections, tab)
        if (updated) {
            notifyCollectionsChanged()
            if (tab.isFolderTab || tab.isCollectionTab) {
                refreshInheritedDefaultsForOpenTabs()
            }
        }
        return updated
    }

    private fun syncTabToNodeRecursive(
        nodes: MutableList<CollectionNode>,
        tab: RequestTabState,
    ): Boolean {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            if ((node.isFolder || node.isCollection) && (node.id == tab.id || (tab.isCollectionTab && (node.id == tab.collectionId || tab.id == "collection_${node.id}")))) {
                val variableEntriesSnapshot = tab.variables
                    .filter { it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val headerEntriesSnapshot = tab.headers
                    .filter { it.kind != HeaderKind.SYSTEM && it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val userHeadersSnapshot = tab.headers
                    .filter { it.kind == HeaderKind.USER && it.enabled }
                    .map { it.key to it.value }
                val oldName = node.name
                val finalName = if (tab.isCollectionTab && tab.name != oldName) {
                    disambiguateCollectionName(tab.name.trim(), node.id)
                } else {
                    tab.name
                }
                if (tab.isCollectionTab && finalName != tab.name) {
                    tab.name = finalName
                    tab.collectionName = finalName
                }
                if (tab.isCollectionTab && finalName != oldName) {
                    updateTabsCollectionName(oldName, finalName)
                    tab.reanchorSavedSnapshotAfterRename(oldName)
                }
                val updatedStorageExt = if (tab.isCollectionTab) {
                    if (tab.storageType == StorageType.GIT) {
                        StorageExtension(
                            type = StorageType.GIT,
                            git = GitStorageConfig(
                                remoteUrl = tab.gitRemoteUrl.trim(),
                                branch = tab.gitBranch.trim().ifBlank { "main" },
                                credentials = GitCredentialsConfig(type = "system"),
                            ),
                        )
                    } else {
                        StorageExtension(type = StorageType.LOCAL, git = null)
                    }
                } else {
                    node.storageExtension
                }
                val effectiveProtoFiles = if (tab.isCollectionTab) {
                    val src = if (collectionProtoFiles.isNotEmpty() || tab.collectionProtoFiles.isEmpty()) collectionProtoFiles else tab.collectionProtoFiles
                    src.map { ProtoFileItemState(it.path, it.syncToGit) }
                } else {
                    node.protoFiles
                }
                val effectiveImportPaths = if (tab.isCollectionTab) {
                    val src = if (collectionImportPaths.isNotEmpty() || tab.collectionImportPaths.isEmpty()) collectionImportPaths else tab.collectionImportPaths
                    src.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) }
                } else {
                    node.importPaths
                }
                nodes[index] = node.copy(
                    name = finalName,
                    summary = if (tab.isCollectionTab) tab.collectionSummary else node.summary,
                    version = if (tab.isCollectionTab) tab.collectionVersion else node.version,
                    protoFiles = effectiveProtoFiles,
                    importPaths = effectiveImportPaths,
                    storageExtension = updatedStorageExt,
                    variableEntries = variableEntriesSnapshot,
                    queryEntries = null,
                    headerEntries = headerEntriesSnapshot,
                    userHeaders = userHeadersSnapshot,
                    authType = tab.authType.takeIf { it != AuthType.NONE },
                    authUsername = tab.authUsername.ifBlank { null },
                    authPassword = tab.authPassword.ifBlank { null },
                    authToken = tab.authToken.ifBlank { null },
                    authApiKey = tab.authApiKey.ifBlank { null },
                    authApiValue = tab.authApiValue.ifBlank { null },
                    authApiPlacement = normalizeApiKeyPlacement(tab.authApiPlacement),
                    authOAuth2Flow = tab.authOAuth2Flow,
                    authOAuth2AccessTokenUrl = tab.authOAuth2AccessTokenUrl.ifBlank { null },
                    authOAuth2RefreshTokenUrl = tab.authOAuth2RefreshTokenUrl.ifBlank { null },
                    authOAuth2AuthorizationUrl = tab.authOAuth2AuthorizationUrl.ifBlank { null },
                    authOAuth2RedirectUri = tab.authOAuth2RedirectUri.ifBlank { null },
                    authOAuth2Scope = tab.authOAuth2Scope.ifBlank { null },
                    authOAuth2ClientId = tab.authOAuth2ClientId.ifBlank { null },
                    authOAuth2ClientSecret = tab.authOAuth2ClientSecret.ifBlank { null },
                    authOAuth2CredentialsPlacement = tab.authOAuth2CredentialsPlacement,
                    authOAuth2Username = tab.authOAuth2Username.ifBlank { null },
                    authOAuth2Password = tab.authOAuth2Password.ifBlank { null },
                    preRequestScript = tab.preRequestScript.ifBlank { null },
                    testScript = tab.testScript.ifBlank { null },
                )
                if (tab.isCollectionTab) {
                    collectionProtoFiles.clear()
                    collectionProtoFiles.addAll(effectiveProtoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
                    collectionImportPaths.clear()
                    collectionImportPaths.addAll(effectiveImportPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })
                    tab.collectionProtoFiles.clear()
                    tab.collectionProtoFiles.addAll(effectiveProtoFiles.map { ProtoFileItemState(it.path, it.syncToGit) })
                    tab.collectionImportPaths.clear()
                    tab.collectionImportPaths.addAll(effectiveImportPaths.map { ProtoImportPathState(it.path, it.enabled, it.syncToGit) })
                }
                return true
            }
            if (!node.isFolder && node.id == tab.id) {
                val effectiveHeaderSource = if (tab.kind == RequestKind.GRPC) tab.grpcMetadata else tab.headers
                val userHeadersSnapshot = effectiveHeaderSource
                    .filter { it.kind == HeaderKind.USER && it.enabled }
                    .map { it.key to it.value }
                    .toMutableList()
                val queryEntriesSnapshot = tab.params
                    .filter { it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val headerEntriesSnapshot = effectiveHeaderSource
                    .filter { it.kind != HeaderKind.INHERITED }
                    .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) }
                val sseAccept = tab.headers.firstOrNull { isSseAccept(it.key, it.value, it.enabled) }
                if (sseAccept != null &&
                    userHeadersSnapshot.none { it.first.equals(SystemHeaderRules.ACCEPT, ignoreCase = true) }
                ) {
                    userHeadersSnapshot.add(sseAccept.key to sseAccept.value)
                }
                val bodyContentsSnapshot: Map<String, String> =
                    tab.bodyContents.entries.associate { it.key.name to it.value }
                val formEntriesSnapshot = tab.formRows.map { r ->
                    FormDataEntryState(r.key, r.type, r.value, r.description, r.enabled, r.bytesBase64)
                }
                val urlencodedSnapshot = tab.urlencodedRows.map { r ->
                    FormDataEntryState(r.key, r.type, r.value, r.description, r.enabled)
                }
                nodes[index] = node.copy(
                    requestRef = node.requestRef ?: tab.requestRef ?: generateUuid(),
                    name   = tab.name,
                    method = tab.method,
                    url    = tab.url,
                    bodyType           = if (tab.kind == RequestKind.GRPC) BodyType.JSON else tab.bodyType.takeIf { it != BodyType.NONE },
                    bodyContent        = if (tab.kind == RequestKind.GRPC) tab.grpcMessageBody.ifBlank { null } else tab.bodyContent.ifBlank { null },
                    bodyContents       = bodyContentsSnapshot,
                    formDataEntries    = formEntriesSnapshot,
                    urlencodedEntries  = urlencodedSnapshot,
                    authType           = tab.authType.takeIf { it != AuthType.NONE },
                    authUsername       = tab.authUsername.ifBlank { null },
                    authPassword       = tab.authPassword.ifBlank { null },
                    authToken          = tab.authToken.ifBlank { null },
                    authApiKey         = tab.authApiKey.ifBlank { null },
                    authApiValue       = tab.authApiValue.ifBlank { null },
                    authApiPlacement   = normalizeApiKeyPlacement(tab.authApiPlacement),
                    authOAuth2Flow     = tab.authOAuth2Flow,
                    authOAuth2AccessTokenUrl = tab.authOAuth2AccessTokenUrl.ifBlank { null },
                    authOAuth2RefreshTokenUrl = tab.authOAuth2RefreshTokenUrl.ifBlank { null },
                    authOAuth2AuthorizationUrl = tab.authOAuth2AuthorizationUrl.ifBlank { null },
                    authOAuth2RedirectUri = tab.authOAuth2RedirectUri.ifBlank { null },
                    authOAuth2Scope    = tab.authOAuth2Scope.ifBlank { null },
                    authOAuth2ClientId = tab.authOAuth2ClientId.ifBlank { null },
                    authOAuth2ClientSecret = tab.authOAuth2ClientSecret.ifBlank { null },
                    authOAuth2CredentialsPlacement = tab.authOAuth2CredentialsPlacement,
                    authOAuth2Username = tab.authOAuth2Username.ifBlank { null },
                    authOAuth2Password = tab.authOAuth2Password.ifBlank { null },
                    userHeaders        = userHeadersSnapshot,
                    queryEntries       = queryEntriesSnapshot,
                    headerEntries      = headerEntriesSnapshot,
                    preRequestScript   = tab.preRequestScript.ifBlank { null },
                    testScript         = tab.testScript.ifBlank { null },
                    kind               = tab.kind,
                    mcpConfig          = tab.mcpConfig.copy(
                        url = tab.url.ifBlank { tab.mcpConfig.url },
                    ),
                    protoFilePath      = tab.protoFilePath?.ifBlank { null },
                    protoSyncToGit     = tab.protoSyncToGit,
                    grpcMethod         = tab.grpcMethod?.ifBlank { null },
                    grpcMethodType     = tab.grpcMethodType.ifBlank { "unary" },
                    webSocketVariants  = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketMessageVariants.toList() else emptyList(),
                    webSocketTimeout   = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketTimeout else null,
                    webSocketKeepAliveInterval = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketKeepAliveInterval else null,
                    webSocketDocs      = if (tab.kind == RequestKind.WEBSOCKET) tab.webSocketDocs.ifBlank { null } else null,
                    webSocketMessageFormat = if (tab.kind == RequestKind.WEBSOCKET) tab.effectiveWebSocketFormat else null,
                )
                return true
            }
            if ((node.isFolder || node.isCollection) && node.children.isNotEmpty()) {
                if (syncTabToNodeRecursive(node.children, tab)) return true
            }
        }
        return false
    }

    private fun expandAncestorsForRequest(nodes: List<CollectionNode>, requestId: String): Boolean {
        for (node in nodes) {
            if (node.id == requestId) return true
            if ((node.isFolder || node.isCollection) && node.children.isNotEmpty()) {
                if (expandAncestorsForRequest(node.children, requestId)) {
                    collectionExpandedState[node.id] = true
                    return true
                }
            }
        }
        return false
    }

    private fun generateUniqueName(base: String, existingNames: Set<String>): String {
        if (base !in existingNames) return base
        var index = 2
        while ("$base $index" in existingNames) index++
        return "$base $index"
    }

    private fun ensureDefaultCollectionFolder(): CollectionNode {
        collections.firstOrNull { it.isFolder }?.let { return it }

        val existingNames = collections.map { it.name }.toSet()
        val folder = CollectionNode(
            id = generateUuid(),
            name = generateUniqueName("Default Collection", existingNames),
            isFolder = true,
            children = mutableStateListOf(),
        )
        collections.add(folder)
        notifyCollectionsChanged()
        selectedCollectionId = folder.id
        return folder
    }

    /** Open a history / collection request as a new tab. */
    fun openRequest(requestId: String? = null, name: String, method: HttpMethodType, url: String) {
        if (requestId != null) {
            selectedRequestId = requestId
        }
        val existing = if (requestId != null) {
            openTabs.indexOfFirst { it.id == requestId }
        } else {
            openTabs.indexOfFirst { it.url == url && it.method == method }
        }
        if (existing >= 0) {
            activeTabIndex = existing
            selectedRequestId = openTabs[existing].id
        } else {
            addTab(requestId = requestId ?: generateUuid(), name = name, method = method, url = url)
        }
    }

    /** Open the environment edit dialog for the given environment index. */
    fun openEnvEdit(index: Int) {
        editingEnvIndex = index
        showEnvEditDialog = true
    }
}
