# Implementation Plan: RabbitMQ Messaging (Publish/Subscribe) & RPC Requests

**Branch**: `031-asyncapi-rabbitmq` | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/031-asyncapi-rabbitmq/spec.md`

## Summary

Add first-class RabbitMQ messaging support (Publish, Subscribe, and RPC request-reply over AMQP 0-9-1) directly into Clapi's OpenCollection schema as a new `RabbitMqRequestItem` (`type: "rabbitmq"`), without any companion AsyncAPI file (AsyncAPI import/export is deferred to a separate future feature). Reuse Clapi's existing authentication hierarchy (`Inherit`, `None`, `Basic`, `Bearer`, `OAuth2`, plus TLS client certificates for AMQP `EXTERNAL`), integrate RabbitMQ execution into `core-network` (via `com.rabbitmq:amqp-client`) and `RequestExecutionService` (including scripts, assertions, and test suites), expose a dedicated Compose Desktop RabbitMQ editor in `ui-shared`, and extend Clapi's MCP server (`mcp-server`) with RabbitMQ authoring, execution, bounded subscription (`clapi_subscribe_rabbitmq`), and strict zero-exfiltration secret redaction.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (KMP single `jvm("desktop")` target), JDK 25  
**Primary Dependencies**: Compose Multiplatform 1.12.0, Ktor 3.6.1, Kotlinx Coroutines 1.10.2, Kotlinx Serialization 1.8.1, `kaml` 0.104.0, `networknt` JSON Schema Validator 3.0.9, `com.rabbitmq:amqp-client:5.37.1` (new in `core-network` `desktopMain`), Javet 6.0.3 (scripts)  
**Storage**: OpenCollection v1.0.0 JSON/YAML files via `BundledCollectionStorageDriver` & `OpenCollectionSerializer` (local workspace + Git-synced collections); secrets/credentials remain in KSafe / external secret managers  
**Testing**: `kotlin-test`, `kotlinx-coroutines-test`, JUnit 4, mocked `RabbitMqClient` unit tests in core/mcp modules, and real RabbitMQ 4 Docker Compose integration tests in `qa-tests`  
**Target Platform**: Desktop JVM (Linux, macOS, Windows) + Headless CLI MCP server (`--mcp`)  
**Project Type**: Multi-module Kotlin Multiplatform Desktop API Client + Embedded/CLI MCP Server  
**Performance Goals**: Local Publish-to-Subscribe delivery visible in UI < 1s; RPC client-side overhead < 100ms; responsive UI under high-throughput bursts via bounded timeline ring-buffer and `basic.qos` prefetch  
**Constraints**: Zero plaintext secret exfiltration in MCP outputs/logs; strict OpenCollection schema validation (`opencollection_v1.0.0.json`); bounded MCP subscription calls (never hang indefinitely)  
**Scale/Scope**: 6 Gradle modules touched (`core-model`, `core-storage`, `core-network`, `mcp-server`, `ui-shared`, `qa-tests`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` currently contains the default template placeholders (no ratified project-specific constraints beyond `DEVELOPMENT.md` and `.github/copilot-instructions.md`). We enforce Clapi's established architectural principles:
- **Multi-module separation**: Domain models in `core-model`, serialization/schema in `core-storage`, AMQP transport & execution in `core-network`, MCP tools in `mcp-server`, Compose UI in `ui-shared`, Docker-backed E2E in `qa-tests`.
- **Schema & Round-Trip Integrity**: `opencollection_v1.0.0.json` and `OpenCollectionSerializer` stay in sync; no untyped hacks.
- **Zero-Exfiltration Security**: All MCP responses/logs pass through `SecretSanitizer`.
- **Testability**: `RabbitMqClient` is abstracted behind an interface in `core-network` so unit tests run fast without Docker, while `qa-tests` validates against a real RabbitMQ broker.

*Post-Design Re-Check*: All Phase 1 artifacts (`data-model.md`, `contracts/*`, `quickstart.md`) satisfy these gates with zero violations.

## Project Structure

### Documentation (this feature)

