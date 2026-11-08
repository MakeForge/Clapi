package io.github.crustack.clapi.core.storage.opencollection

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.GrpcRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionItem
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

object OpenCollectionSerializer {

    /**
     * Folder items cannot carry an `extensions` property: the OpenCollection schema
     * sets `additionalProperties: false` on `Folder`. Ids are stored here instead and
     * copied back onto [FolderItem.extensions] after deserialize.
     */
    const val FOLDER_IDS_KEY = "folderIds"

    val json: Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val kaml: Yaml = Yaml.default

    private val KNOWN_DOCUMENT_KEYS = setOf(
        "opencollection", "info", "config", "items", "request", "docs", "bundled", "extensions"
    )

    private val KNOWN_FOLDER_KEYS = setOf(
        "info", "items", "request", "docs", "extensions"
    )

    private val KNOWN_HTTP_REQUEST_KEYS = setOf(
        "info", "http", "runtime", "settings", "docs", "examples", "extensions"
    )

    private val KNOWN_GRPC_REQUEST_KEYS = setOf(
        "info", "grpc", "runtime", "docs", "extensions"
    )

    fun serializeJson(document: OpenCollectionDocument): String {
        val normalized = normalizeSecretVariables(document)
        val rootElement = json.encodeToJsonElement(OpenCollectionDocument.serializer(), normalized)
        val enriched = enrichJsonElementWithExtensions(rootElement, normalized)
        return json.encodeToString(JsonElement.serializer(), enriched)
    }

    fun deserializeJson(content: String): OpenCollectionDocument {
        val rootElement = json.parseToJsonElement(content)
        val rootObject = rootElement.jsonObject
        return deserializeFromJsonObject(rootObject)
    }

    fun serializeYaml(document: OpenCollectionDocument): String {
        val normalized = normalizeSecretVariables(document)
        val rootElement = json.encodeToJsonElement(OpenCollectionDocument.serializer(), normalized)
        val enriched = enrichJsonElementWithExtensions(rootElement, normalized)
        return jsonElementToYaml(enriched).trim()
    }

    // Secret variables are always written as `secret: true`; secret values never reach the document model.
    private fun normalizeSecretVariables(document: OpenCollectionDocument): OpenCollectionDocument {
        val config = document.config ?: return document
        val environments = config.environments.map { environment ->
            environment.copy(variables = environment.variables.map { entry ->
                if (entry is SecretVariable && !entry.secret) entry.copy(secret = true) else entry
            })
        }
        return document.copy(config = config.copy(environments = environments))
    }

    fun deserializeYaml(content: String): OpenCollectionDocument {
        val yamlNode = kaml.parseToYamlNode(content)
        val jsonElement = yamlNodeToJsonElement(yamlNode, content.lines())
        return deserializeFromJsonObject(jsonElement.jsonObject)
    }

    private fun enrichJsonElementWithExtensions(
        rootElement: JsonElement,
        document: OpenCollectionDocument
    ): JsonElement {
        val rootObj = rootElement.jsonObject.toMutableMap()

        // Document-level extensions are allowed. Folder ids go here, not on the folder item.
        val extensions = document.extensions.toMutableMap()
        val folderIds = collectFolderIds(document.items)
        if (folderIds.isNotEmpty()) {
            extensions[FOLDER_IDS_KEY] = JsonObject(folderIds)
        } else {
            extensions.remove(FOLDER_IDS_KEY)
        }
        if (extensions.isNotEmpty()) {
            rootObj["extensions"] = JsonObject(extensions)
        } else {
            rootObj.remove("extensions")
        }

        // Enrich items
        val itemsArray = rootObj["items"]?.jsonArray
        if (itemsArray != null && document.items.isNotEmpty()) {
            rootObj["items"] = JsonArray(enrichItemsForSerialization(itemsArray, document.items))
        }

        return JsonObject(rootObj)
    }

    private fun enrichItemsForSerialization(
        itemsJson: JsonArray,
        items: List<OpenCollectionItem>
    ): List<JsonElement> {
        return items.mapIndexed { index, item ->
            val baseItemObj = itemsJson.getOrNull(index)?.jsonObject?.toMutableMap() ?: mutableMapOf()
            when (item) {
                is FolderItem -> {
                    // Do not write item.extensions onto the folder. The schema rejects that key.
                    val subItems = baseItemObj["items"]?.jsonArray
                    if (subItems != null && item.items.isNotEmpty()) {
                        baseItemObj["items"] = JsonArray(enrichItemsForSerialization(subItems, item.items))
                    }
                }
                is HttpRequestItem -> {
                    // OpenCollection HttpRequest schema has additionalProperties: false;
                    // item extensions are not written into the item object directly.
                }
                is GrpcRequestItem -> {
                    // OpenCollection GrpcRequest schema has additionalProperties: false;
                    // item extensions are not written into the item object directly.
                }
            }
            JsonObject(baseItemObj)
        }
    }

    private fun deserializeFromJsonObject(rootObject: JsonObject): OpenCollectionDocument {
        // Unknown document-level fields to capture into extensions
        val extraTopLevel = rootObject.filterKeys { it !in KNOWN_DOCUMENT_KEYS }

        // Deserialize standard document structure
        val baseDoc = json.decodeFromJsonElement<OpenCollectionDocument>(rootObject)

        // Capture item-level unknown fields recursively
        val itemsJson = rootObject["items"]?.jsonArray
        val enrichedItems = if (itemsJson != null) {
            enrichItems(baseDoc.items, itemsJson)
        } else {
            baseDoc.items
        }

        // Merge extra top-level fields into document extensions.
        // folderIds is a persistence index, not a user extension: apply it, then drop it.
        val mergedExtensions = if (extraTopLevel.isNotEmpty()) {
            extraTopLevel + baseDoc.extensions
        } else {
            baseDoc.extensions
        }
        val folderIds = (mergedExtensions[FOLDER_IDS_KEY] as? JsonObject) ?: JsonObject(emptyMap())
        val itemsWithIds = applyFolderIds(enrichedItems, folderIds)

        return baseDoc.copy(
            items = itemsWithIds,
            extensions = mergedExtensions - FOLDER_IDS_KEY
        )
    }

