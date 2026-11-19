# Contract: OpenCollection `RabbitMqRequestItem` Schema Extension

**Feature**: `031-asyncapi-rabbitmq` | **Date**: 2026-11-20

## 1. OpenCollection Schema Extension (`opencollection_v1.0.0.json`)

- `$defs.Item.oneOf` adds `{"$ref": "#/$defs/RabbitMqRequest"}`.
- `$defs.RabbitMqRequest` defines:
  - `info`: `{ name (required), type: const "rabbitmq" (required), description, seq, tags }`
  - `rabbitmq`: `{ mode (enum: publish|subscribe|rpc, required), connection (required: { host, port, vhost, useTls }), exchange ({ name, type, durable, autoDelete }), queue ({ name, durable, exclusive, autoDelete }), routingKey, replyQueue, headers, properties, message (single or variants), auth }`
  - `runtime`: `HttpRequestRuntime` (variables, scripts, assertions, actions)
  - `settings`: `{ connectionTimeoutMs, heartbeatSeconds, publisherConfirms, mandatory, declareTopology, ackMode, prefetchCount, maxTimelineMessages, rpcTimeoutMs, replyStrategy, useTlsClientCert }`
  - `docs`: Markdown string

---

## 2. Example `RabbitMqRequestItem` Entries (YAML)

### Publish Mode
```yaml
info:
  name: Publish Order Created
  type: rabbitmq
  seq: 1
  tags:
    - rabbitmq
    - events
rabbitmq:
  mode: publish
  connection:
    host: "{{rmqHost}}"
    port: 5672
    vhost: /
    useTls: false
  exchange:
    name: orders.events
    type: topic
    durable: true
    autoDelete: false
  routingKey: order.created
  headers:
    - name: x-source
      value: clapi
      disabled: false
  properties:
    contentType: application/json
    deliveryMode: 2
  message:
    - title: Standard Order
      type: json
      data: '{"orderId": "{{orderId}}", "currency": "EUR"}'
      selected: true
  auth: inherit
settings:
  connectionTimeoutMs: 10000
  heartbeatSeconds: 30
  publisherConfirms: true
  mandatory: false
  declareTopology: true
docs: |
  Publishes an `order.created` event to the `orders.events` topic exchange.
```

### Subscribe Mode
```yaml
info:
  name: Subscribe Order Events
  type: rabbitmq
  seq: 2
rabbitmq:
  mode: subscribe
  connection:
    host: "{{rmqHost}}"
    port: 5672
    vhost: /
    useTls: false
  exchange:
    name: orders.events
    type: topic
    durable: true
    autoDelete: false
  queue:
    name: ""
    durable: false
    exclusive: true
    autoDelete: true
  routingKey: "order.#"
  auth: inherit
settings:
  declareTopology: true
  ackMode: manual
  prefetchCount: 50
  maxTimelineMessages: 1000
```

### RPC Mode
```yaml
info:
  name: Calculate Order Total (RPC)
  type: rabbitmq
  seq: 3
rabbitmq:
  mode: rpc
  connection:
    host: "{{rmqHost}}"
    port: 5672
    vhost: /
    useTls: false
  exchange:
    name: ""
    type: default
  routingKey: orders.rpc.calculate
  properties:
    contentType: application/json
    deliveryMode: 2
  message:
    type: json
    data: '{"orderId": "{{orderId}}", "items": [{"sku": "A1", "qty": 2}]}'
  auth: inherit
settings:
  rpcTimeoutMs: 15000
  replyStrategy: direct_reply_to
runtime:
  scripts:
    - type: after-response
      code: |
        test("total is positive", () => {
          expect(res.body.total).toBeGreaterThan(0);
        });
```
