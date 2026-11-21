# Data Model: RabbitMQ Messaging (Publish/Subscribe) & RPC Requests

**Feature**: `031-asyncapi-rabbitmq` | **Date**: 2026-11-20

## 1. OpenCollection Entities (`core-model` — `opencollection` package)

### `ItemType` (extended enum)
Adds:
- `RABBITMQ("rabbitmq")`

### `RabbitMqOperationMode` (enum)
- `PUBLISH("publish")`
- `SUBSCRIBE("subscribe")`
- `RPC("rpc")`

### `RabbitMqExchangeType` (enum)
- `DEFAULT("default")` (empty exchange name `""`, routes directly to queue by routing key)
- `DIRECT("direct")`
- `TOPIC("topic")`
- `FANOUT("fanout")`
- `HEADERS("headers")`

### `AmqpAckMode` (enum)
- `AUTO("auto")`
- `MANUAL("manual")`

### `RabbitMqReplyStrategy` (enum)
- `DIRECT_REPLY_TO("direct_reply_to")`
- `TEMPORARY_QUEUE("temporary_queue")`
- `NAMED_QUEUE("named_queue")`

### `RabbitMqRequestInfo` (implements `ItemInfo`)
| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `String` | Yes | Display name in sidebar and tabs |
| `description` | `Description?` | No | Optional short description |
| `type` | `ItemType` | Yes | Defaults to `ItemType.RABBITMQ` (`"rabbitmq"`) |
| `seq` | `Double?` | No | Ordering sequence in folder/collection |
| `tags` | `List<String>` | No | User tags |

### `RabbitMqConnectionConfig`
| Field | Type | Default | Notes |
|---|---|---|---|
| `host` | `String` | `"localhost"` | Hostname, `host:port`, or `amqp(s)://...` URL (supports `{{var}}`) |
| `port` | `Int?` | `5672` | Broker port (default `5672` for `amqp`, `5671` for `amqps`) |
| `vhost` | `String` | `"/"` | RabbitMQ virtual host (supports `{{var}}`) |
| `useTls` | `Boolean` | `false` | Enables `amqps://` TLS connection |

### `RabbitMqExchangeConfig`
| Field | Type | Default | Notes |
|---|---|---|---|
| `name` | `String` | `""` | Exchange name (`""` for default exchange; supports `{{var}}`) |
| `type` | `RabbitMqExchangeType` | `DIRECT` | Exchange type (`default`, `direct`, `topic`, `fanout`, `headers`) |
| `durable` | `Boolean` | `true` | Used when `declareTopology = true` |
| `autoDelete` | `Boolean` | `false` | Used when `declareTopology = true` |

### `RabbitMqQueueConfig`
| Field | Type | Default | Notes |
|---|---|---|---|
| `name` | `String` | `""` | Queue name (empty = temporary exclusive auto-delete queue for Subscribe; supports `{{var}}`) |
| `durable` | `Boolean` | `true` | Used when `declareTopology = true` |
| `exclusive` | `Boolean` | `false` | Exclusive consumer queue |
| `autoDelete` | `Boolean` | `false` | Auto-delete queue when unused |

### `AmqpMessageProperties`
Optional AMQP 0-9-1 BasicProperties configured on the request:
| Field | Type | Default | Notes |
|---|---|---|---|
| `contentType` | `String?` | `"application/json"` | MIME type (supports `{{var}}`) |
| `contentEncoding` | `String?` | `null` | e.g., `"utf-8"` |
| `deliveryMode` | `Int?` | `2` | `1` = transient, `2` = persistent |
| `priority` | `Int?` | `null` | `0..9` |
| `correlationId` | `String?` | `null` | Auto-generated UUID for RPC if blank |
| `replyTo` | `String?` | `null` | Explicit `reply_to` property override |
| `expiration` | `String?` | `null` | Per-message TTL in ms (string per AMQP spec) |
| `messageId` | `String?` | `null` | Optional message ID |
| `type` | `String?` | `null` | Application message type |
| `appId` | `String?` | `null` | Producer application ID |

### `RabbitMqRequestDetails`
| Field | Type | Required | Notes |
|---|---|---|---|
| `mode` | `RabbitMqOperationMode` | Yes | `PUBLISH`, `SUBSCRIBE`, or `RPC` |
| `connection` | `RabbitMqConnectionConfig` | Yes | Broker host, port, vhost, TLS |
| `exchange` | `RabbitMqExchangeConfig?` | No | Target/source exchange configuration |
| `queue` | `RabbitMqQueueConfig?` | No | Target/source queue configuration (Subscribe or default-exchange Publish/RPC) |
| `routingKey` | `String` | No | Routing key or binding pattern (e.g., `order.created`, `order.#`; supports `{{var}}`) |
| `replyQueue` | `String?` | No | Named reply queue used when `mode = RPC` and `replyStrategy = NAMED_QUEUE` |
| `headers` | `List<HttpRequestHeader>` | No | Custom AMQP application headers (name, value, description, disabled) |
| `properties` | `AmqpMessageProperties?` | No | AMQP BasicProperties |
| `message` | `WebSocketMessagePayload?` | No | Reuses single/variants message payload model (`text`, `json`, `xml`, `binary`) |
| `auth` | `AuthConfig?` | No | Supports `Inherit`, `None`, `Basic`, `Bearer`, `OAuth2` |

