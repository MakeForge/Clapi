# Feature Specification: gRPC Support

**Feature Branch**: `005-grpc-support`

**Created**: 2026-11-08

**Status**: Draft

**Input**: User description: "gRPC Support - Add new `OpenCollectionItem` type GRPCRequestItem (see @opencollection_v1.0.0.json) to OpenCollectionItem.kt - When "+" on a folder is clicked -> show context menu to either create HTTP or gRPC Request - in the sidebar a grpc request should look exactly like a http request but instead of the MethodBadge it simply shows GrpcBadge, which looks the same but just display "gRPC" with methodPath background color - grpc request Editor for GRPC requests should be similar to Insomnia (see @insomnia_grpc_request.png): - you get a text input for the `url`, just like for http request - Dropdown to select grpc method to use (can select any of the currently selected proto file) - Button to upload/change .proto file (shows dialog similar to @insomnia_grpc_select_proto.png, but only with single "Select Proto file" button for now, display what file is currently, and save button, will set the grpc request's `protoFilePath` (see OpenCollection schema)) - Headers editor (for grpc request's `metadata` field) - message editor (similar to HTTP Request Body Editor for JSON input), - Scripts Editors same as for HTTP Requests (for grpc script types see opencollection schema json) - ability to use variables exactly like in http requests editor - notes for the actual implementation of executing grpc requests: see @ideas_grpc.md - ensure import/export of grpc requests works as well - ensure the qa-tests are extended to test grpc requests, by including grpc requests in the fixtures jsons and adding simple example grpc endpoint to DummyApiServer that resembles the existing http example endpoint for testing (use kotlinx.rpc, see @kotlinx_rpc.md)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Identify gRPC Requests in Collection Sidebar (Priority: P1)

Users organizing API collections need to add gRPC requests alongside existing HTTP requests within folders. When a user clicks the "+" action button on any folder in the collection sidebar, the application displays a context menu offering options to create either an "HTTP Request" or a "gRPC Request". When selecting "gRPC Request", a new request item is created and added to the folder. In the sidebar tree, the gRPC request displays identically to HTTP requests, with its name and item layout matching standard requests, except the method badge displays "gRPC" with the distinctive Patch method theme color styling.

**Why this priority**: Discoverability and creation of gRPC requests within the primary collection tree is the foundational user entry point for using gRPC features.

**Independent Test**: Click "+" on a folder, select "gRPC Request" from the context menu, verify a new item is created in that folder, verify that the sidebar renders a badge displaying "gRPC" using the Patch accent color, and verify clicking the item opens the request in an editor tab.

**Acceptance Scenarios**:

1. **Given** a collection folder with a "+" action button, **When** the user clicks the "+" button, **Then** a context menu appears presenting "New HTTP Request" and "New gRPC Request".
2. **Given** the context menu is open, **When** the user selects "New gRPC Request", **Then** a new gRPC request item is created with default name "New Request" and added to the folder.
3. **Given** a gRPC request item in the sidebar tree, **When** rendered in the collection hierarchy, **Then** it shows the request name and a `GrpcBadge` displaying "gRPC" with the `MethodPatch` color scheme.
4. **Given** an open tab for a gRPC request, **When** displayed in the tab bar, **Then** the tab displays the request title and the "gRPC" badge.

---

### User Story 2 - Configure gRPC Target URL and Select Proto Methods (Priority: P1)

Users need to target a gRPC server and pick which RPC method to call. In the gRPC request editor, the user enters an endpoint address (e.g. `localhost:50051`) in a URL text input supporting variable substitution (e.g., `{{grpc_host}}:{{grpc_port}}`). The user clicks a proto file management action which opens a "Select Proto File" dialog. In the dialog, the user sees the currently selected `.proto` file (or "No file selected"), clicks "Select Proto File" to pick a `.proto` file from disk, and clicks "Save" to apply the selection. Upon saving, the editor updates the request's `protoFilePath`, parses the `.proto` file, and populates a method dropdown listing all services and methods found in the proto file (e.g. `demo.GreeterService/SayHello`). The user selects their target RPC method.

**Why this priority**: Specifying the connection endpoint and defining the RPC method schema via `.proto` are essential prerequisites to constructing and executing any gRPC call.

**Independent Test**: Open a gRPC request, enter a URL, click the proto file button, pick a valid `.proto` file in the dialog, click Save, and verify the method dropdown is populated with the services and methods defined in the proto file.

**Acceptance Scenarios**:

