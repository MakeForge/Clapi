# Contract: Dynamic gRPC Client & Proto Parser

## Service Interfaces (`core-network`)

### 1. `ProtoParser` Interface

Responsible for compiling and extracting service and method definitions from `.proto` files on disk.

```kotlin
package io.github.crustack.clapi.core.network.grpc

import java.io.File

interface ProtoParser {
    /**
     * Parses a .proto file into a structured descriptor representation.
     *
     * @param protoFile The root .proto file on disk.
     * @param importDirectories Additional include paths for imports.
     * @return ProtoSchemaDefinition containing discovered services, methods, and types.
     * @throws ProtoParseException if protoc fails or file cannot be compiled.
     */
    suspend fun parse(
        protoFile: File,
        importDirectories: List<File> = emptyList(),
    ): ProtoSchemaDefinition
}

class ProtoParseException(
    message: String,
    val exitCode: Int? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

---

### 2. `DynamicGrpcClient` Interface

Responsible for executing dynamic unary RPC calls using Protobuf dynamic messages and managed gRPC channels.

```kotlin
package io.github.crustack.clapi.core.network.grpc

interface DynamicGrpcClient {
    /**
     * Executes a dynamic Unary gRPC call.
     *
     * @param request Execution parameters including endpoint, proto file, method, and JSON body.
     * @return GrpcCallResponse with status, headers/trailers, latency, and formatted JSON output.
     */
    suspend fun executeUnary(
        request: GrpcCallRequest
    ): GrpcCallResponse

    /**
     * Closes any pooled channels and cleans up resources.
     */
    fun shutdown()
}
```

---

### 3. Execution Behavior & Error Contract

- **Channel Reuse**: Channels are cached per `(host, port, useTls)`. Channels are reused across requests to the same target unless closed.
- **Variable Interpolation**: Endpoint strings (e.g. `{{host}}:{{port}}`), metadata values, and message payload templates MUST be resolved before calling `DynamicGrpcClient.executeUnary`.
- **Status Codes**: gRPC status codes (0 = OK, 1 = CANCELLED, 2 = UNKNOWN, 3 = INVALID_ARGUMENT, 4 = DEADLINE_EXCEEDED, 5 = NOT_FOUND, 14 = UNAVAILABLE, etc.) are converted to human-readable status labels and metrics.
- **Failures**: Network failures or invalid inputs (malformed JSON, unreachable host) return a `GrpcCallResponse` with non-zero `statusCode`, descriptive `errorMessage`, and no exception escaping to crash the caller.
