package io.github.makeforge.clapi.mcp.server.backend

import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestDefinition
import io.github.makeforge.clapi.core.model.ResponseDefinition
import io.github.makeforge.clapi.core.model.ResponseMetrics
import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthApiKey
import io.github.makeforge.clapi.core.model.opencollection.AuthConfig
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.AzureKeyVaultExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.CollectionConfig
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.CollectionProtobufConfig
import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretVariable
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecrets
import io.github.makeforge.clapi.core.model.opencollection.FileBody
import io.github.makeforge.clapi.core.model.opencollection.FileBodyVariant
import io.github.makeforge.clapi.core.model.opencollection.FolderInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.FormParamEntry
import io.github.makeforge.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.makeforge.clapi.core.model.opencollection.GcpSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBody
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyPayload
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyVariant
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.GrpcMetadata
import io.github.makeforge.clapi.core.model.opencollection.GrpcMethodType
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestBody
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParam
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParamType
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.ItemType
import io.github.makeforge.clapi.core.model.opencollection.KeePassXcExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.KeeperSecurityExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.MultipartFormBody
import io.github.makeforge.clapi.core.model.opencollection.MultipartPartEntry
import io.github.makeforge.clapi.core.model.opencollection.MultipartPartType
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionItem
import io.github.makeforge.clapi.core.model.opencollection.ProtoFile
import io.github.makeforge.clapi.core.model.opencollection.ProtoFileImportPath
import io.github.makeforge.clapi.core.model.opencollection.RawBody
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.RequestBodyType
import io.github.makeforge.clapi.core.model.opencollection.RequestDefaults
import io.github.makeforge.clapi.core.model.opencollection.Script
import io.github.makeforge.clapi.core.model.opencollection.ScriptType
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableEntry
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestSettings
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.model.storage.getStorageExtension
import io.github.makeforge.clapi.core.network.ApiClient
import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.NetworkEvent
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.execution.DefaultHttpExecutionAdapter
import io.github.makeforge.clapi.core.network.execution.HttpExecutionAdapter
import io.github.makeforge.clapi.core.network.execution.RequestExecutionService
import io.github.makeforge.clapi.core.network.grpc.DynamicGrpcClient
import io.github.makeforge.clapi.core.scripting.AssertionResult
import io.github.makeforge.clapi.core.scripting.ClapiScriptEngine
import io.github.makeforge.clapi.core.scripting.ScriptContext
import io.github.makeforge.clapi.core.scripting.ScriptEngine
import io.github.makeforge.clapi.core.storage.exporters.OpenApiExporter
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.core.storage.importers.SpecificationImporter
import io.github.makeforge.clapi.core.storage.opencollection.CanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.makeforge.clapi.core.storage.opencollection.fileExists
import io.github.makeforge.clapi.core.storage.opencollection.readTextFile
import io.github.makeforge.clapi.core.storage.opencollection.writeTextFile
import io.github.makeforge.clapi.mcp.server.inspection.JsonPathInspector
import io.github.makeforge.clapi.mcp.server.inspection.ResponseSnapshotCache
import io.github.makeforge.clapi.mcp.server.security.SecretSanitizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

@Serializable
internal data class HeadlessStoredVariable(
    val name: String,
    val value: String = "",
    val enabled: Boolean = true,
    val secret: Boolean = false,
    val kind: String? = null,
    val variableType: String = "string",
    val dynamicScript: String = "",
    val dynamicParameters: List<DynamicVariableParameter> = emptyList(),
)

@Serializable
internal data class HeadlessPrivateState(
    val globalVariables: List<HeadlessStoredVariable> = emptyList(),
    val activeEnvironment: String? = null,
    val scopedSecrets: Map<String, String> = emptyMap(),
)

/**
 * Headless [McpWorkspaceBackend] operating directly on [CanonicalCollectionRepository] (`workspace.json`),
 * `private-state.json`, [RequestExecutionService], [ScriptEngine], [SpecificationImporter],
 * [OpenApiExporter], and [GitRepositoryManager].
 */
