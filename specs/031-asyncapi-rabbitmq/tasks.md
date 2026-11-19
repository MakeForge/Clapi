---

description: "Actionable, dependency-ordered task list for RabbitMQ Messaging (Publish/Subscribe) & RPC Requests"
---

# Tasks: RabbitMQ Messaging (Publish/Subscribe) & RPC Requests

**Input**: Design documents from `/specs/031-asyncapi-rabbitmq/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Unit, schema validation, MCP security, and Docker-backed RabbitMQ E2E tests are included as defined in `plan.md` and `quickstart.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`–`US7`)
- Exact file paths are included in every task description.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add AMQP 0-9-1 client dependency and RabbitMQ Docker Compose test fixture.

- [X] T001 Add `amqp-client = "5.37.1"` (`com.rabbitmq:amqp-client`) version and library alias to `gradle/libs.versions.toml`
- [X] T002 Add `implementation(libs.amqp.client)` to `desktopMain` dependencies in `core-network/build.gradle.kts`
- [X] T003 [P] Create RabbitMQ 4 Alpine Docker Compose service with healthcheck (`rabbitmq-diagnostics -q ping`) in `qa-tests/docker/docker-compose.rabbitmq.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core OpenCollection models, schema extension, serializer updates, and AMQP client abstraction required by all user stories.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] Create OpenCollection RabbitMQ models (`RabbitMqOperationMode`, `RabbitMqExchangeType`, `AmqpAckMode`, `RabbitMqReplyStrategy`, `RabbitMqConnectionConfig`, `RabbitMqExchangeConfig`, `RabbitMqQueueConfig`, `AmqpMessageProperties`, `RabbitMqRequestDetails`, `RabbitMqRequestSettings`) in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionRabbitMq.kt` with constraints (`port` in `1..65535`, `rpcTimeoutMs` clamped `100..300_000`, `prefetchCount` clamped `1..10_000`, `maxTimelineMessages` clamped `10..50_000`)
- [X] T005 [P] Create RabbitMQ runtime models (`RabbitMqConnectionStatus`, `AmqpAckStatus`, `RabbitMqDeliveryMessage`, `RabbitMqPublishResult`, `RabbitMqRpcResult`) in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/RabbitMqModels.kt`
- [X] T006 [P] Add `RequestKind.RABBITMQ` to `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/McpModels.kt`
- [X] T007 Add `ItemType.RABBITMQ("rabbitmq")`, `RabbitMqRequestInfo`, `RabbitMqRequestItem`, and `"rabbitmq"` / `ItemType.RABBITMQ` polymorphic branch in `OpenCollectionItemSerializer` in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/OpenCollectionItem.kt` (depends on T004)
- [X] T008 Extend OpenCollection JSON Schema with `$defs.RabbitMqRequest` (matching `contracts/opencollection-rabbitmq-contract.md`) and add `{"$ref": "#/$defs/RabbitMqRequest"}` to `$defs.Item.oneOf` in `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json`
- [X] T009 Update `OpenCollectionSerializer` (`KNOWN_RABBITMQ_REQUEST_KEYS`, serialization/deserialization, and extension preservation) in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/opencollection/OpenCollectionSerializer.kt` (depends on T007)
- [X] T010 [P] Update `OpenCollectionImporter` and `OpenApiExporter` to count/handle `RabbitMqRequestItem` cleanly in `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/OpenCollectionImporter.kt` and `core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/exporters/OpenApiExporter.kt` (depends on T007)
- [X] T011 [P] Define `RabbitMqClient`, `RabbitMqAuthCredentials`, `RabbitMqPublishRequest`, `RabbitMqSubscribeRequest`, `RabbitMqRpcRequest`, and `RabbitMqSubscriptionSession` interfaces/models in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqClient.kt` (depends on T004, T005)
- [X] T012 Implement `DesktopRabbitMqClient` connection/channel factory bootstrap (host, port, vhost, TLS/`amqps`, heartbeat, connectionTimeoutMs, SASL PLAIN/EXTERNAL, client certificate `SSLContext`) in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/DesktopRabbitMqClient.kt` (depends on T002, T011)

