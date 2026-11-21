# Research & Technical Decisions: RabbitMQ Messaging (Publish/Subscribe) & RPC Requests

**Feature**: `031-asyncapi-rabbitmq` | **Date**: 2026-11-20

## 1. AMQP 0-9-1 Client Library for RabbitMQ on JVM 25

- **Decision**: Use the official RabbitMQ Java Client `com.rabbitmq:amqp-client:5.37.1` in `core-network` (`desktopMain`).
- **Rationale**:
  - Official, actively maintained RabbitMQ AMQP 0-9-1 client with full support for exchanges, queues, routing keys, publisher confirms (`confirmSelect` / `waitForConfirmsOrDie`), `mandatory` + `ReturnListener`, `basic.qos` prefetch, manual `basicAck`/`basicNack` with requeue, Direct Reply-to (`amq.rabbitmq.reply-to`), TLS/SSL (`amqps://`), SASL `PLAIN` and `EXTERNAL` (X.509 client certificates), and RabbitMQ OAuth 2.0 JWT authentication.
  - Zero heavy transitive dependencies (only `slf4j-api`, which Clapi already bridges to `tinylog` via `slf4j-tinylog`).
  - Fully compatible with JDK 25 and Kotlin coroutines (wrapping blocking broker calls on `Dispatchers.IO` and bridging `DeliverCallback` / `CancelCallback` to Kotlin `Flow` / `Channel`).
- **Alternatives Considered**:
  - *Raw TCP/AMQP 0-9-1 implementation on Ktor sockets*: Rejected due to high protocol complexity (framing, heartbeats, channel multiplexing, SASL, confirms, recovery) and maintenance burden.
  - *Spring AMQP*: Rejected due to massive Spring dependency tree incompatible with Clapi's lightweight KMP Desktop footprint.
  - *Community Kotlin AMQP clients*: Rejected due to smaller ecosystem maturity and incomplete support for Direct Reply-to, SASL EXTERNAL, and publisher confirms.

## 2. OpenCollection Schema Extension (`RabbitMqRequestItem`)

- **Decision**: Add `ItemType.RABBITMQ` (`"rabbitmq"`) and `RabbitMqRequestItem` directly to `core-model` (`OpenCollectionItem` sealed hierarchy), extend `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json` with a new `$defs.RabbitMqRequest` definition referenced in `$defs.Item.oneOf`, and update `OpenCollectionSerializer` + `OpenCollectionItemSerializer`.
- **Rationale**:
  - Self-contained storage inside OpenCollection matches Clapi's exact pattern for `HttpRequestItem`, `GrpcRequestItem`, `GraphQLRequestItem`, and `WebSocketRequestItem`.
  - Eliminates multi-file reference synchronization, broken-reference states, and sidecar file management across local storage, exports, and Git sync.
  - Keeps the door wide open for a future AsyncAPI import/export feature that maps cleanly between AsyncAPI 3.0 AMQP bindings and `RabbitMqRequestItem` fields.
- **Alternatives Considered**:
  - *Companion AsyncAPI document next to OpenCollection*: Explicitly rejected by user decision in favor of direct OpenCollection storage and keeping AsyncAPI import/export as a separate future feature.
  - *Storing RabbitMQ data inside `HttpRequestItem` extensions*: Rejected; breaks first-class protocol modeling, UI badges, schema validation, and MCP authoring.

## 3. RabbitMQ Authentication & Inheritance Mapping

- **Decision**: Reuse Clapi's existing `AuthConfig` hierarchy (`Inherit`, `None`, `Basic`, `Bearer`, `OAuth2`) plus collection-level `ClientCertificate` (mapped to AMQP TLS `EXTERNAL` SASL when enabled), resolving `Inherit` by walking parent folders to the collection root and interpolating variables/external secrets at execution time.
- **Rationale**:
  - Maps cleanly onto RabbitMQ's supported authentication backends:
    - `None`: default/anonymous credentials (`guest`/`guest` or blank credentials).
    - `Basic`: AMQP `PLAIN` SASL (`username` + `password`).
    - `Bearer` / `OAuth2`: RabbitMQ `rabbitmq_auth_backend_oauth2` convention—sets the resolved JWT/access token as the AMQP `password` and uses optional configured `username` (defaulting to `""`).
    - `TLS Client Certificate (EXTERNAL)`: configures `ConnectionFactory.setSaslConfig(DefaultSaslConfig.EXTERNAL)` with the collection/request TLS `SSLContext` over `amqps://`.
- **Alternatives Considered**:
  - *Creating a separate RabbitMQ-only credential store*: Rejected; breaks auth inheritance from folders/collections and duplicates secret manager integration.

## 4. RabbitMQ RPC (Request-Reply) Correlation Strategy

- **Decision**: Support three reply strategies in `RabbitMqRequestSettings.replyStrategy`:
  1. `direct_reply_to` (default): consumes from RabbitMQ's pseudo-queue `amq.rabbitmq.reply-to` with `autoAck = true` *before* publishing, and sets `replyTo = "amq.rabbitmq.reply-to"`.
  2. `temporary_queue`: declares a server-named exclusive auto-delete queue, starts consuming, and sets `replyTo` to that queue name.
  3. `named_queue`: consumes from the user-configured `replyQueue` and filters deliveries by `correlationId`.
- **Rationale**:
  - RabbitMQ's official RPC best practice is Direct Reply-to (zero extra queue declaration overhead, lowest latency), while some services explicitly use temporary or named reply queues. Supporting all three covers 100% of real-world RabbitMQ RPC setups.
  - A fresh UUID `correlationId` is generated automatically if the user does not supply one; a `CompletableDeferred<RabbitMqDeliveryMessage>` awaits the matching delivery under `withTimeout(rpcTimeoutMs)` and cleans up the consumer in a `finally` block (also failing fast if the AMQP `ShutdownListener` triggers mid-flight).

## 5. MCP Server Integration & Bounded Subscription

- **Decision**: Extend existing MCP tools (`clapi_list_tree`, `clapi_get_request`, `clapi_upsert_request`, `clapi_execute_request`, `clapi_run_test_suite`, `clapi_export_collection`) to support `kind = "rabbitmq"`, and add one dedicated bounded subscription tool `clapi_subscribe_rabbitmq` (`maxMessages` default 10 / clamped `1..100`, `timeoutMs` default 5000 / clamped `250..30000`).
- **Rationale**:
  - Matches existing MCP conventions in `mcp-server` (unified `clapi_upsert_request` / `clapi_execute_request` for single-shot Publish and RPC, plus a dedicated bounded `clapi_subscribe_rabbitmq` tool so LLM tool calls never hang indefinitely).
  - All payloads, AMQP headers, properties, and broker errors pass through `SecretSanitizer` before returning to MCP clients.

## 6. Integration Testing Strategy with RabbitMQ

- **Decision**: Add `qa-tests/docker/docker-compose.rabbitmq.yml` running `rabbitmq:4-alpine` (with healthcheck `rabbitmq-diagnostics -q ping`) to plug directly into `qa-tests`'s existing `DockerComposeTask`, plus unit/contract tests in `core-model`, `core-storage`, `core-network`, and `mcp-server` using fake/mocked `RabbitMqClient` implementations that run without Docker.
- **Rationale**:
  - `qa-tests/build.gradle.kts` already auto-discovers `qa-tests/docker/*compose*.y*ml` and manages `docker compose up -d --wait` / `down -v`. Adding a RabbitMQ compose file gives deterministic end-to-end verification of real AMQP Publish, Subscribe, Direct Reply-to RPC, Named-Queue RPC, and Basic Auth without slowing down fast unit tests.
