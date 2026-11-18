# Tasks: Clapi Model Context Protocol (MCP) Server

**Input**: Design documents from `specs/029-mcp-server/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `research.md`, `data-model.md`, `contracts/`

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [ID] [P?] [Story] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`, `[US6]`)
- Exact file paths and verbatim constraints from `data-model.md` and `contracts/` are included in task descriptions.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize `:mcp-server` Gradle module and wire dependencies across the multi-module project.

- [X] T001 Register `":mcp-server"` in `settings.gradle.kts`
- [X] T002 Create `mcp-server/build.gradle.kts` configuring `jvm("desktop")` target and dependencies on `:core-model`, `:core-network`, `:core-storage`, `:core-scripting`, `libs.coroutines.core`, `libs.serialization.json`, `libs.ktor.server.core`, `libs.ktor.server.netty`, `libs.ktor.server.content.negotiation`, `libs.ktor.serialization.kotlinx.json`, `libs.kotlin.test`, `libs.coroutines.test`, and `libs.ktor.server.test.host`
- [X] T003 Add `api(project(":mcp-server"))` to `commonMain.dependencies` in `ui-shared/build.gradle.kts` and verify module compilation via `rtk gradlew :mcp-server:compileKotlinDesktop :ui-shared:compileKotlinDesktop`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core DTOs, `McpWorkspaceBackend` contract, dual identifier resolution (`NodeLookupResolver`), and JSON-RPC 2.0 MCP protocol dispatcher (`ClapiMcpServer`) that MUST be complete before any user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 Create DTO models and `McpWorkspaceBackend` interface in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/McpWorkspaceBackend.kt` defining `WorkspaceTreeNodeDto` (`kind` in `"collection"`, `"folder"`, `"http"`, `"grpc"`, `"graphql"`, `"websocket"`), `SanitizedVariableDto` (`kind` in `"regular"` | `"secret"` | `"dynamic"`, `variableType` in `"string"` | `"number"` | `"boolean"` | `"object"`, `value` as `"********"` for `secret`), `SanitizedAuthDto` (`type` in `"NONE"`, `"BASIC"`, `"BEARER"`, `"API_KEY"`, `"OAUTH2"`, `"JWT"`, `"INHERIT"`), `SanitizedExternalSecretDeclarationDto`, `SanitizedRequestDto`, `SanitizedCollectionDto`, `SanitizedFolderDto`, `ExecutionSnapshot`, `ExecutionResultDto`, and all tool input/output DTOs per `data-model.md`
- [X] T005 [P] Implement `NodeLookupResolver` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/NodeLookupResolver.kt` resolving collections, folders, and requests by exact UUID or slash-delimited path (`"CollectionName/FolderName/RequestName"`, with escaped `\/` support) and throwing `AmbiguousIdentifierException` listing each matching item's `id`, `name`, `kind`, and `slashPath` when a slash path matches `> 1` items
- [X] T006 [P] Implement `ResponseSnapshotCache` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/inspection/ResponseSnapshotCache.kt` as a thread-safe in-memory LRU cache (`max 50 requests`) storing sanitized `ExecutionSnapshot` instances keyed by request UUID
- [X] T007 Implement base `ClapiMcpServer` JSON-RPC 2.0 protocol dispatcher in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/ClapiMcpServer.kt` supporting MCP protocol versions `"2025-06-18"` (default) and `"2024-11-05"` (legacy), handling `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`, `resources/list`, `resources/templates/list`, `resources/read`, `resources/subscribe`, `resources/unsubscribe`, `prompts/list`, `prompts/get`, `completion/complete`, and `logging/setLevel` using `:core-model` (`McpModels.kt`)
- [X] T008 Create base `HeadlessWorkspaceBackend` class in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt` backed by `CanonicalCollectionRepository` (`workspace.json`), headless private state (`private-state.json`), `RequestExecutionService`, `ClapiScriptEngine`, `SpecificationImporter`, `OpenApiExporter`, and `GitRepositoryManager`
- [X] T009 [P] Write unit tests for `NodeLookupResolver` (UUID match, slash path match, escaped slash, not-found error, and `> 1` match disambiguation error) in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/NodeLookupResolverTest.kt`

**Checkpoint**: Foundation ready — user story implementation can now proceed.

---

## Phase 3: User Story 1 - Workspace Discovery, Context Reading & Multi-Protocol Request Authoring (Priority: P1) 🎯 MVP