1. **Given** an active gRPC request editor, **When** viewing the top request bar, **Then** an address/URL text input is provided supporting dynamic variable tokens (e.g., `{{baseUrl}}`).
2. **Given** the user clicks the proto file action button in the request bar, **When** the "Select Proto File" dialog opens, **Then** it shows the currently configured file path, a "Select Proto File" button, and a "Save" button.
3. **Given** the "Select Proto File" dialog, **When** the user picks a `.proto` file and clicks "Save", **Then** the dialog closes and the request's `protoFilePath` is updated.
4. **Given** a valid `.proto` file associated with the request, **When** the method selector dropdown is opened, **Then** it lists all available RPC methods grouped by service, indicating the streaming type (e.g., Unary).
5. **Given** the user selects a method from the dropdown, **When** selection is confirmed, **Then** the request method field is updated to the chosen method (e.g., `package.Service/MethodName`).

---

### User Story 3 - Author gRPC Metadata, JSON Message Payload, and Scripts (Priority: P1)

Users need to send request headers (gRPC metadata), provide the message body, and execute automated pre-request and post-request test scripts. In the editor tabs, the user has:
- A "Headers" (metadata) editor: a key-value grid for custom metadata entries with name, value, description, and an enable/disable checkbox.
- A "Message" editor: a code/text editor allowing users to enter the request payload formatted as JSON matching the proto message schema.
- "Pre-request" and "Post-request" (Tests) script editors: identical to HTTP request scripting, supporting variable reading/writing and response inspection.
- Variable resolution: `{{variable}}` syntax is resolved seamlessly across URL, metadata values, and message payload text.

**Why this priority**: Authoring payload data and headers is required to formulate real-world requests and authenticate with services.

**Independent Test**: Enter JSON data in the Message editor, add a header key-value in the Headers tab with a variable token, write a test script that validates the response, and verify all inputs are saved with the request.

**Acceptance Scenarios**:

1. **Given** the "Headers" tab in the gRPC editor, **When** the user adds metadata, **Then** key-value pairs can be entered, edited, toggled enabled/disabled, or removed.
2. **Given** the "Message" tab in the gRPC editor, **When** the user inputs a JSON string representing the request message, **Then** syntax highlighting and formatting support are provided.
3. **Given** the "Scripts" tab (Pre-request & Post-request), **When** script code is added, **Then** scripts are stored with the gRPC request runtime according to OpenCollection schema.
4. **Given** environment or collection variables, **When** referenced with `{{variable_name}}` in the URL, headers, or message body, **Then** variables resolve correctly prior to execution.

---

### User Story 4 - Execute Unary gRPC Call and View Response (Priority: P1)

A user wants to invoke a gRPC endpoint and inspect the result. The user clicks "Send". The application resolves variables, executes any pre-request scripts, serializes the JSON message body to the proto message format, dispatches the unary RPC to the target server, executes post-request test scripts, and displays the response in the response panel. The response panel shows the status (e.g., `OK 0` or gRPC error codes), execution duration (latency in milliseconds), response metadata (headers and trailers), and the response body pretty-printed as JSON.

**Why this priority**: Executing requests and inspecting responses is the core purpose of an API client.

**Independent Test**: Connect to a running gRPC server with a valid `.proto` and JSON payload, click Send, and verify that the response status code, elapsed time, and JSON response body are rendered in the response view.

**Acceptance Scenarios**:

1. **Given** a configured gRPC request with reachable URL and valid message payload, **When** the user clicks "Send", **Then** the request is dispatched and a loading/executing indicator is displayed.
2. **Given** a successful gRPC response, **When** response arrives, **Then** the response status (e.g., `OK`), response latency, and JSON-formatted response payload are displayed.
3. **Given** response metadata or trailers returned by the server, **When** viewing the response headers tab, **Then** the metadata entries are listed clearly.
4. **Given** a gRPC server error (e.g. `NOT_FOUND`, `UNAVAILABLE`), **When** the call completes with an error, **Then** the gRPC status code, error message, and any error details are displayed in the response pane without application crash.

---

### User Story 5 - Import and Export gRPC Requests in OpenCollection Format (Priority: P2)

Users need to export and import collections containing gRPC requests to collaborate with teammates and persist their workspace on disk. When exporting a collection or saving it to the filesystem, gRPC requests are serialized strictly according to the OpenCollection v1.0.0 specification (`info.type = "grpc"`, `grpc` details with `url`, `method`, `protoFilePath`, `metadata`, `message`, and `runtime` scripts/variables). When importing or opening an OpenCollection file or directory, gRPC requests are parsed into `GrpcRequestItem` instances and appear in the workspace with all properties intact.

**Why this priority**: Preserves user work across sessions and guarantees complete interoperability with the OpenCollection standard.

**Independent Test**: Export a collection containing both HTTP and gRPC requests to OpenCollection format. Re-import the collection into a fresh workspace and assert that all gRPC fields (`url`, `method`, `protoFilePath`, `metadata`, `message`, `runtime`) match the original exactly.

