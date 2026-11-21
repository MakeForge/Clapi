# Contract: Legacy Collection Migration

**Feature**: `001-opencollection-data-model`
**Contract Name**: `LegacyMigrationContract`
**Module**: `core-storage`

## 1. Overview
Defines the migration engine interface for upgrading legacy Clapi/ReqLab collections and workspaces (`type: "clapiCollection"`, `type: "clapiWorkspace"`) to the canonical OpenCollection format.

---

## 2. Interface Definitions

```kotlin
package io.github.crustack.core.storage.migration

import io.github.crustack.core.model.OpenCollectionDocument

sealed interface MigrationTarget {
    data class WorkspaceStorage(val storageKey: String = "clapi.workspace") : MigrationTarget
    data class FileCollection(val path: String) : MigrationTarget
}

data class MigrationResult(
    val status: MigrationStatus,
    val reports: List<MigrationReport>,
    val migratedDocuments: List<OpenCollectionDocument> = emptyList(),
    val failureCause: Throwable? = null
)

data class MigrationReport(
    val sourceName: String,
    val targetDocumentName: String,
    val totalItems: Int,
    val foldersCount: Int,
    val requestsCount: Int,
    val environmentsCount: Int,
    val preservedExtensionsCount: Int,
    val unrepresentableFields: List<String> = emptyList(),
    val backupLocation: String? = null
)

enum class MigrationStatus {
    SUCCESS,
    FAILED,
    SKIPPED_ALREADY_MIGRATED
}

interface LegacyCollectionMigrator {
    /**
     * Determines whether the payload or file at the given target is legacy format.
     */
    fun isLegacyFormat(rawJsonOrPath: String): Boolean

    /**
     * Executes migration for the given target.
     * Guaranteed:
     * 1. Creates an immutable backup before attempting any write (FR-029).
     * 2. Validates converted document against OpenCollection v1.0.0 schema (FR-006, FR-029).
     * 3. Leaves original data byte-identical if validation or write fails (FR-029, SC-005).
     * 4. Is idempotent: subsequent runs on already-migrated targets return SKIPPED (FR-030, SC-006).
     */
    suspend fun migrate(target: MigrationTarget): MigrationResult
}
```

---

## 3. Migration Field Mapping Rules

| Legacy Clapi Concept | OpenCollection Equivalent | Handling Strategy |
|---|---|---|
| `ClapiCollectionDto.name` | `info.name` | Direct mapping |
| `FolderDto.name`, nested `folders` | `FolderItem` (`info.name`, `items`) | Preserves nested hierarchy and sequence |
| `RequestDto.method` | `http.method` | Direct enum mapping (uppercase) |
| `RequestDto.url` | `http.url` | Direct template string mapping |
| `RequestDto.queryEntries` | `http.params` with `type = "query"` | Preserves row enabled flags & secrets |
| `RequestDto.headerEntries` | `http.headers` | Preserves row enabled flags & secrets |
| `RequestDto.bodyType`, `bodyContent` | `http.body` (`RawBody`, `FormUrlEncodedBody`, `MultipartFormBody`, `FileBody`) | Mapped to matching OpenCollection body subtype |
| `RequestDto.auth*` | `http.auth` | Mapped to `AuthBasic`, `AuthBearer`, `AuthApiKey`, `AuthOAuth2` |
| `RequestDto.preRequestScript` | `runtime.scripts[type = "before-request"]` | Code preserved byte-identical |
| `RequestDto.testScript` | `runtime.scripts[type = "after-response"]` | Code preserved byte-identical |
| `ClapiEnvironmentDto` | `config.environments` | Name, variables (Regular & Secret) mapped |
| `kind = RequestKind.MCP`, `mcp*` | `extensions.clapi.mcp` | Saved under Clapi extension object (FR-022, FR-023) |
| Unknown legacy fields | `extensions.clapi.legacyFields` | Preserved as JSON element bag (FR-031) |