**Goal**: AI assistants can browse the workspace hierarchy, read collection and request definitions as resources or tools, and create, update, or delete collections, folders, and multi-protocol requests (HTTP, gRPC, GraphQL, WebSocket) by UUID or slash path.

**Independent Test**: Connect to `ClapiMcpServer`, read `clapi://tree`, invoke `clapi_manage_collection` and `clapi_manage_folder`, create and update HTTP, gRPC, GraphQL, and WebSocket requests via `clapi_upsert_request`, retrieve them by both UUID and slash path via `clapi_get_request` and `clapi://requests/{requestId}`, and verify deletion requires `confirm == true`.

### Implementation for User Story 1

- [X] T010 [P] [US1] Implement `WorkspaceTools` (`clapi_list_tree`, `clapi_manage_collection`, `clapi_manage_folder`) schema definitions and handlers in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/WorkspaceTools.kt`, enforcing the Destructive Confirmation Invariant (`confirm == true` required when `action == "delete"`, returning `isError = true` without modifying state if `false` or omitted)
- [X] T011 [P] [US1] Implement `RequestTools` (`clapi_get_request`, `clapi_upsert_request`, `clapi_delete_request`) schema definitions and handlers in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/RequestTools.kt` supporting `"http"`, `"grpc"`, `"graphql"`, and `"websocket"` request kinds, all `BodyType` values (`"NONE"`, `"JSON"`, `"XML"`, `"HTML"`, `"JAVASCRIPT"`, `"FORM_DATA"`, `"X_WWW_FORM_URLENCODED"`, `"RAW_TEXT"`, `"BINARY"`, `"GRAPHQL"`), pre-request/test scripts, `.proto` config, GraphQL variants/settings, WebSocket variants, and enforcing `confirm == true` on `clapi_delete_request`
- [X] T012 [US1] Implement workspace, collection, folder, and multi-protocol request CRUD operations in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt` (`listTree`, `getCollection`, `manageCollection`, `manageFolder`, `getRequest`, `upsertRequest`, `deleteRequest`), preserving existing stored auth credentials whenever an update passes `"********"` for a masked credential field
- [X] T013 [US1] Implement `McpResourceRegistry` for `clapi://tree`, `clapi://collections/{collectionId}`, and `clapi://requests/{requestId}` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/resources/McpResourceRegistry.kt` and register `WorkspaceTools`, `RequestTools`, and `McpResourceRegistry` in `ClapiMcpServer.kt`
- [X] T014 [P] [US1] Write unit tests in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/WorkspaceAndRequestToolsTest.kt` verifying tree listing (< 4 KB for 100 requests), collection/folder CRUD with defaults and `.proto` paths, multi-protocol request authoring (HTTP, gRPC, GraphQL, WebSocket), dual UUID/slash-path lookup, and `confirm == true` delete guard

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - Request Execution, Test Suite Runner & Context-Safe Response Inspection (Priority: P1)

**Goal**: AI assistants can execute HTTP, gRPC, and GraphQL requests with transient variable overrides and environment selection, run folder/collection test suites, read console logs and latest response resources, and inspect large responses via JSONPath or UTF-8 byte ranges.

**Independent Test**: Execute a request returning a >50 KB JSON payload via `clapi_execute_request`, verify `bodyPreview` is truncated to `maxBodyLength` (default `4096` bytes) with `_truncated: true` and `_totalBytes`, query nested fields via `clapi_inspect_response` using `jsonPath` and `byteOffset`/`length`, run `clapi_run_test_suite` on a folder with `clapi.test` assertions, and read `clapi://responses/{requestId}/latest` and `clapi://logs/console`.

### Implementation for User Story 2

