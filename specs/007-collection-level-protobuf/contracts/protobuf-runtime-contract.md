# Runtime Contract: Protobuf Resolution & Dynamic Execution

## 1. ProtocJarParser Include Path Resolution

- **Signature**:
  ```kotlin
  suspend fun parseProtoFile(
      protoFilePath: String,
      includePaths: List<String> = emptyList(),
  ): ProtoSchemaDefinition
  ```
- **Behavior**:
  - Validates that `protoFilePath` exists and is a readable file.
  - Generates include directories:
    - Adds `File(protoFilePath).parentFile`.
    - For each item in `includePaths`:
      - If path is a directory: adds `-I<dir>`.
      - If path is a file: adds its `parentFile` and candidate ancestor directories so relative imports (e.g. `import "protobuf/common/metadata.proto";`) resolve against their root directory.
    - Normalizes and deduplicates all include directories.
  - Runs protoc with `--include_imports`, `--include_std_types`, `-I<dir>` for each resolved directory, `--descriptor_set_out=<tempFile>`, and `<protoFilePath>`.
  - Parses compiled `FileDescriptorSet` into `ProtoSchemaDefinition`.

---

## 2. Dynamic gRPC Client Contract

- **Signature**:
  ```kotlin
  suspend fun executeUnary(request: GrpcCallRequest): GrpcCallResponse
  ```
- **Behavior**:
  - Dispatches `protoParser.parseProtoFile(request.protoFilePath, includePaths = request.includePaths)`.
  - If compilation fails due to missing imported proto files or syntax errors, returns a formatted `GrpcCallResponse` with `statusCode = INVALID_ARGUMENT (3)` and `errorMessage` containing protoc compiler error output.
  - When compilation succeeds, builds dynamic descriptor set, serializes JSON payload to Protobuf `DynamicMessage`, invokes unary RPC, and maps response back to JSON and metadata trailers.

---

## 3. Proto File Selection Contract in Request Editor

- **Dialog**: `SelectProtoFileDialog`
- **Inputs**:
  - `currentProtoFilePath: String?`
  - `collectionProtoFiles: List<String>` (collection-level proto file paths)
  - `onSave: (protoFilePath: String) -> Unit`
- **Behavior**:
  - Displays currently selected path.
  - If `collectionProtoFiles` is not empty:
    - Renders a selection list/dropdown labeled "Collection Proto Files".
    - Clicking an item sets the active path.
  - "Select from computer..." button enables manual file chooser.
  - Saving commits the selected path to `tab.grpcProtoFilePath`.