class HeadlessWorkspaceBackend(
    val baseDirectory: String? = null,
    val canonicalRepository: CanonicalCollectionRepository = DefaultCanonicalCollectionRepository(baseDirectory = baseDirectory),
    apiClient: ApiClient = KtorApiClient(),
    grpcClient: DynamicGrpcClient? = null,
    val executionAdapter: HttpExecutionAdapter = DefaultHttpExecutionAdapter,
    val executionService: RequestExecutionService = RequestExecutionService(
        apiClient = apiClient,
        adapter = executionAdapter,
        grpcClient = grpcClient,
    ),
    val scriptEngine: ScriptEngine = ClapiScriptEngine(),
    val specificationImporter: SpecificationImporter = SpecificationImporter.default(),
    val gitRepositoryManager: GitRepositoryManager = if (baseDirectory != null) {
        GitRepositoryManager(baseStorageDir = "$baseDirectory/git-repos")
    } else {
        GitRepositoryManager()
    },
    val snapshotCache: ResponseSnapshotCache = ResponseSnapshotCache(),
) : McpWorkspaceBackend {

    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private var inMemoryPrivateState = HeadlessPrivateState()
    private val consoleLogs = ArrayDeque<SanitizedConsoleEntryDto>(200)
    private val stableItemIds = mutableMapOf<String, String>() // slashPath -> uuid

    private fun logConsole(message: String, level: String = "INFO", knownSecrets: Set<String> = emptySet()) {
        val clean = SecretSanitizer.scrubText(message, knownSecrets)
        synchronized(consoleLogs) {
            if (consoleLogs.size >= 200) {
                consoleLogs.removeFirst()
            }
            consoleLogs.addLast(
                SanitizedConsoleEntryDto(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    message = clean,
                )
            )
        }
    }

    private fun loadPrivateState(): HeadlessPrivateState {
        val dir = baseDirectory ?: return inMemoryPrivateState
        val path = "$dir/private-state.json"
        if (!fileExists(path)) return inMemoryPrivateState
        return runCatching {
            val raw = readTextFile(path)
            json.decodeFromString(HeadlessPrivateState.serializer(), raw).also {
                inMemoryPrivateState = it
            }
        }.getOrDefault(inMemoryPrivateState)
    }

    private fun savePrivateState(state: HeadlessPrivateState) {
        inMemoryPrivateState = state
        val dir = baseDirectory ?: return
        val path = "$dir/private-state.json"
        runCatching {
            val existingObj = if (fileExists(path)) {
                runCatching { json.parseToJsonElement(readTextFile(path)).jsonObject }.getOrNull()
            } else null
            val encoded = json.encodeToJsonElement(HeadlessPrivateState.serializer(), state).jsonObject
            val merged = buildJsonObject {
                existingObj?.forEach { (k, v) -> put(k, v) }
                encoded.forEach { (k, v) -> put(k, v) }
            }
            writeTextFile(path, json.encodeToString(JsonObject.serializer(), merged))
        }
    }

    private suspend fun loadOrCreateGlobalDoc(): OpenCollectionDocument {
        return canonicalRepository.loadGlobalCollection() ?: OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Workspace"),
            config = CollectionConfig(environments = emptyList()),
            items = emptyList(),
        )
    }

    private fun resolveNodeId(
        item: OpenCollectionItem,
        slashPath: String,
        globalDoc: OpenCollectionDocument,
    ): String {
        val extId = (item.extensions["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (extId != null) {
            stableItemIds[slashPath] = extId
            return extId
        }
        val clapiRequests = (globalDoc.extensions["clapi"] as? JsonObject)
            ?.get("requests") as? JsonObject
        val fromDocMeta = clapiRequests?.get(item.info.name)?.jsonObject
            ?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (fromDocMeta != null) {
            stableItemIds[slashPath] = fromDocMeta
            return fromDocMeta
        }
        return stableItemIds.getOrPut(slashPath) {
            UUID.nameUUIDFromBytes(slashPath.toByteArray()).toString()
        }
    }

    private fun withItemId(item: OpenCollectionItem, id: String): OpenCollectionItem {
        val newExt = item.extensions + ("id" to JsonPrimitive(id))
        return when (item) {
            is FolderItem -> item.copy(extensions = newExt)
            is HttpRequestItem -> item.copy(extensions = newExt)
            is GrpcRequestItem -> item.copy(extensions = newExt)
            is GraphQLRequestItem -> item.copy(extensions = newExt)
            is WebSocketRequestItem -> item.copy(extensions = newExt)
        }
    }

    private fun buildTreeFromDoc(doc: OpenCollectionDocument): List<WorkspaceTreeNodeDto> {
        fun mapItem(item: OpenCollectionItem, parentSegments: List<String>, isTopLevelCollection: Boolean): WorkspaceTreeNodeDto {
            val segments = parentSegments + item.info.name
            val slashPath = NodeLookupResolver.buildSlashPath(segments)
            val id = resolveNodeId(item, slashPath, doc)
            return when (item) {
                is FolderItem -> WorkspaceTreeNodeDto(
                    id = id,
                    name = item.info.name,
                    kind = if (isTopLevelCollection) "collection" else "folder",
                    method = null,
                    url = null,
                    slashPath = slashPath,
                    children = item.items.map { child -> mapItem(child, segments, isTopLevelCollection = false) },
                )
                is HttpRequestItem -> WorkspaceTreeNodeDto(
                    id = id,
                    name = item.info.name,
                    kind = "http",
                    method = item.http.method.uppercase(),
                    url = item.http.url,
                    slashPath = slashPath,
                )
                is GrpcRequestItem -> WorkspaceTreeNodeDto(
                    id = id,
                    name = item.info.name,
                    kind = "grpc",
                    method = item.grpc.methodType?.value ?: "unary",
                    url = item.grpc.url,
                    slashPath = slashPath,
                )
                is GraphQLRequestItem -> WorkspaceTreeNodeDto(
                    id = id,
                    name = item.info.name,
                    kind = "graphql",
                    method = (item.graphql.method ?: "POST").uppercase(),
                    url = item.graphql.url,
                    slashPath = slashPath,
                )
                is WebSocketRequestItem -> WorkspaceTreeNodeDto(
                    id = id,
                    name = item.info.name,
                    kind = "websocket",
                    method = "WS",
                    url = item.websocket.url,
                    slashPath = slashPath,
                )
            }
        }

        return doc.items.filterIsInstance<FolderItem>().map { col ->
            mapItem(col, emptyList(), isTopLevelCollection = true)
        }
    }

    // ── Tree, Collections & Folders ──────────────────────────────────────

    override suspend fun listTree(
        collectionFilter: String?,
        kindFilter: String?,
        query: String?,
    ): List<WorkspaceTreeNodeDto> = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        var roots = buildTreeFromDoc(doc)

        if (!collectionFilter.isNullOrBlank()) {
            val targetNode = NodeLookupResolver.resolve(roots, collectionFilter, setOf("collection"))
            roots = roots.filter { it.id == targetNode.id }
        }

        val normKind = kindFilter?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val normQuery = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (normKind == null && normQuery == null) {
            return@withLock roots
        }

        fun filterNode(node: WorkspaceTreeNodeDto): WorkspaceTreeNodeDto? {
            if (node.kind == "collection" || node.kind == "folder") {
                val filteredChildren = node.children.mapNotNull(::filterNode)
                val selfMatchesQuery = normQuery != null && node.name.lowercase().contains(normQuery)
                return if (filteredChildren.isNotEmpty() || (normKind == null && selfMatchesQuery)) {
                    node.copy(children = filteredChildren)
                } else {
                    null
                }
            }
            val kindMatches = normKind == null || node.kind.lowercase() == normKind
            val queryMatches = normQuery == null ||
                node.name.lowercase().contains(normQuery) ||
                node.url?.lowercase()?.contains(normQuery) == true ||
                node.slashPath.lowercase().contains(normQuery)
            return if (kindMatches && queryMatches) node else null
        }

        roots.mapNotNull(::filterNode)
    }

    override suspend fun getCollection(idOrPath: String): SanitizedCollectionDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val node = NodeLookupResolver.resolve(roots, idOrPath, setOf("collection"))
        val folder = findCollectionFolderById(doc, node.id, node.slashPath)
            ?: throw NodeNotFoundException(idOrPath, "collection")
        toSanitizedCollectionDto(folder, node)
    }

    override suspend fun manageCollection(input: ManageCollectionInput): SanitizedCollectionDto = mutex.withLock {
        val action = input.action.trim().lowercase()
        when (action) {
            "create" -> {
                val rawName = input.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Collection 'name' is required for action='create'.")
                val doc = loadOrCreateGlobalDoc()
                val existingNames = doc.items.filterIsInstance<FolderItem>().map { it.info.name }.toSet()
                val uniqueName = io.github.makeforge.clapi.core.storage.opencollection.generateUniqueName(rawName, existingNames)
                val newId = UUID.randomUUID().toString()
                val defaults = buildRequestDefaults(
                    existing = null,
                    authInput = input.auth,
                    headersInput = input.headers,
                    variablesInput = null,
                    preRequestScript = input.preRequestScript,
                    testScript = input.testScript,
                )
                val ext = buildCollectionExtensions(
                    id = newId,
                    existing = emptyMap(),
                    summary = input.summary,
                    version = input.version,
                    protoFiles = input.protoFiles,
                    importPaths = input.importPaths,
                )
                val folder = FolderItem(
                    info = FolderInfo(
                        name = uniqueName,
                        description = input.summary?.let { Description(content = it) },
                    ),
                    items = emptyList(),
                    request = defaults,
                    extensions = ext,
                )
                val updatedDoc = doc.copy(items = doc.items + folder)
                canonicalRepository.saveGlobalCollection(updatedDoc)
                val slashPath = NodeLookupResolver.buildSlashPath(listOf(uniqueName))
                stableItemIds[slashPath] = newId
                val treeNode = buildTreeFromDoc(updatedDoc).first { it.id == newId }
                toSanitizedCollectionDto(folder, treeNode)
            }
            "update" -> {
                val targetId = input.collectionId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: input.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("'collectionId' is required for action='update'.")
                val doc = loadOrCreateGlobalDoc()
                val roots = buildTreeFromDoc(doc)
                val node = NodeLookupResolver.resolve(roots, targetId, setOf("collection"))
                val existingFolder = findCollectionFolderById(doc, node.id, node.slashPath)
                    ?: throw NodeNotFoundException(targetId, "collection")
                val newName = input.name?.trim()?.takeIf { it.isNotEmpty() } ?: existingFolder.info.name
                val updatedDefaults = buildRequestDefaults(
                    existing = existingFolder.request,
                    authInput = input.auth,
                    headersInput = input.headers,
                    variablesInput = null,
                    preRequestScript = input.preRequestScript,
                    testScript = input.testScript,
                )
                val updatedExt = buildCollectionExtensions(
                    id = node.id,
                    existing = existingFolder.extensions,
                    summary = input.summary,
                    version = input.version,
                    protoFiles = input.protoFiles,
                    importPaths = input.importPaths,
                )
                val updatedFolder = existingFolder.copy(
                    info = existingFolder.info.copy(
                        name = newName,
                        description = input.summary?.let { Description(content = it) } ?: existingFolder.info.description,
                    ),
                    request = updatedDefaults,
                    extensions = updatedExt,
                )
                val updatedItems = doc.items.map { item ->
                    if (item is FolderItem && resolveNodeId(item, NodeLookupResolver.buildSlashPath(listOf(item.info.name)), doc) == node.id) {
                        updatedFolder
                    } else item
                }
                val updatedDoc = doc.copy(items = updatedItems)
                canonicalRepository.saveGlobalCollection(updatedDoc)
                val newSlashPath = NodeLookupResolver.buildSlashPath(listOf(newName))
                stableItemIds[newSlashPath] = node.id
                val updatedTreeNode = buildTreeFromDoc(updatedDoc).first { it.id == node.id }
                toSanitizedCollectionDto(updatedFolder, updatedTreeNode)
            }
            else -> throw IllegalArgumentException("Unsupported action '$action' for clapi_manage_collection.")
        }
    }

    override suspend fun deleteCollection(idOrPath: String, confirm: Boolean): DeleteResultDto = mutex.withLock {
        if (!confirm) {
            throw DestructiveConfirmationRequiredException("delete_collection", idOrPath)
        }
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val node = NodeLookupResolver.resolve(roots, idOrPath, setOf("collection"))
        val updatedItems = doc.items.filterNot { item ->
            item is FolderItem && resolveNodeId(item, NodeLookupResolver.buildSlashPath(listOf(item.info.name)), doc) == node.id
        }
        canonicalRepository.saveGlobalCollection(doc.copy(items = updatedItems))
        DeleteResultDto(deleted = true, id = node.id, name = node.name, kind = "collection")
    }

    override suspend fun manageFolder(input: ManageFolderInput): SanitizedFolderDto = mutex.withLock {
        val action = input.action.trim().lowercase()
        when (action) {
            "create" -> {
                val parentId = input.parentId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("'parentId' (collection or parent folder) is required for action='create'.")
                val folderName = input.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Folder 'name' is required for action='create'.")
                val doc = loadOrCreateGlobalDoc()
                val roots = buildTreeFromDoc(doc)
                val parentNode = NodeLookupResolver.resolve(roots, parentId, setOf("collection", "folder"))
                val newId = UUID.randomUUID().toString()
                val defaults = buildRequestDefaults(
                    existing = null,
                    authInput = input.auth,
                    headersInput = input.headers,
                    variablesInput = null,
                    preRequestScript = input.preRequestScript,
                    testScript = input.testScript,
                )
                val newFolder = FolderItem(
                    info = FolderInfo(name = folderName),
                    items = emptyList(),
                    request = defaults,
                    extensions = mapOf("id" to JsonPrimitive(newId)),
                )
                val updatedDoc = insertItemIntoParent(doc, roots, parentNode.id, newFolder)
                canonicalRepository.saveGlobalCollection(updatedDoc)
                val newSlashPath = "${parentNode.slashPath}/${NodeLookupResolver.escapeSegment(folderName)}"
                stableItemIds[newSlashPath] = newId
                val updatedRoots = buildTreeFromDoc(updatedDoc)
                val createdNode = NodeLookupResolver.resolve(updatedRoots, newId, setOf("folder"))
                val collectionId = findEnclosingCollectionId(updatedRoots, newId) ?: parentNode.id
                toSanitizedFolderDto(
                    folder = newFolder,
                    node = createdNode,
                    collectionId = collectionId,
                    parentFolderId = if (parentNode.kind == "folder") parentNode.id else null,
                )
            }
            "update" -> {
                val folderId = input.folderId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("'folderId' is required for action='update'.")
                val doc = loadOrCreateGlobalDoc()
                val roots = buildTreeFromDoc(doc)
                val targetNode = NodeLookupResolver.resolve(roots, folderId, setOf("folder"))
                val existingFolder = findItemById(doc, roots, targetNode.id) as? FolderItem
                    ?: throw NodeNotFoundException(folderId, "folder")
                val updatedDefaults = buildRequestDefaults(
                    existing = existingFolder.request,
                    authInput = input.auth,
                    headersInput = input.headers,
                    variablesInput = null,
                    preRequestScript = input.preRequestScript,
                    testScript = input.testScript,
                )
                val newName = input.name?.trim()?.takeIf { it.isNotEmpty() } ?: existingFolder.info.name
                val updatedFolder = withItemId(
                    existingFolder.copy(
                        info = existingFolder.info.copy(name = newName),
                        request = updatedDefaults,
                    ),
                    targetNode.id,
                ) as FolderItem

                var updatedDoc = replaceItemById(doc, roots, targetNode.id, updatedFolder)
                if (!input.parentId.isNullOrBlank()) {
                    val midRoots = buildTreeFromDoc(updatedDoc)
                    val newParent = NodeLookupResolver.resolve(midRoots, input.parentId, setOf("collection", "folder"))
                    val withoutOld = removeItemById(updatedDoc, midRoots, targetNode.id)
                    val withoutRoots = buildTreeFromDoc(withoutOld)
                    updatedDoc = insertItemIntoParent(withoutOld, withoutRoots, newParent.id, updatedFolder)
                }
                canonicalRepository.saveGlobalCollection(updatedDoc)
                val updatedRoots = buildTreeFromDoc(updatedDoc)
                val updatedNode = NodeLookupResolver.resolve(updatedRoots, targetNode.id, setOf("folder"))
                val collectionId = findEnclosingCollectionId(updatedRoots, targetNode.id).orEmpty()
                val parentFolderId = findParentFolderId(updatedRoots, targetNode.id)
                toSanitizedFolderDto(updatedFolder, updatedNode, collectionId, parentFolderId)
            }
            else -> throw IllegalArgumentException("Unsupported action '$action' for clapi_manage_folder.")
        }
    }

    override suspend fun deleteFolder(idOrPath: String, confirm: Boolean): DeleteResultDto = mutex.withLock {
        if (!confirm) {
            throw DestructiveConfirmationRequiredException("delete_folder", idOrPath)
        }
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val targetNode = NodeLookupResolver.resolve(roots, idOrPath, setOf("folder"))
        val updatedDoc = removeItemById(doc, roots, targetNode.id)
        canonicalRepository.saveGlobalCollection(updatedDoc)
        DeleteResultDto(deleted = true, id = targetNode.id, name = targetNode.name, kind = "folder")
    }

    // ── Multi-Protocol Requests ──────────────────────────────────────────

    private val requestKinds = setOf("http", "grpc", "graphql", "websocket")

    override suspend fun getRequest(idOrPath: String): SanitizedRequestDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val node = NodeLookupResolver.resolve(roots, idOrPath, requestKinds)
        val item = findItemById(doc, roots, node.id)
            ?: throw NodeNotFoundException(idOrPath, "request")
        val collectionId = findEnclosingCollectionId(roots, node.id).orEmpty()
        val parentFolderId = findParentFolderId(roots, node.id)
        toSanitizedRequestDto(item, node, collectionId, parentFolderId)
    }

    override suspend fun upsertRequest(input: UpsertRequestInput): SanitizedRequestDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)

        val existingNode: WorkspaceTreeNodeDto? = input.requestId?.trim()?.takeIf { it.isNotEmpty() }?.let { reqId ->
            runCatching { NodeLookupResolver.resolve(roots, reqId, requestKinds) }.getOrNull()
        }

        if (existingNode != null) {
            val existingItem = findItemById(doc, roots, existingNode.id)
                ?: throw NodeNotFoundException(existingNode.id, "request")
            val updatedItem = buildUpdatedRequestItem(existingItem, existingNode.id, input)
            var updatedDoc = replaceItemById(doc, roots, existingNode.id, updatedItem)
            if (!input.parentId.isNullOrBlank()) {
                val midRoots = buildTreeFromDoc(updatedDoc)
                val newParent = NodeLookupResolver.resolve(midRoots, input.parentId, setOf("collection", "folder"))
                val withoutOld = removeItemById(updatedDoc, midRoots, existingNode.id)
                val withoutRoots = buildTreeFromDoc(withoutOld)
                updatedDoc = insertItemIntoParent(withoutOld, withoutRoots, newParent.id, updatedItem)
            }
            canonicalRepository.saveGlobalCollection(updatedDoc)
            val updatedRoots = buildTreeFromDoc(updatedDoc)
            val finalNode = NodeLookupResolver.resolve(updatedRoots, existingNode.id, requestKinds)
            val collectionId = findEnclosingCollectionId(updatedRoots, finalNode.id).orEmpty()
            val parentFolderId = findParentFolderId(updatedRoots, finalNode.id)
            toSanitizedRequestDto(updatedItem, finalNode, collectionId, parentFolderId)
        } else {
            // Create new request
            val parentNode: WorkspaceTreeNodeDto
            val requestName: String
            if (!input.parentId.isNullOrBlank()) {
                parentNode = NodeLookupResolver.resolve(roots, input.parentId, setOf("collection", "folder"))
                requestName = input.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: input.requestId?.let { NodeLookupResolver.parseSlashPath(it).lastOrNull() }
                    ?: throw IllegalArgumentException("Request 'name' is required when creating a new request.")
            } else if (!input.requestId.isNullOrBlank()) {
                val segments = NodeLookupResolver.parseSlashPath(input.requestId)
                if (segments.size < 2) {
                    throw IllegalArgumentException("To create a new request, provide 'parentId' (collection or folder) and 'name', or a slash path 'Collection/RequestName' in 'requestId'.")
                }
                val parentPath = NodeLookupResolver.buildSlashPath(segments.dropLast(1))
                parentNode = NodeLookupResolver.resolve(roots, parentPath, setOf("collection", "folder"))
                requestName = input.name?.trim()?.takeIf { it.isNotEmpty() } ?: segments.last()
            } else {
                throw IllegalArgumentException("Creating a request requires 'parentId' and 'name'.")
            }

            val newId = UUID.randomUUID().toString()
            val createdItem = buildNewRequestItem(newId, requestName, input)
            val updatedDoc = insertItemIntoParent(doc, roots, parentNode.id, createdItem)
            canonicalRepository.saveGlobalCollection(updatedDoc)
            val newSlashPath = "${parentNode.slashPath}/${NodeLookupResolver.escapeSegment(requestName)}"
            stableItemIds[newSlashPath] = newId
            val updatedRoots = buildTreeFromDoc(updatedDoc)
            val createdNode = NodeLookupResolver.resolve(updatedRoots, newId, requestKinds)
            val collectionId = findEnclosingCollectionId(updatedRoots, newId) ?: parentNode.id
            val parentFolderId = if (parentNode.kind == "folder") parentNode.id else null
            toSanitizedRequestDto(createdItem, createdNode, collectionId, parentFolderId)
        }
    }

    override suspend fun deleteRequest(idOrPath: String, confirm: Boolean): DeleteResultDto = mutex.withLock {
        if (!confirm) {
            throw DestructiveConfirmationRequiredException("delete_request", idOrPath)
        }
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val targetNode = NodeLookupResolver.resolve(roots, idOrPath, requestKinds)
        val updatedDoc = removeItemById(doc, roots, targetNode.id)
        canonicalRepository.saveGlobalCollection(updatedDoc)
        snapshotCache.remove(targetNode.id)
        DeleteResultDto(deleted = true, id = targetNode.id, name = targetNode.name, kind = targetNode.kind)
    }

    // ── Execution, Inspection & Test Suite ───────────────────────────────

    override suspend fun executeRequest(input: ExecuteRequestInput): ExecutionResultDto {
        val snapshot = executeSingleRequestInternal(
            idOrPath = input.requestId,
            environmentNameOverride = input.environmentName,
            transientVariables = input.transientVariables,
        )
        return ResponseSnapshotCache.toExecutionResultDto(snapshot, input.maxBodyLength)
    }

    override suspend fun inspectResponse(input: InspectResponseInput): InspectResponseResultDto {
        val node = mutex.withLock {
            val doc = loadOrCreateGlobalDoc()
            val roots = buildTreeFromDoc(doc)
            NodeLookupResolver.resolve(roots, input.requestId, requestKinds)
        }
        val snapshot = snapshotCache.get(node.id)
            ?: throw IllegalStateException("No cached execution response found for request '${node.slashPath}' (id=${node.id}). Execute the request first with clapi_execute_request.")
        if (snapshot.isBinary) {
            return InspectResponseResultDto(
                requestId = node.id,
                jsonPath = input.jsonPath,
                byteOffset = 0,
                returnedBytes = 0,
                totalBytes = snapshot.totalBytes.toInt(),
                hasMore = false,
                content = "[Binary payload: ${snapshot.contentType ?: "application/octet-stream"}, ${snapshot.totalBytes} bytes]",
            )
        }
        return JsonPathInspector.inspect(
            requestId = node.id,
            sanitizedBodyText = snapshot.sanitizedBodyText,
            jsonPath = input.jsonPath,
            byteOffset = input.byteOffset,
            length = input.length,
        )
    }

    override suspend fun getLatestResponse(idOrPath: String, maxBodyLength: Int): ExecutionResultDto? {
        val node = mutex.withLock {
            val doc = loadOrCreateGlobalDoc()
            val roots = buildTreeFromDoc(doc)
            runCatching { NodeLookupResolver.resolve(roots, idOrPath, requestKinds) }.getOrNull()
        } ?: return null
        val snapshot = snapshotCache.get(node.id) ?: return null
        return ResponseSnapshotCache.toExecutionResultDto(snapshot, maxBodyLength)
    }

    override suspend fun runTestSuite(input: RunTestSuiteInput): TestSuiteResultDto {
        val (targetNode, executableRequests) = mutex.withLock {
            val doc = loadOrCreateGlobalDoc()
            val roots = buildTreeFromDoc(doc)
            val target = NodeLookupResolver.resolve(roots, input.targetId, setOf("collection", "folder"))
            val leaves = NodeLookupResolver.flatten(listOf(target)).filter {
                it.kind in setOf("http", "grpc", "graphql")
            }
            target to leaves
        }

        val startTime = System.currentTimeMillis()
        val itemResults = mutableListOf<TestSuiteItemResultDto>()
        var passedReqs = 0
        var failedReqs = 0
        var totalAsserts = 0
        var passedAsserts = 0
        var failedAsserts = 0

        for (reqNode in executableRequests) {
            val reqStart = System.currentTimeMillis()
            val itemResult = try {
                val snapshot = executeSingleRequestInternal(
                    idOrPath = reqNode.id,
                    environmentNameOverride = input.environmentName,
                    transientVariables = emptyMap(),
                )
                val assertions = snapshot.assertions
                val allAssertionsPassed = assertions.all { it.passed }
                val statusOk = snapshot.statusCode in 200..399 || (assertions.isNotEmpty() && allAssertionsPassed)
                val passed = statusOk && allAssertionsPassed && snapshot.scriptError == null
                TestSuiteItemResultDto(
                    requestId = reqNode.id,
                    name = reqNode.name,
                    slashPath = reqNode.slashPath,
                    statusCode = snapshot.statusCode,
                    passed = passed,
                    durationMs = snapshot.metrics.responseTimeMs,
                    assertions = assertions,
                    error = snapshot.scriptError,
                )
            } catch (e: Exception) {
                TestSuiteItemResultDto(
                    requestId = reqNode.id,
                    name = reqNode.name,
                    slashPath = reqNode.slashPath,
                    statusCode = 0,
                    passed = false,
                    durationMs = (System.currentTimeMillis() - reqStart).coerceAtLeast(0),
                    assertions = emptyList(),
                    error = e.message ?: "Execution failed",
                )
            }

            itemResults.add(itemResult)
            if (itemResult.passed) passedReqs++ else failedReqs++
            totalAsserts += itemResult.assertions.size
            passedAsserts += itemResult.assertions.count { it.passed }
            failedAsserts += itemResult.assertions.count { !it.passed }

            if (!itemResult.passed && input.stopOnFirstFailure) {
                break
            }
        }

        val totalDuration = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
        return TestSuiteResultDto(
            targetName = targetNode.slashPath,
            totalRequests = itemResults.size,
            passedRequests = passedReqs,
            failedRequests = failedReqs,
            totalAssertions = totalAsserts,
            passedAssertions = passedAsserts,
            failedAssertions = failedAsserts,
            durationMs = totalDuration,
            results = itemResults,
        )
    }

    override suspend fun getConsoleLogs(limit: Int): List<SanitizedConsoleEntryDto> {
        synchronized(consoleLogs) {
            return consoleLogs.toList().takeLast(limit.coerceIn(1, 200))
        }
    }

    // ── Environments & Variables ─────────────────────────────────────────

    override suspend fun listEnvironments(includeVariables: Boolean): EnvironmentsOverviewDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val priv = loadPrivateState()
        val envs = doc.config?.environments.orEmpty()
        EnvironmentsOverviewDto(
            activeEnvironment = priv.activeEnvironment,
            globalVariables = priv.globalVariables.map(::toSanitizedGlobalVariableDto),
            environments = envs.map { env ->
                toSanitizedEnvironmentDto(env, envs, priv.activeEnvironment, includeVariables)
            },
        )
    }

    override suspend fun getEnvironmentDetails(name: String): SanitizedEnvironmentDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val priv = loadPrivateState()
        val envs = doc.config?.environments.orEmpty()
        val env = envs.firstOrNull { it.name.equals(name.trim(), ignoreCase = false) }
            ?: envs.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: throw NodeNotFoundException(name, "environment")
        toSanitizedEnvironmentDto(env, envs, priv.activeEnvironment, includeVariables = true)
    }

    override suspend fun setActiveEnvironment(name: String?): EnvironmentsOverviewDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val priv = loadPrivateState()
        val envs = doc.config?.environments.orEmpty()
        val targetName = name?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedName = if (targetName != null) {
            val found = envs.firstOrNull { it.name == targetName }
                ?: envs.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                ?: throw NodeNotFoundException(targetName, "environment")
            found.name
        } else null
        val updatedPriv = priv.copy(activeEnvironment = resolvedName)
        savePrivateState(updatedPriv)
        EnvironmentsOverviewDto(
            activeEnvironment = resolvedName,
            globalVariables = updatedPriv.globalVariables.map(::toSanitizedGlobalVariableDto),
            environments = envs.map { env ->
                toSanitizedEnvironmentDto(env, envs, resolvedName, includeVariables = true)
            },
        )
    }

    override suspend fun upsertEnvironment(input: UpsertEnvironmentInput): SanitizedEnvironmentDto = mutex.withLock {
        val targetName = input.name.trim()
        if (targetName.isEmpty()) {
            throw IllegalArgumentException("Environment 'name' cannot be empty.")
        }
        val doc = loadOrCreateGlobalDoc()
        val priv = loadPrivateState()
        val envs = doc.config?.environments.orEmpty().toMutableList()
        val existingIdx = envs.indexOfFirst { it.name.equals(targetName, ignoreCase = true) }
        val finalName = input.newName?.trim()?.takeIf { it.isNotEmpty() } ?: targetName
        val parentExtends = input.extends?.trim()?.takeIf { it.isNotEmpty() }

        if (parentExtends != null) {
            validateNoEnvironmentCycle(finalName, parentExtends, envs)
        }

        val extSecrets = if (input.externalSecrets != null) {
            buildExternalSecrets(input.externalSecretsProvider, input.externalSecrets)
        } else {
            envs.getOrNull(existingIdx)?.externalSecrets
        }

        val updatedEnv = if (existingIdx >= 0) {
            val prev = envs[existingIdx]
            prev.copy(
                name = finalName,
                color = input.color ?: prev.color,
                extends = if (input.extends != null) parentExtends else prev.extends,
                description = input.description?.let { Description(content = it) } ?: prev.description,
                externalSecrets = extSecrets,
            ).also { envs[existingIdx] = it }
        } else {
            Environment(
                name = finalName,
                color = input.color,
                extends = parentExtends,
                description = input.description?.let { Description(content = it) },
                variables = emptyList(),
                externalSecrets = extSecrets,
            ).also { envs.add(it) }
        }

        val updatedDoc = doc.copy(
            config = (doc.config ?: CollectionConfig()).copy(environments = envs)
        )
        canonicalRepository.saveGlobalCollection(updatedDoc)
        toSanitizedEnvironmentDto(updatedEnv, envs, priv.activeEnvironment, includeVariables = true)
    }

    override suspend fun deleteEnvironment(name: String, confirm: Boolean): DeleteResultDto = mutex.withLock {
        if (!confirm) {
            throw DestructiveConfirmationRequiredException("delete_environment", name)
        }
        val doc = loadOrCreateGlobalDoc()
        val priv = loadPrivateState()
        val envs = doc.config?.environments.orEmpty()
        val target = envs.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: throw NodeNotFoundException(name, "environment")
        val remaining = envs.filterNot { it.name == target.name }
        canonicalRepository.saveGlobalCollection(
            doc.copy(config = (doc.config ?: CollectionConfig()).copy(environments = remaining))
        )
        if (priv.activeEnvironment == target.name) {
            savePrivateState(priv.copy(activeEnvironment = null))
        }
        DeleteResultDto(deleted = true, id = target.name, name = target.name, kind = "environment")
    }

    override suspend fun setVariable(input: SetVariableInput): SanitizedVariableDto = mutex.withLock {
        val key = input.key.trim()
        if (key.isEmpty()) throw IllegalArgumentException("Variable 'key' cannot be blank.")
        val scope = input.scope.trim().lowercase()
        val action = input.action.trim().lowercase()
        val kind = input.kind.trim().lowercase()
        val varType = VariableType.fromValue(input.variableType) ?: VariableType.STRING

        when (scope) {
            "global" -> {
                val priv = loadPrivateState()
                val globals = priv.globalVariables.toMutableList()
                val idx = globals.indexOfFirst { it.name == key }
                if (action == "delete") {
                    if (idx >= 0) globals.removeAt(idx)
                    savePrivateState(priv.copy(globalVariables = globals))
                    return@withLock SanitizedVariableDto(key = key, value = "", kind = kind, variableType = varType.value, enabled = false)
                }
                val prev = globals.getOrNull(idx)
                val effectiveValue = if (kind == "secret") {
                    SecretSanitizer.preserveSecretIfMasked(input.value, prev?.value.orEmpty())
                } else {
                    input.value
                }
                val stored = HeadlessStoredVariable(
                    name = key,
                    value = effectiveValue,
                    enabled = input.enabled,
                    secret = kind == "secret",
                    kind = kind,
                    variableType = varType.value,
                    dynamicScript = input.dynamicScript.orEmpty(),
                    dynamicParameters = input.dynamicParameters,
                )
                if (idx >= 0) globals[idx] = stored else globals.add(stored)
                savePrivateState(priv.copy(globalVariables = globals))
                toSanitizedGlobalVariableDto(stored)
            }
            "environment" -> {
                val priv = loadPrivateState()
                val envName = input.target?.trim()?.takeIf { it.isNotEmpty() }
                    ?: priv.activeEnvironment
                    ?: throw IllegalArgumentException("'target' (environment name) is required when scope='environment' and no active environment is set.")
                val doc = loadOrCreateGlobalDoc()
                val envs = doc.config?.environments.orEmpty().toMutableList()
                val envIdx = envs.indexOfFirst { it.name.equals(envName, ignoreCase = true) }
                if (envIdx < 0) throw NodeNotFoundException(envName, "environment")
                val env = envs[envIdx]
                val secretKey = scopedSecretKey("environment", env.name, key)
                val updatedSecrets = priv.scopedSecrets.toMutableMap()
                if (action == "delete") {
                    updatedSecrets.remove(secretKey)
                } else if (kind == "secret") {
                    val prevSecret = updatedSecrets[secretKey].orEmpty()
                    updatedSecrets[secretKey] = SecretSanitizer.preserveSecretIfMasked(input.value, prevSecret)
                } else {
                    updatedSecrets.remove(secretKey)
                }
                savePrivateState(priv.copy(scopedSecrets = updatedSecrets))

                val updatedVars = mutateVariableEntryList(env.variables, key, action, kind, varType, input)
                envs[envIdx] = env.copy(variables = updatedVars)
                canonicalRepository.saveGlobalCollection(
                    doc.copy(config = (doc.config ?: CollectionConfig()).copy(environments = envs))
                )
                val entry = updatedVars.firstOrNull { it.name == key }
                entry?.let { e -> SecretSanitizer.sanitizeVariable(e) { updatedSecrets[scopedSecretKey("environment", env.name, it)] } }
                    ?: SanitizedVariableDto(key = key, value = "", kind = kind, variableType = varType.value, enabled = false)
            }
            "collection", "folder" -> {
                val targetId = input.target?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("'target' (collection or folder ID/path) is required when scope='$scope'.")
                val doc = loadOrCreateGlobalDoc()
                val priv = loadPrivateState()
                val roots = buildTreeFromDoc(doc)
                val node = NodeLookupResolver.resolve(roots, targetId, setOf(scope))
                val folder = (if (scope == "collection") {
                    findCollectionFolderById(doc, node.id, node.slashPath)
                } else {
                    findItemById(doc, roots, node.id) as? FolderItem
                }) ?: throw NodeNotFoundException(targetId, scope)

                val secretKey = scopedSecretKey(scope, node.id, key)
                val updatedSecrets = priv.scopedSecrets.toMutableMap()
                if (action == "delete") {
                    updatedSecrets.remove(secretKey)
                } else if (kind == "secret") {
                    val prevSecret = updatedSecrets[secretKey].orEmpty()
                    updatedSecrets[secretKey] = SecretSanitizer.preserveSecretIfMasked(input.value, prevSecret)
                } else {
                    updatedSecrets.remove(secretKey)
                }
                savePrivateState(priv.copy(scopedSecrets = updatedSecrets))

                val existingDefaults = folder.request ?: RequestDefaults()
                val updatedVars = mutateVariableEntryList(existingDefaults.variables, key, action, kind, varType, input)
                val updatedFolder = folder.copy(
                    request = existingDefaults.copy(variables = updatedVars)
                )
                val updatedDoc = replaceItemById(doc, roots, node.id, updatedFolder)
                canonicalRepository.saveGlobalCollection(updatedDoc)
                val entry = updatedVars.firstOrNull { it.name == key }
                entry?.let { e -> SecretSanitizer.sanitizeVariable(e) { updatedSecrets[scopedSecretKey(scope, node.id, it)] } }
                    ?: SanitizedVariableDto(key = key, value = "", kind = kind, variableType = varType.value, enabled = false)
            }
            else -> throw IllegalArgumentException("Unsupported variable scope '$scope'. Expected 'global', 'environment', 'collection', or 'folder'.")
        }
    }

    // ── Import, Export & Git Sync ────────────────────────────────────────

    override suspend fun importSpecification(input: ImportSpecInput): SanitizedCollectionDto = mutex.withLock {
        val raw = when {
            !input.rawContent.isNullOrBlank() -> input.rawContent
            !input.filePath.isNullOrBlank() -> {
                if (!fileExists(input.filePath)) {
                    throw IllegalArgumentException("Specification file not found at '${input.filePath}'.")
                }
                readTextFile(input.filePath)
            }
            else -> throw IllegalArgumentException("Either 'rawContent' or 'filePath' must be provided to clapi_import_specification.")
        }

        val scanResult = specificationImporter.scanContent(raw)
        val successResult = when (scanResult) {
            is ImportScanResult.Success -> scanResult
            is ImportScanResult.Failure -> {
                val details = if (scanResult.details.isNotEmpty()) " (${scanResult.details.joinToString("; ")})" else ""
                throw IllegalArgumentException("Specification import failed: ${scanResult.message}$details")
            }
        }

        val doc = loadOrCreateGlobalDoc()
        val existingNames = doc.items.filterIsInstance<FolderItem>().map { it.info.name }.toSet()
        val desiredName = input.collectionNameOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: successResult.specTitle.takeIf { it.isNotBlank() }
            ?: successResult.folders.firstOrNull()?.name
            ?: "Imported Collection"
        val uniqueName = io.github.makeforge.clapi.core.storage.opencollection.generateUniqueName(desiredName, existingNames)
        val newId = UUID.randomUUID().toString()
        val importedItems: List<OpenCollectionItem> = if (
            successResult.folders.size == 1 &&
            successResult.folders.first().name.equals(successResult.specTitle, ignoreCase = true)
        ) {
            successResult.folders.first().folder.items
        } else {
            successResult.folders.map { it.folder }
        }
        val importedRequestDefaults = if (successResult.folders.size == 1) {
            successResult.folders.first().folder.request
        } else null

        val ext = buildCollectionExtensions(
            id = newId,
            existing = emptyMap(),
            summary = null,
            version = null,
            protoFiles = null,
            importPaths = null,
        )
        val newFolder = FolderItem(
            info = FolderInfo(name = uniqueName),
            items = importedItems,
            request = importedRequestDefaults,
            extensions = ext,
        )

        val importedEnvs = successResult.environments.map { it.environment }
        val mergedEnvs = mergeEnvironments(doc.config?.environments.orEmpty(), importedEnvs)
        val updatedDoc = doc.copy(
            config = (doc.config ?: CollectionConfig()).copy(environments = mergedEnvs),
            items = doc.items + newFolder,
        )
        canonicalRepository.saveGlobalCollection(updatedDoc)
        val slashPath = NodeLookupResolver.buildSlashPath(listOf(uniqueName))
        stableItemIds[slashPath] = newId
        val treeNode = buildTreeFromDoc(updatedDoc).first { it.id == newId }
        toSanitizedCollectionDto(newFolder, treeNode)
    }

    override suspend fun exportCollection(input: ExportCollectionInput): ExportResultDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val node = NodeLookupResolver.resolve(roots, input.collectionId, setOf("collection"))
        val folder = findCollectionFolderById(doc, node.id, node.slashPath)
            ?: throw NodeNotFoundException(input.collectionId, "collection")
        val standaloneDoc = SecretSanitizer.sanitizeDocumentForExport(
            OpenCollectionDocument(
                opencollection = "1.0.0",
                info = CollectionInfo(
                    name = folder.info.name,
                    summary = folder.info.description?.content,
                ),
                config = doc.config,
                request = folder.request,
                items = folder.items,
                extensions = folder.extensions,
            )
        )
        val format = input.format.trim().lowercase()
        val content = when (format) {
            "opencollection-yaml" -> OpenCollectionSerializer.serializeYaml(standaloneDoc)
            "opencollection-json" -> OpenCollectionSerializer.serializeJson(standaloneDoc)
            "openapi-yaml" -> OpenApiExporter.exportToYaml(standaloneDoc)
            "openapi-json" -> json.encodeToString(JsonObject.serializer(), OpenApiExporter.exportToJsonElement(standaloneDoc))
            else -> throw IllegalArgumentException("Unsupported export format '$format'. Expected 'opencollection-yaml', 'opencollection-json', 'openapi-yaml', or 'openapi-json'.")
        }
        ExportResultDto(
            collectionName = folder.info.name,
            format = format,
            content = content,
        )
    }

    override suspend fun gitSync(input: GitSyncInput): GitSyncResultDto = mutex.withLock {
        val doc = loadOrCreateGlobalDoc()
        val roots = buildTreeFromDoc(doc)
        val node = NodeLookupResolver.resolve(roots, input.collectionId, setOf("collection"))
        val folder = findCollectionFolderById(doc, node.id, node.slashPath)
            ?: throw NodeNotFoundException(input.collectionId, "collection")
        val storageExt = folder.extensions.getStorageExtension()
        if (storageExt?.type != StorageType.GIT || storageExt.git?.remoteUrl.isNullOrBlank()) {
            throw IllegalArgumentException("Collection '${folder.info.name}' is not configured for Git synchronization.")
        }
        val remoteUrl = storageExt.git!!.remoteUrl
        val branch = storageExt.git!!.branch.ifBlank { "main" }
        val repoDir = gitRepositoryManager.getRepositoryDirectory(remoteUrl)
        val colDir = gitRepositoryManager.getCollectionDirectory(remoteUrl, folder.info.name)
        val action = input.action.trim().lowercase()

        when (action) {
            "status" -> {
                val st = gitRepositoryManager.checkStatus(repoDir)
                GitSyncResultDto(
                    collectionName = folder.info.name,
                    action = "status",
                    branch = st.branch.ifBlank { branch },
                    remoteUrl = remoteUrl,
                    status = if (st.isClean) "CLEAN" else if (st.hasConflicts) "CONFLICTED" else "MODIFIED",
                    modifiedFiles = st.changes.map { it.relativePath },
                    message = st.lastError ?: if (st.isClean) "Working tree is clean." else "${st.changes.size} modified file(s).",
                )
            }
            "commit" -> {
                val msg = input.commitMessage?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("'commitMessage' is required when action='commit'.")
                val standalone = OpenCollectionDocument(
                    opencollection = "1.0.0",
                    info = CollectionInfo(name = folder.info.name, summary = folder.info.description?.content),
                    request = folder.request,
                    items = folder.items,
                    extensions = folder.extensions,
                )
                writeTextFile("$colDir/collection.opencollection.yaml", OpenCollectionSerializer.serializeYaml(standalone))
                val res = gitRepositoryManager.commit(repoDir, msg)
                val st = gitRepositoryManager.checkStatus(repoDir)
                GitSyncResultDto(
                    collectionName = folder.info.name,
                    action = "commit",
                    branch = st.branch.ifBlank { branch },
                    remoteUrl = remoteUrl,
                    status = if (res.isSuccess) "COMMITTED" else "ERROR",
                    modifiedFiles = st.changes.map { it.relativePath },
                    message = (res.stdout + "\n" + res.stderr).trim().ifBlank { "Commit completed." },
                )
            }
            "pull" -> {
                val res = gitRepositoryManager.pull(repoDir, branch)
                val yamlPath = "$colDir/collection.opencollection.yaml"
                if (res.isSuccess && fileExists(yamlPath)) {
                    val pulledDoc = OpenCollectionSerializer.deserializeYaml(readTextFile(yamlPath))
                    val updatedFolder = folder.copy(
                        items = pulledDoc.items,
                        request = pulledDoc.request,
                    )
                    val updatedDoc = replaceItemById(doc, roots, node.id, updatedFolder)
                    canonicalRepository.saveGlobalCollection(updatedDoc)
                }
                val st = gitRepositoryManager.checkStatus(repoDir)
                GitSyncResultDto(
                    collectionName = folder.info.name,
                    action = "pull",
                    branch = st.branch.ifBlank { branch },
                    remoteUrl = remoteUrl,
                    status = if (res.isSuccess) "PULLED" else "ERROR",
                    modifiedFiles = st.changes.map { it.relativePath },
                    message = (res.stdout + "\n" + res.stderr).trim().ifBlank { "Pull completed." },
                )
            }
            "push" -> {
                val res = gitRepositoryManager.push(repoDir, branch)
                val st = gitRepositoryManager.checkStatus(repoDir)
                GitSyncResultDto(
                    collectionName = folder.info.name,
                    action = "push",
                    branch = st.branch.ifBlank { branch },
                    remoteUrl = remoteUrl,
                    status = if (res.isSuccess) "PUSHED" else "ERROR",
                    modifiedFiles = st.changes.map { it.relativePath },
                    message = (res.stdout + "\n" + res.stderr).trim().ifBlank { "Push completed." },
                )
            }
            else -> throw IllegalArgumentException("Unsupported Git action '$action'. Expected 'status', 'commit', 'pull', or 'push'.")
        }
    }

    // ── Internal Execution Pipeline ──────────────────────────────────────

    private suspend fun executeSingleRequestInternal(
        idOrPath: String,
        environmentNameOverride: String?,
        transientVariables: Map<String, String>,
    ): ExecutionSnapshot {
        val (doc, priv, node, item, ancestorFolders, collectionFolder) = mutex.withLock {
            val d = loadOrCreateGlobalDoc()
            val p = loadPrivateState()
            val roots = buildTreeFromDoc(d)
            val resolvedNode = NodeLookupResolver.resolve(roots, idOrPath, requestKinds)
            val resolvedItem = findItemById(d, roots, resolvedNode.id)
                ?: throw NodeNotFoundException(idOrPath, "request")
            val chain = findAncestorFolderItems(d, roots, resolvedNode.id)
            val colFolder = chain.firstOrNull()
                ?: throw NodeNotFoundException(idOrPath, "collection")
            ExecutionLookupContext(d, p, resolvedNode, resolvedItem, chain.drop(1), colFolder)
        }

        if (item is WebSocketRequestItem) {
            throw IllegalArgumentException(
                "WebSocket request '${node.slashPath}' requires an interactive persistent socket session and cannot be executed synchronously via clapi_execute_request."
            )
        }

        val allEnvs = doc.config?.environments.orEmpty()
        val activeEnvName = environmentNameOverride?.trim()?.takeIf { it.isNotEmpty() } ?: priv.activeEnvironment
        val activeEnv = activeEnvName?.let { name ->
            allEnvs.firstOrNull { it.name == name } ?: allEnvs.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        val knownSecrets = mutableSetOf<String>()
        val basicAuthPairs = mutableListOf<Pair<String, String>>()

        // Collect known secrets from global, environment (including ancestors), collection, folder, and request scopes
        priv.globalVariables.filter { it.secret && it.value.isNotBlank() }.forEach { knownSecrets.add(it.value) }
        priv.scopedSecrets.values.filter { it.isNotBlank() }.forEach { knownSecrets.add(it) }
        collectAuthSecrets(collectionFolder.request?.auth, knownSecrets, basicAuthPairs)
        ancestorFolders.forEach { collectAuthSecrets(it.request?.auth, knownSecrets, basicAuthPairs) }

        val collectionStandaloneDoc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = collectionFolder.info.name),
            config = doc.config,
            request = collectionFolder.request,
            items = collectionFolder.items,
            extensions = collectionFolder.extensions,
        )

        val globalVarMap = priv.globalVariables
            .filter { it.enabled && it.kind != "dynamic" && (!it.secret || it.value.isNotBlank()) }
            .associate { it.name to it.value }
            .toMutableMap()
        val envVarMap = resolveEffectiveEnvironmentVariables(activeEnv, allEnvs, priv).toMutableMap()
        val colId = resolveNodeId(collectionFolder, NodeLookupResolver.buildSlashPath(listOf(collectionFolder.info.name)), doc)
        val colVarMap = extractStaticVariableMap(
            collectionFolder.request?.variables.orEmpty(),
            secretLookup = { priv.scopedSecrets[scopedSecretKey("collection", colId, it)] },
        ).toMutableMap()
        val folderLayers = ancestorFolders.reversed().map { f ->
            val fId = (f.extensions["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            extractStaticVariableMap(
                f.request?.variables.orEmpty(),
                secretLookup = { priv.scopedSecrets[scopedSecretKey("folder", fId, it)] },
            )
        }
        val reqVarList = when (item) {
            is HttpRequestItem -> item.runtime?.variables.orEmpty()
            is GraphQLRequestItem -> item.runtime?.variables.orEmpty()
            is GrpcRequestItem -> item.runtime?.variables.orEmpty()
            else -> emptyList()
        }
        val reqVarMap = extractStaticVariableMap(
            reqVarList,
            secretLookup = { priv.scopedSecrets[scopedSecretKey("request", node.id, it)] },
        ).toMutableMap()

        fun buildLayers(): List<Map<String, String>> =
            listOf(transientVariables, reqVarMap, envVarMap) + folderLayers + listOf(colVarMap, globalVarMap)

        val preScripts = buildList {
            collectionFolder.request?.scripts.orEmpty()
                .filter { it.type == ScriptType.BEFORE_REQUEST && it.code.isNotBlank() }
                .forEach { add(it.code) }
            ancestorFolders.forEach { f ->
                f.request?.scripts.orEmpty()
                    .filter { it.type == ScriptType.BEFORE_REQUEST && it.code.isNotBlank() }
                    .forEach { add(it.code) }
            }
            val itemScripts = when (item) {
                is HttpRequestItem -> item.runtime?.scripts.orEmpty()
                is GraphQLRequestItem -> item.runtime?.scripts.orEmpty()
                is GrpcRequestItem -> item.runtime?.scripts.orEmpty()
                else -> emptyList()
            }
            itemScripts.filter {
                (it.type == ScriptType.BEFORE_REQUEST || it.type == ScriptType.GRPC_BEFORE_CALL_START) && it.code.isNotBlank()
            }.forEach { add(it.code) }
        }

        val testScripts = buildList {
            collectionFolder.request?.scripts.orEmpty()
                .filter { (it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE) && it.code.isNotBlank() }
                .forEach { add(it.code) }
            ancestorFolders.forEach { f ->
                f.request?.scripts.orEmpty()
                    .filter { (it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE) && it.code.isNotBlank() }
                    .forEach { add(it.code) }
            }
            val itemScripts = when (item) {
                is HttpRequestItem -> item.runtime?.scripts.orEmpty()
                is GraphQLRequestItem -> item.runtime?.scripts.orEmpty()
                is GrpcRequestItem -> item.runtime?.scripts.orEmpty()
                else -> emptyList()
            }
            itemScripts.filter {
                (it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.GRPC_AFTER_CALL_END) && it.code.isNotBlank()
            }.forEach { add(it.code) }
        }

        val allLogs = mutableListOf<String>()
        val allAssertions = mutableListOf<AssertionResult>()
        var scriptError: String? = null

        return when (item) {
            is HttpRequestItem, is GraphQLRequestItem -> {
                val itemAuth = if (item is HttpRequestItem) item.http.auth else (item as GraphQLRequestItem).graphql.auth
                collectAuthSecrets(itemAuth, knownSecrets, basicAuthPairs)

                var executableReq: RequestDefinition = if (item is HttpRequestItem) {
                    executionAdapter.toExecutableRequest(item, ancestorFolders, collectionStandaloneDoc)
                } else {
                    executionAdapter.toExecutableRequest(item as GraphQLRequestItem, ancestorFolders, collectionStandaloneDoc)
                }.copy(id = node.id)

                // Run pre-request scripts
                for (code in preScripts) {
                    val ctx = ScriptContext(
                        url = VariableResolver.resolve(executableReq.url, buildLayers(), removeUnresolved = false),
                        method = executableReq.method.name,
                        variables = envVarMap,
                        globalVariables = globalVarMap,
                        collectionVariables = colVarMap,
                        requestHeaders = executableReq.headers.filter { it.enabled }.associate { it.key to it.value },
                        requestQueryParams = executableReq.queryParams.filter { it.enabled }.associate { it.key to it.value },
                        requestBody = executableReq.body.content,
                    )
                    val res = scriptEngine.executePreRequestScript(code, ctx)
                    allLogs.addAll(res.logs)
                    res.newVariables.forEach { (k, v) -> envVarMap[k] = v }
                    res.newGlobalVariables.forEach { (k, v) -> globalVarMap[k] = v }
                    res.newCollectionVariables.forEach { (k, v) -> colVarMap[k] = v }
                    res.newRequestVariables.forEach { (k, v) -> reqVarMap[k] = v }
                    executableReq = applyScriptMutations(executableReq, res.requestMutations)
                    if (!res.success && res.error != null) {
                        scriptError = res.error
                        break
                    }
                }

                val effectiveLayers = buildLayers()
                val dynamicVars = collectAllDynamicVariables(priv, activeEnv, allEnvs, collectionFolder, ancestorFolders, reqVarList)

                // Resolve variable values inside knownSecrets for any resolved auth params
                executableReq.auth.params.values.filter { it.isNotBlank() }.forEach { rawParam ->
                    val resolvedParam = VariableResolver.resolve(rawParam, effectiveLayers, removeUnresolved = false)
                    if (resolvedParam.length >= SecretSanitizer.MIN_SCRUB_SECRET_LENGTH) {
                        knownSecrets.add(resolvedParam)
                    }
                }

                var finalResponse: ResponseDefinition? = null
                var networkFailureMsg: String? = null
                executionService.execute(executableReq, effectiveLayers, dynamicVars).collect { event ->
                    when (event) {
                        is NetworkEvent.Success -> finalResponse = event.response
                        is NetworkEvent.Failure -> networkFailureMsg = event.error.message
                        else -> Unit
                    }
                }

                if (finalResponse == null) {
                    val cleanErr = SecretSanitizer.scrubText(networkFailureMsg ?: "Request execution failed.", knownSecrets)
                    logConsole("Request '${node.slashPath}' failed: $cleanErr", "ERROR", knownSecrets)
                    throw IllegalStateException("Request execution failed for '${node.slashPath}': $cleanErr")
                }

                val resp = finalResponse!!
                val bodyText = resp.assembledText ?: resp.bodyText

                // Run post-request test scripts
                for (code in testScripts) {
                    val ctx = ScriptContext(
                        url = VariableResolver.resolve(executableReq.url, effectiveLayers, removeUnresolved = false),
                        method = executableReq.method.name,
                        statusCode = resp.statusCode,
                        responseBody = bodyText,
                        responseSizeBytes = resp.metrics.responseSizeBytes,
                        variables = envVarMap,
                        globalVariables = globalVarMap,
                        collectionVariables = colVarMap,
                        responseHeaders = resp.headers.associate { it.key to it.value },
                        responseTimeMs = resp.metrics.responseTimeMs,
                        requestHeaders = executableReq.headers.filter { it.enabled }.associate { it.key to it.value },
                        requestQueryParams = executableReq.queryParams.filter { it.enabled }.associate { it.key to it.value },
                        requestBody = executableReq.body.content,
                        streamEvents = resp.streamEvents,
                        assembledText = resp.assembledText,
                    )
                    val res = scriptEngine.executeTestScript(code, ctx)
                    allLogs.addAll(res.logs)
                    allAssertions.addAll(res.assertions)
                    if (!res.success && res.error != null && scriptError == null) {
                        scriptError = res.error
                    }
                }

                val resolvedOutHeaders = executableReq.headers.map { h ->
                    h.copy(
                        key = VariableResolver.resolve(h.key, effectiveLayers, removeUnresolved = false),
                        value = VariableResolver.resolve(h.value, effectiveLayers, removeUnresolved = false),
                    )
                }.toMutableList()

                // Include auth header representation for request header inspection
                when (executableReq.auth.type) {
                    io.github.makeforge.clapi.core.model.AuthType.BEARER,
                    io.github.makeforge.clapi.core.model.AuthType.JWT -> {
                        if (resolvedOutHeaders.none { it.key.equals("Authorization", ignoreCase = true) }) {
                            resolvedOutHeaders.add(
                                KeyValueEntry(
                                    key = "Authorization",
                                    value = "Bearer ${MASKED_SECRET_PLACEHOLDER}",
                                    secret = true,
                                )
                            )
                        }
                    }
                    io.github.makeforge.clapi.core.model.AuthType.BASIC -> {
                        if (resolvedOutHeaders.none { it.key.equals("Authorization", ignoreCase = true) }) {
                            resolvedOutHeaders.add(
                                KeyValueEntry(
                                    key = "Authorization",
                                    value = "Basic ${MASKED_SECRET_PLACEHOLDER}",
                                    secret = true,
                                )
                            )
                        }
                    }
                    io.github.makeforge.clapi.core.model.AuthType.API_KEY -> {
                        val keyName = executableReq.auth.params["key"].orEmpty()
                        if (keyName.isNotBlank() && resolvedOutHeaders.none { it.key.equals(keyName, ignoreCase = true) }) {
                            resolvedOutHeaders.add(
                                KeyValueEntry(
                                    key = keyName,
                                    value = MASKED_SECRET_PLACEHOLDER,
                                    secret = true,
                                )
                            )
                        }
                    }
                    else -> Unit
                }

                val isBinary = ResponseSnapshotCache.isBinaryContentType(resp.contentType)
                val rawSnapshot = ExecutionSnapshot(
                    requestId = node.id,
                    slashPath = node.slashPath,
                    executedAtEpochMillis = resp.executedAtEpochMillis,
                    statusCode = resp.statusCode,
                    statusText = resp.statusText,
                    contentType = resp.contentType,
                    isBinary = isBinary,
                    totalBytes = if (isBinary) resp.metrics.responseSizeBytes else bodyText.encodeToByteArray().size.toLong(),
                    sanitizedRequestHeaders = SecretSanitizer.sanitizeHeaders(resolvedOutHeaders, knownSecrets),
                    sanitizedResponseHeaders = SecretSanitizer.sanitizeHeaders(resp.headers, knownSecrets),
                    sanitizedBodyText = if (isBinary) "" else bodyText,
                    metrics = resp.metrics,
                    assertions = allAssertions,
                    scriptLogs = allLogs,
                    scriptError = scriptError,
                )
                val sanitizedSnapshot = SecretSanitizer.sanitizeExecutionSnapshot(rawSnapshot, knownSecrets, basicAuthPairs)
                sanitizedSnapshot.scriptLogs.forEach { logConsole(it, "INFO", knownSecrets) }
                sanitizedSnapshot.scriptError?.let { logConsole(it, "ERROR", knownSecrets) }
                snapshotCache.put(sanitizedSnapshot)
                sanitizedSnapshot
            }
            is GrpcRequestItem -> {
                collectAuthSecrets(item.grpc.auth, knownSecrets, basicAuthPairs)
                val effectiveLayers = buildLayers()
                val colProtoFiles = extractCollectionProtoFiles(collectionFolder)
                val colImportPaths = extractCollectionImportPaths(collectionFolder)
                    .filter { it.enabled }
                    .map { it.path }
                val protoOverride = item.grpc.protoFilePath?.takeIf { it.isNotBlank() }
                    ?: colProtoFiles.firstOrNull()?.path
                val startMs = System.currentTimeMillis()
                val grpcResp = executionService.executeGrpc(
                    requestItem = item,
                    protoFilePathOverride = protoOverride,
                    variableLayers = effectiveLayers,
                    includePaths = colImportPaths,
                )
                val elapsed = grpcResp.durationMs.takeIf { it > 0 } ?: (System.currentTimeMillis() - startMs).coerceAtLeast(0)
                val bodyText = grpcResp.responseJson.orEmpty().ifBlank { grpcResp.errorMessage.orEmpty() }
                val statusInt = if (grpcResp.statusCode == 0) 200 else 500
                val rawSnapshot = ExecutionSnapshot(
                    requestId = node.id,
                    slashPath = node.slashPath,
                    executedAtEpochMillis = System.currentTimeMillis(),
                    statusCode = statusInt,
                    statusText = grpcResp.statusDescription.ifBlank { if (grpcResp.statusCode == 0) "OK" else "ERROR" },
                    contentType = "application/grpc+json",
                    isBinary = false,
                    totalBytes = bodyText.encodeToByteArray().size.toLong(),
                    sanitizedRequestHeaders = item.grpc.metadata.filter { it.disabled != true }.map {
                        SecretSanitizer.sanitizeHeader(it.name, it.value, knownSecrets = knownSecrets)
                    },
                    sanitizedResponseHeaders = grpcResp.responseMetadata.map { (k, v) ->
                        SecretSanitizer.sanitizeHeader(k, v, knownSecrets = knownSecrets)
                    },
                    sanitizedBodyText = bodyText,
                    metrics = ResponseMetrics(
                        statusCode = statusInt,
                        responseTimeMs = elapsed,
                        responseSizeBytes = bodyText.encodeToByteArray().size.toLong(),
                    ),
                    assertions = allAssertions,
                    scriptLogs = allLogs,
                    scriptError = grpcResp.errorMessage,
                )
                val sanitizedSnapshot = SecretSanitizer.sanitizeExecutionSnapshot(rawSnapshot, knownSecrets, basicAuthPairs)
                snapshotCache.put(sanitizedSnapshot)
                sanitizedSnapshot
            }
            else -> throw IllegalArgumentException("Unsupported request kind for execution: ${node.kind}")
        }
    }

    private data class ExecutionLookupContext(
        val doc: OpenCollectionDocument,
        val priv: HeadlessPrivateState,
        val node: WorkspaceTreeNodeDto,
        val item: OpenCollectionItem,
        val ancestorFolders: List<FolderItem>,
        val collectionFolder: FolderItem,
    )

    // ── Helper & Mapping Methods ─────────────────────────────────────────

    private fun applyScriptMutations(
        req: RequestDefinition,
        mutations: io.github.makeforge.clapi.core.scripting.ScriptRequestMutations,
    ): RequestDefinition {
        var updated = req
        mutations.url?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(url = it) }
        mutations.method?.takeIf { it.isNotBlank() }?.let { m ->
            io.github.makeforge.clapi.core.model.HttpMethodType.entries
                .firstOrNull { it.name.equals(m, ignoreCase = true) }
                ?.let { updated = updated.copy(method = it) }
        }
        mutations.body?.let { b ->
            updated = updated.copy(body = updated.body.copy(content = b))
        }
        if (mutations.headers.isNotEmpty()) {
            val hList = updated.headers.toMutableList()
            mutations.headers.forEach { (k, v) ->
                val idx = hList.indexOfFirst { it.key.equals(k, ignoreCase = true) }
                if (idx >= 0) hList[idx] = hList[idx].copy(value = v, enabled = true)
                else hList.add(KeyValueEntry(key = k, value = v, enabled = true))
            }
            updated = updated.copy(headers = hList)
        }
        if (mutations.queryParams.isNotEmpty()) {
            val qList = updated.queryParams.toMutableList()
            mutations.queryParams.forEach { (k, v) ->
                val idx = qList.indexOfFirst { it.key == k }
                if (idx >= 0) qList[idx] = qList[idx].copy(value = v, enabled = true)
                else qList.add(KeyValueEntry(key = k, value = v, enabled = true))
            }
            updated = updated.copy(queryParams = qList)
        }
        return updated
    }

    private fun scopedSecretKey(scope: String, target: String, key: String): String =
        "${scope.trim().lowercase()}:${target.trim()}:${key.trim()}"

    private fun collectAuthSecrets(
        auth: AuthConfig?,
        outSecrets: MutableSet<String>,
        basicPairs: MutableList<Pair<String, String>>,
    ) {
        when (auth) {
            is AuthBasic -> {
                if (auth.password.isNotBlank()) {
                    outSecrets.add(auth.password)
                    basicPairs.add(auth.username to auth.password)
                }
            }
            is AuthBearer -> if (auth.token.isNotBlank()) outSecrets.add(auth.token)
            is AuthApiKey -> if (auth.value.isNotBlank()) outSecrets.add(auth.value)
            is AuthOAuth2 -> {
                auth.credentials?.clientSecret?.takeIf { it.isNotBlank() }?.let(outSecrets::add)
                auth.resourceOwner?.password?.takeIf { it.isNotBlank() }?.let(outSecrets::add)
            }
            else -> Unit
        }
    }

    private fun resolveEffectiveEnvironmentVariables(
        env: Environment?,
        allEnvs: List<Environment>,
        priv: HeadlessPrivateState,
    ): Map<String, String> {
        if (env == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        val visited = mutableSetOf<String>()
        fun walk(current: Environment) {
            if (!visited.add(current.name)) return
            current.extends?.trim()?.takeIf { it.isNotEmpty() }?.let { parentName ->
                allEnvs.firstOrNull { it.name == parentName }?.let(::walk)
            }
            for (v in current.variables) {
                if (v.disabled == true || v.name.isBlank()) continue
                when (v) {
                    is RegularVariable -> result[v.name] = v.value?.asString.orEmpty()
                    is SecretVariable -> {
                        val s = priv.scopedSecrets[scopedSecretKey("environment", current.name, v.name)].orEmpty()
                        if (s.isNotBlank()) result[v.name] = s
                    }
                    is DynamicVariable -> result.remove(v.name)
                }
            }
        }
        walk(env)
        return result
    }

    private fun extractStaticVariableMap(
        variables: List<VariableEntry>,
        secretLookup: ((String) -> String?)? = null,
    ): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (v in variables) {
            if (v.disabled == true || v.name.isBlank()) continue
            when (v) {
                is RegularVariable -> map[v.name] = v.value?.asString.orEmpty()
                is SecretVariable -> {
                    val s = secretLookup?.invoke(v.name).orEmpty()
                    if (s.isNotBlank()) map[v.name] = s
                }
                is DynamicVariable -> Unit
            }
        }
        return map
    }

    private fun collectAllDynamicVariables(
        priv: HeadlessPrivateState,
        activeEnv: Environment?,
        allEnvs: List<Environment>,
        collectionFolder: FolderItem,
        ancestorFolders: List<FolderItem>,
        requestVars: List<VariableEntry>,
    ): List<DynamicVariable> {
        val map = LinkedHashMap<String, DynamicVariable>()
        priv.globalVariables.filter { it.enabled && it.kind == "dynamic" && it.dynamicScript.isNotBlank() }.forEach { g ->
            map[g.name] = DynamicVariable(
                name = g.name,
                type = VariableType.fromValue(g.variableType) ?: VariableType.STRING,
                script = g.dynamicScript,
                parameters = g.dynamicParameters,
            )
        }
        collectionFolder.request?.variables.orEmpty().filterIsInstance<DynamicVariable>()
            .filter { it.disabled != true }
            .forEach { map[it.name] = it }
        ancestorFolders.forEach { f ->
            f.request?.variables.orEmpty().filterIsInstance<DynamicVariable>()
                .filter { it.disabled != true }
                .forEach { map[it.name] = it }
        }
        if (activeEnv != null) {
            val visited = mutableSetOf<String>()
            fun walk(curr: Environment) {
                if (!visited.add(curr.name)) return
                curr.extends?.trim()?.takeIf { it.isNotEmpty() }?.let { pName ->
                    allEnvs.firstOrNull { it.name == pName }?.let(::walk)
                }
                curr.variables.forEach { v ->
                    if (v.disabled != true) {
                        if (v is DynamicVariable) map[v.name] = v
                        else map.remove(v.name)
                    }
                }
            }
            walk(activeEnv)
        }
        requestVars.filterIsInstance<DynamicVariable>()
            .filter { it.disabled != true }
            .forEach { map[it.name] = it }
        return map.values.toList()
    }

    private fun validateNoEnvironmentCycle(
        targetName: String,
        parentName: String,
        allEnvs: List<Environment>,
    ) {
        if (targetName.equals(parentName, ignoreCase = true)) {
            throw IllegalArgumentException("Environment '$targetName' cannot extend itself.")
        }
        val visited = mutableSetOf(targetName.lowercase())
        var current: String? = parentName
        while (!current.isNullOrBlank()) {
            if (!visited.add(current.lowercase())) {
                throw IllegalArgumentException("Circular environment inheritance chain detected involving '$targetName' and '$parentName'.")
            }
            val env = allEnvs.firstOrNull { it.name.equals(current, ignoreCase = true) }
            current = env?.extends?.trim()
        }
    }

    private fun mutateVariableEntryList(
        existing: List<VariableEntry>,
        key: String,
        action: String,
        kind: String,
        varType: VariableType,
        input: SetVariableInput,
    ): List<VariableEntry> {
        val list = existing.toMutableList()
        val idx = list.indexOfFirst { it.name == key }
        if (action == "delete") {
            if (idx >= 0) list.removeAt(idx)
            return list
        }
        val prev = list.getOrNull(idx)
        val disabledFlag = if (input.enabled) null else true
        val newEntry: VariableEntry = when (kind) {
            "secret" -> {
                SecretVariable(
                    name = key,
                    type = varType,
                    disabled = disabledFlag,
                    secret = true,
                )
            }
            "dynamic" -> {
                val prevDyn = prev as? DynamicVariable
                DynamicVariable(
                    name = key,
                    type = varType,
                    script = input.dynamicScript ?: prevDyn?.script.orEmpty(),
                    parameters = input.dynamicParameters.ifEmpty { prevDyn?.parameters.orEmpty() },
                    disabled = disabledFlag,
                )
            }
            else -> {
                val valObj = if (varType == VariableType.STRING) {
                    VariableValue.of(input.value)
                } else {
                    VariableValue.of(varType, input.value)
                }
                RegularVariable(
                    name = key,
                    value = valObj,
                    disabled = disabledFlag,
                )
            }
        }
        if (idx >= 0) list[idx] = newEntry else list.add(newEntry)
        return list
    }

    private fun buildExternalSecrets(
        provider: String?,
        declarations: List<SanitizedExternalSecretDeclarationDto>,
    ): ExternalSecrets? {
        if (declarations.isEmpty()) return null
        val mapped = declarations.map { d ->
            val disabledFlag = if (d.enabled) null else true
            when (d.providerType.trim().lowercase()) {
                "aws-secrets-manager" -> AwsSecretsManagerExternalSecret(
                    name = d.name,
                    secretName = d.path,
                    targetField = d.targetField,
                    customFilters = d.customFilters,
                    disabled = disabledFlag,
                )
                "azure-key-vault" -> AzureKeyVaultExternalSecret(
                    name = d.name,
                    secretName = d.path,
                    vaultName = d.url.orEmpty(),
                    targetField = d.targetField,
                    customFilters = d.customFilters,
                    disabled = disabledFlag,
                )
                "gcp-secrets-manager" -> GcpSecretsManagerExternalSecret(
                    name = d.name,
                    secretId = d.path,
                    projectId = d.url.orEmpty(),
                    targetField = d.targetField,
                    customFilters = d.customFilters,
                    disabled = disabledFlag,
                )
                "keeper-security-cloud" -> KeeperSecurityExternalSecret(
                    name = d.name,
                    recordTitle = d.path,
                    targetField = d.targetField,
                    customFilters = d.customFilters,
                    disabled = disabledFlag,
                )
                "keepassxc" -> KeePassXcExternalSecret(
                    name = d.name,
                    url = d.url.orEmpty(),
                    targetField = d.targetField,
                    customFilters = d.customFilters,
                    disabled = disabledFlag,
                )
                else -> HashicorpVaultExternalSecret(
                    name = d.name,
                    path = d.path,
                    disabled = disabledFlag,
                )
            }
        }
        val providerEnum = ExternalSecretType.fromValue(provider ?: declarations.first().providerType)
            ?: ExternalSecretType.HASHICORP_VAULT_SERVER
        return ExternalSecrets(
            type = providerEnum,
            variables = mapped,
        )
    }

    private fun mergeEnvironments(existing: List<Environment>, imported: List<Environment>): List<Environment> {
        if (imported.isEmpty()) return existing
        val result = existing.toMutableList()
        val names = result.map { it.name }.toMutableSet()
        for (env in imported) {
            val unique = io.github.makeforge.clapi.core.storage.opencollection.generateUniqueName(env.name, names)
            names.add(unique)
            result.add(env.copy(name = unique))
        }
        return result
    }

    private fun toSanitizedGlobalVariableDto(v: HeadlessStoredVariable): SanitizedVariableDto {
        val kind = v.kind ?: when {
            v.secret -> "secret"
            v.dynamicScript.isNotBlank() -> "dynamic"
            else -> "regular"
        }
        val isSecret = kind == "secret" || v.secret
        return SanitizedVariableDto(
            key = v.name,
            value = if (isSecret) MASKED_SECRET_PLACEHOLDER else if (kind == "dynamic") "" else v.value,
            kind = kind,
            variableType = v.variableType,
            secret = isSecret,
            isSet = if (kind == "dynamic") v.dynamicScript.isNotBlank() else v.value.isNotBlank(),
            enabled = v.enabled,
            dynamicScript = if (kind == "dynamic") v.dynamicScript else null,
            dynamicParameters = if (kind == "dynamic") v.dynamicParameters else emptyList(),
        )
    }

    private fun toSanitizedEnvironmentDto(
        env: Environment,
        allEnvs: List<Environment>,
        activeEnvName: String?,
        includeVariables: Boolean,
    ): SanitizedEnvironmentDto {
        val priv = loadPrivateState()
        val projectedVars = if (includeVariables) {
            buildEnvironmentVariableProjections(env, allEnvs, priv)
        } else emptyList()
        val extVars = env.externalSecrets?.variables.orEmpty().map(SecretSanitizer::sanitizeExternalSecretDeclaration)
        return SanitizedEnvironmentDto(
            name = env.name,
            color = env.color,
            extends = env.extends,
            description = env.description?.content,
            isActive = activeEnvName != null && env.name == activeEnvName,
            variables = projectedVars,
            externalSecretsProvider = env.externalSecrets?.type?.value,
            externalSecrets = extVars,
        )
    }

    private fun buildEnvironmentVariableProjections(
        target: Environment,
        allEnvs: List<Environment>,
        priv: HeadlessPrivateState,
    ): List<SanitizedVariableDto> {
        val inheritedMap = LinkedHashMap<String, Pair<VariableEntry, String>>()
        val visited = mutableSetOf(target.name)
        fun walkAncestors(parentName: String?) {
            if (parentName.isNullOrBlank() || !visited.add(parentName)) return
            val parent = allEnvs.firstOrNull { it.name == parentName } ?: return
            walkAncestors(parent.extends?.trim())
            parent.variables.forEach { v ->
                if (v.disabled != true && v.name.isNotBlank()) {
                    inheritedMap[v.name] = v to parent.name
                }
            }
        }
        walkAncestors(target.extends?.trim())

        val result = mutableListOf<SanitizedVariableDto>()
        val localKeys = mutableSetOf<String>()
        for (v in target.variables) {
            localKeys.add(v.name)
            val baseDto = SecretSanitizer.sanitizeVariable(v) {
                priv.scopedSecrets[scopedSecretKey("environment", target.name, it)]
            }
            val ancestor = inheritedMap[v.name]
            result.add(
                baseDto.copy(
                    sourceEnvName = target.name,
                    isInherited = false,
                    isOverridden = ancestor != null,
                )
            )
        }
        for ((k, pair) in inheritedMap) {
            if (k !in localKeys) {
                val baseDto = SecretSanitizer.sanitizeVariable(pair.first) {
                    priv.scopedSecrets[scopedSecretKey("environment", pair.second, it)]
                }
                result.add(
                    baseDto.copy(
                        sourceEnvName = pair.second,
                        isInherited = true,
                        isOverridden = false,
                    )
                )
            }
        }
        return result
    }

    private fun findCollectionFolderById(
        doc: OpenCollectionDocument,
        targetId: String,
        slashPath: String,
    ): FolderItem? =
        doc.items.filterIsInstance<FolderItem>().firstOrNull { col ->
            val colPath = NodeLookupResolver.buildSlashPath(listOf(col.info.name))
            resolveNodeId(col, colPath, doc) == targetId || colPath == slashPath
        }

    private fun findItemById(
        doc: OpenCollectionDocument,
        roots: List<WorkspaceTreeNodeDto>,
        targetId: String,
    ): OpenCollectionItem? {
        fun search(items: List<OpenCollectionItem>, nodes: List<WorkspaceTreeNodeDto>): OpenCollectionItem? {
            for (i in items.indices) {
                val item = items[i]
                val node = nodes.getOrNull(i) ?: continue
                if (node.id == targetId) return item
                if (item is FolderItem) {
                    val found = search(item.items, node.children)
                    if (found != null) return found
                }
            }
            return null
        }
        val topFolders = doc.items.filterIsInstance<FolderItem>()
        return search(topFolders, roots)
    }

    private fun findAncestorFolderItems(
        doc: OpenCollectionDocument,
        roots: List<WorkspaceTreeNodeDto>,
        targetId: String,
    ): List<FolderItem> {
        fun search(
            items: List<OpenCollectionItem>,
            nodes: List<WorkspaceTreeNodeDto>,
            ancestors: List<FolderItem>,
        ): List<FolderItem>? {
            for (i in items.indices) {
                val item = items[i]
                val node = nodes.getOrNull(i) ?: continue
                if (node.id == targetId) return ancestors
                if (item is FolderItem) {
                    val found = search(item.items, node.children, ancestors + item)
                    if (found != null) return found
                }
            }
            return null
        }
        return search(doc.items.filterIsInstance<FolderItem>(), roots, emptyList()).orEmpty()
    }

    private fun findEnclosingCollectionId(roots: List<WorkspaceTreeNodeDto>, targetId: String): String? {
        for (col in roots) {
            if (col.id == targetId) return col.id
            if (NodeLookupResolver.flatten(col.children).any { it.id == targetId }) {
                return col.id
            }
        }
        return null
    }

    private fun findParentFolderId(roots: List<WorkspaceTreeNodeDto>, targetId: String): String? {
        fun walk(parent: WorkspaceTreeNodeDto): String? {
            for (child in parent.children) {
                if (child.id == targetId) {
                    return if (parent.kind == "folder") parent.id else null
                }
                val nested = walk(child)
                if (nested != null) return nested
            }
            return null
        }
        for (col in roots) {
            val found = walk(col)
            if (found != null) return found
        }
        return null
    }

    private fun insertItemIntoParent(
        doc: OpenCollectionDocument,
        roots: List<WorkspaceTreeNodeDto>,
        parentId: String,
        newItem: OpenCollectionItem,
    ): OpenCollectionDocument {
        fun walk(items: List<OpenCollectionItem>, nodes: List<WorkspaceTreeNodeDto>): List<OpenCollectionItem> =
            items.mapIndexed { idx, item ->
                val node = nodes.getOrNull(idx)
                if (item is FolderItem && node != null) {
                    if (node.id == parentId) {
                        withItemId(item.copy(items = item.items + newItem), node.id)
                    } else {
                        withItemId(item.copy(items = walk(item.items, node.children)), node.id)
                    }
                } else if (node != null) {
                    withItemId(item, node.id)
                } else {
                    item
                }
            }

        val topFolders = doc.items.filterIsInstance<FolderItem>()
        return doc.copy(items = walk(topFolders, roots))
    }

    private fun replaceItemById(
        doc: OpenCollectionDocument,
        roots: List<WorkspaceTreeNodeDto>,
        targetId: String,
        replacement: OpenCollectionItem,
    ): OpenCollectionDocument {
        fun walk(items: List<OpenCollectionItem>, nodes: List<WorkspaceTreeNodeDto>): List<OpenCollectionItem> =
            items.mapIndexed { idx, item ->
                val node = nodes.getOrNull(idx)
                if (node?.id == targetId) {
                    withItemId(replacement, targetId)
                } else if (item is FolderItem && node != null) {
                    withItemId(item.copy(items = walk(item.items, node.children)), node.id)
                } else if (node != null) {
                    withItemId(item, node.id)
                } else {
                    item
                }
            }

        val topFolders = doc.items.filterIsInstance<FolderItem>()
        return doc.copy(items = walk(topFolders, roots))
    }

    private fun removeItemById(
        doc: OpenCollectionDocument,
        roots: List<WorkspaceTreeNodeDto>,
        targetId: String,
    ): OpenCollectionDocument {
        fun walk(items: List<OpenCollectionItem>, nodes: List<WorkspaceTreeNodeDto>): List<OpenCollectionItem> {
            val result = mutableListOf<OpenCollectionItem>()
            for (idx in items.indices) {
                val item = items[idx]
                val node = nodes.getOrNull(idx)
                if (node?.id == targetId) continue
                if (item is FolderItem && node != null) {
                    result.add(withItemId(item.copy(items = walk(item.items, node.children)), node.id))
                } else if (node != null) {
                    result.add(withItemId(item, node.id))
                } else {
                    result.add(item)
                }
            }
            return result
        }

        val topFolders = doc.items.filterIsInstance<FolderItem>()
        return doc.copy(items = walk(topFolders, roots))
    }

    private fun buildRequestDefaults(
        existing: RequestDefaults?,
        authInput: SanitizedAuthDto?,
        headersInput: List<SanitizedKeyValueDto>?,
        variablesInput: List<SanitizedVariableDto>?,
        preRequestScript: String?,
        testScript: String?,
    ): RequestDefaults? {
        val mergedAuth = SecretSanitizer.mergeAuthUpdate(authInput, existing?.auth)
        val mergedHeaders = headersInput?.map { h ->
            val prevHeaderVal = existing?.headers?.firstOrNull { it.name.equals(h.key, ignoreCase = true) }?.value.orEmpty()
            HttpRequestHeader(
                name = h.key,
                value = SecretSanitizer.preserveSecretIfMasked(h.value, prevHeaderVal),
                description = h.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                disabled = if (h.enabled) null else true,
            )
        } ?: existing?.headers.orEmpty()

        val mergedVars = variablesInput?.map { v ->
            val prevVar = existing?.variables?.firstOrNull { it.name == v.key }
            toVariableEntry(v, prevVar)
        } ?: existing?.variables.orEmpty()

        val mergedScripts = mergeScripts(existing?.scripts.orEmpty(), preRequestScript, testScript)
        if (mergedAuth == null && mergedHeaders.isEmpty() && mergedVars.isEmpty() && mergedScripts.isEmpty()) {
            return existing
        }
        return RequestDefaults(
            headers = mergedHeaders,
            auth = mergedAuth,
            variables = mergedVars,
            scripts = mergedScripts,
            settings = existing?.settings,
        )
    }

    private fun mergeScripts(
        existing: List<Script>,
        preRequestScript: String?,
        testScript: String?,
        isGrpc: Boolean = false,
    ): List<Script> {
        val preType = if (isGrpc) ScriptType.GRPC_BEFORE_CALL_START else ScriptType.BEFORE_REQUEST
        val testType = if (isGrpc) ScriptType.GRPC_AFTER_CALL_END else ScriptType.TESTS
        val result = existing.toMutableList()
        if (preRequestScript != null) {
            result.removeAll { it.type == ScriptType.BEFORE_REQUEST || it.type == ScriptType.GRPC_BEFORE_CALL_START }
            if (preRequestScript.isNotBlank()) {
                result.add(Script(type = preType, code = preRequestScript))
            }
        }
        if (testScript != null) {
            result.removeAll {
                it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.GRPC_AFTER_CALL_END
            }
            if (testScript.isNotBlank()) {
                result.add(Script(type = testType, code = testScript))
            }
        }
        return result
    }

    private fun toVariableEntry(dto: SanitizedVariableDto, existing: VariableEntry?): VariableEntry {
        val varType = VariableType.fromValue(dto.variableType) ?: VariableType.STRING
        val disabled = if (dto.enabled) null else true
        return when (dto.kind.lowercase()) {
            "secret" -> {
                SecretVariable(
                    name = dto.key,
                    type = varType,
                    disabled = disabled,
                    secret = true,
                )
            }
            "dynamic" -> DynamicVariable(
                name = dto.key,
                type = varType,
                script = dto.dynamicScript.orEmpty(),
                parameters = dto.dynamicParameters,
                disabled = disabled,
            )
            else -> RegularVariable(
                name = dto.key,
                value = if (varType == VariableType.STRING) VariableValue.of(dto.value) else VariableValue.of(varType, dto.value),
                disabled = disabled,
            )
        }
    }

    private fun buildCollectionExtensions(
        id: String,
        existing: Map<String, JsonElement>,
        summary: String?,
        version: String?,
        protoFiles: List<ProtoFileDto>?,
        importPaths: List<ProtoImportPathDto>?,
    ): Map<String, JsonElement> {
        val ext = existing.toMutableMap()
        ext["id"] = JsonPrimitive(id)
        val prevClapi = (ext["clapi"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        prevClapi["isCollection"] = JsonPrimitive(true)
        summary?.let { prevClapi["summary"] = JsonPrimitive(it) }
        version?.let { prevClapi["version"] = JsonPrimitive(it) }
        protoFiles?.let { files ->
            prevClapi["protoFiles"] = buildJsonArray {
                files.forEach { f ->
                    add(buildJsonObject {
                        put("path", f.path)
                        put("syncToGit", f.syncToGit)
                    })
                }
            }
        }
        importPaths?.let { paths ->
            prevClapi["importPaths"] = buildJsonArray {
                paths.forEach { p ->
                    add(buildJsonObject {
                        put("path", p.path)
                        put("enabled", p.enabled)
                        put("syncToGit", p.syncToGit)
                    })
                }
            }
        }
        ext["clapi"] = JsonObject(prevClapi)
        return ext
    }

    private fun extractCollectionProtoFiles(folder: FolderItem): List<ProtoFileDto> {
        val arr = (folder.extensions["clapi"] as? JsonObject)?.get("protoFiles") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sync = obj["syncToGit"]?.jsonPrimitive?.booleanOrNull ?: false
            ProtoFileDto(path = path, syncToGit = sync)
        }
    }

    private fun extractCollectionImportPaths(folder: FolderItem): List<ProtoImportPathDto> {
        val arr = (folder.extensions["clapi"] as? JsonObject)?.get("importPaths") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val sync = obj["syncToGit"]?.jsonPrimitive?.booleanOrNull ?: false
            ProtoImportPathDto(path = path, enabled = enabled, syncToGit = sync)
        }
    }

    private fun buildNewRequestItem(id: String, name: String, input: UpsertRequestInput): OpenCollectionItem {
        val kind = (input.kind ?: when {
            input.grpc != null -> "grpc"
            input.graphql != null -> "graphql"
            input.websocket != null -> "websocket"
            else -> "http"
        }).trim().lowercase()
        val ext = mapOf("id" to JsonPrimitive(id))
        val desc = input.summary?.takeIf { it.isNotBlank() }?.let { Description(content = it) }
        val auth = SecretSanitizer.mergeAuthUpdate(input.auth, null)
        val headers = input.headers.orEmpty().map { h ->
            HttpRequestHeader(
                name = h.key,
                value = h.value,
                description = h.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                disabled = if (h.enabled) null else true,
            )
        }
        val params = input.queryParams.orEmpty().map { q ->
            HttpRequestParam(
                name = q.key,
                value = q.value,
                description = q.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                type = HttpRequestParamType.QUERY,
                disabled = if (q.enabled) null else true,
            )
        }
        val vars = input.variables.orEmpty().map { toVariableEntry(it, null) }

        return when (kind) {
            "grpc" -> {
                val g = input.grpc ?: GrpcRequestConfigDto()
                val scripts = mergeScripts(emptyList(), input.preRequestScript, input.testScript, isGrpc = true)
                GrpcRequestItem(
                    info = GrpcRequestInfo(name = name, description = desc),
                    grpc = GrpcRequestDetails(
                        url = input.url.orEmpty(),
                        method = g.grpcMethod,
                        methodType = GrpcMethodType.fromValue(g.grpcMethodType) ?: GrpcMethodType.UNARY,
                        protoFilePath = g.protoFilePath,
                        metadata = g.metadata.map { GrpcMetadata(name = it.name, value = it.value, disabled = if (it.disabled) true else null) },
                        message = g.message ?: input.bodyContent,
                        auth = auth,
                    ),
                    runtime = if (vars.isNotEmpty() || scripts.isNotEmpty()) GrpcRequestRuntime(variables = vars, scripts = scripts) else null,
                    docs = input.summary,
                    extensions = ext,
                )
            }
            "graphql" -> {
                val gql = input.graphql ?: GraphQlRequestConfigDto(query = input.bodyContent.orEmpty())
                val scripts = mergeScripts(emptyList(), input.preRequestScript, input.testScript)
                val bodyPayload = if (gql.variants.isNotEmpty()) {
                    GraphQLBodyPayload.Variants(
                        gql.variants.map { v ->
                            GraphQLBodyVariant(
                                title = v.title,
                                selected = v.selected,
                                body = GraphQLBody(query = v.query, variables = v.variables),
                            )
                        }
                    )
                } else {
                    GraphQLBodyPayload.Single(GraphQLBody(query = gql.query, variables = gql.variablesJson))
                }
                GraphQLRequestItem(
                    info = GraphQLRequestInfo(name = name, description = desc),
                    graphql = GraphQLRequestDetails(
                        method = gql.method.uppercase(),
                        url = input.url.orEmpty(),
                        headers = headers,
                        params = params,
                        body = bodyPayload,
                        auth = auth,
                    ),
                    runtime = if (vars.isNotEmpty() || scripts.isNotEmpty()) GraphQLRequestRuntime(variables = vars, scripts = scripts) else null,
                    docs = input.summary,
                    extensions = ext,
                )
            }
            "websocket" -> {
                val ws = input.websocket ?: WebSocketRequestConfigDto()
                val scripts = mergeScripts(emptyList(), input.preRequestScript, input.testScript)
                val msgPayload = if (ws.variants.isNotEmpty()) {
                    WebSocketMessagePayload.Variants(
                        ws.variants.map { v ->
                            WebSocketMessageVariant(
                                title = v.title,
                                selected = v.selected,
                                message = WebSocketMessage(type = WebSocketMessageType.fromValue(v.type), data = v.data),
                            )
                        }
                    )
                } else if (!input.bodyContent.isNullOrEmpty()) {
                    WebSocketMessagePayload.Single(
                        WebSocketMessage(type = WebSocketMessageType.fromValue(ws.messageFormat), data = input.bodyContent)
                    )
                } else null
                WebSocketRequestItem(
                    info = WebSocketRequestInfo(name = name, description = desc),
                    websocket = WebSocketRequestDetails(
                        url = input.url.orEmpty(),
                        headers = headers,
                        message = msgPayload,
                        auth = auth,
                    ),
                    runtime = if (vars.isNotEmpty() || scripts.isNotEmpty()) WebSocketRequestRuntime(variables = vars, scripts = scripts) else null,
                    settings = if (ws.timeout != null || ws.keepAliveInterval != null) {
                        WebSocketRequestSettings(
                            timeout = ws.timeout?.let { JsonPrimitive(it) },
                            keepAliveInterval = ws.keepAliveInterval?.let { JsonPrimitive(it) },
                        )
                    } else null,
                    docs = input.summary,
                    extensions = ext,
                )
            }
            else -> {
                val scripts = mergeScripts(emptyList(), input.preRequestScript, input.testScript)
                val body = buildHttpRequestBody(input.bodyType, input.bodyContent, input.formDataEntries)
                HttpRequestItem(
                    info = HttpRequestInfo(name = name, description = desc),
                    http = HttpRequestDetails(
                        method = (input.method ?: "GET").uppercase(),
                        url = input.url.orEmpty(),
                        headers = headers,
                        params = params,
                        body = body,
                        auth = auth,
                    ),
                    runtime = if (vars.isNotEmpty() || scripts.isNotEmpty()) HttpRequestRuntime(variables = vars, scripts = scripts) else null,
                    docs = input.summary,
                    extensions = ext,
                )
            }
        }
    }

    private fun buildUpdatedRequestItem(
        existing: OpenCollectionItem,
        id: String,
        input: UpsertRequestInput,
    ): OpenCollectionItem {
        val targetKind = input.kind?.trim()?.lowercase()
        val currentKind = when (existing) {
            is HttpRequestItem -> "http"
            is GrpcRequestItem -> "grpc"
            is GraphQLRequestItem -> "graphql"
            is WebSocketRequestItem -> "websocket"
            else -> "http"
        }
        if (targetKind != null && targetKind != currentKind) {
            return buildNewRequestItem(id, input.name ?: existing.info.name, input)
        }

        val newName = input.name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.info.name
        val newDesc = input.summary?.let { Description(content = it) } ?: existing.info.description

        return when (existing) {
            is HttpRequestItem -> {
                val mergedHeaders = input.headers?.map { h ->
                    val prevVal = existing.http.headers.firstOrNull { it.name.equals(h.key, ignoreCase = true) }?.value.orEmpty()
                    HttpRequestHeader(
                        name = h.key,
                        value = SecretSanitizer.preserveSecretIfMasked(h.value, prevVal),
                        description = h.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                        disabled = if (h.enabled) null else true,
                    )
                } ?: existing.http.headers
                val mergedParams = input.queryParams?.map { q ->
                    HttpRequestParam(
                        name = q.key,
                        value = q.value,
                        description = q.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                        type = HttpRequestParamType.QUERY,
                        disabled = if (q.enabled) null else true,
                    )
                } ?: existing.http.params
                val mergedAuth = SecretSanitizer.mergeAuthUpdate(input.auth, existing.http.auth)
                val mergedBody = if (input.bodyType != null || input.bodyContent != null || input.formDataEntries != null) {
                    buildHttpRequestBody(
                        bodyType = input.bodyType ?: inferBodyTypeString(existing.http.body),
                        bodyContent = input.bodyContent ?: extractRawBodyText(existing.http.body),
                        formDataEntries = input.formDataEntries,
                    )
                } else existing.http.body
                val mergedVars = input.variables?.map { v ->
                    val prev = existing.runtime?.variables?.firstOrNull { it.name == v.key }
                    toVariableEntry(v, prev)
                } ?: existing.runtime?.variables.orEmpty()
                val mergedScripts = mergeScripts(existing.runtime?.scripts.orEmpty(), input.preRequestScript, input.testScript)
                withItemId(
                    existing.copy(
                        info = existing.info.copy(name = newName, description = newDesc),
                        http = existing.http.copy(
                            method = (input.method ?: existing.http.method).uppercase(),
                            url = input.url ?: existing.http.url,
                            headers = mergedHeaders,
                            params = mergedParams,
                            body = mergedBody,
                            auth = mergedAuth,
                        ),
                        runtime = (existing.runtime ?: HttpRequestRuntime()).copy(
                            variables = mergedVars,
                            scripts = mergedScripts,
                        ),
                        docs = input.summary ?: existing.docs,
                    ),
                    id,
                )
            }
            is GrpcRequestItem -> {
                val g = input.grpc
                val mergedAuth = SecretSanitizer.mergeAuthUpdate(input.auth, existing.grpc.auth)
                val mergedMeta = g?.metadata?.map { m ->
                    val prevVal = existing.grpc.metadata.firstOrNull { it.name.equals(m.name, ignoreCase = true) }?.value.orEmpty()
                    GrpcMetadata(
                        name = m.name,
                        value = SecretSanitizer.preserveSecretIfMasked(m.value, prevVal),
                        disabled = if (m.disabled) true else null,
                    )
                } ?: existing.grpc.metadata
                val mergedVars = input.variables?.map { v ->
                    val prev = existing.runtime?.variables?.firstOrNull { it.name == v.key }
                    toVariableEntry(v, prev)
                } ?: existing.runtime?.variables.orEmpty()
                val mergedScripts = mergeScripts(existing.runtime?.scripts.orEmpty(), input.preRequestScript, input.testScript, isGrpc = true)
                withItemId(
                    existing.copy(
                        info = existing.info.copy(name = newName, description = newDesc),
                        grpc = existing.grpc.copy(
                            url = input.url ?: existing.grpc.url,
                            method = g?.grpcMethod ?: existing.grpc.method,
                            methodType = g?.grpcMethodType?.let { GrpcMethodType.fromValue(it) } ?: existing.grpc.methodType,
                            protoFilePath = g?.protoFilePath ?: existing.grpc.protoFilePath,
                            metadata = mergedMeta,
                            message = g?.message ?: input.bodyContent ?: existing.grpc.message,
                            auth = mergedAuth,
                        ),
                        runtime = (existing.runtime ?: GrpcRequestRuntime()).copy(
                            variables = mergedVars,
                            scripts = mergedScripts,
                        ),
                        docs = input.summary ?: existing.docs,
                    ),
                    id,
                )
            }
            is GraphQLRequestItem -> {
                val gql = input.graphql
                val mergedHeaders = input.headers?.map { h ->
                    val prevVal = existing.graphql.headers.firstOrNull { it.name.equals(h.key, ignoreCase = true) }?.value.orEmpty()
                    HttpRequestHeader(
                        name = h.key,
                        value = SecretSanitizer.preserveSecretIfMasked(h.value, prevVal),
                        disabled = if (h.enabled) null else true,
                    )
                } ?: existing.graphql.headers
                val mergedAuth = SecretSanitizer.mergeAuthUpdate(input.auth, existing.graphql.auth)
                val mergedBody = when {
                    gql != null && gql.variants.isNotEmpty() -> GraphQLBodyPayload.Variants(
                        gql.variants.map { v ->
                            GraphQLBodyVariant(
                                title = v.title,
                                selected = v.selected,
                                body = GraphQLBody(query = v.query, variables = v.variables),
                            )
                        }
                    )
                    gql != null -> GraphQLBodyPayload.Single(
                        GraphQLBody(query = gql.query, variables = gql.variablesJson)
                    )
                    input.bodyContent != null -> GraphQLBodyPayload.Single(
                        GraphQLBody(query = input.bodyContent, variables = existing.graphql.body?.singleBody?.variables)
                    )
                    else -> existing.graphql.body
                }
                val mergedVars = input.variables?.map { v ->
                    val prev = existing.runtime?.variables?.firstOrNull { it.name == v.key }
                    toVariableEntry(v, prev)
                } ?: existing.runtime?.variables.orEmpty()
                val mergedScripts = mergeScripts(existing.runtime?.scripts.orEmpty(), input.preRequestScript, input.testScript)
                withItemId(
                    existing.copy(
                        info = existing.info.copy(name = newName, description = newDesc),
                        graphql = existing.graphql.copy(
                            method = (gql?.method ?: input.method ?: existing.graphql.method ?: "POST").uppercase(),
                            url = input.url ?: existing.graphql.url,
                            headers = mergedHeaders,
                            body = mergedBody,
                            auth = mergedAuth,
                        ),
                        runtime = (existing.runtime ?: GraphQLRequestRuntime()).copy(
                            variables = mergedVars,
                            scripts = mergedScripts,
                        ),
                        docs = input.summary ?: existing.docs,
                    ),
                    id,
                )
            }
            is WebSocketRequestItem -> {
                val ws = input.websocket
                val mergedHeaders = input.headers?.map { h ->
                    val prevVal = existing.websocket.headers.firstOrNull { it.name.equals(h.key, ignoreCase = true) }?.value.orEmpty()
                    HttpRequestHeader(
                        name = h.key,
                        value = SecretSanitizer.preserveSecretIfMasked(h.value, prevVal),
                        disabled = if (h.enabled) null else true,
                    )
                } ?: existing.websocket.headers
                val mergedAuth = SecretSanitizer.mergeAuthUpdate(input.auth, existing.websocket.auth)
                val mergedMsg = when {
                    ws != null && ws.variants.isNotEmpty() -> WebSocketMessagePayload.Variants(
                        ws.variants.map { v ->
                            WebSocketMessageVariant(
                                title = v.title,
                                selected = v.selected,
                                message = WebSocketMessage(type = WebSocketMessageType.fromValue(v.type), data = v.data),
                            )
                        }
                    )
                    input.bodyContent != null -> WebSocketMessagePayload.Single(
                        WebSocketMessage(
                            type = WebSocketMessageType.fromValue(ws?.messageFormat ?: "text"),
                            data = input.bodyContent,
                        )
                    )
                    else -> existing.websocket.message
                }
                val mergedVars = input.variables?.map { v ->
                    val prev = existing.runtime?.variables?.firstOrNull { it.name == v.key }
                    toVariableEntry(v, prev)
                } ?: existing.runtime?.variables.orEmpty()
                val mergedScripts = mergeScripts(existing.runtime?.scripts.orEmpty(), input.preRequestScript, input.testScript)
                withItemId(
                    existing.copy(
                        info = existing.info.copy(name = newName, description = newDesc),
                        websocket = existing.websocket.copy(
                            url = input.url ?: existing.websocket.url,
                            headers = mergedHeaders,
                            message = mergedMsg,
                            auth = mergedAuth,
                        ),
                        runtime = (existing.runtime ?: WebSocketRequestRuntime()).copy(
                            variables = mergedVars,
                            scripts = mergedScripts,
                        ),
                        docs = input.summary ?: existing.docs,
                    ),
                    id,
                )
            }
            else -> existing
        }
    }

    private fun buildHttpRequestBody(
        bodyType: String?,
        bodyContent: String?,
        formDataEntries: List<FormDataEntryDto>?,
    ): HttpRequestBody? {
        val norm = bodyType?.trim()?.uppercase()
        if (norm == "NONE") return null
        if (norm == null && bodyContent == null && formDataEntries.isNullOrEmpty()) return null

        return when (norm) {
            "FORM_DATA", "MULTIPART-FORM", "MULTIPART_FORM" -> MultipartFormBody(
                data = formDataEntries.orEmpty().map { e ->
                    MultipartPartEntry(
                        name = e.key,
                        type = if (e.type.equals("FILE", ignoreCase = true)) MultipartPartType.FILE else MultipartPartType.TEXT,
                        value = JsonPrimitive(e.value),
                        description = e.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                        disabled = if (e.enabled) null else true,
                    )
                }
            )
            "X_WWW_FORM_URLENCODED", "FORM-URLENCODED", "FORM_URLENCODED" -> FormUrlEncodedBody(
                data = formDataEntries.orEmpty().map { e ->
                    FormParamEntry(
                        name = e.key,
                        value = e.value,
                        description = e.description.takeIf { it.isNotBlank() }?.let { Description(content = it) },
                        disabled = if (e.enabled) null else true,
                    )
                }
            )
            "BINARY", "FILE" -> FileBody(
                data = if (!bodyContent.isNullOrBlank()) {
                    listOf(FileBodyVariant(filePath = bodyContent, contentType = "application/octet-stream", selected = true))
                } else emptyList()
            )
            "XML" -> RawBody(type = RequestBodyType.XML, data = bodyContent.orEmpty())
            "RAW_TEXT", "TEXT", "HTML", "JAVASCRIPT" -> RawBody(type = RequestBodyType.TEXT, data = bodyContent.orEmpty())
            else -> RawBody(type = RequestBodyType.JSON, data = bodyContent.orEmpty())
        }
    }

    private fun inferBodyTypeString(body: HttpRequestBody?): String =
        when (body) {
            null -> "NONE"
            is MultipartFormBody -> "FORM_DATA"
            is FormUrlEncodedBody -> "X_WWW_FORM_URLENCODED"
            is FileBody -> "BINARY"
            is RawBody -> when (body.type) {
                RequestBodyType.JSON -> "JSON"
                RequestBodyType.XML -> "XML"
                else -> "RAW_TEXT"
            }
        }

    private fun extractRawBodyText(body: HttpRequestBody?): String? =
        when (body) {
            is RawBody -> body.data
            is FileBody -> body.data.firstOrNull { it.selected }?.filePath ?: body.data.firstOrNull()?.filePath
            else -> null
        }

    private fun toSanitizedCollectionDto(
        folder: FolderItem,
        node: WorkspaceTreeNodeDto,
    ): SanitizedCollectionDto {
        val storage = folder.extensions.getStorageExtension()
        val clapiObj = folder.extensions["clapi"] as? JsonObject
        val summary = clapiObj?.get("summary")?.jsonPrimitive?.contentOrNull ?: folder.info.description?.content
        val version = clapiObj?.get("version")?.jsonPrimitive?.contentOrNull
        val preScript = folder.request?.scripts.orEmpty()
            .firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
        val testScript = folder.request?.scripts.orEmpty()
            .firstOrNull { it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE }?.code
        return SanitizedCollectionDto(
            id = node.id,
            name = folder.info.name,
            slashPath = node.slashPath,
            summary = summary,
            version = version,
            storageType = (storage?.type ?: StorageType.LOCAL).name,
            gitRemoteUrl = storage?.git?.remoteUrl,
            gitBranch = storage?.git?.branch,
            auth = SecretSanitizer.sanitizeAuth(folder.request?.auth),
            headers = SecretSanitizer.sanitizeHttpHeaders(folder.request?.headers.orEmpty()),
            variables = folder.request?.variables.orEmpty().map(SecretSanitizer::sanitizeVariable),
            preRequestScript = preScript,
            testScript = testScript,
            protoFiles = extractCollectionProtoFiles(folder),
            importPaths = extractCollectionImportPaths(folder),
            children = node.children,
        )
    }

    private fun toSanitizedFolderDto(
        folder: FolderItem,
        node: WorkspaceTreeNodeDto,
        collectionId: String,
        parentFolderId: String?,
    ): SanitizedFolderDto {
        val preScript = folder.request?.scripts.orEmpty()
            .firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
        val testScript = folder.request?.scripts.orEmpty()
            .firstOrNull { it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE }?.code
        return SanitizedFolderDto(
            id = node.id,
            name = folder.info.name,
            slashPath = node.slashPath,
            collectionId = collectionId,
            parentFolderId = parentFolderId,
            auth = SecretSanitizer.sanitizeAuth(folder.request?.auth),
            headers = SecretSanitizer.sanitizeHttpHeaders(folder.request?.headers.orEmpty()),
            variables = folder.request?.variables.orEmpty().map(SecretSanitizer::sanitizeVariable),
            preRequestScript = preScript,
            testScript = testScript,
            children = node.children,
        )
    }

    private fun toSanitizedRequestDto(
        item: OpenCollectionItem,
        node: WorkspaceTreeNodeDto,
        collectionId: String,
        parentFolderId: String?,
    ): SanitizedRequestDto =
        when (item) {
            is HttpRequestItem -> {
                val preScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
                val testScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE }?.code
                val formEntries = when (val b = item.http.body) {
                    is MultipartFormBody -> b.data.map { part ->
                        FormDataEntryDto(
                            key = part.name,
                            type = part.type.value.uppercase(),
                            value = (part.value as? JsonPrimitive)?.contentOrNull ?: part.value.toString(),
                            description = part.description?.content.orEmpty(),
                            enabled = part.disabled != true,
                        )
                    }
                    is FormUrlEncodedBody -> b.data.map { param ->
                        FormDataEntryDto(
                            key = param.name,
                            type = "TEXT",
                            value = param.value,
                            description = param.description?.content.orEmpty(),
                            enabled = param.disabled != true,
                        )
                    }
                    else -> emptyList()
                }
                SanitizedRequestDto(
                    id = node.id,
                    name = item.info.name,
                    slashPath = node.slashPath,
                    collectionId = collectionId,
                    parentFolderId = parentFolderId,
                    kind = "http",
                    summary = item.info.description?.content ?: item.docs,
                    method = item.http.method.uppercase(),
                    url = item.http.url,
                    queryParams = item.http.params.map { p ->
                        SanitizedKeyValueDto(
                            key = p.name,
                            value = p.value,
                            enabled = p.disabled != true,
                            description = p.description?.content.orEmpty(),
                        )
                    },
                    headers = SecretSanitizer.sanitizeHttpHeaders(item.http.headers),
                    auth = SecretSanitizer.sanitizeAuth(item.http.auth),
                    bodyType = inferBodyTypeString(item.http.body),
                    bodyContent = extractRawBodyText(item.http.body),
                    formDataEntries = formEntries,
                    variables = item.runtime?.variables.orEmpty().map(SecretSanitizer::sanitizeVariable),
                    preRequestScript = preScript,
                    testScript = testScript,
                )
            }
            is GrpcRequestItem -> {
                val preScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.BEFORE_REQUEST || it.type == ScriptType.GRPC_BEFORE_CALL_START }?.code
                val testScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.GRPC_AFTER_CALL_END }?.code
                SanitizedRequestDto(
                    id = node.id,
                    name = item.info.name,
                    slashPath = node.slashPath,
                    collectionId = collectionId,
                    parentFolderId = parentFolderId,
                    kind = "grpc",
                    summary = item.info.description?.content ?: item.docs,
                    url = item.grpc.url,
                    auth = SecretSanitizer.sanitizeAuth(item.grpc.auth),
                    bodyContent = item.grpc.message,
                    variables = item.runtime?.variables.orEmpty().map(SecretSanitizer::sanitizeVariable),
                    preRequestScript = preScript,
                    testScript = testScript,
                    grpc = GrpcRequestConfigDto(
                        protoFilePath = item.grpc.protoFilePath,
                        grpcMethod = item.grpc.method,
                        grpcMethodType = item.grpc.methodType?.value ?: "unary",
                        metadata = item.grpc.metadata.map { m ->
                            val clean = SecretSanitizer.sanitizeHeader(m.name, m.value, enabled = m.disabled != true)
                            GrpcMetadataDto(name = clean.key, value = clean.value, disabled = m.disabled == true)
                        },
                        message = item.grpc.message,
                    ),
                )
            }
            is GraphQLRequestItem -> {
                val preScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
                val testScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE }?.code
                val variants = item.graphql.body?.variantList.orEmpty().map { v ->
                    GraphQlVariantDto(
                        title = v.title,
                        query = v.body.query,
                        variables = v.body.variables,
                        selected = v.selected == true,
                    )
                }
                val activeVariant = variants.firstOrNull { it.selected } ?: variants.firstOrNull()
                SanitizedRequestDto(
                    id = node.id,
                    name = item.info.name,
                    slashPath = node.slashPath,
                    collectionId = collectionId,
                    parentFolderId = parentFolderId,
                    kind = "graphql",
                    summary = item.info.description?.content ?: item.docs,
                    method = (item.graphql.method ?: "POST").uppercase(),
                    url = item.graphql.url,
                    queryParams = item.graphql.params.map { p ->
                        SanitizedKeyValueDto(
                            key = p.name,
                            value = p.value,
                            enabled = p.disabled != true,
                            description = p.description?.content.orEmpty(),
                        )
                    },
                    headers = SecretSanitizer.sanitizeHttpHeaders(item.graphql.headers),
                    auth = SecretSanitizer.sanitizeAuth(item.graphql.auth),
                    bodyType = "GRAPHQL",
                    bodyContent = activeVariant?.query,
                    variables = item.runtime?.variables.orEmpty().map(SecretSanitizer::sanitizeVariable),
                    preRequestScript = preScript,
                    testScript = testScript,
                    graphql = GraphQlRequestConfigDto(
                        method = (item.graphql.method ?: "POST").uppercase(),
                        query = activeVariant?.query.orEmpty(),
                        variablesJson = activeVariant?.variables,
                        variants = variants,
                    ),
                )
            }
            is WebSocketRequestItem -> {
                val preScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
                val testScript = item.runtime?.scripts.orEmpty()
                    .firstOrNull { it.type == ScriptType.TESTS || it.type == ScriptType.AFTER_RESPONSE }?.code
                val variants = item.websocket.message?.variantList.orEmpty().map { v ->
                    WebSocketVariantDto(
                        title = v.title,
                        type = v.message.type.value,
                        data = v.message.data,
                        selected = v.selected == true,
                    )
                }
                val activeVariant = variants.firstOrNull { it.selected } ?: variants.firstOrNull()
                SanitizedRequestDto(
                    id = node.id,
                    name = item.info.name,
                    slashPath = node.slashPath,
                    collectionId = collectionId,
                    parentFolderId = parentFolderId,
                    kind = "websocket",
                    summary = item.info.description?.content ?: item.docs,
                    url = item.websocket.url,
                    headers = SecretSanitizer.sanitizeHttpHeaders(item.websocket.headers),
                    auth = SecretSanitizer.sanitizeAuth(item.websocket.auth),
                    bodyContent = activeVariant?.data,
                    variables = item.runtime?.variables.orEmpty().map(SecretSanitizer::sanitizeVariable),
                    preRequestScript = preScript,
                    testScript = testScript,
                    websocket = WebSocketRequestConfigDto(
                        messageFormat = activeVariant?.type ?: "text",
                        timeout = (item.settings?.timeout as? JsonPrimitive)?.contentOrNull,
                        keepAliveInterval = (item.settings?.keepAliveInterval as? JsonPrimitive)?.contentOrNull,
                        variants = variants,
                    ),
                )
            }
            else -> throw IllegalArgumentException("Cannot convert folder to SanitizedRequestDto.")
        }
}
