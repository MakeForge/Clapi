package io.github.crustack.clapi.ui.shared.persistence

import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.FormEntryType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.McpConnectionConfig
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessage
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType
import io.github.crustack.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.crustack.clapi.core.model.storage.StorageType
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.FormDataEntryState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.MutableFormDataRow
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.state.normalizeApiKeyPlacement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.github.crustack.clapi.ui.shared.platform.PlatformStorage

/**
 * Saves and restores open request tabs using PlatformStorage.
 * Persists: name, method, url, body-type, body-content, params, headers, dirty-flag.
 */
object TabsRepository {

    private const val STORAGE_KEY = "clapi.tabs"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Save ───────────────────────────────────────────────────────────────

    fun save(state: AppState): Boolean {
        return runCatching {
            val arr = buildJsonArray {
                state.openTabs.forEach { tab ->
                    add(buildJsonObject {
                        put("id",          tab.id)
                        put("requestRef",  tab.requestRef ?: "")
                        put("name",        tab.name)
                        put("method",      tab.method.name)
                        put("url",         tab.url)
                        put("collectionName", tab.collectionName ?: "")
                        put("collectionId", tab.collectionId ?: "")
                        put("folderPath", buildJsonArray { tab.folderPath.forEach { add(JsonPrimitive(it)) } })
                        put("bodyType",    tab.bodyType.name)
                        put("lastRawSubtype", tab.lastRawSubtype.name)
                        put("bodyContent", tab.bodyContent)
                        put("bodyContents", buildJsonObject {
                            tab.bodyContents.forEach { entry ->
                                if (entry.value.isNotBlank()) put(entry.key.name, entry.value)
                            }
                        })
                        put("authType",    tab.authType.name)
                        put("authUsername", tab.authUsername)
                        put("authPassword", tab.authPassword)
                        put("authToken",    tab.authToken)
                        put("authApiKey",   tab.authApiKey)
                        put("authApiValue", tab.authApiValue)
                        put("authApiPlacement", normalizeApiKeyPlacement(tab.authApiPlacement))
                        put("preRequestScript", tab.preRequestScript)
                        put("testScript", tab.testScript)
                        put("retryEnabled", tab.retryEnabled)
                        put("retryCount", tab.retryCount)
                        put("retryDelayMs", tab.retryDelayMs)
                        tab.lastSavedTimestamp?.let { put("lastSavedTimestamp", it) }
                        put("savedSnapshot", tab.savedSnapshotForPersistence())
                        put("snapshotFormat", 2)
                        put("isDirty",     tab.isDirty)
                        put("params",  kvListJson(tab.params))
                        put("variables", kvListJson(tab.variables))
                        put("headers", kvListJson(tab.headers))
                        put("grpcMetadata", kvListJson(tab.grpcMetadata))
                        put("protoFilePath", tab.protoFilePath ?: "")
                        put("protoSyncToGit", tab.protoSyncToGit)
                        put("grpcMethod", tab.grpcMethod ?: "")
                        put("grpcMethodType", tab.grpcMethodType)
                        put("formRows", formRowListJson(tab.formRows))
                        put("urlencodedRows", formRowListJson(tab.urlencodedRows))
                        put("kind", tab.kind.name)
                        put("collectionSummary", tab.collectionSummary ?: "")
                        put("collectionVersion", tab.collectionVersion ?: "")
                        put("storageType", tab.storageType.name)
                        put("gitRemoteUrl", tab.gitRemoteUrl)
                        put("gitBranch", tab.gitBranch)
                        put("webSocketTimeout", tab.webSocketTimeout ?: "inherit")
                        put("webSocketKeepAliveInterval", tab.webSocketKeepAliveInterval ?: "inherit")
                        put("webSocketDocs", tab.webSocketDocs)
                        put("webSocketMessageFormat", tab.effectiveWebSocketFormat.value)
                        put("webSocketOutgoingPayload", tab.webSocketOutgoingPayload)
                        put("webSocketVariants", buildJsonArray {
                            tab.webSocketMessageVariants.forEach { v ->
                                add(buildJsonObject {
                                    put("title", v.title)
                                    put("selected", v.selected)
                                    put("type", v.message.type.value)
                                    put("data", v.message.data)
                                })
                            }
                        })
                        put("graphQlMethod", tab.graphQlMethod)
                        put("graphQlQuery", tab.graphQlQuery)
                        put("graphQlVariables", tab.graphQlVariables)
                        put("graphQlDocs", tab.graphQlDocs)
                        put("graphQlTimeout", tab.graphQlTimeout ?: "inherit")
                        put("graphQlEncodeUrl", tab.graphQlEncodeUrl ?: "inherit")
                        put("graphQlFollowRedirects", tab.graphQlFollowRedirects ?: "inherit")
                        put("graphQlForwardAuthHeader", tab.graphQlForwardAuthHeader ?: "inherit")
                        put("graphQlMaxRedirects", tab.graphQlMaxRedirects ?: "inherit")
                        put("graphQlVariants", buildJsonArray {
                            tab.graphQlVariants.forEach { v ->
                                add(buildJsonObject {
                                    put("title", v.title)
                                    put("selected", v.selected)
                                    put("query", v.body.query)
                                    put("variables", v.body.variables ?: "")
                                })
                            }
                        })
                        put("mcp", json.parseToJsonElement(
                            json.encodeToString(McpConnectionConfig.serializer(), tab.mcpConfig),
                        ))
                    })
                }
            }
            val root = buildJsonObject {
                put("activeIndex", state.activeTabIndex)
                put("tabs", arr)
            }
            PlatformStorage.putString(STORAGE_KEY, root.toString())
            true
        }.getOrElse { false }
    }

