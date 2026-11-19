# Feature Specification: RabbitMQ Messaging (Publish/Subscribe) & RPC Requests

**Feature Branch**: `031-asyncapi-rabbitmq`

**Created**: 2026-11-20

**Status**: Draft

**Input**: User description: "Add RabbitMQ message publisher/subscribers + RPC over RabbitMQ. Add a new OpenCollectionItem type/request type for RabbitMQ publish/subscriber/RPC types and extend the OpenCollection schema to store all needed data for RabbitMQ types directly in it (do not store an AsyncAPI document next to OpenCollection; AsyncAPI import/export will be a separate future feature). Handle authentication (inherit auth etc.). Add/adjust appropriate MCP Server tools if needed."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First-Class RabbitMQ Request Type in OpenCollection & Collection Sidebar (Priority: P1)

As an API developer organizing multi-protocol collections in Clapi, I want RabbitMQ requests (supporting Publish, Subscribe, and RPC modes) to be a first-class request type (`RabbitMqRequestItem`) directly inside OpenCollection—just like HTTP, gRPC, GraphQL, and WebSocket requests—so that all RabbitMQ connection, routing, payload, auth, and execution settings live self-contained inside my collection and round-trip cleanly across saves, exports, and Git sync.

**Why this priority**: First-class modeling in OpenCollection and discoverability in the collection tree are the foundation for the entire RabbitMQ workflow.

**Independent Test**: Click "+" on any folder or collection in the sidebar, select "New RabbitMQ Request", verify a new `RabbitMqRequestItem` appears with a distinct RabbitMQ badge, configure broker connection and routing settings, save the collection, export/reload the OpenCollection file, and verify all RabbitMQ fields persist cleanly inside the OpenCollection document and pass schema validation.

**Acceptance Scenarios**:

1. **Given** a collection folder or collection root with an add action button ("+"), **When** the user clicks "+", **Then** the menu includes "New RabbitMQ Request" alongside HTTP, gRPC, GraphQL, and WebSocket options.
2. **Given** a RabbitMQ request in the sidebar tree or tab bar, **When** rendered, **Then** it displays its name and a distinct RabbitMQ badge indicating its mode (`PUB`, `SUB`, or `RPC`) styled with a dedicated theme color.
3. **Given** a collection containing one or more `RabbitMqRequestItem` entries, **When** saved, exported, imported, or Git-synced as OpenCollection JSON/YAML, **Then** all RabbitMQ connection, topology/routing, properties, headers, message/variants, auth, runtime scripts/variables, settings, and docs serialize directly inside the OpenCollection item and pass OpenCollection schema validation without any external sidecar document.
4. **Given** a user switches a RabbitMQ request's mode between Publish, Subscribe, and RPC in the editor, **When** switching modes, **Then** the editor reveals mode-appropriate controls (routing key/publisher confirms for Publish, queue/binding/ack controls for Subscribe, reply strategy/timeout for RPC) while preserving shared connection, auth, and payload configuration.

---

### User Story 2 - Publish Messages to RabbitMQ (Priority: P1)

As a developer testing event-driven services, I want to configure and publish messages to a RabbitMQ exchange (with routing key) or queue, compose payloads with variable interpolation and message variants, configure AMQP message properties and custom headers, and receive clear confirmation of publication.

**Why this priority**: Publishing events and commands to RabbitMQ is the primary day-to-day testing action for asynchronous services and delivers immediate standalone value.

**Independent Test**: Create a RabbitMQ request in Publish mode targeting a broker host, vhost, exchange, and routing key, enter a JSON payload with environment variables (e.g., `{{orderId}}`), set custom AMQP headers and content type, click "Publish", and verify the message arrives on a bound RabbitMQ queue with the resolved payload, properties, and headers while Clapi displays publish status, timing, and delivery confirmation.

**Acceptance Scenarios**:

1. **Given** a `RabbitMqRequestItem` in Publish mode, **When** opened in the editor, **Then** the editor provides connection configuration (host/URL, port, virtual host, TLS toggle), target exchange (name, type, optional auto-declare) and routing key, message payload editor with format selection and message variants, AMQP headers/properties tab, Auth tab, Scripts tab, Settings tab, and Docs tab.
2. **Given** a publish request with environment or dynamic variables in the broker host, vhost, exchange, routing key, headers, or message body, **When** the user clicks "Publish", **Then** Clapi resolves all variables, connects to RabbitMQ using the effective authentication, publishes the message (supporting publisher confirms when enabled), and displays a delivery result showing confirmed/sent/returned status, elapsed time, message size, and effective routing metadata.
3. **Given** an exchange or queue does not yet exist on the broker, **When** the user enables optional topology auto-declaration in settings, **Then** Clapi declares the configured exchange/queue/binding before publishing; when disabled, Clapi surfaces a clear broker diagnostic if the target does not exist.
4. **Given** a user saves multiple payload examples for a RabbitMQ request, **When** switching between message variants, **Then** the active payload updates accordingly and persists in the OpenCollection item.

---

### User Story 3 - Subscribe and Consume Messages from RabbitMQ in Real Time (Priority: P1)

As a developer debugging asynchronous consumers and event streams, I want to subscribe to a RabbitMQ queue or bind a temporary/named queue to an exchange and routing pattern, observe incoming messages in a live chronological timeline, inspect message payloads, routing keys, and AMQP headers/properties, and control acknowledgment behavior.

**Why this priority**: Observing events emitted by services is the counterpart to publishing and is essential for end-to-end verification of asynchronous workflows.

**Independent Test**: Configure a `RabbitMqRequestItem` in Subscribe mode targeting a RabbitMQ queue or exchange/routing key binding, click "Subscribe", publish test messages to that exchange/queue from another tab or external producer, verify each message appears immediately in the live timeline with timestamp, routing key, exchange, headers, properties, and formatted payload, and click "Unsubscribe" to cleanly close the consumer.

**Acceptance Scenarios**:

1. **Given** a `RabbitMqRequestItem` in Subscribe mode, **When** the user clicks "Subscribe", **Then** Clapi connects to RabbitMQ, attaches a consumer to the configured queue (or creates and binds an exclusive/auto-delete queue to the configured exchange and routing key pattern when no fixed queue name is specified), and transitions the connection status indicator to "Connected / Listening".
2. **Given** an active RabbitMQ subscription, **When** messages arrive from the broker, **Then** each message is appended immediately to a chronological timeline showing timestamp, exchange, routing key, payload size, content type, AMQP properties (`message_id`, `correlation_id`, `reply_to`, `delivery_mode`, etc.), custom headers, and an expandable formatted body with copy support.
3. **Given** an active subscription configured with manual acknowledgment mode, **When** a message arrives, **Then** the user can explicitly acknowledge (`Ack`), reject (`Nack`), or reject-and-requeue (`Requeue`) individual messages from the timeline.
4. **Given** an active subscription accumulating messages, **When** the user filters the timeline (by text, routing key, or header), clears the timeline, switches to another tab, or clicks "Unsubscribe", **Then** filtering and clearing update the view without dropping the subscription, background tabs continue receiving messages with an active indicator, and unsubscribing cleanly cancels the consumer and closes the connection.

---

### User Story 4 - RPC (Request-Reply) Over RabbitMQ (Priority: P1)

As a developer testing microservices that communicate via RabbitMQ RPC (request-reply pattern), I want to send an RPC request message over RabbitMQ with automatic `correlation_id` and `reply_to` handling, await the correlated reply message within a configurable timeout, and view the reply payload, headers, properties, and round-trip latency just like a synchronous request-response call.

**Why this priority**: RPC over RabbitMQ is explicitly required as a core capability and requires dedicated correlation, reply-queue management, and timeout handling beyond fire-and-forget publishing.

**Independent Test**: Configure a `RabbitMqRequestItem` in RPC mode pointing to a RabbitMQ RPC request exchange/routing key or queue (using RabbitMQ Direct Reply-to, a temporary exclusive reply queue, or a named reply queue), send a request payload to a running RabbitMQ RPC responder, verify Clapi automatically attaches a unique `correlation_id` and `reply_to`, receives the correlated reply, ignores unrelated messages on the reply queue, and displays the reply body, headers, properties, and round-trip duration.

**Acceptance Scenarios**:

1. **Given** a `RabbitMqRequestItem` in RPC mode, **When** the user clicks "Send RPC", **Then** Clapi prepares the reply channel (supporting RabbitMQ Direct Reply-to `amq.rabbitmq.reply-to` by default, temporary exclusive queues, or a named reply queue), generates a unique `correlation_id` (unless explicitly overridden), publishes the request with `reply_to` and `correlation_id`, and awaits the matching reply.
2. **Given** an in-flight RabbitMQ RPC request, **When** the responder publishes a reply matching the `correlation_id`, **Then** Clapi captures the reply, stops waiting, cleans up any temporary consumer, displays the formatted reply body, reply headers, AMQP properties, and round-trip elapsed time, and runs any configured post-request test assertions/scripts against the reply.
3. **Given** an in-flight RabbitMQ RPC request, **When** no matching reply arrives before the configured RPC timeout expires, **Then** Clapi aborts waiting, cleans up the temporary consumer, and displays a clear timeout error with the elapsed timeout and target routing details.
4. **Given** multiple messages arrive on a shared reply queue, **When** a message's `correlation_id` does not match the active RPC call, **Then** Clapi ignores it for the current RPC call rather than returning the wrong response.

---

### User Story 5 - Authentication & Auth Inheritance for RabbitMQ Connections (Priority: P1)

As a developer working across local, staging, and production RabbitMQ brokers, I want `RabbitMqRequestItem` requests to participate in Clapi's existing authentication hierarchy—including inheriting authentication from parent folders or collections—and resolving credentials from environment variables and external secret managers.

**Why this priority**: Production and staging RabbitMQ brokers require authentication (username/password, OAuth 2.0/JWT bearer tokens, or TLS client certificates). Integrating with Clapi's existing auth inheritance and secret resolution prevents credential duplication and protects secrets.

**Independent Test**: Configure Basic Auth credentials (referencing a secret variable) on a parent collection or folder, create a `RabbitMqRequestItem` set to "Inherit" auth, execute a Publish or RPC operation against an authenticated RabbitMQ broker, and verify authentication succeeds using the inherited credentials.

**Acceptance Scenarios**:

1. **Given** a `RabbitMqRequestItem` with authentication set to "Inherit", **When** connecting to RabbitMQ, **Then** Clapi resolves the effective authentication configuration by walking up parent folders to the collection root (matching existing HTTP, gRPC, GraphQL, and WebSocket auth inheritance behavior).
2. **Given** effective authentication is Basic Auth (username and password), **When** connecting to RabbitMQ, **Then** Clapi interpolates variables/secrets and authenticates using AMQP username/password credentials.
3. **Given** effective authentication is Bearer Token or OAuth 2.0, **When** connecting to RabbitMQ, **Then** Clapi acquires or resolves the token and passes it using RabbitMQ's OAuth 2.0 / JWT authentication mechanism (token supplied as password with configurable or empty username).
4. **Given** effective authentication is None or TLS Client Certificate (EXTERNAL), **When** connecting to RabbitMQ, **Then** Clapi connects with default/anonymous credentials or TLS client certificate (`EXTERNAL` SASL) authentication respectively, honoring collection-level client certificates and TLS settings (`amqps://`).

---

### User Story 6 - MCP Server Tools & Resources for RabbitMQ Requests (Priority: P2)

As an API developer collaborating with an AI assistant via Clapi's MCP server, I want the MCP server to inspect, author, export, and execute `RabbitMqRequestItem` requests—including publishing RabbitMQ messages, running RabbitMQ RPC calls, and performing bounded message consumption—while strictly enforcing Clapi's zero-exfiltration secret redaction rules.

**Why this priority**: Clapi's MCP server is a first-class workflow for AI-assisted API authoring and debugging; extending it ensures AI agents can work seamlessly with RabbitMQ requests alongside HTTP, gRPC, GraphQL, and WebSocket requests.

**Independent Test**: Connect an MCP client to Clapi, list the workspace tree to see `RabbitMqRequestItem` entries, create/update a RabbitMQ Publish, Subscribe, and RPC request via MCP, execute a Publish and an RPC request via MCP, run a bounded Subscribe sample (e.g., max 5 messages or 3-second timeout), and verify all credentials and secret variables are masked/redacted in every MCP response.

**Acceptance Scenarios**:

1. **Given** a workspace containing `RabbitMqRequestItem` entries, **When** an AI agent calls workspace discovery tools or reads request resources, **Then** RabbitMQ requests appear in the workspace tree with type `rabbitmq` and mode badge (`PUB`, `SUB`, or `RPC`), and reading a request returns the complete sanitized RabbitMQ configuration with credentials masked.
2. **Given** a target collection or folder, **When** an AI agent calls `clapi_upsert_request` with `kind: "rabbitmq"`, **Then** the agent can create or update RabbitMQ connection settings, mode, exchange/routing key/queue, reply strategy, AMQP properties, headers, message variants, auth, scripts, and settings.
3. **Given** a saved RabbitMQ Publish or RPC `RabbitMqRequestItem`, **When** an AI agent calls `clapi_execute_request` (with optional environment and transient variable overrides), **Then** Clapi resolves variables and inherited auth, runs pre-request scripts, executes the RabbitMQ Publish or RPC call, runs post-request assertions, caches the latest response/result for `clapi_inspect_response`, and returns a context-safe, secret-scrubbed summary.
4. **Given** a saved RabbitMQ Subscribe `RabbitMqRequestItem`, **When** an AI agent calls `clapi_subscribe_rabbitmq` specifying a maximum message count and/or timeout window, **Then** Clapi consumes up to the requested limit within the timeout, cleanly unsubscribes, and returns the collected messages within the configured preview byte budget with truncation metadata and full secret redaction.
5. **Given** any RabbitMQ execution, error diagnostic, message payload, or header contains a known secret variable or broker credential, **When** returned through any MCP tool or resource, **Then** all credentials and resolved secrets are masked or scrubbed before reaching the AI client.

---

### User Story 7 - Scripts, Test Assertions & Collection Test Suite Integration (Priority: P3)

As a QA engineer or developer automating API checks, I want pre-request scripts, post-request test assertions, and collection/folder test suite runs to support RabbitMQ Publish and RPC `RabbitMqRequestItem` requests so that event-driven and RPC workflows can be validated alongside HTTP, gRPC, and GraphQL requests.

**Why this priority**: Unifies automated verification across synchronous and asynchronous protocols, enabling mixed-protocol test suites.

**Independent Test**: Add a pre-request script that sets a dynamic variable and a post-request assertion checking a field in a RabbitMQ RPC reply, run the parent folder as a test suite, and verify the RabbitMQ RPC request executes in sequence and reports pass/fail assertion results alongside other requests.

**Acceptance Scenarios**:

1. **Given** a `RabbitMqRequestItem` (Publish or RPC) with a pre-request script, **When** executed, **Then** the pre-request script runs before variable interpolation and broker transmission, allowing dynamic modification of variables or headers.
2. **Given** a RabbitMQ RPC `RabbitMqRequestItem` with post-request test assertions, **When** the reply is received, **Then** the assertions evaluate against the reply payload, headers, AMQP properties, and timing, and report pass/fail results.
3. **Given** a folder or collection containing HTTP, gRPC, GraphQL, and RabbitMQ Publish/RPC `RabbitMqRequestItem` requests, **When** executed via the test suite runner (UI or MCP), **Then** Publish and RPC items run in sequence and contribute to the aggregated pass/fail summary, while indefinite Subscribe items are skipped with an explicit informational status.

---

### Edge Cases

