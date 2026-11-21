# Contract: MCP Server Tools & Resources for RabbitMQ Requests

**Feature**: `031-asyncapi-rabbitmq` | **Date**: 2026-11-20

All tools and resources follow Clapi's MCP JSON-RPC 2.0 protocol (`2025-06-18` / `2024-11-05`) and enforce `SecretSanitizer` zero-exfiltration masking on every response, header, payload, and error message.

---

## 1. Extended Existing Tools

### `clapi_list_tree` / `clapi://tree`
- Includes `RabbitMqRequestItem` nodes with `kind: "rabbitmq"` and `method` set to `"PUB"`, `"SUB"`, or `"RPC"`.

### `clapi_get_request` / `clapi://requests/{requestId}`
- Returns `SanitizedRequestDto` with `kind: "rabbitmq"` and a `rabbitmq` block containing:
  - `mode` (`publish` | `subscribe` | `rpc`)
  - `connection` (`host`, `port`, `vhost`, `useTls`)
  - `exchange` (`name`, `type`, `durable`, `autoDelete`)
  - `queue` (`name`, `durable`, `exclusive`, `autoDelete`)
  - `routingKey`, `replyQueue`
  - `headers` (sensitive headers masked), `properties`, `variants`, `settings`
  - `auth` (credentials masked as `"********"`)

### `clapi_upsert_request`
- `kind` enum extended with `"rabbitmq"`.
- Accepts `rabbitmq` config object:
  - `mode`: `"publish"` | `"subscribe"` | `"rpc"`
  - `connection`: `{ host, port, vhost, useTls }`
  - `exchange`: `{ name, type, durable, autoDelete }`
  - `queue`: `{ name, durable, exclusive, autoDelete }`
  - `routingKey`, `replyQueue`
  - `properties`: `{ contentType, contentEncoding, deliveryMode, priority, correlationId, replyTo, expiration, messageId, type, appId }`
  - `variants`: `[{ title, type, data, selected }]`
  - `settings`: `{ connectionTimeoutMs, heartbeatSeconds, publisherConfirms, mandatory, declareTopology, ackMode, prefetchCount, maxTimelineMessages, rpcTimeoutMs, replyStrategy, useTlsClientCert }`
  - Plus standard top-level fields: `headers`, `auth`, `preRequestScript`, `postRequestScript`, `summary`/`docs`.

### `clapi_execute_request`
- Supports `kind: "rabbitmq"`:
  - **Publish**: resolves variables & inherited auth, runs pre-request script, publishes to RabbitMQ (with optional publisher confirm and mandatory return detection), runs post-request assertions, caches result, and returns `ExecutionResultDto` (`protocol: "amqp"`, delivery status, timing, sanitized routing/header metadata, and assertion results).
  - **RPC**: resolves variables & inherited auth, runs pre-request script, executes correlated RabbitMQ RPC call, awaits reply within `rpcTimeoutMs`, runs post-request assertions against the reply, caches reply for `clapi_inspect_response` (JSONPath / byte-range), and returns sanitized reply preview (`maxBodyLength` budget), AMQP headers/properties, round-trip duration, and test assertion results.
  - **Subscribe**: returns an actionable validation error directing the agent to call `clapi_subscribe_rabbitmq` with bounded `maxMessages` and `timeoutMs`.

### `clapi_run_test_suite`
- Executes `Publish` and `RPC` `RabbitMqRequestItem` entries sequentially alongside HTTP, gRPC, and GraphQL requests.
- Marks indefinite `Subscribe` items as `SKIPPED` with reason `"Indefinite RabbitMQ subscription skipped in test suite"`.

### `clapi_export_collection`
- OpenCollection JSON/YAML exports include all `RabbitMqRequestItem` entries with secrets masked/omitted.

---

## 2. New MCP Tool (`clapi_subscribe_rabbitmq`)

### `clapi_subscribe_rabbitmq`
- **Description**: Performs a bounded RabbitMQ subscription on a saved Subscribe `RabbitMqRequestItem`, collecting up to `maxMessages` messages or waiting up to `timeoutMs`, then cleanly unsubscribing and returning secret-scrubbed messages within the byte budget.
- **Input Schema**:
  - `requestId` (`string`, required): Request UUID or slash path.
  - `maxMessages` (`integer`, optional, default `10`, clamped `1..100`).
  - `timeoutMs` (`integer`, optional, default `5000`, clamped `250..30000`).
  - `environmentName` (`string`, optional): Environment override.
  - `transientVariables` (`object`, optional): Highest-precedence variable overrides.
  - `maxBodyLength` (`integer`, optional, default `4096`, clamped `256..65536`): Per-message preview byte cap.
- **Output**:
  ```json
  {
    "requestId": "uuid",
    "elapsedMs": 1240,
    "messageCount": 2,
    "truncated": false,
    "messages": [
      {
        "timestamp": 1763600000000,
        "exchange": "orders.events",
        "routingKey": "order.created",
        "contentType": "application/json",
        "headers": { "x-source": "clapi" },
        "properties": { "deliveryMode": 2 },
        "bodyPreview": "{\"orderId\":\"123\"}",
        "byteLength": 17,
        "isBinary": false,
        "_truncated": false
      }
    ]
  }
  ```
