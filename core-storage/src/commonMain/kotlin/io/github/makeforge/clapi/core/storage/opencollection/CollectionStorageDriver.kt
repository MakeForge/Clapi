package io.github.makeforge.clapi.core.storage.opencollection

import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface StorageLocation {
    data class FilePath(val path: String) : StorageLocation
    data class MemoryKey(val key: String) : StorageLocation
}

open class CollectionStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class ValidationIssue(
    val path: String,
    val message: String,
    val keyword: String? = null
)

class CollectionValidationException(
    message: String,
    val issues: List<ValidationIssue> = emptyList(),
    cause: Throwable? = null
) : CollectionStorageException(message, cause)

class StorageReadException(message: String, cause: Throwable? = null) : CollectionStorageException(message, cause)

class StorageWriteException(message: String, cause: Throwable? = null) : CollectionStorageException(message, cause)

interface CollectionStorageDriver {
    /**
     * Reads and parses an OpenCollection document from the given location.
     * Throws [CollectionValidationException] if the document fails schema validation.
     * Throws [StorageReadException] if the location cannot be read.
     */
    suspend fun read(location: StorageLocation): OpenCollectionDocument

    /**
     * Serializes and writes an OpenCollection document to the given location.
     * Validates against the official schema before writing.
     * Throws [CollectionValidationException] if validation fails.
     * Throws [StorageWriteException] if writing fails.
     */
    suspend fun write(location: StorageLocation, document: OpenCollectionDocument)

    /**
     * Deletes the collection at the specified location.
     */
    suspend fun delete(location: StorageLocation)
}

class BundledCollectionStorageDriver(
    private val validator: OpenCollectionSchemaValidator = OpenCollectionSchemaValidator()
) : CollectionStorageDriver {

    private data class CachedWrite(
        val content: String,
        val fileStamp: Pair<Long, Long>? = null,
    )

    private val storeMutex = Mutex()
    private val memoryStore = mutableMapOf<String, String>()
    private val lastValidatedWrites = mutableMapOf<StorageLocation, CachedWrite>()

    override suspend fun read(location: StorageLocation): OpenCollectionDocument {
        return when (location) {
            is StorageLocation.FilePath -> {
                val content = try {
                    if (!fileExists(location.path)) {
                        throw StorageReadException("File does not exist: ${location.path}")
                    }
                    readTextFile(location.path)
                } catch (e: StorageReadException) {
                    throw e
                } catch (e: Exception) {
                    throw StorageReadException("Failed to read file from ${location.path}: ${e.message}", e)
                }

                val isYaml = location.path.endsWith(".yaml", ignoreCase = true) ||
                    location.path.endsWith(".yml", ignoreCase = true)

                if (isYaml) {
                    validator.validateYamlOrThrow(content)
                    try {
                        OpenCollectionSerializer.deserializeYaml(content)
                    } catch (e: CollectionValidationException) {
                        throw e
                    } catch (e: Exception) {
                        throw StorageReadException("Failed to parse YAML collection from ${location.path}: ${e.message}", e)
                    }
                } else {
                    validator.validateOrThrow(content)
                    try {
                        OpenCollectionSerializer.deserializeJson(content)
                    } catch (e: CollectionValidationException) {
                        throw e
                    } catch (e: Exception) {
                        throw StorageReadException("Failed to parse JSON collection from ${location.path}: ${e.message}", e)
                    }
                }
            }
            is StorageLocation.MemoryKey -> {
                val content = storeMutex.withLock { memoryStore[location.key] }
                    ?: throw StorageReadException("No collection found at memory key: ${location.key}")
                validator.validateOrThrow(content)
                try {
                    OpenCollectionSerializer.deserializeJson(content)
                } catch (e: CollectionValidationException) {
                    throw e
                } catch (e: Exception) {
                    throw StorageReadException("Failed to parse collection from memory key ${location.key}: ${e.message}", e)
                }
            }
        }
    }

    override suspend fun write(location: StorageLocation, document: OpenCollectionDocument) {
        val docToWrite = if (document.bundled == true) document else document.copy(bundled = true)
        when (location) {
            is StorageLocation.FilePath -> {
                val isYaml = location.path.endsWith(".yaml", ignoreCase = true) ||
                    location.path.endsWith(".yml", ignoreCase = true)

                val content = if (isYaml) {
                    OpenCollectionSerializer.serializeYaml(docToWrite)
                } else {
                    OpenCollectionSerializer.serializeJson(docToWrite)
                }

                // Skip only when both content AND on-disk mtime+size still match our last validated write,
                // so external edits (e.g., git pull or manual file changes) are never silently ignored.
                val cached = storeMutex.withLock { lastValidatedWrites[location] }
                val currentStamp = fileStamp(location.path)
                if (cached != null && cached.content == content && currentStamp != null && cached.fileStamp == currentStamp) {
                    return
                }

                if (isYaml) {
                    validator.validateYamlOrThrow(content)
                } else {
                    validator.validateOrThrow(content)
                }

                try {
                    writeTextFile(location.path, content)
                    val writtenStamp = fileStamp(location.path)
                    storeMutex.withLock {
                        lastValidatedWrites[location] = CachedWrite(content = content, fileStamp = writtenStamp)
                    }
                } catch (e: Exception) {
                    throw StorageWriteException("Failed to write collection to ${location.path}: ${e.message}", e)
                }
            }
            is StorageLocation.MemoryKey -> {
                val content = OpenCollectionSerializer.serializeJson(docToWrite)
                val skip = storeMutex.withLock {
                    lastValidatedWrites[location]?.content == content && memoryStore.containsKey(location.key)
                }
                if (skip) return
                validator.validateOrThrow(content)
                storeMutex.withLock {
                    memoryStore[location.key] = content
                    lastValidatedWrites[location] = CachedWrite(content = content)
                }
            }
        }
    }

    override suspend fun delete(location: StorageLocation) {
        storeMutex.withLock {
            lastValidatedWrites.remove(location)
        }
        when (location) {
            is StorageLocation.FilePath -> {
                try {
                    if (fileExists(location.path)) {
                        val deleted = deleteFile(location.path)
                        if (!deleted) {
                            throw StorageWriteException("Failed to delete file at ${location.path}")
                        }
                    }
                } catch (e: StorageWriteException) {
                    throw e
                } catch (e: Exception) {
                    throw StorageWriteException("Failed to delete collection file at ${location.path}: ${e.message}", e)
                }
            }
            is StorageLocation.MemoryKey -> {
                storeMutex.withLock {
                    memoryStore.remove(location.key)
                }
            }
        }
    }
}

expect fun readTextFile(path: String): String
expect fun writeTextFile(path: String, content: String)
expect fun deleteFile(path: String): Boolean
expect fun fileExists(path: String): Boolean
expect fun fileStamp(path: String): Pair<Long, Long>?
expect fun getDefaultStorageBaseDirectory(): String
expect fun copyFileOrDirectory(sourcePath: String, destinationPath: String): Boolean
expect fun copyProtoFilesFromDirectory(sourceDir: String, destinationDir: String): Boolean