**Acceptance Scenarios**:

1. **Given** a collection with gRPC requests, **When** the collection is exported to OpenCollection JSON or YAML, **Then** each gRPC request is represented with `info.type: "grpc"` and the full `grpc` property structure matching OpenCollection v1.0.0 schema.
2. **Given** an OpenCollection file containing gRPC requests, **When** imported or loaded into Clapi, **Then** polymorphic deserialization parses the item as `GrpcRequestItem`.
3. **Given** an imported gRPC request, **When** opened in the UI, **Then** all settings (`url`, `method`, `protoFilePath`, metadata, message, and scripts) are faithfully restored.

---

### User Story 6 - Automated QA Test Suite and Mock gRPC Server Integration (Priority: P2)

Engineers and automated CI pipelines need to continuously verify that gRPC requests operate reliably. The automated test suite (`qa-tests`) includes test fixtures containing gRPC requests. The mock server (`DummyApiServer`) hosts an example gRPC service (matching the test user/greeter workflows of the HTTP mock endpoints). Automated end-to-end tests verify creating, executing unary requests against the dummy server, verifying response data and metadata, testing script execution, and round-trip collection import/export.

**Why this priority**: Guarantees regressions are caught early and ensures the stability of gRPC operations.

**Independent Test**: Run `./gradlew :qa-tests:test` and verify that the gRPC E2E tests execute unary calls against `DummyApiServer` and assert correct status, JSON response conversion, and metadata handling.

**Acceptance Scenarios**:

1. **Given** `DummyApiServer`, **When** started during test execution, **Then** it exposes both existing HTTP endpoints and a gRPC service endpoint on a test port.
2. **Given** the test fixtures collection (`clapi-test-collection.json`), **When** loaded, **Then** it includes valid gRPC request examples targeting the test server.
3. **Given** the automated QA test suite, **When** tests execute, **Then** end-to-end gRPC requests are sent to `DummyApiServer` and assert payload response, status code, and header round-trips.

---

### Edge Cases

- **Missing or Invalid Proto File**: If a user selects a file that does not exist or has invalid Protobuf syntax, the application displays an inline error alert explaining that the proto file could not be compiled or parsed, and clears or disables the method selector until a valid file is provided.
- **Proto File Moved or Deleted on Disk**: If a previously saved request references a `protoFilePath` that is no longer accessible on disk, the UI indicates that the proto file is missing and prompts the user to reselect the file.
- **Malformed JSON Payload**: If the user sends a message whose JSON structure does not match the protobuf input message schema, the application reports a descriptive JSON parsing/validation error prior to sending or displays the validation failure clearly.
- **Connection Failure / Timeout**: If the gRPC host cannot be reached or the port is closed, the client fails gracefully with a status code (e.g. `UNAVAILABLE: Connection refused`) without freezing the UI.
- **Special Characters in Metadata**: Metadata keys and values containing spaces, colons, or binary suffixes (e.g. `-bin`) are handled according to gRPC metadata standards.
- **Proto Files with Imports**: When a `.proto` file imports other `.proto` files in the same directory or standard Google protobuf libraries (like `google/protobuf/timestamp.proto` or `empty.proto`), dependencies resolve without failure.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Data Model & Schema
- **FR-001**: The system MUST implement `GrpcRequestItem` as a variant of `OpenCollectionItem` adhering to the OpenCollection v1.0.0 specification.
- **FR-002**: The polymorphic deserializer for `OpenCollectionItem` MUST distinguish `GrpcRequestItem` by detecting the presence of the `grpc` object or `info.type == "grpc"`.
- **FR-003**: The data model MUST capture all OpenCollection gRPC fields: `url`, `method`, `methodType` (unary, client-streaming, server-streaming, bidi-streaming), `protoFilePath`, `metadata` (list of name, value, description, disabled), `message` (JSON payload string or message variants), `auth`, `runtime` (variables, scripts, assertions), and `docs`.

#### Sidebar & Creation UI
- **FR-004**: When the user clicks the "+" button on any folder in the collection sidebar, the application MUST present a menu to create either an "HTTP Request" or a "gRPC Request".
- **FR-005**: In the collection sidebar tree, gRPC requests MUST display a `GrpcBadge` showing the text "gRPC".
- **FR-006**: The `GrpcBadge` MUST use the same visual dimensions and typography as `MethodBadge` and MUST use the `MethodPatch` theme color for its text and background tint.
- **FR-007**: When a gRPC request is opened in an editor tab, the tab header MUST display the request name and the `GrpcBadge`.

