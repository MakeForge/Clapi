# Research: OpenCollection Canonical Data Model

**Feature**: `001-opencollection-data-model`
**Date**: 2026-11-07
**Status**: Completed

## 1. OpenCollection Specification Version & Schema Validation

### Decision
Target **OpenCollection Specification Version 1.0.0**. The validation authority is the official OpenCollection v1.0.0 JSON Schema (`docs/Clapi/todos/opencollection_v1.0.0.json` with ID `https://schema.opencollection.com/opencollection/v1.0.0.json`).
For schema validation on the JVM/desktop, use `com.networknt:json-schema-validator` (Draft-07 compliant).

### Rationale
- OpenCollection v1.0.0 is the official released specification standard for interoperable API collections.
- `com.networknt:json-schema-validator` is an industry-standard, high-performance, RFC/Draft-07-compliant validator for JVM that provides precise error paths (JSON Pointer), schema keywords, and human-readable messages, satisfying FR-005, FR-006, and FR-009.
- Bundling the official schema JSON resource in `core-model` or `core-storage` enables fast, offline validation on open and save without network dependencies.

### Alternatives Considered
- *Custom hand-written Kotlin validation*: High maintenance, prone to divergence from the official specification, violates FR-005 ("validate against the official OpenCollection JSON schema").
- *io.github.optimum-code:json-schema-validator*: Multiplatform, but less battle-tested against edge cases of Draft-07 schemas compared to NetworkNT.
- *org.everit.json.schema*: Older and unmaintained library with incomplete Draft-07 support.

---

## 2. Serialization Formats & Tooling

### Decision
Support **both JSON and YAML** formats for OpenCollection documents, with JSON as the default for bundled storage in Clapi.
Use `kotlinx.serialization.json` for JSON and `com.charleskorn.kaml:kaml` for YAML parsing and formatting.
Preserve unknown and unmodeled fields through open-and-save cycles using an internal unmodeled field bag (`JsonObject` / `Map<String, JsonElement>`) or custom serializers on top of `kotlinx.serialization`.

### Rationale
- OpenCollection allows collections to be formatted as JSON or YAML (`.json`, `.yaml`, `.yml`).
- `kaml` is the standard YAML engine built on `kotlinx.serialization`, allowing identical data classes to serialize and deserialize cleanly between JSON and YAML.
- Storing unmodeled properties in a structured pass-through map guarantees semantic preservation (FR-007, FR-008, SC-002) without losing third-party metadata.

### Alternatives Considered
- *JSON-only*: Breaks interoperability with tools that produce OpenCollection YAML collections.
- *Jackson YAML*: Heavyweight dependency with extensive transitive baggage, doesn't integrate natively with Kotlin Multiplatform `@Serializable` classes.
- *Lossless AST text-preserving YAML parser*: Out of scope because FR-008 explicitly notes: "The system MUST preserve the meaning of supported data through a deserialize, modify, serialize, deserialize cycle. Textual formatting and comments are not required to be preserved."

---

## 3. Storage Architecture & Layout Strategy

### Decision
For the initial delivery, implement the **Bundled Document Layout** (`bundled: true`, single collection file per collection), per FR-034.
Structure the storage architecture around a generic `CollectionStorageDriver` interface:
```kotlin
interface CollectionStorageDriver {
    suspend fun read(location: StorageLocation): OpenCollectionDocument
    suspend fun write(location: StorageLocation, document: OpenCollectionDocument)
    suspend fun delete(location: StorageLocation)
}
```
The primary implementation will be `BundledCollectionStorageDriver`. The interface design abstracts file layout so that a `DirectoryCollectionStorageDriver` can be introduced in a future milestone without affecting the `CollectionRepository`, editor, or execution layers (satisfying FR-033 and FR-034).

### Rationale
- Clapi is a desktop application currently operating with collection-level persistence (`PlatformStorage`, single-file import/export).
- Bundled documents are self-contained, easily validated against `opencollection_v1.0.0.json`, and easy to share.
- Isolating storage behind `CollectionStorageDriver` keeps filesystem paths and layouts completely hidden from the editor and execution engine (FR-033).

