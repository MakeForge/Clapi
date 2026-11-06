# Data Model: OpenCollection Canonical Data Model

**Feature**: `001-opencollection-data-model`
**Date**: 2026-11-07
**Status**: Draft

## 1. Domain Entities & Hierarchy

```
OpenCollectionDocument (Root)
 ├── info: CollectionInfo
 ├── config: CollectionConfig?
 │    ├── environments: List<Environment>
 │    │    └── variables: List<VariableEntry> (RegularVariable | SecretVariable)
 │    ├── proxy: ProxyConfig?
 │    └── clientCertificates: List<ClientCertificate>
 ├── request: RequestDefaults? (Collection-level defaults)
 │    ├── headers: List<HttpRequestHeader>
 │    ├── auth: AuthConfig?
 │    ├── variables: List<RegularVariable>
 │    └── scripts: List<Script>
 ├── items: List<OpenCollectionItem> [ORDERED]
 │    ├── FolderItem (Folder container)
 │    │    ├── info: FolderInfo
 │    │    ├── request: RequestDefaults? (Folder-level inheritance)
 │    │    └── items: List<OpenCollectionItem> [NESTED ORDERED]
 │    └── HttpRequestItem (Protocol-specific request)
 │         ├── info: HttpRequestInfo
 │         ├── http: HttpRequestDetails
 │         │    ├── method: String
 │         │    ├── url: String
 │         │    ├── headers: List<HttpRequestHeader>
 │         │    ├── params: List<HttpRequestParam>
 │         │    ├── body: HttpRequestBody?
 │         │    └── auth: AuthConfig?
 │         ├── runtime: HttpRequestRuntime?
 │         │    ├── variables: List<RegularVariable>
 │         │    ├── scripts: List<Script>
 │         │    └── assertions: List<Assertion>
 │         └── settings: HttpRequestSettings?
 └── extensions: Map<String, JsonElement>
      └── clapi: ClapiExtensionData
           ├── mcp: List<McpConnectionConfigDto>
           └── legacyFields: Map<String, JsonElement>
```

---

## 2. Entity Specifications

### 2.1 `OpenCollectionDocument` (Canonical Root)
The primary collection document representing an OpenCollection collection.

| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `opencollection` | `String` | Specification version | Yes | Must match `"1.0.0"` (FR-002) |
| `info` | `CollectionInfo` | Collection metadata | Yes | Valid `CollectionInfo` |
| `config` | `CollectionConfig?` | Collection configuration | No | Contains environments, proxy, etc. |
| `items` | `List<OpenCollectionItem>` | Ordered collection items | Yes | Preserves order; elements must be valid items (FR-004, FR-010) |
| `request` | `RequestDefaults?` | Collection-level defaults | No | Inherited by child items |
| `docs` | `Documentation?` | Extended documentation | No | Content string or MIME-typed object |
| `bundled` | `Boolean?` | Standalone bundle indicator | No | Defaults to `true` for standalone files |
| `extensions` | `Map<String, JsonElement>` | Implementer extension bag | No | Valid JSON object; preserves unknown & Clapi extensions (FR-007, FR-023) |

---

### 2.2 `OpenCollectionItem` (Polymorphic Sealed Interface)
Represents any item within a collection or folder. A folder is **never** an HTTP request (FR-010).

```kotlin
sealed interface OpenCollectionItem {
    val info: ItemInfo
    val extensions: Map<String, JsonElement>
}
```

#### 2.2.1 `FolderItem`
A container for grouping nested items.

| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `info` | `FolderInfo` | Folder metadata | Yes | `info.type` must be `"folder"`. Name cannot be blank. |
| `items` | `List<OpenCollectionItem>` | Nested items | Yes | Preserves ordering. Can contain subfolders or requests. |
| `request` | `RequestDefaults?` | Folder defaults | No | Inherited by descendant requests |
| `docs` | `Documentation?` | Folder documentation | No | String or typed documentation |
| `extensions` | `Map<String, JsonElement>` | Extensions | No | Free-form JSON object |

#### 2.2.2 `HttpRequestItem`
An executable HTTP request item.

| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `info` | `HttpRequestInfo` | Request metadata | Yes | `info.type` must be `"http"`. Name cannot be blank. |
| `http` | `HttpRequestDetails` | Protocol details | Yes | Valid method, URL, headers, params, body, auth (FR-011) |
| `runtime` | `HttpRequestRuntime?` | Execution scripts & vars | No | Pre/post scripts, request variables, assertions |
| `settings` | `HttpRequestSettings?` | Execution settings | No | Redirects, timeout, URL encoding |
| `docs` | `String?` | Request documentation | No | Markdown/text |
| `examples` | `List<HttpRequestExample>` | Saved examples | No | Request/response pairs |
| `extensions` | `Map<String, JsonElement>` | Extensions | No | Preserves Clapi-specific UI tags (e.g. `authType: jwt`) |

---

### 2.3 HTTP Protocol Details (`HttpRequestDetails`)

| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `method` | `String` | HTTP verb | Yes | GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD, TRACE, CONNECT |
| `url` | `String` | Target URL (with `{{var}}`) | Yes | Valid URL template string |
| `headers` | `List<HttpRequestHeader>` | HTTP headers | No | Header key/value pairs with disabled flag |
| `params` | `List<HttpRequestParam>` | Query/path params | No | Param key/value pairs with disabled flag & type (`query` \| `path`) |
| `body` | `HttpRequestBody?` | Request body | No | One of `RawBody`, `FormUrlEncodedBody`, `MultipartFormBody`, `FileBody` |
| `auth` | `AuthConfig?` | Authentication config | No | One of supported auth types or `inherit` |

---