#### Request Editor & Proto File Management
- **FR-008**: The gRPC request editor MUST provide an address bar with a URL input field for the gRPC endpoint.
- **FR-009**: The request bar MUST provide an action button to open the "Select Proto File" dialog.
- **FR-010**: The "Select Proto File" dialog MUST display the current proto file path (or indicate none is selected), a "Select Proto File" file picker action, and a "Save" action.
- **FR-011**: Saving a proto file in the dialog MUST update the request's `protoFilePath`.
- **FR-012**: The request bar MUST provide a method selection dropdown that displays all RPC methods defined in the selected `.proto` file.
- **FR-013**: Selecting an RPC method MUST set the request's `method` (in `package.Service/Method` format) and update `methodType`.

#### Payload, Metadata, and Scripts Editors
- **FR-014**: The editor MUST include a "Headers" tab allowing editing of gRPC metadata entries (`name`, `value`, `disabled`, and optional `description`).
- **FR-015**: The editor MUST include a "Message" tab providing a JSON code editor for entering the RPC request message payload.
- **FR-016**: The editor MUST include "Pre-request" and "Post-request" script tabs supporting custom automation scripts compatible with the Clapi scripting runtime.
- **FR-017**: The editor and execution engine MUST substitute variable tokens (e.g. `{{var}}`) across the URL, metadata values, and message payload before dispatching the request.

#### Request Execution & Response Handling
- **FR-018**: The system MUST support executing Unary gRPC calls against target gRPC servers using dynamic message serialization based on the selected `.proto` file.
- **FR-019**: The response viewer MUST display the gRPC response status code and message (e.g. `OK (0)` or error name), execution time in milliseconds, and the response body converted to formatted JSON.
- **FR-020**: The response viewer MUST display response metadata and trailers received from the gRPC server.
- **FR-021**: In case of network errors or server exceptions, the UI MUST display the gRPC status and error description without crashing.

#### Import, Export & Testing
- **FR-022**: The system MUST support exporting collections containing gRPC requests to OpenCollection format with zero data loss.
- **FR-023**: The system MUST support importing collections containing gRPC requests from OpenCollection JSON and YAML files.
- **FR-024**: Test fixtures in `qa-tests` MUST include sample gRPC requests targeting test endpoints.
- **FR-025**: `DummyApiServer` MUST provide a gRPC service endpoint for end-to-end integration testing.
- **FR-026**: Automated QA tests MUST verify gRPC request creation, method inspection, execution, response verification, and serialization roundtrips.

---

### Key Entities

- **GrpcRequestItem**: Represents a complete gRPC request item in an OpenCollection collection, containing `info` (`GrpcRequestInfo`), `grpc` (`GrpcRequestDetails`), `runtime` (`GrpcRequestRuntime`), and optional documentation.
- **GrpcRequestDetails**: Holds the protocol-level configuration: `url`, `method` (full RPC method path), `methodType` (streaming mode), `protoFilePath` (local path to the protobuf definition), `metadata` (headers), `message` (JSON payload), and optional `auth`.
- **GrpcMetadata**: Represents a single key-value header or trailer entry sent alongside an RPC, including `name`, `value`, `disabled` flag, and description.
- **ProtoSchemaDescriptor**: Parsed representation of a `.proto` file holding discovered services, methods, input message schemas, and output message schemas used to populate the UI and validate payloads.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new gRPC request, pick a `.proto` file, and select an RPC method from the dropdown in under 30 seconds.
- **SC-002**: 100% of valid Unary gRPC requests return formatted JSON response bodies and correct status indicators matching server responses.
- **SC-003**: 100% of collection export-and-reimport cycles preserve all gRPC request properties without data loss or schema drift.
- **SC-004**: Variable replacement succeeds across 100% of supported locations: endpoint URL, metadata key-values, and message JSON text.
- **SC-005**: All automated gRPC integration tests in `qa-tests` execute and pass reliably in the CI test suite.
- **SC-006**: Syntax errors in `.proto` files or JSON payloads produce clear, human-readable error messages within 1 second of parsing.

---

## Assumptions

- **Unary Execution Scope**: While the OpenCollection schema and proto parser recognize all four RPC types (`unary`, `client-streaming`, `server-streaming`, `bidi-streaming`), the execution engine and UI in this phase focus on Unary RPC execution. Streaming calls will indicate their streaming type and be fully supported in subsequent iterations.
- **Local Proto File Storage**: In this phase, `protoFilePath` points to a local file accessible on the user's filesystem (relative to collection root or absolute path). Advanced multi-file proto registries and reflection services are deferred to future enhancements.
- **Plaintext vs TLS**: Standard gRPC endpoints without a protocol prefix or with `grpc://` default to plaintext connection, while `grpcs://` indicates TLS/SSL connection.
- **Scripting API Compatibility**: Pre-request and post-request scripts for gRPC requests utilize the existing JavaScript scripting sandbox with access to environment variables, request metadata/body, and response status/data.