- [X] T015 [P] [US2] Implement `JsonPathInspector` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/inspection/JsonPathInspector.kt` supporting JSONPath queries (`$`, `.field`, `['field']`, `[index]`, `[*]`, `[start:end]`, `..field`) over `kotlinx.serialization.json.JsonElement` as well as UTF-8 `byteOffset` (default `0`) and `length` (default `4096`, clamped to `256..65536`) slicing
- [X] T016 [P] [US2] Implement `ExecutionTools` (`clapi_execute_request`, `clapi_inspect_response`, `clapi_run_test_suite`) schema definitions and handlers in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/ExecutionTools.kt` with `maxBodyLength` defaulting to `4096` and clamped to `256..65536`, binary MIME detection (`image/*`, `audio/*`, `video/*`, `application/octet-stream`, `application/pdf`, `application/zip` returning `_binary: true`), and truncation hint generation
- [X] T017 [US2] Implement `executeRequest`, `inspectResponse`, `runTestSuite`, and `getConsoleLogs` (last 100 entries) in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt`, integrating `RequestExecutionService`, `ClapiScriptEngine` (pre-request mutations, `clapi.sendRequest`, and post-request `clapi.test` assertions), and `ResponseSnapshotCache`
- [X] T018 [US2] Register `clapi://responses/{requestId}/latest` and `clapi://logs/console` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/resources/McpResourceRegistry.kt` and wire `ExecutionTools` into `ClapiMcpServer.kt`
- [X] T019 [P] [US2] Write unit tests for `JsonPathInspector` and `ExecutionTools` in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/JsonPathInspectorTest.kt` and `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/ExecutionToolsTest.kt` covering 4 KB preview truncation, binary content summarization, JSONPath extraction, byte-range pagination, script console logs, and test suite pass/fail aggregation

**Checkpoint**: User Stories 1 and 2 are complete and independently testable.

---

## Phase 5: User Story 3 - Zero-Exfiltration Secret & Credential Protection (Priority: P1)

**Goal**: Enforce strict zero-exfiltration guarantees across all MCP resources, tools, execution snapshots, console logs, exports, and error messages so plaintext secret variable values, auth credentials, and external secret manager tokens are never exposed to the AI agent.

**Independent Test**: Configure secret variables (`kind = "secret"`), Bearer/Basic/OAuth2 auth credentials, and external secret declarations; verify all resource reads and tool outputs mask secret values as `"********"`, and verify that executing a request against an echo endpoint that reflects headers and secret tokens in the response body scrubs every occurrence of resolved secrets (length ≥ 4) with `"[REDACTED_SECRET]"` before caching `ExecutionSnapshot`.

### Implementation for User Story 3

- [X] T020 [US3] Implement `SecretSanitizer` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/security/SecretSanitizer.kt` per `contracts/security-sanitization-contract.md`:
  - Mask all variables with `kind == VariableKind.SECRET` (`secret == true` or `SecretVariable`) to `value = "********"`, `secret = true`, `isSet = value.isNotBlank()`
  - Mask `SanitizedAuthDto` credential fields (`password`, `token`, `apiValue`, `oauth2ClientSecret`, `oauth2Password`) to `"********"` with `isConfigured: Boolean`
  - Mask headers matching `{"authorization", "proxy-authorization", "x-api-key", "api-key", "token", "cookie", "set-cookie", "x-auth-token"}` (case-insensitive) or `secret == true`, preserving auth scheme prefix (e.g., `"Bearer ********"`)
  - Scrub all resolved secret values where `secret.trim().length >= 4` (sorted longest-first, including URL-encoded and Basic Auth Base64-encoded variants) with `"[REDACTED_SECRET]"` across response bodies, headers, script logs, assertion messages, and exception messages
  - Sanitize `OpenCollectionDocument` instances prior to export
- [X] T021 [US3] Integrate `SecretSanitizer` into `HeadlessWorkspaceBackend.kt`, `McpResourceRegistry.kt`, and `ClapiMcpServer.kt` error boundaries so `ExecutionSnapshot` is sanitized prior to storage in `ResponseSnapshotCache` and all tool/resource/error outputs pass through `SecretSanitizer`
- [X] T022 [P] [US3] Write exhaustive security unit tests in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/SecretSanitizerTest.kt` verifying secret variable masking across all scopes, write-only secret updates, auth credential masking and `"********"` preservation on update, sensitive header scheme masking, echo-body/log/error scrubbing for secrets with length ≥ 4 (and non-corruption for length < 4), and external secret provider credential omission

**Checkpoint**: Zero-exfiltration security boundary enforced and verified across all MCP operations.

---

## Phase 6: User Story 4 - Environment & Scoped Typed Variable Management (Priority: P2)

**Goal**: AI assistants can list, create, update, delete, and activate environments (with `extends` inheritance chain validation and external secret declaration mappings) and manage typed variables (`regular`, `secret`, `dynamic` with `string`, `number`, `boolean`, `object` data types) across Global, Environment, Collection, and Folder scopes.

**Independent Test**: Create parent and child environments via `clapi_upsert_environment`, verify circular `extends` chains (`A -> B -> A`) are rejected, configure `regular`, `secret`, and `dynamic` variables across `global`, `environment`, `collection`, and `folder` scopes via `clapi_set_variable`, activate the child environment via `clapi_set_active_environment`, and read `clapi://environments` and `clapi://environments/{envName}` to verify inherited/overridden variable projections with secrets masked.

