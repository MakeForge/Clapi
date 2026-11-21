# Quickstart Validation Guide: RabbitMQ Messaging & RPC

**Feature**: `031-asyncapi-rabbitmq` | **Date**: 2026-11-20

## Prerequisites
- JDK 25 installed (per `DEVELOPMENT.md`).
- Docker / Docker Compose available for real RabbitMQ broker integration tests in `qa-tests`.
- Use `rtk gradlew ...` for all Gradle invocations per project rules.

## 1. Unit & Contract Tests (No Docker Required)

Run fast unit, OpenCollection serialization/schema validation, mocked RabbitMQ execution, and MCP server tests:

```bash
rtk gradlew :core-model:desktopTest :core-storage:desktopTest :core-network:desktopTest :mcp-server:desktopTest
```

**Expected outcomes**:
- `RabbitMqRequestItem` serializes/deserializes cleanly in both OpenCollection JSON and YAML and passes `OpenCollectionSchemaValidator` (`opencollection_v1.0.0.json`).
- Auth inheritance (`Inherit`, `None`, `Basic`, `Bearer`, `OAuth2`, TLS `EXTERNAL`) resolves accurately from folder/collection hierarchy.
- MCP tools (`clapi_list_tree`, `clapi_get_request`, `clapi_upsert_request`, `clapi_execute_request`, `clapi_subscribe_rabbitmq`, `clapi_run_test_suite`) pass protocol and `SecretSanitizer` redaction tests.

## 2. End-to-End Integration Tests with Real RabbitMQ (`qa-tests`)

`qa-tests` automatically starts `qa-tests/docker/docker-compose.rabbitmq.yml` before `jvmTest` and tears it down afterward:

```bash
rtk gradlew :qa-tests:jvmTest --tests "*RabbitMq*"
```

**Expected outcomes**:
- **Publish + Subscribe**: Publishing to a topic exchange with routing key `order.created` delivers the message to a live Subscribe consumer bound to `order.#` within 1 second, preserving AMQP properties, custom headers, and resolved variables.
- **Manual Ack/Nack**: Consuming with `ackMode = MANUAL` allows explicit `ack`, `nack`, and `requeue`.
- **RPC (Direct Reply-to & Named Queue)**: Sending an RPC request attaches a unique `correlationId` and `replyTo`, receives the correlated reply from a test responder, ignores mismatched correlation IDs, and fails cleanly on timeout.
- **Auth Inheritance**: Connecting with Basic Auth inherited from a parent collection succeeds against the authenticated RabbitMQ broker.

## 3. Desktop UI Verification

Launch Clapi Desktop:

```bash
rtk gradlew :ui-desktop:run
```

Verify interactively:
1. Click `+` on a collection/folder -> select **New RabbitMQ Request** -> confirm badge renders (`PUB` / `SUB` / `RPC`).
2. Configure host `localhost`, port `5672`, vhost `/`, Auth = `Inherit` (or `Basic` `guest`/`guest`), exchange & routing key, and publish/subscribe/RPC against a local RabbitMQ instance.
3. Save and reopen Clapi -> verify all RabbitMQ fields persist inside OpenCollection with zero data loss.
