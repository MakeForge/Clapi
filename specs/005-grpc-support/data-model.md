# Data Model: gRPC Support

## Entities & Schemas

### 1. OpenCollection gRPC Models (`core-model`)

```kotlin
package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

@Serializable
data class GrpcRequestInfo(
    override val name: String,
    override val description: Description? = null,
    override val type: String = "grpc",
    override val seq: Double? = null,
    override val tags: List<String> = emptyList(),
) : ItemInfo

@Serializable
data class GrpcMetadata(
    val name: String,
    val value: String,
    val description: Description? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class GrpcRequestDetails(
    val url: String,
    val method: String? = null,
    val methodType: String? = "unary", // "unary", "client-streaming", "server-streaming", "bidi-streaming"
    val protoFilePath: String? = null,
    val metadata: List<GrpcMetadata> = emptyList(),
    val message: String? = null,
    val auth: AuthConfig? = null,
)

@Serializable
data class GrpcRequestRuntime(
    val variables: List<VariableEntry> = emptyList(),
    val scripts: Scripts? = null,
    val assertions: List<Assertion> = emptyList(),
)

@Serializable
data class GrpcRequestItem(
    override val info: GrpcRequestInfo,
    val grpc: GrpcRequestDetails,
    val runtime: GrpcRequestRuntime? = null,
    val docs: String? = null,
    @Transient override val extensions: Map<String, JsonElement> = emptyMap(),
) : OpenCollectionItem
```

---

### 2. Runtime & Service Entities (`core-network`)

```kotlin
package io.github.crustack.clapi.core.network.grpc

data class ProtoMethodInfo(
    val serviceFullName: String,
    val methodName: String,
    val fullMethodName: String, // "package.Service/Method"
    val inputTypeName: String,
    val outputTypeName: String,
    val isClientStreaming: Boolean,
    val isServerStreaming: Boolean,
)

data class ProtoSchemaDefinition(
    val protoFilePath: String,
    val services: List<ProtoServiceInfo>,
    val fileDescriptorBytes: ByteArray? = null,
)

data class ProtoServiceInfo(
    val fullName: String,
    val methods: List<ProtoMethodInfo>,
)

data class GrpcCallRequest(
    val requestId: String,
    val host: String,
    val port: Int,
    val useTls: Boolean = false,
    val protoFilePath: String,
    val fullMethodName: String,
    val jsonPayload: String,
    val metadata: List<Pair<String, String>> = emptyList(),
)

data class GrpcCallResponse(
    val requestId: String,
    val statusCode: Int,
    val statusDescription: String,
    val responseJson: String?,
    val durationMs: Long,
    val responseMetadata: List<Pair<String, String>> = emptyList(),
    val errorMessage: String? = null,
)
```

---

### 3. UI State Entities (`ui-shared`)

#### `RequestKind`
Extend `RequestKind` in `core-model`:
```kotlin
enum class RequestKind {
    HTTP,
    MCP,
    FOLDER,
    GRPC
}
```

#### `CollectionNode` Extension
`CollectionNode` encapsulates state for rendering the collection sidebar tree:
- `kind: RequestKind` (set to `RequestKind.GRPC` for gRPC requests)
- `grpcMethod: String?`: Selected full RPC method (e.g. `demo.GreeterService/SayHello`)
- `protoFilePath: String?`: Local filesystem path to the selected `.proto` file
- `grpcMetadata: List<KeyValueEntry>`: Key-value metadata rows

#### `RequestTabState` Extension
`RequestTabState` holds open tab data:
- `kind: RequestKind = RequestKind.HTTP` (or `RequestKind.GRPC`)
- `protoFilePath: String?`
- `grpcMethod: String?`
- `grpcMetadata: SnapshotStateList<MutableKeyValue>`
- `grpcMessageBody: String`
- `availableMethods: List<ProtoMethodInfo>` (populated when proto is parsed)

---

## State Transitions

### Request Creation & Editing Flow
1. **Creation**:
   - User clicks "+" on Folder -> Context Menu -> Selects "New gRPC Request".
   - `CollectionNode` created with `kind = RequestKind.GRPC`, `name = "New Request"`, default method `""`, `protoFilePath = null`.
2. **Tab Opened**:
   - Tab instantiated with `RequestTabState(kind = RequestKind.GRPC)`.
   - `GrpcRequestEditor` displayed.
3. **Proto Selected**:
   - User clicks "Proto" button -> Modal dialog opens.
   - User picks `.proto` file -> Clicks "Save".
   - `tabState.protoFilePath` updated.
   - Background job parses `.proto` via `ProtoParser` -> Populates `tabState.availableMethods`.
4. **Method Selected**:
   - User chooses method from dropdown -> `tabState.grpcMethod` set to `package.Service/Method`.
5. **Execution**:
   - User clicks "Send".
   - Variables in URL, metadata, and message are substituted.
   - Dynamic gRPC client compiles dynamic message, attaches metadata, calls remote endpoint.
   - Response status, latency, headers, and formatted JSON are rendered in the response panel.