### Implementation for User Story 4

- [X] T023 [P] [US4] Implement `EnvironmentTools` (`clapi_list_environments`, `clapi_set_active_environment`, `clapi_upsert_environment`, `clapi_delete_environment`, `clapi_set_variable`) schema definitions and handlers in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/EnvironmentTools.kt` supporting `scope` in `"global"` | `"environment"` | `"collection"` | `"folder"`, `kind` in `"regular"` | `"secret"` | `"dynamic"`, `variableType` in `"string"` | `"number"` | `"boolean"` | `"object"`, `dynamicScript`, `dynamicParameters`, and `confirm == true` on `clapi_delete_environment`
- [X] T024 [US4] Implement environment CRUD, cycle-safe inheritance validation (`extends` self-reference and ancestor cycle rejection), effective inherited variable projections (`isInherited`, `isOverridden`, `sourceEnvName`), external secret declaration mapping, and scoped typed variable persistence in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt`
- [X] T025 [US4] Register `clapi://environments` and `clapi://environments/{envName}` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/resources/McpResourceRegistry.kt` and wire `EnvironmentTools` into `ClapiMcpServer.kt`
- [X] T026 [P] [US4] Write unit tests in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/EnvironmentToolsTest.kt` verifying environment inheritance projections, cycle rejection (`A -> B -> A`), active environment switching, external secret declaration metadata, and `clapi_set_variable` across all 4 scopes, 3 variable kinds, and 4 variable types

**Checkpoint**: Environment and scoped typed variable management complete and independently testable.

---

## Phase 7: User Story 5 - Specification Import, Export & Git Sync Operations (Priority: P2)

**Goal**: AI assistants can import OpenAPI 3.0+, OpenCollection 1.0.0 (YAML/JSON), and Insomnia v4/v5 specifications, export collections to sanitized OpenCollection (JSON/YAML) or OpenAPI 3.0.3 (JSON/YAML), and execute Git sync actions (`status`, `commit`, `pull`, `push`) on Git-backed collections.

**Independent Test**: Call `clapi_import_specification` with OpenAPI 3.0 YAML and Insomnia v5 content, verify Swagger 2.0 is rejected with a clear diagnostic, export the imported collection via `clapi_export_collection` in all 4 formats (`opencollection-yaml`, `opencollection-json`, `openapi-yaml`, `openapi-json`) verifying secrets are stripped/masked, and invoke `clapi_git_sync` (`status` and `commit`) on a Git-initialized collection.

### Implementation for User Story 5

- [X] T027 [P] [US5] Implement `SyncImportExportTools` (`clapi_import_specification`, `clapi_export_collection`, `clapi_git_sync`) schema definitions and handlers in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/SyncImportExportTools.kt` supporting `format` in `"opencollection-yaml"` | `"opencollection-json"` | `"openapi-yaml"` | `"openapi-json"` and `action` in `"status"` | `"commit"` | `"pull"` | `"push"`
- [X] T028 [US5] Implement `importSpecification` (via `SpecificationImporter.default()`), `exportCollection` (via `OpenCollectionSerializer` and `OpenApiExporter` after `SecretSanitizer` redaction), and `gitSync` (via `GitRepositoryManager`) in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt` and register `SyncImportExportTools` in `ClapiMcpServer.kt`
- [X] T029 [P] [US5] Write unit tests in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/SyncImportExportToolsTest.kt` verifying OpenAPI 3.0/OpenCollection/Insomnia import, Swagger 2.0 diagnostic failure, sanitized export in all 4 formats, and Git status/commit operations

**Checkpoint**: Import, export, and Git sync tools are complete and independently testable.

---

## Phase 8: User Story 6 - Dual Operating Modes (Headless CLI & Desktop Embedded) and Guided Workflow Prompts (Priority: P3)

**Goal**: Run the Clapi MCP server either headlessly via `--mcp [--dir <path>]` over `stdio` (with zero non-JSON-RPC bytes on `stdout`) or embedded inside the Clapi Desktop app (`http://127.0.0.1:<port>/mcp`) with live `AppState` UI synchronization and Settings UI controls, plus built-in workflow prompts (`debug_api_failure`, `generate_api_tests`, `curl_to_clapi_request`).