### 2.4 Request Body Types (`HttpRequestBody`)

```kotlin
sealed interface HttpRequestBody {
    val type: String
}
```

1. **`RawBody`**:
   - `type`: `"json"` \| `"text"` \| `"xml"` \| `"sparql"`
   - `data`: `String` (raw body content)
   - *GraphQL*: Sent as `RawBody` with `type = "json"` formatted as `{ "query": "...", "variables": ... }`.
2. **`FormUrlEncodedBody`**:
   - `type`: `"form-urlencoded"`
   - `data`: `List<FormParamEntry>` (`name`, `value`, `description?`, `disabled?`)
3. **`MultipartFormBody`**:
   - `type`: `"multipart-form"`
   - `data`: `List<MultipartPartEntry>` (`name`, `type`: `"text"` \| `"file"`, `value`: `String` \| `List<String>`, `contentType?`, `disabled?`)
4. **`FileBody`**:
   - `type`: `"file"`
   - `data`: `List<FileBodyVariant>` (`filePath`, `contentType`, `selected`)

---

### 2.5 Authentication Models (`AuthConfig`)

```kotlin
sealed interface AuthConfig

data class AuthBasic(val username: String, val password: String) : AuthConfig
data class AuthBearer(val token: String) : AuthConfig
data class AuthApiKey(val key: String, val value: String, val placement: ApiKeyPlacement) : AuthConfig
data class AuthOAuth2(val flow: OAuth2FlowConfig) : AuthConfig
data object AuthInherit : AuthConfig
```

**Validation & Mapping Rules**:
- `AuthType.JWT`: Mapped to `AuthBearer` on wire with `extensions.clapi.authType = "jwt"` in item extensions.
- `ApiKeyPlacement`: Either `"header"` or `"query"`.
- `AuthInherit`: Indicates the item inherits authentication from its parent folder or collection.
- Secret sanitization: Secrets must not be logged or exposed in unmasked debug views.

---

### 2.6 Environments & Variables

#### `Environment`
| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `name` | `String` | Environment name | Yes | Unique within collection config |
| `color` | `String?` | UI color tag | No | Valid hex color or CSS name |
| `description` | `Description?` | Description | No | Text or typed object |
| `variables` | `List<VariableEntry>` | Environment variables | Yes | Contains `RegularVariable` and `SecretVariable` |

#### `VariableEntry`
- **`RegularVariable`**:
  - `name: String`
  - `value: VariableValue` (string or `{type, data}`)
  - `description: Description?`
  - `disabled: Boolean?`
- **`SecretVariable`**:
  - `secret: Boolean = true`
  - `name: String`
  - `type: String` ("string" | "number" | "boolean" | "null" | "object")
  - `description: Description?`
  - `disabled: Boolean?`
  - **Notice**: Secret values are **never serialized** to the OpenCollection document (FR-017). They are resolved from local secure storage.

---

### 2.7 Scripts & Assertions

#### `Script`
| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `type` | `String` | Lifecycle phase | Yes | `"before-request"` \| `"after-response"` \| `"tests"` \| `"hooks"` |
| `code` | `String` | JavaScript code | Yes | Execution handled by unchanged `ClapiScriptEngine` |

#### `Assertion`
| Field | Type | Description | Required | Validation Rules |
|---|---|---|---|---|
| `expression` | `String` | Target expression | Yes | E.g. `response.status` |
| `operator` | `String` | Comparison operator | Yes | `eq`, `contains`, `ne`, etc. |
| `value` | `String?` | Expected value | No | Comparison operand |
| `disabled` | `Boolean?` | Disabled flag | No | Defaults to false |

---

### 2.8 Clapi Extension Data (`extensions.clapi`)

```kotlin
@Serializable
data class ClapiExtensionData(
    val mcp: List<McpConnectionConfigDto> = emptyList(),
    val authType: String? = null,
    val legacyFields: Map<String, JsonElement> = emptyMap()
)
```
- Preserves MCP connections without converting them to fake HTTP requests (FR-022, FR-023).
- Preserves unmodeled fields from legacy Clapi collections.

---

### 2.9 Migration Report (`MigrationReport`)

```kotlin
data class MigrationReport(
    val sourcePath: String,
    val targetCollectionName: String,
    val itemsCount: Int,
    val foldersCount: Int,
    val requestsCount: Int,
    val environmentsCount: Int,
    val preservedExtensions: List<String>,
    val unrepresentableFields: List<String>,
    val status: MigrationStatus,
    val errorMessage: String? = null
)

enum class MigrationStatus {
    SUCCESS,
    FAILED,
    SKIPPED_ALREADY_MIGRATED
}
```

---

## 3. Separation of Persisted vs Runtime State (FR-024)

| Concern | Canonical Persisted (`OpenCollectionDocument`) | Runtime State (`AppState` / Non-Collection Storage) |
|---|---|---|
| Collection hierarchy & items | **Yes** | Reflected in UI tree |
| Request method, URL, headers, body | **Yes** | Loaded into active tab |
| Environments & non-secret variables | **Yes** | In-memory editable layer |
| Secret variable values | **No** (omitted per FR-017) | Stored in local secure storage |
| In-flight request Job & abort tokens | **No** | `RequestTabState.currentJob` |
| Open editor tabs & active tab index | **No** | `TabsRepository` (`clapi.tabs`) |
| Last execution response & metrics | **No** | `RequestTabState.response` |
| Console logs & network event logs | **No** | In-memory console log buffers |
| Request execution history | **No** | `HistoryRepository` (`clapi.history`) |
| Folder expansion state | **No** | UI state (`collectionExpandedState`) |
| Temporary uncommitted edits / undo | **No** | `EditorViewModel` cache |
