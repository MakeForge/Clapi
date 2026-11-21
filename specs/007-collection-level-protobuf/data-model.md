# Data Model: Collection-Level Protobuf Management

## Domain Entities & Schema

### 1. OpenCollection Document Models (`core-model`)

```kotlin
package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.Serializable

@Serializable
data class CollectionConfig(
    val environments: List<Environment> = emptyList(),
    val protobuf: CollectionProtobufConfig? = null,
    val proxy: ProxyConfig? = null,
    val clientCertificates: List<ClientCertificate> = emptyList(),
)

@Serializable
data class CollectionProtobufConfig(
    val protoFiles: List<ProtoFile> = emptyList(),
    val importPaths: List<ProtoFileImportPath> = emptyList(),
)

@Serializable
data class ProtoFile(
    val type: String = "file",
    val path: String,
)

@Serializable
data class ProtoFileImportPath(
    val path: String,
    val disabled: Boolean? = null,
)
```

### 2. Application UI State Models (`ui-shared`)

```kotlin
package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ProtoFileItemState(
    initialPath: String,
) {
    var path by mutableStateOf(initialPath)
    val fileName: String get() = path.substringAfterLast(java.io.File.separatorChar).substringAfterLast('/')
    val directory: String get() = path.substringBeforeLast(java.io.File.separatorChar, "").substringBeforeLast('/', "")
}

class ProtoImportPathState(
    initialPath: String,
    initialEnabled: Boolean = true,
) {
    var path by mutableStateOf(initialPath)
    var enabled by mutableStateOf(initialEnabled)
    val fileName: String get() = path.substringAfterLast(java.io.File.separatorChar).substringAfterLast('/')
}
```

### 3. RequestKind Enum Extension (`core-model`)

```kotlin
package io.github.makeforge.clapi.core.model

enum class RequestKind {
    HTTP,
    MCP,
    FOLDER,
    GRPC,
    COLLECTION,
}
```

### 4. gRPC Network Models Extension (`core-network`)

```kotlin
package io.github.makeforge.clapi.core.network.grpc

data class GrpcCallRequest(
    val requestId: String,
    val host: String,
    val port: Int,
    val useTls: Boolean = false,
    val protoFilePath: String,
    val fullMethodName: String,
    val jsonPayload: String,
    val metadata: List<Pair<String, String>> = emptyList(),
    val includePaths: List<String> = emptyList(),
)
```

---

## State Transitions & Persistence

### 1. Opening Collection Settings
1. **User Action**: User clicks top-level "Collection" in the sidebar tree.
2. **Tab Check**:
   - `val existing = state.openTabs.indexOfFirst { it.kind == RequestKind.COLLECTION }`
   - If `existing >= 0`: `state.activeTabIndex = existing`.
   - Else: `state.openTabs.add(RequestTabState(id = "collection_settings", name = "Collection", kind = RequestKind.COLLECTION))` and `state.activeTabIndex = state.openTabs.lastIndex`.

### 2. Adding Proto Files
1. **User Action**: In CollectionEditor Protobuf tab, user clicks "Add Proto Files".
2. **File Chooser**: Platform file picker opens with multi-select enabled.
3. **State Mutation**: Selected filesystem paths are appended to `state.collectionProtoFiles`.
4. **Auto-Persist**: Triggers `persistWorkspaceAsync()` saving `config.protobuf.protoFiles` to canonical `collection.json`.

### 3. Adding / Toggling Import Paths
1. **User Action**: In CollectionEditor Protobuf tab, user clicks "Add Import Paths".
2. **State Mutation**: Selected filesystem paths are added to `state.collectionImportPaths` with `enabled = true`.
3. **Toggle Action**: Clicking checkbox toggles `entry.enabled`.
4. **Auto-Persist**: Triggers `persistWorkspaceAsync()`, setting `disabled = !entry.enabled`.

### 4. gRPC Request Execution Flow
```text
User clicks "Send" (gRPC Request)
  │
  ▼
Collect enabled import paths:
state.collectionImportPaths.filter { it.enabled }.map { it.path }
  │
  ▼
RequestExecutionService.executeGrpc(
  requestItem,
  includePaths = enabledImportPaths
)
  │
  ▼
NettyDynamicGrpcClient.executeUnary(
  GrpcCallRequest(..., includePaths = includePaths)
)
  │
  ▼
ProtocJarParser.parseProtoFile(
  protoFilePath = request.protoFilePath,
  includePaths = request.includePaths
)
  │
  ▼
protoc resolves `import "protobuf/common/metadata.proto";`
  │
  ▼
RPC Executed & Response Rendered
```