    private fun collectFolderIds(
        items: List<OpenCollectionItem>,
        prefix: String = "",
    ): Map<String, JsonElement> {
        val ids = linkedMapOf<String, JsonElement>()
        items.forEachIndexed { index, item ->
            val path = if (prefix.isEmpty()) index.toString() else "$prefix/$index"
            if (item is FolderItem) {
                val id = item.extensions["id"]
                if (id != null && id !is JsonNull && !(id is JsonPrimitive && id.content.isBlank())) {
                    ids[path] = id
                }
                ids.putAll(collectFolderIds(item.items, path))
            }
        }
        return ids
    }

    private fun applyFolderIds(
        items: List<OpenCollectionItem>,
        folderIds: JsonObject,
        prefix: String = "",
    ): List<OpenCollectionItem> {
        return items.mapIndexed { index, item ->
            val path = if (prefix.isEmpty()) index.toString() else "$prefix/$index"
            when (item) {
                is FolderItem -> {
                    val id = folderIds[path]
                    val extensions = if (id != null) item.extensions + ("id" to id) else item.extensions
                    item.copy(
                        items = applyFolderIds(item.items, folderIds, path),
                        extensions = extensions,
                    )
                }
                else -> item
            }
        }
    }

    private fun enrichItems(items: List<OpenCollectionItem>, itemsJson: JsonArray): List<OpenCollectionItem> {
        return items.mapIndexed { index, item ->
            val itemJson = itemsJson.getOrNull(index)?.jsonObject ?: return@mapIndexed item
            when (item) {
                is FolderItem -> {
                    val extraFolderKeys = itemJson.filterKeys { it !in KNOWN_FOLDER_KEYS }
                    val mergedExt = if (extraFolderKeys.isNotEmpty()) extraFolderKeys + item.extensions else item.extensions
                    val subItemsJson = itemJson["items"]?.jsonArray
                    val subEnriched = if (subItemsJson != null) enrichItems(item.items, subItemsJson) else item.items
                    item.copy(items = subEnriched, extensions = mergedExt)
                }
                is HttpRequestItem -> {
                    val extraRequestKeys = itemJson.filterKeys { it !in KNOWN_HTTP_REQUEST_KEYS }
                    val mergedExt = if (extraRequestKeys.isNotEmpty()) extraRequestKeys + item.extensions else item.extensions
                    item.copy(extensions = mergedExt)
                }
                is GrpcRequestItem -> {
                    val extraRequestKeys = itemJson.filterKeys { it !in KNOWN_GRPC_REQUEST_KEYS }
                    val mergedExt = if (extraRequestKeys.isNotEmpty()) extraRequestKeys + item.extensions else item.extensions
                    item.copy(extensions = mergedExt)
                }
            }
        }
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

    // kaml does not expose scalar quoting, so check the source character at the node's start position.
    private fun isQuotedScalar(node: YamlScalar, sourceLines: List<String>): Boolean {
        val line = sourceLines.getOrNull(node.location.line - 1) ?: return false
        val quote = line.getOrNull(node.location.column - 1) ?: return false
        return quote == '"' || quote == '\''
    }

    fun jsonElementToYaml(element: JsonElement, indent: Int = 0): String {
        val indentStr = "  ".repeat(indent)
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    val s = element.content
                    val needsQuotes = s.isEmpty() ||
                        s.startsWith("{") || s.startsWith("[") ||
                        s.startsWith("*") || s.startsWith("&") ||
                        s.startsWith("!") || s.startsWith("|") ||
                        s.startsWith(">") || s.startsWith("%") ||
                        s.startsWith("@") || s.startsWith("`") ||
                        s.startsWith("- ") || s.startsWith("? ") ||
                        s.contains(":") || s.contains("#") ||
                        s.contains("\n") || s.contains("\"") ||
                        s.equals("true", ignoreCase = true) ||
                        s.equals("false", ignoreCase = true) ||
                        s.equals("null", ignoreCase = true) ||
                        s.toLongOrNull() != null || s.toDoubleOrNull() != null

                    if (needsQuotes) {
                        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
                    } else {
                        s
                    }
                } else {
                    element.content
                }
            }
            is JsonNull -> "null"
            is JsonArray -> {
                if (element.isEmpty()) "[]"
                else {
                    val sb = StringBuilder()
                    for (item in element) {
                        sb.append("\n").append(indentStr).append("- ")
                        sb.append(jsonElementToYaml(item, indent + 1).trimStart())
                    }
                    sb.toString()
                }
            }
            is JsonObject -> {
                if (element.isEmpty()) "{}"
                else {
                    val sb = StringBuilder()
                    for ((key, value) in element) {
                        sb.append("\n").append(indentStr).append(key).append(": ")
                        when (value) {
                            is JsonObject -> sb.append(jsonElementToYaml(value, indent + 1))
                            is JsonArray -> sb.append(jsonElementToYaml(value, indent + 1))
                            else -> sb.append(jsonElementToYaml(value, indent + 1))
                        }
                    }
                    sb.toString()
                }
            }
        }
    }
}
