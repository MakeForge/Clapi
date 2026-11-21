# Contract: Collection Repository & Storage Driver

**Feature**: `001-opencollection-data-model`
**Contract Name**: `CollectionRepositoryContract`
**Module**: `core-storage` / `core-model`

## 1. Overview
Specifies the interface boundaries for collection operations. Callers (such as the UI, view models, or CLI tools) interact exclusively with high-level collection domain operations using canonical OpenCollection structures. Filesystem layouts and persistence mechanics are completely encapsulated behind storage drivers.

---

## 2. Interface Definitions

### 2.1 Storage Driver Interface (FR-033, FR-034)
```kotlin
package io.github.makeforge.clapi.core.storage

import io.github.makeforge.clapi.core.model.OpenCollectionDocument

sealed interface StorageLocation {
    data class FilePath(val path: String) : StorageLocation
    data class MemoryKey(val key: String) : StorageLocation
}

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
```

### 2.2 Canonical Collection Repository Interface (FR-025)
```kotlin
package io.github.makeforge.clapi.core.storage

import io.github.makeforge.clapi.core.model.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.OpenCollectionItem
import kotlinx.coroutines.flow.Flow

interface CanonicalCollectionRepository {
    /**
     * Observes all active collections in the workspace.
     */
    fun observeCollections(): Flow<List<OpenCollectionDocument>>

    /**
     * Loads a collection document by identifier/name.
     */
    suspend fun getCollection(id: String): OpenCollectionDocument?

    /**
     * Saves or updates a collection document.
     * Validates schema before writing.
     */
    suspend fun saveCollection(document: OpenCollectionDocument)

    /**
     * Deletes a collection document.
     */
    suspend fun deleteCollection(id: String)

    /**
     * Imports an OpenCollection document from a string (JSON or YAML).
     * Validates against schema and ensures unique naming.
     */
    suspend fun importCollection(rawContent: String): OpenCollectionDocument

    /**
     * Exports a collection document to a string formatted as JSON or YAML.
     */
    suspend fun exportCollection(document: OpenCollectionDocument, format: ExportFormat = ExportFormat.JSON): String
}

enum class ExportFormat { JSON, YAML }
```

---

## 3. Behavioral Invariants
1. **No Runtime Leakage (FR-024)**:
   - Methods on `CanonicalCollectionRepository` take and return pure collection definitions. They NEVER accept or return tab states, in-flight jobs, history entries, or responses.
2. **Pre-Save Validation (FR-006)**:
   - `saveCollection` and `write` MUST validate the document against `opencollection_v1.0.0.json`. Invalid documents throw `CollectionValidationException` and are not written to disk.
3. **No Filesystem Leakage (FR-033)**:
   - Callers outside the storage module deal with collections by ID/document. Storage drivers map IDs to filesystem paths or storage keys internally.
