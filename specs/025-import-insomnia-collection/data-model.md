# Data Model & Entity Specifications: Insomnia v5 Import

**Feature**: Insomnia v5 Collection & Environment Import
**Date**: 2026-11-15
**Status**: Completed (Phase 1)

---

## 1. Core Data Entities

### Insomnia Document Model (`InsomniaV5Document`)
Represents the deserialized root of an Insomnia v5 export file.

```kotlin
data class InsomniaV5Document(
    val type: String,                       // "collection.insomnia.rest/5.0", "spec.insomnia.rest/5.0", "environment.insomnia.rest/5.0"
    val schemaVersion: String = "5.1",
    val name: String? = null,
    val meta: InsomniaMeta? = null,
    val collection: List<InsomniaItem> = emptyList(),
    val environments: InsomniaEnvironments? = null,
    val spec: InsomniaSpec? = null,
)

data class InsomniaMeta(
    val id: String? = null,
    val description: String? = null,
)

data class InsomniaSpec(
    val contents: String? = null,          // OpenAPI specification YAML/JSON string or map
)
```

### Insomnia Collection Item Hierarchy (`InsomniaItem`)
Represents an item within the Insomnia collection array.

```kotlin
sealed interface InsomniaItem {
    val name: String
    val meta: InsomniaMeta?
}

data class InsomniaFolder(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val children: List<InsomniaItem> = emptyList(),
    val scripts: InsomniaScripts? = null,
    val authentication: InsomniaAuth? = null,
    val headers: List<InsomniaHeader> = emptyList(),
) : InsomniaItem

data class InsomniaHttpRequest(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val url: String,
    val method: String,
    val headers: List<InsomniaHeader> = emptyList(),
    val parameters: List<InsomniaParameter> = emptyList(),
    val pathParameters: List<InsomniaParameter> = emptyList(),
    val body: InsomniaBody? = null,
    val authentication: InsomniaAuth? = null,
    val scripts: InsomniaScripts? = null,
) : InsomniaItem

data class InsomniaGrpcRequest(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val url: String,
    val protoMethodName: String? = null,
    val protoFileId: String? = null,
    val reflectionApi: InsomniaReflectionApi? = null,
    val metadata: List<InsomniaHeader> = emptyList(),
    val body: InsomniaBody? = null,
) : InsomniaItem

data class InsomniaWebSocketRequest(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val url: String,
    val headers: List<InsomniaHeader> = emptyList(),
    val parameters: List<InsomniaParameter> = emptyList(),
    val authentication: InsomniaAuth? = null,
) : InsomniaItem
```

### Supporting Components
```kotlin
data class InsomniaHeader(
    val name: String,
    val value: String,
    val description: String? = null,
    val disabled: Boolean = false,
)

data class InsomniaParameter(
    val name: String,
    val value: String,
    val description: String? = null,
    val disabled: Boolean = false,
)

data class InsomniaBody(
    val mimeType: String? = null,
    val text: String? = null,
    val fileName: String? = null,
    val params: List<InsomniaBodyParam> = emptyList(),
)

data class InsomniaBodyParam(
    val name: String,
    val value: String = "",
    val description: String? = null,
    val disabled: Boolean = false,
    val type: String? = null,
    val fileName: String? = null,
)

data class InsomniaScripts(
    val preRequest: String? = null,
    val afterResponse: String? = null,
)

data class InsomniaEnvironments(
    val name: String = "Base Environment",
    val meta: InsomniaMeta? = null,
    val data: Map<String, Any?> = emptyMap(),
    val subEnvironments: List<InsomniaSubEnvironment> = emptyList(),
)

data class InsomniaSubEnvironment(
    val name: String,
    val meta: InsomniaMeta? = null,
    val data: Map<String, Any?> = emptyMap(),
    val environmentType: String? = null,
)
```

---

## 2. Entity Mapping to Clapi OpenCollection

| Insomnia v5 Entity | Clapi OpenCollection Target | Conversion Rules |
| :--- | :--- | :--- |
| `InsomniaV5Document` | `OpenCollectionDocument` | `info.name` set to document name (or default "Insomnia Import"). Environments mapped to `config.environments`. |
| `InsomniaFolder` | `FolderItem` | Converted recursively; child items become `folder.item`. Folder scripts mapped to folder runtime. |
| `InsomniaHttpRequest` | `RequestItem` with `HttpRequestDetails` | URL, method, headers, parameters mapped. Body converted to text/json/form-data. Scripts converted to before-request/after-response scripts. |
| `InsomniaGrpcRequest` | `RequestItem` with `GrpcRequestDetails` | Host URL, method path, metadata headers, and JSON body message mapped. |
| `InsomniaWebSocketRequest`| `RequestItem` with `WebSocketRequestDetails`| Endpoint URL, headers, and parameters mapped. |
| `InsomniaEnvironments` | `List<Environment>` | Base environment becomes an `Environment`; each `subEnvironment` becomes an `Environment` inheriting or merging base variables. |

---

## 3. UI Dialog State Transitions

```
[SOURCE_SELECTION]
   │
   ├─► User selects Insomnia file (YAML or JSON) or pastes into Clipboard
   │     Format badges visible: [OpenAPI 3.0+] [OpenCollection] [Postman] [Insomnia]
   │
   ▼ User clicks "Scan"
[SCANNING]
   │
   ├─► Success: SpecificationImporter detects INSOMNIA format
   │     Parse tree & extract candidate folders, requests, and environments
   │
   ▼
[ITEM_SELECTION]
   │
   ├─► Header displays "Select Items to Import" with format badge: "Insomnia v5"
   ├─► Review & toggle candidate folders and environments
   ├─► Inline edit/rename if name conflicts with existing workspace collections/environments
   │
   ▼ User clicks "Import"
[IMPORT COMMITTED]
   │
   ├─► Selected collections and environments committed to AppState
   ├─► WorkspaceRepository persists changes to disk
   └─► Dialog dismissed with success toast/log
```
