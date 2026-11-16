package io.github.makeforge.clapi.core.storage.importers

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import io.github.makeforge.clapi.core.model.importers.ImportCandidateEnvironment
import io.github.makeforge.clapi.core.model.importers.ImportCandidateFolder
import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import io.github.makeforge.clapi.core.model.opencollection.ApiKeyPlacement
import io.github.makeforge.clapi.core.model.opencollection.AuthApiKey
import io.github.makeforge.clapi.core.model.opencollection.AuthAwsV4
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthConfig
import io.github.makeforge.clapi.core.model.opencollection.AuthDigest
import io.github.makeforge.clapi.core.model.opencollection.AuthNTLM
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth1
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.CollectionConfig
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.Documentation
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.FileBody
import io.github.makeforge.clapi.core.model.opencollection.FileBodyVariant
import io.github.makeforge.clapi.core.model.opencollection.FolderInfo
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.FormParamEntry
import io.github.makeforge.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBody
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyPayload
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.GrpcMetadata
import io.github.makeforge.clapi.core.model.opencollection.GrpcMethodType
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestBody
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParam
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParamType
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestRuntime
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.MultipartFormBody
import io.github.makeforge.clapi.core.model.opencollection.MultipartPartEntry
import io.github.makeforge.clapi.core.model.opencollection.MultipartPartType
import io.github.makeforge.clapi.core.model.opencollection.OAuth1SignatureMethod
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ClientCredentials
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ResourceOwner
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionItem
import io.github.makeforge.clapi.core.model.opencollection.RawBody
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.RequestBodyType
import io.github.makeforge.clapi.core.model.opencollection.RequestDefaults
import io.github.makeforge.clapi.core.model.opencollection.Script
import io.github.makeforge.clapi.core.model.opencollection.ScriptType
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableEntry
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessage
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessagePayload
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

interface InsomniaImporter {
    /**
     * Inspects whether [rawContent] matches an Insomnia v5 export (YAML or JSON).
     */
    fun canHandle(rawContent: String): Boolean

    /**
     * Scans and parses [rawContent] as an Insomnia v5 document, extracting candidate
     * folders, requests, and environments.
     */
    suspend fun scanContent(rawContent: String): ImportScanResult

    /**
     * Converts an Insomnia v5 document directly into a canonical [OpenCollectionDocument].
     */
    fun importToOpenCollection(rawContent: String): OpenCollectionDocument

    companion object {
        fun default(): InsomniaImporter = DefaultInsomniaImporter()
    }
}