**Checkpoint**: Foundation ready — OpenCollection schema & models compile and validate; AMQP client abstraction is in place.

---

## Phase 3: User Story 1 - First-Class RabbitMQ Request Type in OpenCollection & Collection Sidebar (Priority: P1) 🎯 MVP

**Goal**: Users can create, identify, edit mode/metadata for, and persist `RabbitMqRequestItem` entries directly inside OpenCollection with dedicated sidebar/tab badges and full schema round-trip integrity.

**Independent Test**: Click `+` on any folder/collection, select "New RabbitMQ Request", verify sidebar & tab bar display the RabbitMQ mode badge (`PUB`/`SUB`/`RPC`), save/reload/export OpenCollection JSON & YAML, and verify schema validation and lossless round-trip.

### Tests for User Story 1

- [X] T013 [P] [US1] Add JSON/YAML serialization & polymorphic deserialization tests for `RabbitMqRequestItem` (Publish, Subscribe, RPC modes, variants, properties, settings, auth) in `core-model/src/commonTest/kotlin/io/github/makeforge/clapi/core/model/opencollection/RabbitMqSerializationTest.kt`
- [X] T014 [P] [US1] Add OpenCollection schema validation & `BundledCollectionStorageDriver` round-trip tests (JSON & YAML) for `RabbitMqRequestItem` in `core-storage/src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/opencollection/OpenCollectionRabbitMqStorageTest.kt`

### Implementation for User Story 1