**Independent Test**: Verify `McpPromptRegistry` returns structured prompt messages for all 3 prompts; verify `KtorMcpServerHost` serves Streamable HTTP (`/mcp`) and Legacy SSE (`/mcp/sse`); verify `AppStateWorkspaceBackend` mutates live `AppState` collections/requests/environments and persists to disk; and verify `--mcp` CLI mode handles stdio JSON-RPC cleanly without stdout banner corruption.

### Implementation for User Story 6

- [X] T030 [P] [US6] Implement `McpPromptRegistry` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/prompts/McpPromptRegistry.kt` providing `debug_api_failure(requestId)`, `generate_api_tests(requestId)`, and `curl_to_clapi_request(curlCommand, targetCollection, targetFolder?)` and wire into `ClapiMcpServer.kt`
- [X] T031 [P] [US6] Implement `StdioMcpServerRunner` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/transport/StdioMcpServerRunner.kt` capturing the original `System.out` stream for newline-delimited JSON-RPC responses and redirecting `System.setOut(System.err)` before workspace initialization
- [X] T032 [P] [US6] Implement `KtorMcpServerHost` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/transport/KtorMcpServerHost.kt` binding Ktor Netty strictly to `127.0.0.1` on the configured port and exposing `POST /mcp`, `GET /mcp`, `DELETE /mcp` (Streamable HTTP `2025-06-18` with `Mcp-Session-Id`) and `GET /mcp/sse`, `POST /mcp/messages` (Legacy SSE `2024-11-05`)
- [X] T033 [US6] Add `mcpServerEnabled: Boolean` (default `false`, storage key `"settings.mcpServerEnabled"`) and `mcpServerPort: Int` (default `3333`, storage key `"settings.mcpServerPort"`) to `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppSettings.kt` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/SettingsRepository.kt`
- [X] T034 [US6] Implement `AppStateWorkspaceBackend` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/mcp/server/AppStateWorkspaceBackend.kt` and `EmbeddedMcpServerController` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/mcp/server/EmbeddedMcpServerController.kt`, wiring server start/stop and disposal into `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt` so MCP tool mutations immediately update `AppState` and persist via `WorkspaceRepository`
- [X] T035 [US6] Create `McpServerSettingsSection.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/settings/sections/McpServerSettingsSection.kt` (enable toggle, port input, running status indicator, and copyable client configuration snippet), register `SettingsSection.MCP_SERVER` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/dialogs/settings/SettingsDialog.kt`, and add localization keys in `ui-shared/src/commonMain/resources/i18n/` (`en.json`, `de.json`, `es.json`, `fr.json`)
- [X] T036 [US6] Update `ui-desktop/src/desktopMain/kotlin/io/github/makeforge/clapi/ui/desktop/Main.kt` to intercept `--mcp` (and optional `--dir <path>`) at the start of `main(args)` before any AWT/Compose initialization and launch `StdioMcpServerRunner`
- [X] T037 [P] [US6] Write protocol, transport, and live `AppStateWorkspaceBackend` tests in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/ClapiMcpServerProtocolTest.kt` and `ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/mcp/server/AppStateWorkspaceBackendTest.kt`

**Checkpoint**: Both headless CLI stdio mode and Desktop embedded HTTP mode with live UI sync and prompts are complete and verified.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end QA integration testing, user documentation, and full build verification.

