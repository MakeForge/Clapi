package io.github.crustack.core.storage.opencollection

import io.github.crustack.core.model.opencollection.OpenCollectionDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive

enum class ExportFormat { JSON, YAML }

val OpenCollectionDocument.collectionId: String
    get() = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: info.name

interface CanonicalCollectionRepository {
    fun observeCollections(): Flow<List<OpenCollectionDocument>>
    suspend fun getCollection(id: String): OpenCollectionDocument?
    suspend fun saveCollection(document: OpenCollectionDocument)
    suspend fun deleteCollection(id: String)
    suspend fun importCollection(rawContent: String): OpenCollectionDocument
    suspend fun exportCollection(document: OpenCollectionDocument, format: ExportFormat = ExportFormat.JSON): String
}

class DefaultCanonicalCollectionRepository(
    private val storageDriver: CollectionStorageDriver = BundledCollectionStorageDriver(),
    private val validator: OpenCollectionSchemaValidator = OpenCollectionSchemaValidator(),
    private val baseDirectory: String? = null,
) : CanonicalCollectionRepository {

    private val collectionsState = MutableStateFlow<Map<String, OpenCollectionDocument>>(emptyMap())
    private val idToLocation = mutableMapOf<String, StorageLocation>()

    private fun resolveLocation(id: String): StorageLocation {
        return idToLocation.getOrPut(id) {
            if (baseDirectory != null) {
                val safeName = id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                StorageLocation.FilePath("$baseDirectory/$safeName.json")
            } else {
                StorageLocation.MemoryKey(id)
            }
        }
    }

    override fun observeCollections(): Flow<List<OpenCollectionDocument>> =
        collectionsState.asStateFlow().map { it.values.toList() }

    override suspend fun getCollection(id: String): OpenCollectionDocument? {
        val cached = collectionsState.value[id]
        if (cached != null) return cached

        val byName = collectionsState.value.values.firstOrNull { it.info.name == id }
        if (byName != null) return byName

        return try {
            val location = resolveLocation(id)
            val doc = storageDriver.read(location)
            collectionsState.update { it + (id to doc) }
            doc
        } catch (_: StorageReadException) {
            null
        }
    }

    override suspend fun saveCollection(document: OpenCollectionDocument) {
        val id = document.collectionId
        val location = resolveLocation(id)

        // Storage driver validates schema before write and throws CollectionValidationException if invalid
        storageDriver.write(location, document)

        collectionsState.update { current ->
            current + (id to document)
        }
    }

    override suspend fun deleteCollection(id: String) {
        val location = resolveLocation(id)
        storageDriver.delete(location)
        idToLocation.remove(id)
        collectionsState.update { it - id }
    }

    override suspend fun importCollection(rawContent: String): OpenCollectionDocument {
        val trimmed = rawContent.trim()
        val isJson = trimmed.startsWith("{")

        val doc = if (isJson) {
            validator.validateOrThrow(trimmed)
            OpenCollectionSerializer.deserializeJson(trimmed)
        } else {
            validator.validateYamlOrThrow(trimmed)
            OpenCollectionSerializer.deserializeYaml(trimmed)
        }

        val existingNames = collectionsState.value.values.map { it.info.name }.toSet()
        val uniqueName = generateUniqueName(doc.info.name, existingNames)
        val finalDoc = if (uniqueName != doc.info.name) {
            doc.copy(info = doc.info.copy(name = uniqueName))
        } else {
            doc
        }

        saveCollection(finalDoc)
        return finalDoc
    }

    override suspend fun exportCollection(document: OpenCollectionDocument, format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> {
                val json = OpenCollectionSerializer.serializeJson(document)
                validator.validateOrThrow(json)
                json
            }
            ExportFormat.YAML -> {
                val yaml = OpenCollectionSerializer.serializeYaml(document)
                validator.validateYamlOrThrow(yaml)
                yaml
            }
        }
    }

    private fun generateUniqueName(name: String, existingNames: Set<String>): String {
        if (name !in existingNames) return name
        var counter = 1
        while ("$name ($counter)" in existingNames) {
            counter++
        }
        return "$name ($counter)"
    }
}