- [X] T015 [P] [US1] Add RabbitMQ theme badge color (`MethodRabbitMq`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/theme/ClapiTheme.kt` and `RabbitMqBadge(mode)` (`PUB` / `SUB` / `RPC`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/MethodBadge.kt`
- [X] T016 [P] [US1] Add RabbitMQ UI labels and i18n keys (`New RabbitMQ Request`, mode labels, tab names, diagnostics) in `ui-shared/src/commonMain/resources/i18n/en.json`
- [X] T017 [US1] Add RabbitMQ tab & editor state fields (`rabbitMqMode`, `rabbitMqConnection`, `rabbitMqExchange`, `rabbitMqQueue`, `rabbitMqRoutingKey`, `rabbitMqReplyQueue`, `rabbitMqProperties`, `rabbitMqSettings`, `rabbitMqConnectionStatus`, `rabbitMqMessages`, `rabbitMqLastPublishResult`, `rabbitMqLastRpcResult`) to `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/RequestTabState.kt` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt`
- [X] T018 [US1] Implement bidirectional mapping between `RequestTabState` / `CollectionNode` and `RabbitMqRequestItem` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/OpenCollectionModelMapping.kt` and tab persistence in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/TabsRepository.kt` (depends on T017)
- [X] T019 [US1] Wire "New RabbitMQ Request" into sidebar add menus and render `RabbitMqBadge` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/CollectionTreeNodeMenus.kt`, `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/sidebar/CollectionTreeNode.kt`, and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestTabsBar.kt` (depends on T015, T017)

**Checkpoint**: User Story 1 complete — RabbitMQ requests can be created, displayed, switched between modes, and persisted/validated in OpenCollection.

---

## Phase 4: User Story 5 - Authentication & Auth Inheritance for RabbitMQ Connections (Priority: P1)

**Goal**: `RabbitMqRequestItem` inherits authentication from parent folders/collections (`Inherit`) and resolves `None`, `Basic`, `Bearer`, `OAuth2`, and TLS client certificate (`EXTERNAL`) credentials with variable and external secret manager interpolation.

**Independent Test**: Configure Basic Auth with a secret variable on a parent collection, set a child `RabbitMqRequestItem` to `Inherit`, resolve effective AMQP credentials, and verify PLAIN/OAuth2/EXTERNAL credential mapping works without exposing secrets.

### Tests for User Story 5

- [X] T020 [P] [US5] Add unit tests for folder/collection auth inheritance (`Inherit`, `None`, `Basic`, `Bearer`, `OAuth2`, TLS `EXTERNAL`) and variable/secret interpolation in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqAuthMapperTest.kt`

### Implementation for User Story 5

- [X] T021 [US5] Implement `RabbitMqAuthMapper` (resolving `Inherit` via parent folder/collection chain, mapping `Basic` -> PLAIN, `Bearer`/`OAuth2` -> RabbitMQ JWT password convention, `useTlsClientCert` -> `EXTERNAL` with collection client certificates) in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqAuthMapper.kt` (depends on T011)
- [X] T022 [US5] Integrate `RabbitMqAuthMapper` and `RequestExecutionService.resolveSecrets` / `VariableResolver` into `RabbitMqRequestExecutor` in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqRequestExecutor.kt` (depends on T021)

**Checkpoint**: User Story 5 complete — all RabbitMQ operations share Clapi's full auth inheritance and secret resolution pipeline.

---

## Phase 5: User Story 2 - Publish Messages to RabbitMQ (Priority: P1)

**Goal**: Users can configure and publish messages to RabbitMQ exchanges/routing keys (or queues) with variable interpolation, message variants, AMQP properties/headers, optional topology auto-declaration, publisher confirms, and mandatory return feedback.

**Independent Test**: Configure a Publish `RabbitMqRequestItem` with variables in routing key and JSON payload, custom AMQP headers, and publisher confirms enabled; execute Publish and verify broker delivery confirmation, timing, and mandatory return handling.

### Tests for User Story 2

- [X] T023 [P] [US2] Add unit tests for RabbitMQ Publish (variable interpolation, message variants, AMQP BasicProperties, custom headers, publisher confirms, mandatory unroutable return) using a fake `RabbitMqClient` in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqPublishExecutorTest.kt`

### Implementation for User Story 2

- [X] T024 [US2] Implement `DesktopRabbitMqClient.publish(...)` (optional exchange/queue declaration when `declareTopology = true`, `AMQP.BasicProperties` + headers, `confirmSelect`/`waitForConfirmsOrDie` when `publisherConfirms = true`, `mandatory` + `ReturnListener` capture) in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/DesktopRabbitMqClient.kt` (depends on T012)
- [X] T025 [US2] Implement `RabbitMqRequestExecutor.publish(...)` (inline validation for non-blank resolved host, variable/secret interpolation across host/vhost/exchange/routingKey/headers/properties/body, active variant selection) in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqRequestExecutor.kt` (depends on T022, T024)
- [X] T026 [P] [US2] Create RabbitMQ editor sub-components: `RabbitMqConnectionBar.kt` (mode selector `PUB`/`SUB`/`RPC`, host, port, vhost, TLS toggle, primary action button), `RabbitMqAmqpTab.kt` (exchange name/type/durable/autoDelete, queue, routing key, reply queue), and `RabbitMqHeadersPropsTab.kt` (AMQP BasicProperties + custom headers) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/rabbitmq/`
- [X] T027 [US2] Create `RabbitMqRequestEditor.kt` (integrating ConnectionBar, Message/Variants tab, AMQP tab, Headers & Properties tab, Auth tab, Scripts tab, Settings tab, Docs tab, and Publish confirmation result card) and wire `RequestKind.RABBITMQ` routing in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestEditor.kt` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/MainScreen.kt` (depends on T017, T025, T026)

**Checkpoint**: User Story 2 complete — RabbitMQ Publish works end-to-end from the Desktop UI and core execution layer.

---

## Phase 6: User Story 3 - Subscribe and Consume Messages from RabbitMQ in Real Time (Priority: P1)

**Goal**: Users can subscribe to a named RabbitMQ queue or bind an exclusive/auto-delete queue to an exchange and routing pattern, observe incoming messages in a real-time chronological timeline, filter/clear messages, inspect binary payloads safely, and perform auto or manual ack/nack/requeue.

**Independent Test**: Start a Subscribe session on a queue/exchange binding, publish messages, verify real-time timeline updates with headers/properties/payload, test filtering/clearing and manual Ack/Nack/Requeue, switch tabs verifying background accumulation, and click Unsubscribe to close cleanly.

### Tests for User Story 3

- [X] T028 [P] [US3] Add unit tests for RabbitMQ Subscribe session lifecycle (connecting/connected/disconnected/error states, bounded ring-buffer cap `maxTimelineMessages`, binary payload detection, manual ack/nack/requeue) in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqSubscribeExecutorTest.kt`

### Implementation for User Story 3

- [X] T029 [US3] Implement `DesktopRabbitMqClient.subscribe(...)` (named queue or exclusive auto-delete queue bound to exchange+routingKey, `basicQos(prefetchCount)`, `DeliverCallback`/`CancelCallback`/`ShutdownListener` bridged to Kotlin `Flow<RabbitMqDeliveryMessage>`, plus `ack(deliveryTag)`, `nack(deliveryTag, requeue)`, and clean `close()`) in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/DesktopRabbitMqClient.kt` (depends on T012)
- [X] T030 [US3] Add Subscribe session management (`startSubscription`, `stopSubscription`, `ackMessage`, `nackMessage`, bounded ring-buffer enforcing `maxTimelineMessages` clamped `10..50_000`, active-session tab indicator) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt` and `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutor.kt` (depends on T029)
- [X] T031 [US3] Create `RabbitMqTimelinePane.kt` (status pill, filter by text/routingKey/header, clear button, expandable message rows with exchange/routingKey/timestamp/size/properties/headers, manual Ack/Nack/Requeue buttons, binary hex/base64 viewer, copy button) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/rabbitmq/RabbitMqTimelinePane.kt` and integrate into `RabbitMqRequestEditor.kt` (depends on T027, T030)

**Checkpoint**: User Story 3 complete — live RabbitMQ Subscribe and manual acknowledgment work end-to-end.

---

## Phase 7: User Story 4 - RPC (Request-Reply) Over RabbitMQ (Priority: P1)

**Goal**: Users can send RPC requests over RabbitMQ with automatic `correlation_id` and `reply_to` handling (Direct Reply-to default, temporary exclusive queue, or named reply queue), correlate replies accurately, enforce configurable timeouts, and inspect the reply payload, headers, properties, and latency.

**Independent Test**: Execute an RPC request against a responder using Direct Reply-to and named reply queue strategies; verify `correlation_id` matching, unrelated message ignoring, timeout error diagnostic, and mid-flight broker disconnect fast-fail.

### Tests for User Story 4

- [X] T032 [P] [US4] Add unit tests for RabbitMQ RPC correlation (Direct Reply-to, temporary queue, named queue validation requiring non-blank `replyQueue`, auto-UUID `correlationId`, mismatched `correlationId` ignored, timeout cleanup, mid-flight disconnect fast-fail) in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqRpcExecutorTest.kt`

### Implementation for User Story 4

- [X] T033 [US4] Implement `DesktopRabbitMqClient.rpc(...)` (prepare reply consumer before publish for `DIRECT_REPLY_TO` `amq.rabbitmq.reply-to`, `TEMPORARY_QUEUE`, or `NAMED_QUEUE`; auto-generate UUID `correlationId` if blank; await matching `CompletableDeferred` under `withTimeout(rpcTimeoutMs)`; fail fast on `ShutdownListener`; clean up consumer/channel in `finally`) in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/DesktopRabbitMqClient.kt` (depends on T012)
- [X] T034 [US4] Implement `RabbitMqRequestExecutor.rpc(...)` (validation for `NAMED_QUEUE` requiring non-blank `replyQueue`, clamping `rpcTimeoutMs` to `100..300_000`, variable interpolation, timing metrics) in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqRequestExecutor.kt` (depends on T022, T033)
- [X] T035 [US4] Create `RabbitMqRpcReplyPane.kt` (formatted reply body, headers, AMQP properties, `correlationId`, `replyTo`, round-trip latency, timeout/error display, binary viewer) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/rabbitmq/RabbitMqRpcReplyPane.kt` and integrate into `RabbitMqRequestEditor.kt` and `RequestExecutor.kt` (depends on T027, T034)

**Checkpoint**: User Story 4 complete — all three RabbitMQ modes (Publish, Subscribe, RPC) work end-to-end in the Desktop UI.

---

## Phase 8: User Story 6 - MCP Server Tools & Resources for RabbitMQ Requests (Priority: P2)

**Goal**: AI assistants connected via Clapi's MCP server can discover, author, export, execute (Publish & RPC), and perform bounded Subscribe sampling (`clapi_subscribe_rabbitmq`) on `RabbitMqRequestItem` requests with strict `SecretSanitizer` redaction.

**Independent Test**: Connect an MCP client, list tree, upsert a RabbitMQ Publish/Subscribe/RPC request, inspect sanitized request details, execute Publish and RPC via `clapi_execute_request`, run `clapi_subscribe_rabbitmq` with `maxMessages` and `timeoutMs`, and verify all credentials and resolved secrets are masked/redacted.

### Tests for User Story 6

- [X] T036 [P] [US6] Add MCP unit tests covering `clapi_list_tree`, `clapi_get_request`, `clapi_upsert_request` (`kind: "rabbitmq"`), `clapi_execute_request` (Publish, RPC, and Subscribe rejection guidance), `clapi_subscribe_rabbitmq` (clamping `maxMessages` `1..100` & `timeoutMs` `250..30_000`, byte-budget truncation), and `SecretSanitizer` zero-exfiltration redaction in `mcp-server/src/commonTest/kotlin/io/github/makeforge/clapi/mcp/server/RabbitMqMcpToolsTest.kt`

### Implementation for User Story 6

- [X] T037 [US6] Add RabbitMQ DTOs (`RabbitMqRequestConfigDto`, `SubscribeRabbitMqInput`, `SubscribeRabbitMqResultDto`, `SanitizedRabbitMqMessageDto`) and `subscribeRabbitMq(...)` method to `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/McpWorkspaceBackend.kt`
- [X] T038 [US6] Extend `SecretSanitizer` to mask RabbitMQ auth credentials, sensitive AMQP headers, consumed message bodies, and broker error messages in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/security/SecretSanitizer.kt`
- [X] T039 [US6] Implement RabbitMQ tree projection (`PUB`/`SUB`/`RPC`), `getRequest`, `upsertRequest`, `executeRequest` (Publish & RPC with response snapshot caching for `clapi_inspect_response`), and bounded `subscribeRabbitMq` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt` (depends on T025, T029, T034, T037, T038)
- [X] T040 [P] [US6] Extend `clapi_get_request` and `clapi_upsert_request` input/output schemas for `kind: "rabbitmq"` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/RequestTools.kt` (depends on T037)
- [X] T041 [P] [US6] Extend `clapi_execute_request` and `clapi_run_test_suite`, and add `clapi_subscribe_rabbitmq` tool definition and handler in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/tools/ExecutionTools.kt` (depends on T037)
- [X] T042 [US6] Wire Desktop embedded MCP backend (`DesktopMcpWorkspaceBackend` or UI MCP adapter if present) and verify `ClapiMcpServer` dispatches `clapi_subscribe_rabbitmq` in `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/ClapiMcpServer.kt` (depends on T039, T040, T041)

**Checkpoint**: User Story 6 complete — MCP server supports full RabbitMQ authoring, execution, bounded subscribe, and zero-exfiltration redaction.

---

## Phase 9: User Story 7 - Scripts, Test Assertions & Collection Test Suite Integration (Priority: P3)

**Goal**: Publish and RPC `RabbitMqRequestItem` requests execute pre-request scripts, evaluate post-request test assertions against publish confirmation or RPC reply, and run sequentially inside collection/folder test suites (skipping indefinite Subscribe items with an informational status).

**Independent Test**: Run a folder test suite containing HTTP + RabbitMQ Publish + RabbitMQ RPC (with post-request assertion on reply JSON) + RabbitMQ Subscribe; verify Publish & RPC execute in sequence with assertion results and Subscribe is reported as `SKIPPED`.

### Tests for User Story 7

- [X] T043 [P] [US7] Add unit tests for RabbitMQ pre-request scripts, post-request assertions on Publish confirmation and RPC reply, and mixed-protocol test suite execution (skipping Subscribe) in `core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/RabbitMqScriptingAndSuiteTest.kt`

### Implementation for User Story 7

- [X] T044 [US7] Integrate `RabbitMqRequestItem` (Publish & RPC) into `RequestExecutionService` with pre-request script execution, variable mutation propagation, and post-request test assertion evaluation against Publish/RPC results in `core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/execution/RequestExecutionService.kt` (depends on T025, T034)
- [X] T045 [US7] Update UI test suite runner (`RequestExecutor.kt`) and MCP `HeadlessWorkspaceBackend.runTestSuite` to execute RabbitMQ Publish & RPC items sequentially and report Subscribe items as `SKIPPED` (`"Indefinite RabbitMQ subscription skipped in test suite"`) in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/executor/RequestExecutor.kt` and `mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/backend/HeadlessWorkspaceBackend.kt` (depends on T044)

**Checkpoint**: User Story 7 complete — RabbitMQ Publish & RPC participate fully in Clapi's scripting and test suite automation.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end real-broker verification, full test suite validation, and documentation/cleanup.

- [X] T046 Implement real-broker end-to-end tests (Publish + Subscribe over topic exchange, manual Ack/Nack/Requeue, Direct Reply-to RPC, Named-Queue RPC, RPC timeout, inherited Basic Auth, OpenCollection round-trip, and MCP `clapi_execute_request` + `clapi_subscribe_rabbitmq`) in `qa-tests/src/test/kotlin/io/github/makeforge/clapi/rabbitmq/RabbitMqEndToEndTest.kt` (depends on T003, T024, T029, T033, T042, T045)
- [X] T047 Run unit and contract test verification via `rtk gradlew :core-model:desktopTest :core-storage:desktopTest :core-network:desktopTest :mcp-server:desktopTest :ui-shared:compileKotlinDesktop`
- [X] T048 Run real-broker RabbitMQ E2E tests via `rtk gradlew :qa-tests:jvmTest --tests "*RabbitMq*"` and verify all scenarios in `specs/031-asyncapi-rabbitmq/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)** → **Phase 2 (Foundational)** → **Phases 3–9 (User Stories)** → **Phase 10 (Polish & E2E)**
- **Phase 2** blocks all user story phases.
- **US1 (Phase 3)** and **US5 (Phase 4)** can proceed in parallel right after Phase 2.
- **US2 (Phase 5)**, **US3 (Phase 6)**, and **US4 (Phase 7)** depend on Phase 2 + US5 (`RabbitMqAuthMapper`) for broker execution, and share UI editor integration with US1.
- **US6 (Phase 8)** depends on core execution from US2, US3, and US4.
- **US7 (Phase 9)** depends on US2 and US4 execution (`RabbitMqRequestExecutor.publish` & `rpc`).