- **Broker unreachable, authentication rejected, or vhost access denied**: Clapi transitions the connection/execution state to Error and surfaces a clear diagnostic distinguishing network unreachable, TLS handshake failure, authentication failure, and vhost/permission error without exposing plaintext credentials in logs or MCP outputs.
- **Unroutable published message**: When a message is published with the `mandatory` flag set and no queue is bound to the exchange/routing key, Clapi captures the broker's basic return notification and reports that the message was returned as unroutable along with the broker reply code and text.
- **Non-UTF-8 / binary message payloads**: When a subscribed queue or RPC reply delivers binary bytes, Clapi detects binary content safely without corrupting the UI, displays byte length and content type, and provides safe inspection (hex/base64 view or copy).
- **High-throughput subscription burst**: Clapi bounds the in-memory timeline buffer (dropping oldest entries once the configured cap is reached), enforces configurable prefetch (`basic.qos`), and keeps the UI responsive.
- **RPC broker disconnect mid-flight**: If the broker connection drops while waiting for an RPC reply, Clapi fails the pending RPC call immediately with a connection-lost diagnostic rather than hanging until the timeout expires, and cleans up all resources.
- **Invalid or missing routing target**: If a user attempts to publish or subscribe without required host/target details, Clapi displays inline validation feedback before attempting a broker connection.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (First-Class OpenCollection Item Type)**: The system MUST support a new first-class OpenCollection item type `RabbitMqRequestItem` (`type: "rabbitmq"`) directly inside OpenCollection, extending the OpenCollection schema to store all RabbitMQ request configuration directly within the item (with no external/companion AsyncAPI document).
- **FR-002 (Sidebar & Tab Identification)**: Users MUST be able to create RabbitMQ requests from the collection/folder "+" menu and identify them in the sidebar tree and editor tabs via a distinct RabbitMQ badge indicating the active mode (`PUB`, `SUB`, or `RPC`).
- **FR-003 (RabbitMQ Connection Configuration)**: `RabbitMqRequestItem` MUST store and support configuring RabbitMQ connection parameters including host/URL, port, virtual host (vhost), TLS toggle (`amqp` / `amqps`), heartbeat interval, and connection timeout, with `{{variable}}` interpolation across connection and routing fields.
- **FR-004 (RabbitMQ Message Publishing)**: The system MUST support publishing messages to RabbitMQ exchanges (direct, topic, fanout, headers, default exchange) with routing keys, optional topology auto-declaration, configurable AMQP message properties (`content_type`, `content_encoding`, `delivery_mode`, `priority`, `correlation_id`, `reply_to`, `expiration`, `message_id`, `type`, `app_id`), custom AMQP headers, publisher confirms, and mandatory delivery feedback.
- **FR-005 (RabbitMQ Message Subscription)**: The system MUST support subscribing to RabbitMQ queues—either a named queue or an exclusive/auto-delete queue bound to an exchange and routing pattern—displaying incoming messages in a real-time timeline with filtering, clearing, payload formatting, prefetch configuration, and configurable acknowledgment mode (auto-ack or manual ack/nack/requeue).
- **FR-006 (RabbitMQ RPC / Request-Reply)**: The system MUST support executing RPC calls over RabbitMQ by sending a request with `correlation_id` and `reply_to` (supporting RabbitMQ Direct Reply-to `amq.rabbitmq.reply-to` by default, temporary exclusive queues, and named reply queues), correlating incoming replies by `correlation_id`, enforcing a configurable reply timeout, and returning the reply payload, headers, properties, and round-trip duration.
- **FR-007 (Authentication & Inheritance)**: `RabbitMqRequestItem` MUST support Clapi's authentication configuration including `Inherit` (resolving from parent folder or collection defaults), `None`, `Basic` (AMQP username/password), `Bearer` / `OAuth 2.0` (RabbitMQ OAuth 2.0 / JWT token authentication), and TLS client certificate (`EXTERNAL`) authentication, with full variable and external secret manager resolution.
- **FR-008 (OpenCollection Persistence & Portability)**: `RabbitMqRequestItem` MUST round-trip losslessly through OpenCollection JSON/YAML save/load, collection export/import, and Git sync while passing OpenCollection schema validation.
- **FR-009 (MCP Server Discovery & Authoring)**: The Clapi MCP server MUST expose `RabbitMqRequestItem` entries in workspace tree discovery and request inspection, and MUST allow AI agents to create/update/delete `RabbitMqRequestItem` requests via existing request tools (`kind: "rabbitmq"`).
- **FR-010 (MCP Server Execution & Bounded Consumption)**: The Clapi MCP server MUST support executing RabbitMQ Publish and RPC requests via `clapi_execute_request` (with variable overrides, scripts, assertions, and cached response inspection) and MUST provide a bounded `clapi_subscribe_rabbitmq` tool (capped by message count and/or timeout) so AI agents can sample RabbitMQ queues without hanging indefinitely.
- **FR-011 (MCP Zero-Exfiltration Security)**: All MCP tools, resources, execution results, consumed messages, and error messages for RabbitMQ MUST enforce Clapi's existing zero-exfiltration secret masking and credential redaction rules.
- **FR-012 (Scripts & Test Suite Support)**: Publish and RPC `RabbitMqRequestItem` requests MUST support pre-request scripts, post-request test assertions (evaluated against publish confirmation or RPC reply), and sequential execution in collection/folder test suites (skipping indefinite Subscribe items with an informational status).
- **FR-013 (Message Variants & Variable Interpolation)**: `RabbitMqRequestItem` requests MUST support composing multiple named payload variants and interpolating `{{variable}}` tokens (regular, secret, and dynamic variables) across connection, routing, header, property, and message body fields at execution time.

