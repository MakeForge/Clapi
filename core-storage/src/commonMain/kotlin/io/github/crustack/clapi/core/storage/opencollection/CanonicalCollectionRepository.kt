package io.github.crustack.clapi.core.storage.opencollection

import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.FolderInfo
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive

enum class ExportFormat { JSON, YAML }

val OpenCollectionDocument.collectionId: String
    get() = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: info.name

interface CanonicalCollectionRepository {
    /** Top-level folders of the single global OpenCollection document. */
    fun observeCollections(): Flow<List<FolderItem>>
    suspend fun loadGlobalCollection(): OpenCollectionDocument?
    suspend fun saveGlobalCollection(document: OpenCollectionDocument)
    suspend fun getCollection(id: String): OpenCollectionDocument?
    suspend fun saveCollection(document: OpenCollectionDocument)
    suspend fun deleteCollection(id: String)
    suspend fun importCollection(rawContent: String): OpenCollectionDocument
    suspend fun exportCollection(document: OpenCollectionDocument, format: ExportFormat = ExportFormat.JSON): String
}

/** One global OpenCollection file in [baseDirectory]. Not a user-facing or `clapi*` name. */
const val GLOBAL_COLLECTION_FILENAME = "workspace.json"

fun generateUniqueName(name: String, existingNames: Set<String>): String {
    if (name !in existingNames) return name
    var counter = 1
    while ("$name ($counter)" in existingNames) {
        counter++
    }
    return "$name ($counter)"
}

class DefaultCanonicalCollectionRepository(
    private val storageDriver: CollectionStorageDriver = BundledCollectionStorageDriver(),
    private val validator: OpenCollectionSchemaValidator = OpenCollectionSchemaValidator(),
    val baseDirectory: String? = null,
) : CanonicalCollectionRepository {

    private val globalState = MutableStateFlow<OpenCollectionDocument?>(null)
    private val loadMutex = Mutex()
    private var loaded = false

    private fun globalLocation(): StorageLocation {
        return if (baseDirectory != null) {
            StorageLocation.FilePath("$baseDirectory/$GLOBAL_COLLECTION_FILENAME")
        } else {
            StorageLocation.MemoryKey(GLOBAL_COLLECTION_FILENAME)
        }
    }

    private suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (loaded) return
            loaded = true
            globalState.value = try {
                storageDriver.read(globalLocation())
            } catch (_: StorageReadException) {
                null
            }
        }
    }

    override fun observeCollections(): Flow<List<FolderItem>> = flow {
        ensureLoaded()
        emitAll(globalState.map { document -> document?.topLevelFolders().orEmpty() })
    }

    override suspend fun loadGlobalCollection(): OpenCollectionDocument? {
        ensureLoaded()
        return globalState.value
    }

    override suspend fun saveGlobalCollection(document: OpenCollectionDocument) {
        storageDriver.write(globalLocation(), document)
        globalState.value = document
        loaded = true
    }

    override suspend fun getCollection(id: String): OpenCollectionDocument? {
        ensureLoaded()
        val folder = globalState.value?.topLevelFolders()?.firstOrNull { it.matchesId(id) } ?: return null
        return folder.toStandaloneDocument()
    }

    override suspend fun saveCollection(document: OpenCollectionDocument) {
        ensureLoaded()
        val current = globalState.value ?: emptyGlobalDocument()
        val folder = document.toCollectionFolder()
        val items = current.items.toMutableList()
        val index = items.indexOfFirst { item ->
            item is FolderItem && item.matchesId(document.collectionId)
        }
        if (index >= 0) {
            items[index] = folder
        } else {
            items.add(folder)
        }
        saveGlobalCollection(current.copy(items = items))
    }

    override suspend fun deleteCollection(id: String) {
        ensureLoaded()
        val current = globalState.value ?: return
        val remaining = current.items.filterNot { item ->
            item is FolderItem && item.matchesId(id)
        }
        if (remaining.size == current.items.size) return
        val updated = current.copy(items = remaining)
        if (updated.items.isEmpty() && updated.config?.environments.isNullOrEmpty()) {
            storageDriver.delete(globalLocation())
            globalState.value = null
        } else {
            saveGlobalCollection(updated)
        }
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

        ensureLoaded()
        val existingNames = globalState.value?.topLevelFolders()?.map { it.info.name }?.toSet() ?: emptySet()
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

}

private fun emptyGlobalDocument(): OpenCollectionDocument =
    OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(name = "Workspace"),
        bundled = true,
    )

private fun OpenCollectionDocument.topLevelFolders(): List<FolderItem> =
    items.filterIsInstance<FolderItem>()

private fun FolderItem.matchesId(id: String): Boolean =
    folderIdentity() == id || info.name == id

private fun FolderItem.folderIdentity(): String {
    val raw = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    return raw ?: info.name
}

private fun OpenCollectionDocument.toCollectionFolder(): FolderItem =
    FolderItem(
        info = FolderInfo(name = info.name, type = "folder"),
        items = items,
        extensions = extensions + ("id" to JsonPrimitive(collectionId)),
    )

private fun FolderItem.toStandaloneDocument(): OpenCollectionDocument =
    OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(name = info.name),
        bundled = true,
        items = items,
        extensions = extensions,
    )