### `RabbitMqRequestSettings`
| Field | Type | Default | Notes |
|---|---|---|---|
| `connectionTimeoutMs` | `Long` | `10_000` | AMQP connection/handshake timeout |
| `heartbeatSeconds` | `Int` | `30` | AMQP heartbeat interval |
| `publisherConfirms` | `Boolean` | `true` | Enable RabbitMQ publisher confirms on Publish |
| `mandatory` | `Boolean` | `false` | Set AMQP `mandatory` flag to capture unroutable returns |
| `declareTopology` | `Boolean` | `false` | Declare exchange/queue/binding before Publish/Subscribe/RPC |
| `ackMode` | `AmqpAckMode` | `AUTO` | `AUTO` or `MANUAL` (for Subscribe) |
| `prefetchCount` | `Int` | `50` | Consumer `basic.qos` prefetch limit |
| `maxTimelineMessages` | `Int` | `1_000` | In-memory timeline ring-buffer cap |
| `rpcTimeoutMs` | `Long` | `15_000` | Max wait time for correlated RPC reply |
| `replyStrategy` | `RabbitMqReplyStrategy` | `DIRECT_REPLY_TO` | `DIRECT_REPLY_TO`, `TEMPORARY_QUEUE`, or `NAMED_QUEUE` |
| `useTlsClientCert` | `Boolean` | `false` | Use collection TLS client certificate (`EXTERNAL` SASL) |

### `RabbitMqRequestItem` (implements `OpenCollectionItem`)
| Field | Type | Required | Notes |
|---|---|---|---|
| `info` | `RabbitMqRequestInfo` | Yes | Item metadata (`type = "rabbitmq"`) |
| `rabbitmq` | `RabbitMqRequestDetails` | Yes | All RabbitMQ connection, routing, message, header, property, and auth fields |
| `runtime` | `HttpRequestRuntime?` | No | Variables, pre/post scripts, assertions, actions |
| `settings` | `RabbitMqRequestSettings?` | No | Execution and broker behavior settings |
| `docs` | `String?` | No | Markdown documentation |
| `extensions` | `Map<String, JsonElement>` (`@Transient`) | No | Preserved unknown/extension properties |

---

## 2. Runtime & Execution Entities (`core-model` & `core-network`)

### `RabbitMqConnectionStatus` (state transitions)
`DISCONNECTED` -> `CONNECTING` -> `CONNECTED` -> `DISCONNECTING` -> `DISCONNECTED`
Any active state -> `ERROR(reason: String)` -> `DISCONNECTED`

### `RabbitMqDeliveryMessage` (Subscribe timeline & RPC reply)
| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Unique local message UUID |
| `timestampEpochMillis` | `Long` | Receive or send timestamp |
| `direction` | `MessageDirection` | `SENT` or `RECEIVED` |
| `deliveryTag` | `Long?` | AMQP delivery tag (used for manual ack/nack) |
| `exchange` | `String` | Source/target exchange |
| `routingKey` | `String` | Effective routing key |
| `contentType` | `String?` | MIME type |
| `headers` | `Map<String, String>` | Stringified AMQP headers |
| `properties` | `AmqpMessageProperties` | AMQP BasicProperties |
| `payloadText` | `String?` | Decoded UTF-8 text (`null` if binary) |
| `payloadBytesBase64` | `String?` | Base64 payload when binary or non-UTF8 |
| `isBinary` | `Boolean` | True when payload is binary/non-UTF8 |
| `byteLength` | `Long` | Raw payload byte size |
| `ackStatus` | `AmqpAckStatus` | `AUTO_ACKED`, `PENDING`, `ACKED`, `NACKED`, `REQUEUED` |

### `RabbitMqPublishResult`
- `confirmed: Boolean`, `returnedUnroutable: Boolean`, `returnReplyCode: Int?`, `returnReplyText: String?`, `exchange: String`, `routingKey: String`, `byteLength: Long`, `elapsedMs: Long`, `errorMessage: String?`

### `RabbitMqRpcResult`
- `correlationId: String`, `replyTo: String`, `reply: RabbitMqDeliveryMessage?`, `timedOut: Boolean`, `elapsedMs: Long`, `errorMessage: String?`

---

## 3. Validation Rules
1. `RabbitMqConnectionConfig.host` MUST be non-blank after variable interpolation before attempting a broker connection.
2. `RabbitMqConnectionConfig.port` (when specified) MUST be in `1..65535`.
3. `RabbitMqRequestSettings.rpcTimeoutMs` MUST be clamped to `100..300_000` ms.
4. `RabbitMqRequestSettings.prefetchCount` MUST be clamped to `1..10_000`.
5. `RabbitMqRequestSettings.maxTimelineMessages` MUST be clamped to `10..50_000`.
6. When `mode == RPC` and `replyStrategy == NAMED_QUEUE`, `replyQueue` MUST be non-blank.
7. MCP `clapi_subscribe_rabbitmq` MUST enforce `maxMessages` in `1..100` and `timeoutMs` in `250..30_000`.