### Parallel Opportunities
- **Phase 2**: T004, T005, T006, T008 can run in parallel; T010 and T011 can run in parallel once T007 finishes.
- **Phase 3 (US1)**: T013, T014, T015, T016 can all run in parallel.
- **Phases 5–7**: Core AMQP tests (T023, T028, T032) and UI sub-components (T026, T031, T035) can be built in parallel.
- **Phase 8 (US6)**: T036, T040, T041 can run in parallel once T037 is defined.

## Implementation Strategy

### MVP First (US1 + US5 + US2)
1. Complete Phase 1 (Setup) + Phase 2 (Foundational).
2. Complete Phase 3 (US1 — OpenCollection schema, persistence, sidebar/tab badges) + Phase 4 (US5 — Auth inheritance) + Phase 5 (US2 — RabbitMQ Publish).
3. **STOP and VALIDATE**: Create and publish a RabbitMQ message from Clapi with inherited auth and OpenCollection round-trip persistence.

### Incremental Delivery
4. Add Phase 6 (US3 — Live Subscribe timeline + manual Ack/Nack).
5. Add Phase 7 (US4 — RPC over RabbitMQ with Direct Reply-to / temporary / named reply queues).
6. Add Phase 8 (US6 — MCP Server tools & bounded `clapi_subscribe_rabbitmq`).
7. Add Phase 9 (US7 — Scripts, assertions & test suite integration) + Phase 10 (Docker RabbitMQ E2E & Polish).