### Alternatives Considered
- *Directory-based layout first*: Involves managing complex multi-file directories (`collection.yaml`, nested folder directories, individual item files, attachments), directory tree watching, and atomic multi-file commits. Adds unnecessary risk to the canonical model refactoring milestone.
- *Dual layouts simultaneously*: Increases implementation surface area and violates FR-034 ("The first delivery MUST support one OpenCollection storage layout...").

---

## 4. Item Hierarchy & Protocol Extensibility

### Decision
Replace the old conflated `CollectionNode(isFolder: Boolean)` and `CollectionDefinition` with a polymorphic sealed interface hierarchy in `core-model`:
```kotlin
sealed interface OpenCollectionItem {
    val info: ItemInfo
}

data class FolderItem(
    override val info: FolderInfo,
    val items: List<OpenCollectionItem> = emptyList(),
    val request: RequestDefaults? = null,
    val docs: Documentation? = null,
    val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem

data class HttpRequestItem(
    override val info: HttpRequestInfo,
    val http: HttpRequestDetails,
    val runtime: HttpRequestRuntime? = null,
    val settings: HttpRequestSettings? = null,
    val docs: String? = null,
    val examples: List<HttpRequestExample> = emptyList(),
    val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem
```
Future protocol types (`GrpcRequestItem`, `GraphQLRequestItem`, `WebSocketRequestItem`, `ScriptFileItem`) will implement `OpenCollectionItem` without modifying `FolderItem`, `CollectionConfig`, or storage code.

### Rationale
- Fulfills FR-010 ("The collection model MUST represent items as a choice between folders and protocol-specific requests, so that a folder is never an HTTP request").
- Fulfills FR-013 ("New protocol types MUST be addable without changing the storage, folder, environment, or variable behavior") and SC-010.
- Keeps execution dependencies cleanly isolated: the HTTP execution engine depends only on `HttpRequestDetails` / `HttpRequestItem`, not on folder structure or collection root.

### Alternatives Considered
- *Single flat data class with nullable fields*: Causes folder and request fields to leak into each other, leads to invalid states, violates FR-010.
- *OpenCollectionItem wrapping an untyped Map*: Loses Kotlin type safety and compiler-checked polymorphism.

---

## 5. Non-HTTP Items & MCP Extensions