class DefaultInsomniaImporter(
    private val openApiImporter: OpenApiImporter = OpenApiImporter(),
) : InsomniaImporter {

    companion object {
        const val TYPE_COLLECTION = "collection.insomnia.rest/5.0"
        const val TYPE_SPEC = "spec.insomnia.rest/5.0"
        const val TYPE_ENVIRONMENT = "environment.insomnia.rest/5.0"
        const val TYPE_MOCK = "mock.insomnia.rest/5.0"
        const val TYPE_MCP = "mcpClient.insomnia/5.0"

        private val INSOMNIA_TYPE_MARKERS = listOf(
            TYPE_COLLECTION,
            TYPE_SPEC,
            TYPE_ENVIRONMENT,
            TYPE_MOCK,
            TYPE_MCP,
        )

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val kaml = Yaml.default

        // Normalizes Insomnia Nunjucks environment variable references such as `{{ _.base_url }}` or `{{ base_url }}` to `{{base_url}}`
        private val INSOMNIA_VAR_REGEX = Regex("""\{\{\s*(?:_\.)?([A-Za-z0-9_.-]+)\s*\}\}""")

        internal fun normalizeTemplate(value: String): String =
            INSOMNIA_VAR_REGEX.replace(value) { "{{${it.groupValues[1]}}}" }
    }

    override fun canHandle(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return false
        return INSOMNIA_TYPE_MARKERS.any { marker ->
            trimmed.contains(marker)
        }
    }

    override suspend fun scanContent(rawContent: String): ImportScanResult {
        val document = try {
            parseDocument(rawContent)
        } catch (e: Exception) {
            return ImportScanResult.Failure(
                message = "Failed to parse Insomnia v5 export: ${e.message ?: "Invalid YAML or JSON"}",
                details = listOfNotNull(e.message),
            )
        }

        when {
            document.type == TYPE_MOCK || document.type.startsWith("mock.insomnia.rest/") -> {
                return ImportScanResult.Failure(
                    message = "Insomnia mock server exports (${document.type}) are not supported as request collections.",
                )
            }
            document.type == TYPE_MCP || document.type.startsWith("mcpClient.insomnia/") -> {
                return ImportScanResult.Failure(
                    message = "Insomnia MCP client exports (${document.type}) cannot be imported as request collections.",
                )
            }
            !document.type.startsWith("collection.insomnia.rest/") &&
                !document.type.startsWith("spec.insomnia.rest/") &&
                !document.type.startsWith("environment.insomnia.rest/") -> {
                return ImportScanResult.Failure(
                    message = "Unsupported Insomnia document type '${document.type}'. Expected collection, spec, or environment v5 export.",
                )
            }
        }

        val docTitle = document.name?.takeIf { it.isNotBlank() } ?: "Insomnia Import"
        val environments = mapEnvironments(document.environments)
        val candidateEnvironments = environments.map { env ->
            ImportCandidateEnvironment(
                id = UUID.randomUUID().toString(),
                originalName = env.name,
                name = env.name,
                environment = env,
                variableCount = env.variables.size,
                selected = true,
                hasConflict = false,
            )
        }.toMutableList()

        val candidateFolders = mutableListOf<ImportCandidateFolder>()

        if (document.collection.isNotEmpty()) {
            val openColDoc = mapToOpenCollectionDocument(document, environments)
            val folderId = (openColDoc.extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            val rootFolder = FolderItem(
                info = FolderInfo(
                    name = openColDoc.info.name,
                    description = openColDoc.info.summary?.let { Description(it) },
                    type = "folder",
                ),
                items = openColDoc.items,
                request = openColDoc.request,
                docs = openColDoc.docs,
                extensions = openColDoc.extensions + ("id" to JsonPrimitive(folderId)),
            )
            candidateFolders.add(
                ImportCandidateFolder(
                    id = folderId,
                    originalName = openColDoc.info.name,
                    name = openColDoc.info.name,
                    folder = rootFolder,
                    requestCount = countRequests(openColDoc.items),
                    selected = true,
                    hasConflict = false,
                )
            )
        } else if (document.type.startsWith("spec.insomnia.rest/") && document.spec != null) {
            val specRaw = extractSpecOpenApiString(document.spec)
            if (!specRaw.isNullOrBlank()) {
                val specScan = openApiImporter.scanContent(specRaw)
                if (specScan is ImportScanResult.Success) {
                    val renamedFolders = specScan.folders.mapIndexed { idx, f ->
                        if (idx == 0 && document.name != null && document.name.isNotBlank()) {
                            f.copy(
                                originalName = document.name,
                                name = document.name,
                                folder = f.folder.copy(info = f.folder.info.copy(name = document.name)),
                            )
                        } else {
                            f
                        }
                    }
                    candidateFolders.addAll(renamedFolders)
                    if (candidateEnvironments.isEmpty()) {
                        candidateEnvironments.addAll(specScan.environments)
                    }
                }
            }
        }

        if (candidateFolders.isEmpty() && candidateEnvironments.isEmpty() && !document.type.startsWith("environment.insomnia.rest/")) {
            // Even an empty collection should produce at least the root collection folder
            if (document.type.startsWith("collection.insomnia.rest/")) {
                val folderId = document.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val rootFolder = FolderItem(
                    info = FolderInfo(name = docTitle, type = "folder"),
                    items = emptyList(),
                    extensions = mapOf("id" to JsonPrimitive(folderId)),
                )
                candidateFolders.add(
                    ImportCandidateFolder(
                        id = folderId,
                        originalName = docTitle,
                        name = docTitle,
                        folder = rootFolder,
                        requestCount = 0,
                        selected = true,
                        hasConflict = false,
                    )
                )
            }
        }

        return ImportScanResult.Success(
            format = SpecFormat.INSOMNIA,
            specTitle = docTitle,
            folders = candidateFolders,
            environments = candidateEnvironments,
        )
    }

    override fun importToOpenCollection(rawContent: String): OpenCollectionDocument {
        val document = parseDocument(rawContent)
        when {
            document.type == TYPE_MOCK || document.type.startsWith("mock.insomnia.rest/") ->
                throw IllegalArgumentException("Insomnia mock server exports (${document.type}) are not supported.")
            document.type == TYPE_MCP || document.type.startsWith("mcpClient.insomnia/") ->
                throw IllegalArgumentException("Insomnia MCP client exports (${document.type}) are not supported.")
            !document.type.startsWith("collection.insomnia.rest/") &&
                !document.type.startsWith("spec.insomnia.rest/") &&
                !document.type.startsWith("environment.insomnia.rest/") ->
                throw IllegalArgumentException("Unsupported Insomnia document type '${document.type}'.")
        }

        val environments = mapEnvironments(document.environments)
        if (document.collection.isEmpty() && document.type.startsWith("spec.insomnia.rest/") && document.spec != null) {
            val specRaw = extractSpecOpenApiString(document.spec)
            if (!specRaw.isNullOrBlank()) {
                val specScan = runBlocking { openApiImporter.scanContent(specRaw) }
                if (specScan is ImportScanResult.Success && specScan.folders.isNotEmpty()) {
                    val primaryFolder = specScan.folders.first().folder
                    val docName = document.name?.takeIf { it.isNotBlank() } ?: specScan.specTitle
                    val mergedEnvs = if (environments.isNotEmpty()) {
                        environments
                    } else {
                        specScan.environments.map { it.environment }
                    }
                    val docId = document.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                    return OpenCollectionDocument(
                        opencollection = "1.0.0",
                        info = CollectionInfo(
                            name = docName,
                            summary = document.meta?.description?.takeIf { it.isNotBlank() },
                        ),
                        config = if (mergedEnvs.isNotEmpty()) CollectionConfig(environments = mergedEnvs) else null,
                        items = primaryFolder.items,
                        bundled = true,
                        extensions = mapOf("id" to JsonPrimitive(docId)),
                    )
                }
            }
        }

        return mapToOpenCollectionDocument(document, environments)
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    internal fun parseDocument(rawContent: String): InsomniaV5Document {
        val trimmed = rawContent.trim()
        val rootObj: JsonObject = if (trimmed.startsWith("{")) {
            json.parseToJsonElement(trimmed).jsonObject
        } else {
            val yamlNode = kaml.parseToYamlNode(rawContent)
            yamlNodeToJsonElement(yamlNode, rawContent.lines()).jsonObject
        }

        val type = rootObj["type"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing 'type' field in Insomnia document")
        val schemaVersion = rootObj["schema_version"]?.jsonPrimitive?.contentOrNull ?: "5.1"
        val name = rootObj["name"]?.jsonPrimitive?.contentOrNull
        val meta = parseMeta(rootObj["meta"])
        val collectionArray = rootObj["collection"] as? JsonArray
        val collectionItems = collectionArray?.mapNotNull { parseItem(it) } ?: emptyList()
        val environments = parseEnvironments(rootObj["environments"])
        val spec = parseSpec(rootObj["spec"])
        val scripts = parseScripts(rootObj["scripts"])
        val auth = parseAuth(rootObj["authentication"])
        val headers = parseHeaders(rootObj["headers"])

        // Unsupported root-level fields (`certificates`, `cookieJar`, `testSuites`) are safely bypassed
        // as documented in `scratch/insomnia-import-unsupported.md`.

        return InsomniaV5Document(
            type = type,
            schemaVersion = schemaVersion,
            name = name,
            meta = meta,
            collection = collectionItems,
            environments = environments,
            spec = spec,
            scripts = scripts,
            authentication = auth,
            headers = headers,
        )
    }

    private fun parseMeta(element: JsonElement?): InsomniaMeta? {
        val obj = element as? JsonObject ?: return null
        return InsomniaMeta(
            id = obj["id"]?.jsonPrimitive?.contentOrNull,
            description = obj["description"]?.jsonPrimitive?.contentOrNull,
            created = obj["created"]?.jsonPrimitive?.doubleOrNull,
            modified = obj["modified"]?.jsonPrimitive?.doubleOrNull,
            isPrivate = obj["isPrivate"]?.jsonPrimitive?.booleanOrNull,
            sortKey = obj["sortKey"]?.jsonPrimitive?.doubleOrNull,
        )
    }

    private fun parseSpec(element: JsonElement?): InsomniaSpec? {
        val obj = element as? JsonObject ?: return null
        val contentsEl = obj["contents"]
        val contentsStr = when (contentsEl) {
            is JsonPrimitive -> contentsEl.contentOrNull
            is JsonObject -> OpenCollectionSerializer.jsonElementToYaml(contentsEl).trim()
            else -> null
        }
        return InsomniaSpec(
            contents = contentsStr,
            rawContentsElement = contentsEl,
            file = obj["file"]?.jsonPrimitive?.contentOrNull,
            meta = parseMeta(obj["meta"]),
        )
    }

    private fun extractSpecOpenApiString(spec: InsomniaSpec): String? {
        if (!spec.contents.isNullOrBlank()) return spec.contents
        val rawEl = spec.rawContentsElement ?: return null
        return OpenCollectionSerializer.jsonElementToYaml(rawEl).trim()
    }

    private fun parseItem(element: JsonElement): InsomniaItem? {
        val obj = element as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().takeUnless { it.isNullOrEmpty() } ?: "Untitled"
        val meta = parseMeta(obj["meta"])
        val metaId = meta?.id.orEmpty()
        val url = normalizeTemplate(obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty())

        // 1. Folder check: has non-null `children` array (and no `method` string unless empty)
        val childrenEl = obj["children"]
        if (childrenEl is JsonArray) {
            val childItems = childrenEl.mapNotNull { parseItem(it) }
            val envObj = obj["environment"] as? JsonObject
            val envMap = envObj?.mapValues { (_, v) -> jsonValueToString(v) } ?: emptyMap()
            return InsomniaFolder(
                name = name,
                meta = meta,
                children = childItems,
                scripts = parseScripts(obj["scripts"]),
                authentication = parseAuth(obj["authentication"]),
                headers = parseHeaders(obj["headers"]),
                environment = envMap,
                kvPairData = parseKvPairs(obj["kvPairData"]),
                environmentType = obj["environmentType"]?.jsonPrimitive?.contentOrNull,
            )
        }

        // 2. gRPC request check
        val hasGrpcFields = obj.containsKey("protoMethodName") ||
            obj.containsKey("protoFileId") ||
            obj.containsKey("reflectionApi") ||
            metaId.startsWith("greq")
        if (hasGrpcFields) {
            val reflectionObj = obj["reflectionApi"] as? JsonObject
            val reflection = reflectionObj?.let {
                InsomniaReflectionApi(
                    enabled = it["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                    url = it["url"]?.jsonPrimitive?.contentOrNull,
                    apiKey = it["apiKey"]?.jsonPrimitive?.contentOrNull,
                    module = it["module"]?.jsonPrimitive?.contentOrNull,
                )
            }
            return InsomniaGrpcRequest(
                name = name,
                meta = meta,
                url = url,
                protoMethodName = obj["protoMethodName"]?.jsonPrimitive?.contentOrNull,
                protoFileId = obj["protoFileId"]?.jsonPrimitive?.contentOrNull,
                reflectionApi = reflection,
                metadata = parseHeaders(obj["metadata"]),
                body = parseBody(obj["body"]),
            )
        }

        // 3. WebSocket / SocketIO check
        val methodStr = obj["method"]?.jsonPrimitive?.contentOrNull
        val isWebSocket = metaId.startsWith("ws-req") ||
            metaId.startsWith("socketio-req") ||
            url.startsWith("ws://", ignoreCase = true) ||
            url.startsWith("wss://", ignoreCase = true) ||
            (methodStr == null && obj.containsKey("eventListeners"))
        if (isWebSocket) {
            val wsBodyObj = obj["body"] as? JsonObject
            val wsBody = wsBodyObj?.let {
                InsomniaWebSocketBody(
                    name = it["name"]?.jsonPrimitive?.contentOrNull,
                    value = it["value"]?.jsonPrimitive?.contentOrNull ?: it["text"]?.jsonPrimitive?.contentOrNull,
                    mode = it["mode"]?.jsonPrimitive?.contentOrNull ?: it["mimeType"]?.jsonPrimitive?.contentOrNull,
                )
            }
            return InsomniaWebSocketRequest(
                name = name,
                meta = meta,
                url = url,
                headers = parseHeaders(obj["headers"]),
                parameters = parseParameters(obj["parameters"]),
                pathParameters = parseParameters(obj["pathParameters"]),
                authentication = parseAuth(obj["authentication"]),
                body = wsBody,
            )
        }

        // 4. Default: HTTP request (or folder without children if method is absent and children key exists)
        if (methodStr == null && !obj.containsKey("url")) {
            return InsomniaFolder(
                name = name,
                meta = meta,
                children = emptyList(),
                scripts = parseScripts(obj["scripts"]),
                authentication = parseAuth(obj["authentication"]),
                headers = parseHeaders(obj["headers"]),
            )
        }

        val settingsObj = obj["settings"] as? JsonObject
        val settings = settingsObj?.let {
            InsomniaRequestSettings(
                renderRequestBody = it["renderRequestBody"]?.jsonPrimitive?.booleanOrNull,
                encodeUrl = it["encodeUrl"]?.jsonPrimitive?.booleanOrNull,
                followRedirects = it["followRedirects"]?.jsonPrimitive?.contentOrNull,
                rebuildPath = it["rebuildPath"]?.jsonPrimitive?.booleanOrNull,
            )
        }

        return InsomniaHttpRequest(
            name = name,
            meta = meta,
            url = url,
            method = methodStr?.uppercase() ?: "GET",
            headers = parseHeaders(obj["headers"]),
            parameters = parseParameters(obj["parameters"]),
            pathParameters = parseParameters(obj["pathParameters"]),
            body = parseBody(obj["body"]),
            authentication = parseAuth(obj["authentication"]),
            scripts = parseScripts(obj["scripts"]),
            settings = settings,
        )
    }

    private fun parseHeaders(element: JsonElement?): List<InsomniaHeader> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = normalizeTemplate(obj["value"]?.jsonPrimitive?.contentOrNull ?: "")
            InsomniaHeader(
                name = name,
                value = value,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    private fun parseParameters(element: JsonElement?): List<InsomniaParameter> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = normalizeTemplate(obj["value"]?.jsonPrimitive?.contentOrNull ?: "")
            InsomniaParameter(
                name = name,
                value = value,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    private fun parseBody(element: JsonElement?): InsomniaBody? {
        val obj = element as? JsonObject ?: return null
        val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull
        val text = obj["text"]?.jsonPrimitive?.contentOrNull
        val fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull
        val paramsArr = obj["params"] as? JsonArray
        val params = paramsArr?.mapNotNull { item ->
            val pObj = item as? JsonObject ?: return@mapNotNull null
            val name = pObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            InsomniaBodyParam(
                name = name,
                value = pObj["value"]?.jsonPrimitive?.contentOrNull ?: "",
                description = pObj["description"]?.jsonPrimitive?.contentOrNull,
                disabled = pObj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                type = pObj["type"]?.jsonPrimitive?.contentOrNull,
                fileName = pObj["fileName"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()

        if (mimeType == null && text == null && fileName == null && params.isEmpty()) {
            return null
        }
        return InsomniaBody(
            mimeType = mimeType,
            text = text,
            fileName = fileName,
            params = params,
        )
    }

    private fun parseScripts(element: JsonElement?): InsomniaScripts? {
        val obj = element as? JsonObject ?: return null
        val preRequest = obj["preRequest"]?.jsonPrimitive?.contentOrNull
        val afterResponse = obj["afterResponse"]?.jsonPrimitive?.contentOrNull
        if (preRequest.isNullOrBlank() && afterResponse.isNullOrBlank()) return null
        return InsomniaScripts(
            preRequest = preRequest,
            afterResponse = afterResponse,
        )
    }

    private fun parseAuth(element: JsonElement?): InsomniaAuth? {
        val obj = element as? JsonObject ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        return InsomniaAuth(
            type = type,
            disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            username = obj["username"]?.jsonPrimitive?.contentOrNull?.let(::normalizeTemplate),
            password = obj["password"]?.jsonPrimitive?.contentOrNull?.let(::normalizeTemplate),
            token = obj["token"]?.jsonPrimitive?.contentOrNull?.let(::normalizeTemplate),
            prefix = obj["prefix"]?.jsonPrimitive?.contentOrNull,
            key = obj["key"]?.jsonPrimitive?.contentOrNull?.let(::normalizeTemplate),
            value = obj["value"]?.jsonPrimitive?.contentOrNull?.let(::normalizeTemplate),
            addTo = obj["addTo"]?.jsonPrimitive?.contentOrNull,
            grantType = obj["grantType"]?.jsonPrimitive?.contentOrNull,
            accessTokenUrl = obj["accessTokenUrl"]?.jsonPrimitive?.contentOrNull,
            authorizationUrl = obj["authorizationUrl"]?.jsonPrimitive?.contentOrNull,
            redirectUrl = obj["redirectUrl"]?.jsonPrimitive?.contentOrNull,
            clientId = obj["clientId"]?.jsonPrimitive?.contentOrNull,
            clientSecret = obj["clientSecret"]?.jsonPrimitive?.contentOrNull,
            scope = obj["scope"]?.jsonPrimitive?.contentOrNull,
            credentialsInBody = obj["credentialsInBody"]?.jsonPrimitive?.booleanOrNull,
            accessToken = obj["accessToken"]?.jsonPrimitive?.contentOrNull,
            refreshToken = obj["refreshToken"]?.jsonPrimitive?.contentOrNull,
            accessKeyId = obj["accessKeyId"]?.jsonPrimitive?.contentOrNull,
            secretAccessKey = obj["secretAccessKey"]?.jsonPrimitive?.contentOrNull,
            sessionToken = obj["sessionToken"]?.jsonPrimitive?.contentOrNull,
            region = obj["region"]?.jsonPrimitive?.contentOrNull,
            service = obj["service"]?.jsonPrimitive?.contentOrNull,
            consumerKey = obj["consumerKey"]?.jsonPrimitive?.contentOrNull,
            consumerSecret = obj["consumerSecret"]?.jsonPrimitive?.contentOrNull,
            tokenKey = obj["tokenKey"]?.jsonPrimitive?.contentOrNull,
            tokenSecret = obj["tokenSecret"]?.jsonPrimitive?.contentOrNull,
            signatureMethod = obj["signatureMethod"]?.jsonPrimitive?.contentOrNull,
            domain = obj["domain"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun parseEnvironments(element: JsonElement?): InsomniaEnvironments? {
        val obj = element as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Base Environment"
        val meta = parseMeta(obj["meta"])
        val dataObj = obj["data"] as? JsonObject
        val dataMap = dataObj?.mapValues { (_, v) -> jsonValueToString(v) } ?: emptyMap()
        val color = obj["color"]?.jsonPrimitive?.contentOrNull
        val kvPairs = parseKvPairs(obj["kvPairData"])
        val envType = obj["environmentType"]?.jsonPrimitive?.contentOrNull
        val confidentialKeys = (obj["confidentialKeys"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val subEnvsArr = obj["subEnvironments"] as? JsonArray
        val subEnvs = subEnvsArr?.mapNotNull { subEl ->
            val subObj = subEl as? JsonObject ?: return@mapNotNull null
            val subName = subObj["name"]?.jsonPrimitive?.contentOrNull ?: "Sub Environment"
            val subDataObj = subObj["data"] as? JsonObject
            val subDataMap = subDataObj?.mapValues { (_, v) -> jsonValueToString(v) } ?: emptyMap()
            InsomniaSubEnvironment(
                name = subName,
                meta = parseMeta(subObj["meta"]),
                data = subDataMap,
                color = subObj["color"]?.jsonPrimitive?.contentOrNull,
                kvPairData = parseKvPairs(subObj["kvPairData"]),
                environmentType = subObj["environmentType"]?.jsonPrimitive?.contentOrNull,
                confidentialKeys = (subObj["confidentialKeys"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList(),
            )
        } ?: emptyList()

        return InsomniaEnvironments(
            name = name,
            meta = meta,
            data = dataMap,
            color = color,
            kvPairData = kvPairs,
            environmentType = envType,
            confidentialKeys = confidentialKeys,
            subEnvironments = subEnvs,
        )
    }

    private fun parseKvPairs(element: JsonElement?): List<InsomniaKvPair> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            InsomniaKvPair(
                id = obj["id"]?.jsonPrimitive?.contentOrNull,
                name = name,
                value = obj["value"]?.jsonPrimitive?.contentOrNull ?: "",
                type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "str",
                enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                isConfidential = obj["isConfidential"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    // ── Mapping to OpenCollection ──────────────────────────────────────────────

    private fun mapToOpenCollectionDocument(
        doc: InsomniaV5Document,
        environments: List<Environment>,
    ): OpenCollectionDocument {
        val docName = doc.name?.takeIf { it.isNotBlank() } ?: "Insomnia Import"
        val docId = doc.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val items = doc.collection.map { mapItem(it) }
        val requestDefaults = buildRequestDefaults(
            headers = doc.headers,
            auth = doc.authentication,
            scripts = doc.scripts,
            variables = emptyList(),
        )

        return OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = docName,
                summary = doc.meta?.description?.takeIf { it.isNotBlank() },
            ),
            config = if (environments.isNotEmpty()) CollectionConfig(environments = environments) else null,
            items = items,
            request = requestDefaults,
            docs = doc.meta?.description?.takeIf { it.isNotBlank() }?.let { Documentation(it) },
            bundled = true,
            extensions = mapOf("id" to JsonPrimitive(docId)),
        )
    }

    private fun mapItem(item: InsomniaItem): OpenCollectionItem {
        return when (item) {
            is InsomniaFolder -> mapFolder(item)
            is InsomniaHttpRequest -> mapHttpRequest(item)
            is InsomniaGrpcRequest -> mapGrpcRequest(item)
            is InsomniaWebSocketRequest -> mapWebSocketRequest(item)
        }
    }

    private fun mapFolder(folder: InsomniaFolder): FolderItem {
        val folderId = folder.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val folderVars = extractVariables(
            data = folder.environment,
            kvPairs = folder.kvPairData,
            environmentType = folder.environmentType,
            confidentialKeys = emptySet(),
        )
        val requestDefaults = buildRequestDefaults(
            headers = folder.headers,
            auth = folder.authentication,
            scripts = folder.scripts,
            variables = folderVars,
        )
        val desc = folder.meta?.description?.takeIf { it.isNotBlank() }
        return FolderItem(
            info = FolderInfo(
                name = folder.name,
                description = desc?.let { Description(it) },
                type = "folder",
                seq = folder.meta?.sortKey,
            ),
            items = folder.children.map { mapItem(it) },
            request = requestDefaults,
            docs = desc?.let { Documentation(it) },
            extensions = mapOf("id" to JsonPrimitive(folderId)),
        )
    }

    private fun mapHttpRequest(req: InsomniaHttpRequest): OpenCollectionItem {
        val reqId = req.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val desc = req.meta?.description?.takeIf { it.isNotBlank() }
        val headers = req.headers.map { h ->
            HttpRequestHeader(
                name = h.name,
                value = h.value,
                description = h.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                disabled = if (h.disabled) true else null,
            )
        }
        val queryParams = req.parameters.map { p ->
            HttpRequestParam(
                name = p.name,
                value = p.value,
                description = p.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                type = HttpRequestParamType.QUERY,
                disabled = if (p.disabled) true else null,
            )
        }
        val pathParams = req.pathParameters.map { p ->
            HttpRequestParam(
                name = p.name,
                value = p.value,
                description = p.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                type = HttpRequestParamType.PATH,
                disabled = if (p.disabled) true else null,
            )
        }
        val allParams = queryParams + pathParams
        val auth = mapAuth(req.authentication)
        val scripts = mapScripts(req.scripts)

        // Check if body is GraphQL
        val isGraphQl = req.body?.mimeType?.contains("graphql", ignoreCase = true) == true
        if (isGraphQl && !req.body?.text.isNullOrBlank()) {
            val gqlPayload = parseGraphQlText(req.body!!.text!!)
            return GraphQLRequestItem(
                info = GraphQLRequestInfo(
                    name = req.name,
                    description = desc?.let { Description(it) },
                    type = "graphql",
                    seq = req.meta?.sortKey,
                ),
                graphql = GraphQLRequestDetails(
                    method = req.method,
                    url = req.url,
                    headers = headers,
                    params = allParams,
                    body = GraphQLBodyPayload.Single(gqlPayload),
                    auth = auth,
                ),
                runtime = if (scripts.isNotEmpty()) GraphQLRequestRuntime(scripts = scripts) else null,
                docs = desc,
                extensions = mapOf("id" to JsonPrimitive(reqId)),
            )
        }

        val httpBody = mapHttpBody(req.body, req.headers)
        val settings = req.settings?.let { s ->
            val follow = when (s.followRedirects?.lowercase()) {
                "on" -> JsonPrimitive(true)
                "off" -> JsonPrimitive(false)
                "global" -> JsonPrimitive("inherit")
                else -> null
            }
            val encode = s.encodeUrl?.let { JsonPrimitive(it) }
            if (follow != null || encode != null) {
                HttpRequestSettings(
                    encodeUrl = encode,
                    followRedirects = follow,
                )
            } else null
        }

        return HttpRequestItem(
            info = HttpRequestInfo(
                name = req.name,
                description = desc?.let { Description(it) },
                type = "http",
                seq = req.meta?.sortKey,
            ),
            http = HttpRequestDetails(
                method = req.method,
                url = req.url,
                headers = headers,
                params = allParams,
                body = httpBody,
                auth = auth,
            ),
            runtime = if (scripts.isNotEmpty()) HttpRequestRuntime(scripts = scripts) else null,
            settings = settings,
            docs = desc,
            extensions = mapOf("id" to JsonPrimitive(reqId)),
        )
    }

    private fun parseGraphQlText(rawText: String): GraphQLBody {
        val parsed = runCatching { json.parseToJsonElement(rawText).jsonObject }.getOrNull()
        if (parsed != null && parsed.containsKey("query")) {
            val query = parsed["query"]?.jsonPrimitive?.contentOrNull ?: ""
            val varsEl = parsed["variables"]
            val varsStr = when (varsEl) {
                null, is JsonNull -> null
                is JsonPrimitive -> varsEl.contentOrNull
                else -> varsEl.toString()
            }
            return GraphQLBody(query = query, variables = varsStr)
        }
        return GraphQLBody(query = rawText)
    }

    private fun mapHttpBody(body: InsomniaBody?, headers: List<InsomniaHeader>): HttpRequestBody? {
        if (body == null) return null
        val mime = body.mimeType?.lowercase().orEmpty()

        return when {
            "multipart/form-data" in mime || (mime.isEmpty() && body.params.any { it.type == "file" || it.fileName != null }) -> {
                val parts = body.params.map { p ->
                    val isFile = p.type.equals("file", ignoreCase = true) || !p.fileName.isNullOrBlank()
                    MultipartPartEntry(
                        name = p.name,
                        type = if (isFile) MultipartPartType.FILE else MultipartPartType.TEXT,
                        value = JsonPrimitive(if (isFile) (p.fileName ?: p.value) else p.value),
                        description = p.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                        disabled = if (p.disabled) true else null,
                    )
                }
                MultipartFormBody(data = parts)
            }
            "application/x-www-form-urlencoded" in mime || (body.params.isNotEmpty() && body.text == null) -> {
                val entries = body.params.map { p ->
                    FormParamEntry(
                        name = p.name,
                        value = p.value,
                        description = p.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                        disabled = if (p.disabled) true else null,
                    )
                }
                FormUrlEncodedBody(data = entries)
            }
            !body.fileName.isNullOrBlank() && body.text == null -> {
                FileBody(
                    data = listOf(
                        FileBodyVariant(
                            filePath = body.fileName,
                            contentType = body.mimeType ?: "application/octet-stream",
                            selected = true,
                        )
                    )
                )
            }
            body.text != null -> {
                val contentTypeHeader = headers
                    .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
                    ?.value
                    ?.lowercase()
                    .orEmpty()
                val effectiveMime = mime.ifEmpty { contentTypeHeader }
                val bodyType = when {
                    "json" in effectiveMime -> RequestBodyType.JSON
                    "xml" in effectiveMime -> RequestBodyType.XML
                    "sparql" in effectiveMime -> RequestBodyType.SPARQL
                    looksLikeJson(body.text) -> RequestBodyType.JSON
                    else -> RequestBodyType.TEXT
                }
                RawBody(type = bodyType, data = body.text)
            }
            else -> null
        }
    }

    private fun looksLikeJson(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
    }

    private fun mapGrpcRequest(req: InsomniaGrpcRequest): GrpcRequestItem {
        val reqId = req.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val desc = req.meta?.description?.takeIf { it.isNotBlank() }
        val metadata = req.metadata.map { m ->
            GrpcMetadata(
                name = m.name,
                value = m.value,
                description = m.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                disabled = if (m.disabled) true else null,
            )
        }
        return GrpcRequestItem(
            info = GrpcRequestInfo(
                name = req.name,
                description = desc?.let { Description(it) },
                type = "grpc",
                seq = req.meta?.sortKey,
            ),
            grpc = GrpcRequestDetails(
                url = req.url,
                method = req.protoMethodName,
                methodType = GrpcMethodType.UNARY,
                protoFilePath = req.protoFileId,
                metadata = metadata,
                message = req.body?.text,
            ),
            docs = desc,
            extensions = mapOf("id" to JsonPrimitive(reqId)),
        )
    }

    private fun mapWebSocketRequest(req: InsomniaWebSocketRequest): WebSocketRequestItem {
        val reqId = req.meta?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val desc = req.meta?.description?.takeIf { it.isNotBlank() }
        val headers = req.headers.map { h ->
            HttpRequestHeader(
                name = h.name,
                value = h.value,
                description = h.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                disabled = if (h.disabled) true else null,
            )
        }
        val fullUrl = appendQueryParamsToUrl(req.url, req.parameters)
        val messagePayload = req.body?.value?.let { bodyVal ->
            val msgType = when {
                req.body.mode?.contains("json", ignoreCase = true) == true -> WebSocketMessageType.JSON
                req.body.mode?.contains("xml", ignoreCase = true) == true -> WebSocketMessageType.XML
                looksLikeJson(bodyVal) -> WebSocketMessageType.JSON
                else -> WebSocketMessageType.TEXT
            }
            WebSocketMessagePayload.Single(WebSocketMessage(type = msgType, data = bodyVal))
        }
        return WebSocketRequestItem(
            info = WebSocketRequestInfo(
                name = req.name,
                description = desc?.let { Description(it) },
                type = "websocket",
                seq = req.meta?.sortKey,
            ),
            websocket = WebSocketRequestDetails(
                url = fullUrl,
                headers = headers,
                message = messagePayload,
                auth = mapAuth(req.authentication),
            ),
            docs = desc,
            extensions = mapOf("id" to JsonPrimitive(reqId)),
        )
    }

    private fun appendQueryParamsToUrl(baseUrl: String, params: List<InsomniaParameter>): String {
        val enabled = params.filter { !it.disabled && it.name.isNotBlank() }
        if (enabled.isEmpty()) return baseUrl
        val queryStr = enabled.joinToString("&") { "${it.name}=${it.value}" }
        return if ("?" in baseUrl) "$baseUrl&$queryStr" else "$baseUrl?$queryStr"
    }

    private fun mapAuth(auth: InsomniaAuth?): AuthConfig? {
        if (auth == null || auth.disabled) return null
        return when (auth.type.lowercase()) {
            "basic" -> AuthBasic(
                username = auth.username.orEmpty(),
                password = auth.password.orEmpty(),
            )
            "bearer", "singletoken" -> AuthBearer(
                token = auth.token.orEmpty(),
            )
            "apikey" -> {
                val placement = when (auth.addTo?.lowercase()) {
                    "queryparams", "query" -> ApiKeyPlacement.QUERY
                    else -> ApiKeyPlacement.HEADER
                }
                AuthApiKey(
                    key = auth.key.orEmpty(),
                    value = auth.value.orEmpty(),
                    placement = placement,
                )
            }
            "oauth2" -> {
                val flow = when (auth.grantType?.lowercase()) {
                    "authorization_code" -> OAuth2Flow.AUTHORIZATION_CODE
                    "password", "resource_owner_password_credentials" -> OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS
                    "implicit" -> OAuth2Flow.IMPLICIT
                    else -> OAuth2Flow.CLIENT_CREDENTIALS
                }
                val placement = if (auth.credentialsInBody == true) {
                    OAuth2CredentialsPlacement.BODY
                } else {
                    OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
                }
                AuthOAuth2(
                    flow = flow,
                    accessTokenUrl = auth.accessTokenUrl,
                    authorizationUrl = auth.authorizationUrl,
                    redirectUri = auth.redirectUrl,
                    credentials = OAuth2ClientCredentials(
                        clientId = auth.clientId,
                        clientSecret = auth.clientSecret,
                        placement = placement,
                    ),
                    resourceOwner = if (auth.username != null || auth.password != null) {
                        OAuth2ResourceOwner(username = auth.username, password = auth.password)
                    } else null,
                    scope = auth.scope,
                )
            }
            "iam", "awsv4" -> AuthAwsV4(
                accessKeyId = auth.accessKeyId,
                secretAccessKey = auth.secretAccessKey,
                sessionToken = auth.sessionToken,
                region = auth.region,
                service = auth.service,
            )
            "digest" -> AuthDigest(
                username = auth.username.orEmpty(),
                password = auth.password.orEmpty(),
            )
            "ntlm" -> AuthNTLM(
                username = auth.username.orEmpty(),
                password = auth.password.orEmpty(),
                domain = auth.domain,
            )
            "oauth1" -> AuthOAuth1(
                consumerKey = auth.consumerKey,
                consumerSecret = auth.consumerSecret,
                accessToken = auth.tokenKey,
                accessTokenSecret = auth.tokenSecret,
                signatureMethod = auth.signatureMethod?.let { OAuth1SignatureMethod.fromValue(it) },
            )
            "none" -> null
            // Unsupported schemes (`hawk`, `asap`, `netrc`) safely bypassed per scratch/insomnia-import-unsupported.md
            else -> null
        }
    }

    private fun mapScripts(scripts: InsomniaScripts?): List<Script> {
        if (scripts == null) return emptyList()
        val result = mutableListOf<Script>()
        if (!scripts.preRequest.isNullOrBlank()) {
            result.add(
                Script(
                    type = ScriptType.BEFORE_REQUEST,
                    code = InsomniaScriptTranslator.convertScript(scripts.preRequest.trim()),
                )
            )
        }
        if (!scripts.afterResponse.isNullOrBlank()) {
            result.add(
                Script(
                    type = ScriptType.AFTER_RESPONSE,
                    code = InsomniaScriptTranslator.convertScript(scripts.afterResponse.trim()),
                )
            )
        }
        return result
    }

    private fun buildRequestDefaults(
        headers: List<InsomniaHeader>,
        auth: InsomniaAuth?,
        scripts: InsomniaScripts?,
        variables: List<VariableEntry>,
    ): RequestDefaults? {
        val mappedHeaders = headers.map { h ->
            HttpRequestHeader(
                name = h.name,
                value = h.value,
                description = h.description?.takeIf { it.isNotBlank() }?.let { Description(it) },
                disabled = if (h.disabled) true else null,
            )
        }
        val mappedAuth = mapAuth(auth)
        val mappedScripts = mapScripts(scripts)
        if (mappedHeaders.isEmpty() && mappedAuth == null && mappedScripts.isEmpty() && variables.isEmpty()) {
            return null
        }
        return RequestDefaults(
            headers = mappedHeaders,
            auth = mappedAuth,
            variables = variables,
            scripts = mappedScripts,
        )
    }

    // ── Environments ───────────────────────────────────────────────────────────

    private fun mapEnvironments(insomniaEnvs: InsomniaEnvironments?): List<Environment> {
        if (insomniaEnvs == null) return emptyList()
        val result = mutableListOf<Environment>()

        val baseVars = extractVariables(
            data = insomniaEnvs.data,
            kvPairs = insomniaEnvs.kvPairData,
            environmentType = insomniaEnvs.environmentType,
            confidentialKeys = insomniaEnvs.confidentialKeys.toSet(),
        )

        if (baseVars.isNotEmpty() || insomniaEnvs.subEnvironments.isEmpty()) {
            val baseDesc = insomniaEnvs.meta?.description?.takeIf { it.isNotBlank() }
            result.add(
                Environment(
                    name = insomniaEnvs.name.ifBlank { "Base Environment" },
                    color = insomniaEnvs.color,
                    description = baseDesc?.let { Description(it) },
                    variables = baseVars,
                )
            )
        }

        val baseVarMap = baseVars.associateBy { it.name }

        for (sub in insomniaEnvs.subEnvironments) {
            val subVars = extractVariables(
                data = sub.data,
                kvPairs = sub.kvPairData,
                environmentType = sub.environmentType,
                confidentialKeys = sub.confidentialKeys.toSet(),
            )
            // Merge base environment variables with sub-environment overrides
            val mergedMap = LinkedHashMap<String, VariableEntry>(baseVarMap)
            for (v in subVars) {
                mergedMap[v.name] = v
            }
            val subDesc = sub.meta?.description?.takeIf { it.isNotBlank() }
            result.add(
                Environment(
                    name = sub.name.ifBlank { "Sub Environment" },
                    color = sub.color ?: insomniaEnvs.color,
                    description = subDesc?.let { Description(it) },
                    variables = mergedMap.values.toList(),
                )
            )
        }

        return result
    }

    private fun extractVariables(
        data: Map<String, String>,
        kvPairs: List<InsomniaKvPair>,
        environmentType: String?,
        confidentialKeys: Set<String>,
    ): List<VariableEntry> {
        if (environmentType.equals("kv", ignoreCase = true) || (kvPairs.isNotEmpty() && data.isEmpty())) {
            return kvPairs.map { kv ->
                val disabled = if (!kv.enabled) true else null
                if (kv.type.equals("secret", ignoreCase = true) || kv.isConfidential || kv.name in confidentialKeys) {
                    SecretVariable(
                        name = kv.name,
                        disabled = disabled,
                        secret = true,
                    )
                } else {
                    RegularVariable(
                        name = kv.name,
                        value = VariableValue.of(kv.value),
                        disabled = disabled,
                    )
                }
            }
        }
        return data.map { (key, value) ->
            if (key in confidentialKeys) {
                SecretVariable(name = key, secret = true)
            } else {
                RegularVariable(name = key, value = VariableValue.of(value))
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun countRequests(items: List<OpenCollectionItem>): Int {
        var count = 0
        for (item in items) {
            when (item) {
                is HttpRequestItem -> count++
                is GrpcRequestItem -> count++
                is WebSocketRequestItem -> count++
                is GraphQLRequestItem -> count++
                is FolderItem -> count += countRequests(item.items)
            }
        }
        return count
    }

    private fun jsonValueToString(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.contentOrNull ?: ""
        is JsonNull -> ""
        else -> element.toString()
    }

    private fun yamlNodeToJsonElement(node: YamlNode, sourceLines: List<String>): JsonElement {
        return when (node) {
            is YamlScalar -> {
                val content = node.content
                when {
                    isQuotedScalar(node, sourceLines) -> JsonPrimitive(content)
                    content.equals("true", ignoreCase = true) -> JsonPrimitive(true)
                    content.equals("false", ignoreCase = true) -> JsonPrimitive(false)
                    content.toLongOrNull() != null -> JsonPrimitive(content.toLong())
                    content.toDoubleOrNull() != null -> JsonPrimitive(content.toDouble())
                    else -> JsonPrimitive(content)
                }
            }
            is YamlNull -> JsonNull
            is YamlList -> JsonArray(node.items.map { yamlNodeToJsonElement(it, sourceLines) })
            is YamlMap -> {
                val map = mutableMapOf<String, JsonElement>()
                for ((keyNode, valueNode) in node.entries) {
                    map[keyNode.content] = yamlNodeToJsonElement(valueNode, sourceLines)
                }
                JsonObject(map)
            }
            else -> JsonPrimitive(node.contentToString())
        }
    }

    private fun isQuotedScalar(node: YamlScalar, sourceLines: List<String>): Boolean {
        val line = sourceLines.getOrNull(node.location.line - 1) ?: return false
        val quote = line.getOrNull(node.location.column - 1) ?: return false
        return quote == '"' || quote == '\''
    }
}
