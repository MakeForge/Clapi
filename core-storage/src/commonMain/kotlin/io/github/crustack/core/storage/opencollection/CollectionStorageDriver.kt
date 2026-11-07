package io.github.crustack.core.storage.opencollection

import io.github.crustack.core.model.opencollection.OpenCollectionDocument

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