### Decision
Store MCP connection configurations and non-HTTP items in the OpenCollection document under `extensions.clapi.mcp` (using OpenCollection's standard `extensions` object, which is officially defined as a free-form object for implementer extensions).
Within Clapi's workspace state, MCP items continue to be managed by `McpSessionState` and Clapi's MCP connection models, completely isolated from OpenCollection `HttpRequestItem` entities.

### Rationale
- FR-021: MCP functionality must continue working.
- FR-022: MCP data MUST NOT be stored as an OpenCollection HTTP request.
- FR-023: MCP data and any other Clapi-specific data that OpenCollection does not define MUST be stored in a separate Clapi-specific representation that is documented.
- By using `extensions.clapi`, the document strictly passes official OpenCollection schema validation, while preserving MCP configurations when collections are saved, loaded, or exported.

### Alternatives Considered
- *Model MCP as an Item with type = "mcp" in the `items` array*: Fails schema validation against `opencollection_v1.0.0.json`, as `Item` is a strict `oneOf` consisting of `HttpRequest`, `GraphQLRequest`, `GrpcRequest`, `WebSocketRequest`, `Folder`, and `ScriptFile`.
- *Store MCP in a completely separate file*: Separates MCP from the collection it belongs to, breaking exports and portability.

---

## 6. Authentication Mapping

### Decision
Map Clapi's authentication types to OpenCollection v1.0.0 `Auth` union:
- `AuthType.NONE` / no auth -> `null` (or omitted)
- `AuthType.BASIC` -> `AuthBasic(type = "basic", username = ..., password = ...)`
- `AuthType.BEARER` -> `AuthBearer(type = "bearer", token = ...)`
- `AuthType.API_KEY` -> `AuthApiKey(type = "apikey", key = ..., value = ..., placement = "header"|"query")`
- `AuthType.OAUTH2` -> `AuthOAuth2(...)`
- `AuthType.JWT` -> `AuthBearer(type = "bearer", token = ...)` with `extensions.clapi.authType = "jwt"` preserved so Clapi's specialized JWT UI editor displays properly.
- Inherited auth -> `AuthInherit("inherit")`.

### Rationale
- Satisfies FR-012.
- Aligns precisely with OpenCollection v1.0.0 `Auth` definitions.
- Representing JWT as `bearer` auth on the wire matches RFC 6750, while the `extensions.clapi` tag preserves the user's specific editor tab configuration without violating the schema.

### Alternatives Considered
- *Add custom "jwt" auth type in standard schema*: Causes validation failure against official schema.
- *Discard JWT distinction*: Causes UI to lose JWT-specific editor features on reload.

---

## 7. Environments, Variables & Secrets Handling

### Decision
- Store collection environments under `config.environments` as OpenCollection `Environment` objects.
- Each environment contains `variables: List<Variable | SecretVariable>`.
- Regular variables have `name`, `value` (string or typed), `description`, `disabled`.
- Secret variables are serialized as `SecretVariable(secret = true, name = ..., type = "string")`, **omitting the secret value** from the serialized collection file (FR-017, SC-007).
- Secret values are stored locally in Clapi's secure/local storage keyed by environment ID and variable name, and resolved dynamically at request execution time.
- Variable resolution layers and precedence remain:
  1. Request-scoped variables (`runtime.variables` and script-set variables) [Highest]
  2. Selected Environment variables (`config.environments`)
  3. Collection-level variables (`request.variables` or collection defaults)
  4. Global variables [Lowest]

### Rationale
- Satisfies FR-015, FR-016, FR-017, and SC-007.
- Complies strictly with the OpenCollection schema for `SecretVariable`.
- Ensures zero secret values appear in exported collections, logs, or debug output.

### Alternatives Considered
- *Store secrets in cleartext in collection file*: Critical security vulnerability, violates FR-017 and SC-007.
- *Flatten all variables into a single global map*: Breaks scope isolation, violates FR-015.

---

## 8. Scripts & Assertions Mapping

### Decision
- Pre-request script -> OpenCollection `Script(type = "before-request", code = ...)` in `runtime.scripts` (for request) or `request.scripts` (for collection/folder).
- Test/Post-request script -> OpenCollection `Script(type = "after-response", code = ...)` or `Script(type = "tests", code = ...)`.
- Declarative assertions (if defined) -> OpenCollection `Assertion` objects in `runtime.assertions`.
- The existing JavaScript execution engine (`ClapiScriptEngine`), `reqlab.*` namespace, and `clapi.*` scripting API are kept 100% unchanged (FR-020).

### Rationale
- Maps cleanly to OpenCollection v1.0.0 lifecycle script types (`before-request`, `after-response`, `tests`).
- Preserves full backward compatibility of existing user scripts and tests (FR-018, FR-020, SC-003).

### Alternatives Considered
- *Convert JavaScript tests into declarative assertions*: Impossible for non-trivial script logic; would break complex assertions.
- *Disallow OpenCollection script types*: Incompatible with the OpenCollection standard.

---

## 9. Migration Strategy & Idempotency

### Decision
Implement `LegacyCollectionMigrator` with a strict, multi-step transaction-like workflow:
1. **Detect**: Check file or `clapi.workspace` JSON. If `type == "clapiCollection"` or `type == "clapiWorkspace"`, flag for migration. If `opencollection` version string is already present, skip (idempotent).
2. **Backup**: Create an immutable backup (`clapi.workspace.legacy.bak` or `collection.clapi.bak`).
3. **Transform**: Convert legacy folders, requests, auth, parameters, variables, scripts into `OpenCollectionDocument`. Any unmapped custom fields are stored in `extensions.clapi.legacyFields`.
4. **Validate**: Validate the resulting `OpenCollectionDocument` against the bundled `opencollection_v1.0.0.json` schema.
5. **Persist**: If and only if validation passes, write the canonical document. If validation fails, abort, keep legacy files byte-identical, and report error to user (FR-029, SC-005).
6. **Report**: Produce a structured `MigrationReport` documenting converted items, preserved extension data, and any unrepresentable fields (FR-028, SC-004).

### Rationale
- Fulfills FR-027, FR-028, FR-029, FR-030, FR-031, SC-004, SC-005, and SC-006.
- Prevents data loss during interrupted or failed migrations.
- Safely repeatable (subsequent runs detect canonical format and do not re-run or duplicate items).

### Alternatives Considered
- *In-place destructive migration*: Fails SC-005 if an error occurs.
- *Silent data discarding*: Violates FR-028 and FR-031.