### Key Entities

- **RabbitMqRequestItem**: A first-class item in an OpenCollection document representing an executable RabbitMQ request. Contains `info` (`type: "rabbitmq"`), `rabbitmq` details (connection, mode `publish`/`subscribe`/`rpc`, exchange/routing/queue/reply target, AMQP properties, custom headers, message payload/variants, auth config with `Inherit` support), `runtime` (variables, scripts, assertions), `settings` (timeouts, confirms, mandatory, declareTopology, ackMode, prefetch, rpcTimeout, replyStrategy, TLS client cert), and `docs`.
- **RabbitMQ Operation Execution Result**:
  - For **Publish**: delivery status (confirmed/sent/returned/error), elapsed time, resolved exchange/routing key, payload size, and any broker return code/text.
  - For **Subscribe**: live session state (`disconnected`/`connecting`/`connected`/`error`) and a bounded chronological stream of received messages (timestamp, delivery tag, exchange, routing key, AMQP properties, custom headers, content type, payload, and acknowledgment state).
  - For **RPC**: round-trip latency, correlation ID, reply queue/channel used, received reply payload, reply headers, AMQP properties, test assertion results, or timeout/error diagnostic.
- **Effective RabbitMQ Authentication**: The resolved runtime credential set derived by evaluating `RabbitMqRequestItem` auth (walking parent folder and collection defaults when set to `Inherit`), interpolating environment/scoped/external secret variables, and mapping to the appropriate AMQP authentication mechanism.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new RabbitMQ Publish, Subscribe, or RPC request from the UI, save the collection, and verify all RabbitMQ fields persist self-contained inside the OpenCollection file and pass schema validation in under 2 minutes on first use.
- **SC-002**: 100% of OpenCollection round-trips (save/reload, JSON/YAML export/import, and Git sync) preserve all `RabbitMqRequestItem` connection, routing, property, header, auth, variant, script, and setting fields without data loss.
- **SC-003**: Users can publish a message to RabbitMQ and observe it in a live Clapi Subscribe timeline in under 1 second on a local broker, and execute a RabbitMQ RPC round-trip with automatic correlation and reply rendering with less than 100 ms of client-side overhead beyond broker round-trip time.
- **SC-004**: 100% of MCP tool/resource outputs for RabbitMQ requests, executions, and bounded subscriptions redact configured secrets and broker credentials.
- **SC-005**: An AI assistant connected via MCP can discover, author, and execute RabbitMQ Publish, RPC, and bounded Subscribe operations end-to-end without hanging or exceeding the configured response preview budget.

## Assumptions

- **AsyncAPI Import/Export Deferred**: Importing or exporting AsyncAPI documents to/from `RabbitMqRequestItem` collections is explicitly out of scope for this feature and will be implemented as a separate future feature.
- **Protocol Scope**: Execution targets RabbitMQ over AMQP 0-9-1 (`amqp` and `amqps`).
- **RabbitMQ RPC Reply Strategy**: RabbitMQ Direct Reply-to (`amq.rabbitmq.reply-to`) is the default RPC reply strategy, with full support for temporary exclusive queues and named reply queues when selected.
- **Authentication Mapping**: Clapi's existing `AuthConfig` types (`Inherit`, `None`, `Basic`, `Bearer`, `OAuth 2.0`) and collection-level client certificates are reused; `Basic` maps to AMQP PLAIN credentials, `Bearer`/`OAuth 2.0` maps to RabbitMQ's OAuth 2.0 JWT password mechanism, and TLS client certificates map to AMQP `EXTERNAL` over `amqps`.
- **Target Broker Availability**: Integration and end-to-end tests assume access to a reachable RabbitMQ broker (e.g., via Docker Compose in `qa-tests`), while unit and serialization tests run without external broker dependencies.
