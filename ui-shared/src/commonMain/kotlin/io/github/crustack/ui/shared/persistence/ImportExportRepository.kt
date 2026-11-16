package io.github.crustack.ui.shared.persistence

import io.github.crustack.core.model.opencollection.Environment
import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.core.storage.opencollection.CollectionStorageException
import io.github.crustack.core.storage.opencollection.CollectionValidationException
import io.github.crustack.core.storage.opencollection.ExportFormat
import io.github.crustack.core.storage.opencollection.OpenCollectionSchemaValidator
import io.github.crustack.core.storage.opencollection.OpenCollectionSerializer
import io.github.crustack.core.storage.opencollection.generateUniqueName
import io.github.crustack.ui.shared.state.AppState
import io.github.crustack.ui.shared.state.CollectionNode
import io.github.crustack.ui.shared.state.EnvState
import io.github.crustack.ui.shared.state.MutableKeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ImportExportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

// Converts storage-layer failures into messages that are safe to show in the import/export error dialog.
private inline fun <T> mapOpenCollectionErrors(block: () -> T): T =
    try {
        block()
    } catch (e: CollectionValidationException) {
        throw ImportExportException("The OpenCollection document is invalid. ${e.message}", e)
    } catch (e: CollectionStorageException) {
        throw ImportExportException("The OpenCollection document could not be read or written. ${e.message}", e)
    }

object ImportExportNaming {
    fun generateUniqueCollectionName(name: String, existingNames: Set<String>): String =
        generateUniqueName(name.trim().ifBlank { "Untitled" }, existingNames)

    fun generateUniqueEnvironmentName(name: String, existingNames: Set<String>): String =
        generateUniqueName(name.trim().ifBlank { "Untitled" }, existingNames)
}

data class ClapiEnvironmentDto(
    val name: String,
    val variables: Map<String, String>,
)

object ImportExportRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    private const val UNRECOGNIZED_COLLECTION_MESSAGE =
        "Unrecognized collection format. Expected an OpenCollection or Postman v2/v2.1 collection."

    fun exportCollectionToString(
        collectionRoot: CollectionNode,
        format: ExportFormat = ExportFormat.JSON,
    ): String {
        val doc = collectionRoot.toOpenCollectionDocument()
        return when (format) {
            ExportFormat.JSON -> OpenCollectionSerializer.serializeJson(doc)
            ExportFormat.YAML -> OpenCollectionSerializer.serializeYaml(doc)
        }
    }

    fun importCollectionFromString(state: AppState, rawContent: String): String {
        val trimmed = rawContent.trim()
        val doc: OpenCollectionDocument = if (trimmed.startsWith("{")) {
            val root = parseJsonString(trimmed)
            val rootType = root["type"]?.jsonPrimitive?.contentOrNull
            if (rootType == "clapiCollection" || rootType == "clapiWorkspace") {
                throw ImportExportException(
                    "Legacy format '$rootType' is no longer supported. Please import an OpenCollection or Postman collection."
                )
            }
            when {
                root["opencollection"] != null -> mapOpenCollectionErrors {
                    OpenCollectionSchemaValidator().validateOrThrow(trimmed)
                    OpenCollectionSerializer.deserializeJson(trimmed)
                }
                PostmanImporter.isPostmanCollection(root) ->
                    PostmanImporter.importCollection(root).toOpenCollectionDocument()
                else -> throw ImportExportException(UNRECOGNIZED_COLLECTION_MESSAGE)
            }
        } else if (trimmed.contains("opencollection:")) {
            mapOpenCollectionErrors {
                OpenCollectionSchemaValidator().validateYamlOrThrow(trimmed)
                OpenCollectionSerializer.deserializeYaml(trimmed)
            }
        } else {
            throw ImportExportException(UNRECOGNIZED_COLLECTION_MESSAGE)
        }

        // Merge any environments carried in the imported OpenCollection document by name (FR-016).
        mergeEnvironmentsIntoState(state, doc.config?.environments.orEmpty())

        // Add the imported collection as a top-level OpenCollection Folder with a unique name,
        // keeping its folder id (extensions["id"], FR-019).
        val existingNames = state.collections.map { it.name }.toSet()
        val baseName = doc.info.name.trim().ifBlank { "Untitled" }
        val uniqueName = generateUniqueName(baseName, existingNames)
        val existingIds = collectAllNodeIds(state.collections)
        val clapiRequests = (doc.extensions["clapi"] as? JsonObject)?.get("requests")?.jsonObject
        val topLevelFolder = doc.toCollectionFolder(nameOverride = uniqueName)
        val node = topLevelFolder.toCollectionNode(
            preserveIds = true,
            clapiRequests = clapiRequests,
            path = "",
            existingIds = existingIds,
        )
        state.collections.add(node)
        return uniqueName
    }

    fun mergeEnvironmentsIntoState(state: AppState, imported: List<Environment>) {
        for (incoming in imported) {
            val existing = state.environments.firstOrNull { it.name == incoming.name }
            if (existing == null) {
                state.environments.add(incoming.toEnvState())
            } else {
                existing.mergeFrom(incoming)
            }
        }
    }

    fun exportEnvironmentToString(environment: EnvState): String {
        val root = environmentToJson(environment)
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun importEnvironmentFromString(state: AppState, rawJson: String): String {
        val root = parseJsonString(rawJson)
        val dto = when {
            root["type"]?.jsonPrimitive?.contentOrNull == "clapiEnvironment" -> environmentDtoFromJson(root)
            PostmanImporter.isPostmanEnvironment(root) -> PostmanImporter.importEnvironment(root)
            else -> throw ImportExportException(
                "Unrecognized environment format. Expected a Clapi or Postman environment."
            )
        }
        val existing = state.environments.map { it.name }.toSet()
        val uniqueName = ImportExportNaming.generateUniqueEnvironmentName(dto.name, existing)
        state.environments.add(environmentDtoToState(dto, uniqueName))
        return uniqueName
    }

    private fun parseJsonString(rawJson: String): JsonObject =
        runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { throw ImportExportException("Invalid JSON", it) }

    private fun environmentToJson(environment: EnvState): JsonObject {
        val vars = buildJsonObject {
            environment.variables.forEach { v ->
                val key = v.key.trim()
                if (key.isNotEmpty()) put(key, v.value)
            }
        }
        return buildJsonObject {
            put("type", "clapiEnvironment")
            put("name", environment.name)
            put("variables", vars)
        }
    }

    private fun environmentDtoFromJson(root: JsonObject): ClapiEnvironmentDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Environment name is missing")
        val variables = root["variables"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull ?: "" }
            ?: emptyMap()
        return ClapiEnvironmentDto(name = name, variables = variables)
    }

    private fun environmentDtoToState(dto: ClapiEnvironmentDto, nameOverride: String): EnvState {
        val variables = dto.variables.map { (k, v) -> MutableKeyValue(k, v, enabled = true, secret = false) }
        return EnvState(name = nameOverride, variables = variables)
    }

    private fun collectAllNodeIds(nodes: List<CollectionNode>): Set<String> {
        val ids = mutableSetOf<String>()
        fun visit(list: List<CollectionNode>) {
            for (node in list) {
                ids.add(node.id)
                if (node.isFolder) visit(node.children)
            }
        }
        visit(nodes)
        return ids
    }
}