    // ── Load ───────────────────────────────────────────────────────────────

    /** Loads saved tabs into [state]. Call once on startup before the first recomposition. */
    fun load(state: AppState) {
        val stored = PlatformStorage.getString(STORAGE_KEY) ?: return
        runCatching {
            val root     = json.parseToJsonElement(stored).jsonObject
            val tabsJson = root["tabs"]?.jsonArray ?: return
            if (tabsJson.isEmpty()) return

            // Dispose any cached EditorViewModels before clearing to prevent
            // orphaned coroutine scopes when the workspace is restored from disk.
            state.openTabs.forEach { it.disposeBodyViewModels() }
            // Replace the default single empty tab
            state.openTabs.clear()
            tabsJson.forEach { el ->
                val obj = el.jsonObject
                
                val savedId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                val savedRequestRef = obj["requestRef"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedCollectionName = obj["collectionName"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedCollectionId = obj["collectionId"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val parsedFolderPath = obj["folderPath"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                val reqName = obj["name"]?.jsonPrimitive?.content ?: "Untitled"
                val savedMethod = safeEnum(obj["method"]?.jsonPrimitive?.content, HttpMethodType.GET)
                val savedUrl = obj["url"]?.jsonPrimitive?.content ?: ""

                val savedKind = runCatching {
                    RequestKind.valueOf(obj["kind"]?.jsonPrimitive?.content ?: "HTTP")
                }.getOrDefault(RequestKind.HTTP)

                fun findByRequestRef(nodes: List<io.github.crustack.clapi.ui.shared.state.CollectionNode>, ref: String): io.github.crustack.clapi.ui.shared.state.CollectionNode? {
                    nodes.forEach { node ->
                        if (!node.isFolder && node.requestRef == ref) return node
                        if (node.isFolder) {
                            findByRequestRef(node.children, ref)?.let { return it }
                        }
                    }
                    return null
                }
                
                // Find matching request node mapped to the newly generated UUIDs
                var realId = savedId
                val candidateCollections = when {
                    parsedCollectionId != null -> state.collections.filter { it.id == parsedCollectionId }
                    parsedCollectionName != null -> state.collections.filter { it.name == parsedCollectionName }
                    else -> emptyList()
                }
                var resolvedCollection: io.github.crustack.clapi.ui.shared.state.CollectionNode? = null

                if (savedKind == RequestKind.FOLDER) {
                    fun findFolder(nodes: List<io.github.crustack.clapi.ui.shared.state.CollectionNode>): io.github.crustack.clapi.ui.shared.state.CollectionNode? {
                        for (node in nodes) {
                            if (node.isFolder && (node.id == savedId || node.name == reqName)) return node
                            if (node.isFolder) {
                                findFolder(node.children)?.let { return it }
                            }
                        }
                        return null
                    }
                    val folderNode = findFolder(state.collections)
                    if (folderNode != null) {
                        realId = folderNode.id
                    } else if (state.collections.isNotEmpty()) {
                        // Folder no longer exists in collections — skip dangling tab
                        return@forEach
                    }
                } else {
                    var matchingRequestNode: io.github.crustack.clapi.ui.shared.state.CollectionNode? = null

                    // Strongest match: stable request identity persisted on the tab.
                    if (savedRequestRef != null) {
                        val byRef = findByRequestRef(state.collections, savedRequestRef)
                        if (byRef != null) {
                            realId = byRef.id
                            matchingRequestNode = byRef
                        }
                    }

                    fun resolveFolderScope(root: io.github.crustack.clapi.ui.shared.state.CollectionNode): io.github.crustack.clapi.ui.shared.state.CollectionNode {
                        var cursor: io.github.crustack.clapi.ui.shared.state.CollectionNode = root
                        for (folder in parsedFolderPath) {
                            val next = cursor.children.find { it.name == folder && it.isFolder } ?: break
                            cursor = next
                        }
                        return cursor
                    }

                    if (matchingRequestNode == null) {
                        candidateCollections.forEach { coll ->
                            if (resolvedCollection != null) return@forEach
                            val scope = resolveFolderScope(coll)
                            val bySignature = scope.children.firstOrNull {
                                !it.isFolder && it.name == reqName && it.method == savedMethod && (it.url ?: "") == savedUrl
                            }
                            if (bySignature != null) {
                                realId = bySignature.id
                                resolvedCollection = coll
                                matchingRequestNode = bySignature
                            }
                        }
                    }

                    if (matchingRequestNode == null) {
                        candidateCollections.forEach { coll ->
                            if (resolvedCollection != null) return@forEach
                            val scope = resolveFolderScope(coll)
                            val byName = scope.children.firstOrNull { !it.isFolder && it.name == reqName }
                            if (byName != null) {
                                realId = byName.id
                                resolvedCollection = coll
                                matchingRequestNode = byName
                            }
                        }
                    }

                    if (matchingRequestNode == null) {
                        fun findRequestById(nodes: List<io.github.crustack.clapi.ui.shared.state.CollectionNode>, id: String): io.github.crustack.clapi.ui.shared.state.CollectionNode? {
                            for (n in nodes) {
                                if (!n.isFolder && n.id == id) return n
                                if (n.isFolder) {
                                    findRequestById(n.children, id)?.let { return it }
                                }
                            }
                            return null
                        }
                        matchingRequestNode = findRequestById(state.collections, realId) ?: findRequestById(state.collections, savedId)
                    }

                    if (state.collections.isNotEmpty() && matchingRequestNode == null) {
                        // Request node no longer exists in collections — skip dangling tab
                        return@forEach
                    }
                }

                val resolvedCollectionId = resolvedCollection?.id ?: parsedCollectionId
                val resolvedCollectionName = resolvedCollection?.name ?: parsedCollectionName

                val tab = RequestTabState(
                    id     = realId,
                    requestRef = savedRequestRef,
                    name   = reqName,
                    method = savedMethod,
                    url    = savedUrl,
                    collectionName = resolvedCollectionName,
                    collectionId = resolvedCollectionId,
                    folderPath = parsedFolderPath,
                    kind   = savedKind,
                )
                tab.bodyType    = safeEnum(obj["bodyType"]?.jsonPrimitive?.content, BodyType.JSON)
                tab.lastRawSubtype = safeEnum(obj["lastRawSubtype"]?.jsonPrimitive?.content, BodyType.JSON)
                // Only set bodyContent when non-blank. Setting it to "" would insert a
                // blank entry (e.g. "NONE=") into bodyContents, making allBodyContentsSnapshot
                // differ from the saved snapshot that had an empty map → false dirty state.
                obj["bodyContent"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { tab.bodyContent = it }
                obj["bodyContents"]?.jsonObject?.forEach { (typeName, contentEl) ->
                    runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { type ->
                        tab.bodyContents[type] = contentEl.jsonPrimitive.content
                    }
                }
                tab.authType    = safeEnum(obj["authType"]?.jsonPrimitive?.content, AuthType.NONE)
                tab.authUsername = obj["authUsername"]?.jsonPrimitive?.content ?: ""
                tab.authPassword = obj["authPassword"]?.jsonPrimitive?.content ?: ""
                tab.authToken    = obj["authToken"]?.jsonPrimitive?.content ?: ""
                tab.authApiKey   = obj["authApiKey"]?.jsonPrimitive?.content ?: ""
                tab.authApiValue = obj["authApiValue"]?.jsonPrimitive?.content ?: ""
                tab.authApiPlacement = normalizeApiKeyPlacement(obj["authApiPlacement"]?.jsonPrimitive?.content)
                tab.preRequestScript = obj["preRequestScript"]?.jsonPrimitive?.content ?: ""
                tab.testScript = obj["testScript"]?.jsonPrimitive?.content ?: ""
                tab.retryEnabled = obj["retryEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                tab.retryCount = obj["retryCount"]?.jsonPrimitive?.intOrNull ?: 1
                tab.retryDelayMs = obj["retryDelayMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 250L
                tab.lastSavedTimestamp = obj["lastSavedTimestamp"]?.jsonPrimitive?.content?.toLongOrNull()
                obj["mcp"]?.jsonObject?.let { mcpObj ->
                    tab.mcpConfig = json.decodeFromJsonElement(
                        McpConnectionConfig.serializer(),
                        mcpObj,
                    )
                }

                obj["params"]?.jsonArray?.forEach { kv ->
                    tab.params.add(kvFromJson(kv.jsonObject))
                }
                obj["variables"]?.jsonArray?.forEach { kv ->
                    tab.variables.add(kvFromJson(kv.jsonObject))
                }
                // Prepend saved headers (after the default pair already there)
                val savedHeaders = obj["headers"]?.jsonArray?.map { kvFromJson(it.jsonObject) } ?: emptyList()
                if (savedHeaders.isNotEmpty()) {
                    tab.headers.clear()
                    tab.headers.addAll(savedHeaders)
                } else if (savedKind == RequestKind.FOLDER || savedKind == RequestKind.GRPC || savedKind == RequestKind.WEBSOCKET || savedKind == RequestKind.GRAPHQL) {
                    tab.headers.clear()
                }
                obj["grpcMetadata"]?.jsonArray?.forEach { kv ->
                    tab.grpcMetadata.add(kvFromJson(kv.jsonObject))
                }
                tab.protoFilePath = obj["protoFilePath"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                tab.protoSyncToGit = obj["protoSyncToGit"]?.jsonPrimitive?.booleanOrNull ?: false
                tab.grpcMethod = obj["grpcMethod"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                tab.grpcMethodType = obj["grpcMethodType"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "unary"

                if (savedKind == RequestKind.GRAPHQL) {
                    obj["graphQlMethod"]?.jsonPrimitive?.content?.let { tab.graphQlMethod = it }
                    obj["graphQlQuery"]?.jsonPrimitive?.content?.let { tab.graphQlQuery = it }
                    obj["graphQlVariables"]?.jsonPrimitive?.content?.let { tab.graphQlVariables = it }
                    obj["graphQlDocs"]?.jsonPrimitive?.content?.let { tab.graphQlDocs = it }
                    obj["graphQlTimeout"]?.jsonPrimitive?.content?.let { tab.graphQlTimeout = it }
                    obj["graphQlEncodeUrl"]?.jsonPrimitive?.content?.let { tab.graphQlEncodeUrl = it }
                    obj["graphQlFollowRedirects"]?.jsonPrimitive?.content?.let { tab.graphQlFollowRedirects = it }
                    obj["graphQlForwardAuthHeader"]?.jsonPrimitive?.content?.let { tab.graphQlForwardAuthHeader = it }
                    obj["graphQlMaxRedirects"]?.jsonPrimitive?.content?.let { tab.graphQlMaxRedirects = it }
                    obj["graphQlVariants"]?.jsonArray?.forEach { el ->
                        val vObj = el.jsonObject
                        tab.graphQlVariants.add(
                            io.github.crustack.clapi.core.model.opencollection.GraphQLBodyVariant(
                                title = vObj["title"]?.jsonPrimitive?.content ?: "Variant",
                                selected = vObj["selected"]?.jsonPrimitive?.booleanOrNull ?: false,
                                body = io.github.crustack.clapi.core.model.opencollection.GraphQLBody(
                                    query = vObj["query"]?.jsonPrimitive?.content ?: "",
                                    variables = vObj["variables"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                ),
                            )
                        )
                    }
                    val sel = tab.graphQlVariants.indexOfFirst { it.selected == true }
                    tab.activeGraphQLVariantIndex = if (sel >= 0) sel else 0
                }

                if (savedKind == RequestKind.WEBSOCKET) {
                    tab.webSocketTimeout = obj["webSocketTimeout"]?.jsonPrimitive?.content ?: "inherit"
                    tab.webSocketKeepAliveInterval = obj["webSocketKeepAliveInterval"]?.jsonPrimitive?.content ?: "inherit"
                    tab.webSocketDocs = obj["webSocketDocs"]?.jsonPrimitive?.content ?: ""
                    obj["webSocketMessageFormat"]?.jsonPrimitive?.content?.let { formatStr ->
                        tab.webSocketMessageFormat = WebSocketMessageType.fromValue(formatStr)
                    }
                    tab.webSocketOutgoingPayload = obj["webSocketOutgoingPayload"]?.jsonPrimitive?.content ?: ""
                    obj["webSocketVariants"]?.jsonArray?.forEach { el ->
                        val vObj = el.jsonObject
                        tab.webSocketMessageVariants.add(
                            WebSocketMessageVariant(
                                title = vObj["title"]?.jsonPrimitive?.content ?: "Variant",
                                selected = vObj["selected"]?.jsonPrimitive?.booleanOrNull ?: false,
                                message = WebSocketMessage(
                                    type = WebSocketMessageType.fromValue(vObj["type"]?.jsonPrimitive?.content),
                                    data = vObj["data"]?.jsonPrimitive?.content ?: "",
                                )
                            )
                        )
                    }
                    val selectedVarIdx = tab.webSocketMessageVariants.indexOfFirst { it.selected == true }
                    tab.activeWebSocketVariantIndex = if (selectedVarIdx >= 0) selectedVarIdx else 0
                }

                // Load form rows BEFORE restoreSavedSnapshot so that recomputeDirty()
                // sees the complete state when comparing against the persisted savedSnapshot.
                // (May be absent in older saves → empty list.)
                obj["formRows"]?.jsonArray?.forEach { el ->
                    tab.formRows.add(formRowFromJson(el.jsonObject))
                }
                obj["urlencodedRows"]?.jsonArray?.forEach { el ->
                    tab.urlencodedRows.add(formRowFromJson(el.jsonObject))
                }

                if (savedKind == RequestKind.COLLECTION) {
                    val colNode = state.collections.firstOrNull { it.id == realId || it.id == resolvedCollectionId }
                    tab.collectionSummary = obj["collectionSummary"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: colNode?.summary
                    tab.collectionVersion = obj["collectionVersion"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: colNode?.version
                    tab.storageType = safeEnum(obj["storageType"]?.jsonPrimitive?.content, colNode?.storageType ?: StorageType.LOCAL)
                    tab.gitRemoteUrl = obj["gitRemoteUrl"]?.jsonPrimitive?.content ?: colNode?.gitRemoteUrl ?: ""
                    tab.gitBranch = obj["gitBranch"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: colNode?.gitBranch ?: "main"
                    tab.collectionSnapshotProvider = { state.collectionProtobufSnapshot() }
                }

                val legacyDirty = obj["isDirty"]?.jsonPrimitive?.booleanOrNull ?: false
                val snapshotFormat = obj["snapshotFormat"]?.jsonPrimitive?.intOrNull ?: 1
                tab.restoreSavedSnapshot(
                    // Old snapshots lacked placement and complete MCP fields.
                    // Reanchor clean tabs; retain the dirty flag for unsaved ones.
                    snapshot = if (snapshotFormat >= 2) obj["savedSnapshot"]?.jsonPrimitive?.content else null,
                    legacyDirtyFlag = legacyDirty,
                )

                state.openTabs.add(tab)
            }

            if (state.openTabs.isNotEmpty()) {
                val savedIndex = root["activeIndex"]?.jsonPrimitive?.intOrNull ?: 0
                state.activeTabIndex = savedIndex.coerceIn(0, state.openTabs.size - 1)
            } else {
                state.activeTabIndex = -1
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun formRowListJson(rows: List<MutableFormDataRow>): JsonArray =
        buildJsonArray {
            rows.forEach { r ->
                add(buildJsonObject {
                    put("key",         r.key)
                    put("type",        r.type.name)
                    put("value",       r.value)
                    put("description", r.description)
                    put("enabled",     r.enabled)
                    put("bytesBase64", r.bytesBase64)
                })
            }
        }

    private fun formRowFromJson(obj: JsonObject): MutableFormDataRow =
        MutableFormDataRow(
            key         = obj["key"]?.jsonPrimitive?.content ?: "",
            type        = safeEnum(obj["type"]?.jsonPrimitive?.content, FormEntryType.TEXT),
            value       = obj["value"]?.jsonPrimitive?.content ?: "",
            description = obj["description"]?.jsonPrimitive?.content ?: "",
            enabled     = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            bytesBase64 = obj["bytesBase64"]?.jsonPrimitive?.content ?: "",
        )

    private fun kvListJson(list: List<MutableKeyValue>): JsonArray =
        buildJsonArray {
            list.forEach { kv ->
                add(buildJsonObject {
                    put("key",     kv.key)
                    put("value",   kv.value)
                    put("enabled", kv.enabled)
                    put("secret",  kv.secret)
                    put("kind", kv.kind.name)
                    put("keyLocked", kv.keyLocked)
                })
            }
        }

    private fun kvFromJson(obj: JsonObject): MutableKeyValue =
        MutableKeyValue(
            key     = obj["key"]?.jsonPrimitive?.content     ?: "",
            value   = obj["value"]?.jsonPrimitive?.content   ?: "",
            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            secret  = obj["secret"]?.jsonPrimitive?.booleanOrNull  ?: false,
            kind = safeEnum(obj["kind"]?.jsonPrimitive?.content, HeaderKind.USER),
            keyLocked = obj["keyLocked"]?.jsonPrimitive?.booleanOrNull ?: false,
        )

    private inline fun <reified T : Enum<T>> safeEnum(name: String?, default: T): T =
        if (name == null) default else runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