```text
specs/031-asyncapi-rabbitmq/
├── plan.md                                      # This file
├── research.md                                  # Phase 0 decisions & rationale
├── data-model.md                                # Phase 1 OpenCollection & runtime models
├── quickstart.md                                # Phase 1 validation & test guide
├── contracts/
│   ├── opencollection-rabbitmq-contract.md      # OpenCollection schema & YAML examples
│   └── mcp-rabbitmq-tools-contract.md           # MCP tool & resource contracts
└── checklists/
    └── requirements.md                          # Spec quality checklist
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                        # Add amqp-client = "5.37.1"

core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/
├── opencollection/
│   ├── OpenCollectionItem.kt                    # Add ItemType.RABBITMQ, RabbitMqRequestItem & serializer branch
│   └── OpenCollectionRabbitMq.kt                # RabbitMqOperationMode, Connection/Exchange/Queue/Properties/Details/Settings
├── RabbitMqModels.kt                            # Runtime models: RabbitMqConnectionStatus, RabbitMqDeliveryMessage, Publish/Rpc results
└── RequestKind.kt (or existing enum)            # Add RequestKind.RABBITMQ

core-storage/src/commonMain/
├── resources/opencollection/
│   └── opencollection_v1.0.0.json               # Add $defs.RabbitMqRequest & Item.oneOf entry
└── kotlin/io/github/makeforge/clapi/core/storage/
    ├── opencollection/OpenCollectionSerializer.kt # Serialize/deserialize RabbitMqRequestItem + extensions
    ├── importers/OpenCollectionImporter.kt      # Count/handle RabbitMqRequestItem
    └── exporters/OpenApiExporter.kt             # Safely skip RabbitMqRequestItem when exporting OpenAPI

core-network/
├── build.gradle.kts                             # Add libs.amqp.client in desktopMain
└── src/
    ├── commonMain/kotlin/io/github/makeforge/clapi/core/network/
    │   ├── rabbitmq/
    │   │   ├── RabbitMqClient.kt                # Interface + RabbitMqAuthCredentials + SubscribeSession handle
    │   │   ├── RabbitMqAuthMapper.kt            # Maps Clapi AuthConfig (Inherit/Basic/Bearer/OAuth2/External) to AMQP auth
    │   │   └── RabbitMqRequestExecutor.kt       # Variable/secret resolution + Publish, Subscribe, RPC orchestration
    │   └── execution/RequestExecutionService.kt # Dispatch RabbitMqRequestItem (Publish & RPC) + scripts/assertions
    └── desktopMain/kotlin/io/github/makeforge/clapi/core/network/rabbitmq/
        └── DesktopRabbitMqClient.kt             # com.rabbitmq:amqp-client implementation (Publish, Subscribe, Direct/Temp/Named RPC)

mcp-server/src/commonMain/kotlin/io/github/makeforge/clapi/mcp/server/
├── backend/
│   ├── McpWorkspaceBackend.kt                   # Add RabbitMq DTOs, subscribeRabbitMq(), extend upsert/execute/suite
│   └── HeadlessWorkspaceBackend.kt              # Implement RabbitMq authoring, Publish/RPC execution, bounded subscribe
├── security/SecretSanitizer.kt                  # Sanitize RabbitMqRequestItem, AMQP headers, messages, errors
├── tools/
│   ├── RequestTools.kt                          # Extend clapi_get_request & clapi_upsert_request with kind="rabbitmq"
│   ├── ExecutionTools.kt                        # Extend clapi_execute_request & clapi_run_test_suite; add clapi_subscribe_rabbitmq
│   └── WorkspaceTools.kt                        # Tree badge/mode for rabbitmq
└── ClapiMcpServer.kt                            # Register clapi_subscribe_rabbitmq

ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
├── state/                                       # RabbitMq editor state, live subscription sessions, timeline ring-buffer
├── persistence/OpenCollectionModelMapping.kt    # Bidirectional mapping between UI state and RabbitMqRequestItem
├── theme/                                       # Distinct RabbitMQ badge color (orange/amber)
└── components/
    ├── Sidebar / AddMenu / TabBar               # "New RabbitMQ Request" + PUB/SUB/RPC badge
    └── rabbitmq/                                # RabbitMqRequestEditor, ConnectionBar, MessageTab, AmqpTab, HeadersPropsTab, TimelinePane, RpcReplyPane

qa-tests/
├── docker/docker-compose.rabbitmq.yml           # RabbitMQ 4 Alpine service for automated DockerComposeTask
└── src/test/kotlin/io/github/makeforge/clapi/
    └── rabbitmq/RabbitMqEndToEndTest.kt         # Real-broker Publish, Subscribe, Manual Ack, Direct/Named RPC, Auth, MCP tests
```

**Structure Decision**: Follows Clapi's existing 6-module KMP Desktop layout (`core-model` -> `core-storage` / `core-network` -> `mcp-server` / `ui-shared` -> `qa-tests`) with no new Gradle modules required.

## Complexity Tracking

No constitution violations; no extra complexity justifications required.