- [X] T038 [P] Create end-to-end integration test in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/McpServerE2ETest.kt` connecting Clapi's `McpClient` to `KtorMcpServerHost` and `StdioMcpServerRunner`, exercising multi-protocol request authoring, request execution against `DummyApiServer`, echo-endpoint secret scrubbing, JSONPath response inspection, test suite runner, and specification import/export
- [X] T039 [P] Write user documentation for Clapi MCP Server in `../../docs/automation/mcp-server.md` covering CLI `--mcp` setup, Desktop embedded HTTP mode setup, available resources, tools, prompts, and the zero-exfiltration security architecture
- [X] T040 Run full verification suite across all affected modules via `rtk gradlew :mcp-server:allTests :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:jvmTest` per `specs/029-mcp-server/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: No dependencies — starts immediately.
- **Phase 2: Foundational**: Depends on Phase 1 — **BLOCKS** all user stories.
- **Phase 3: User Story 1 (P1 - Workspace & Request Authoring)**: Depends on Phase 2.
- **Phase 4: User Story 2 (P1 - Execution, Test Suite & Response Inspection)**: Depends on Phase 2 (and uses request lookup from Phase 2/3).
- **Phase 5: User Story 3 (P1 - Zero-Exfiltration Security Pipeline)**: Depends on Phase 2; hardens all outputs from US1/US2/US4/US5.
- **Phase 6: User Story 4 (P2 - Environments & Scoped Typed Variables)**: Depends on Phase 2 and Phase 5 (`SecretSanitizer`). Can run in parallel with Phase 7.
- **Phase 7: User Story 5 (P2 - Import, Export & Git Sync)**: Depends on Phase 2 and Phase 5 (`SecretSanitizer`). Can run in parallel with Phase 6.
- **Phase 8: User Story 6 (P3 - Dual Transports, Live Desktop Sync & Prompts)**: Depends on Phases 3–7 for full tool/resource coverage in `AppStateWorkspaceBackend`.
- **Phase 9: Polish & Cross-Cutting Concerns**: Depends on Phases 3–8 completion.

### Parallel Opportunities

- **Within Phase 2 (Foundational)**: `T005` (`NodeLookupResolver.kt`), `T006` (`ResponseSnapshotCache.kt`), and `T009` (`NodeLookupResolverTest.kt`) can be developed in parallel once `T004` (`McpWorkspaceBackend.kt`) is defined.
- **Within Phase 3 (US1)**: `T010` (`WorkspaceTools.kt`) and `T011` (`RequestTools.kt`) operate on separate files and can be built in parallel.
- **Within Phase 4 (US2)**: `T015` (`JsonPathInspector.kt`) and `T016` (`ExecutionTools.kt`) can be built in parallel.
- **Across Phase 6 (US4) and Phase 7 (US5)**: `EnvironmentTools.kt` (`T023`–`T026`) and `SyncImportExportTools.kt` (`T027`–`T029`) can be implemented in parallel.
- **Within Phase 8 (US6)**: `T030` (`McpPromptRegistry.kt`), `T031` (`StdioMcpServerRunner.kt`), and `T032` (`KtorMcpServerHost.kt`) are in independent files and can be implemented in parallel.
- **Within Phase 9 (Polish)**: `T038` (`McpServerE2ETest.kt`) and `T039` (`../../docs/automation/mcp-server.md`) can run in parallel.

---

## Parallel Example: User Story 1 & User Story 2

```bash
# In Phase 3 (User Story 1), launch tool schema/handler tasks in parallel:
Task T010: "Implement WorkspaceTools in mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/WorkspaceTools.kt"
Task T011: "Implement RequestTools in mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/RequestTools.kt"

# In Phase 4 (User Story 2), launch JSONPath inspector and ExecutionTools in parallel:
Task T015: "Implement JsonPathInspector in mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/inspection/JsonPathInspector.kt"
Task T016: "Implement ExecutionTools in mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/ExecutionTools.kt"
```

---

## Implementation Strategy

### MVP First (Phases 1–5: P1 User Stories)

1. Complete **Phase 1: Setup** and **Phase 2: Foundational** (`:mcp-server` module, DTOs, `NodeLookupResolver`, `ClapiMcpServer`).
2. Complete **Phase 3: User Story 1** (Workspace tree, collection/folder management, multi-protocol request CRUD).
3. Complete **Phase 4: User Story 2** (Request execution, test suite runner, JSONPath/byte-range response inspection).
4. Complete **Phase 5: User Story 3** (Zero-exfiltration `SecretSanitizer` across all outputs and cached snapshots).
5. **Validate P1 Core**: Run `rtk gradlew :mcp-server:allTests` to verify all P1 stories and security invariants.

### Incremental Delivery (Phases 6–9: P2 & P3 User Stories)

6. Add **Phase 6: User Story 4** (Environments with inheritance validation + scoped typed/dynamic variables).
7. Add **Phase 7: User Story 5** (OpenAPI/OpenCollection/Insomnia import, sanitized export, and Git sync).
8. Add **Phase 8: User Story 6** (Stdio CLI `--mcp` entry point, Ktor embedded Desktop server, live `AppStateWorkspaceBackend`, Settings UI, and MCP Prompts).
9. Finish with **Phase 9: Polish** (`McpServerE2ETest` in `:qa-tests` and user documentation).
