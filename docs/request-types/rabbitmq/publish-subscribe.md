---
title: RabbitMQ Publish & Subscribe
description: Publishing messages with confirms/mandatory returns and consuming live queues with manual acknowledgments in Clapi.
---

# RabbitMQ Publish & Subscribe

<p class="badges">
  <span class="badge badge-protocol">RabbitMQ &bull; AMQP 0-9-1</span>
</p>

## Publishing Messages (`PUB`)

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenRabbitMqPublishPreviewLight.W1600dp_H800dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenRabbitMqPublishPreviewDark.W1600dp_H800dp.png"
  alt="Clapi RabbitMQ Publish Request Editor"
/>

In **Publish** mode, Clapi connects to your RabbitMQ broker, resolves all `{{variable}}` tokens across connection, routing, header, property, and body fields, and dispatches the message.

### Exchanges & Routing Keys

Configure the destination in the **AMQP** tab:

- **Exchange Name**: Leave empty (`""`) with type `default` to route directly to a queue whose name matches the **Routing Key**, or specify a named exchange (`direct`, `topic`, `fanout`, `headers`).
- **Routing Key**: Target routing key (e.g., `order.created` or `order.{{event}}`).
- **Auto-Declare Topology**: Enable **Auto-Declare Exchange / Queue / Binding** in the **Settings** tab if you want Clapi to declare the exchange/queue before publishing.

### Reliability Options

- **Publisher Confirms** (enabled by default): Uses AMQP `confirm.select` and waits for broker confirmation before reporting `CONFIRMED`.
- **Mandatory Delivery**: Sets the AMQP `mandatory` flag and attaches a return listener so unroutable messages (e.g., `312 NO_ROUTE`) are captured and clearly reported.

### AMQP Properties & Custom Headers

In the **Headers & Props** tab you can configure standard AMQP `BasicProperties` (`content_type`, `delivery_mode`, `correlation_id`, `reply_to`, `message_id`, `expiration`, `type`, `app_id`) alongside arbitrary custom AMQP application headers.

## Subscribing & Consuming Messages (`SUB`)

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenRabbitMqSubscribePreviewLight.W1600dp_H800dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenRabbitMqSubscribePreviewDark.W1600dp_H800dp.png"
  alt="Clapi RabbitMQ Subscribe and Live Consumer Timeline"
/>

In **Subscribe** mode, Clapi maintains a live AMQP consumer session and streams incoming deliveries into the right-hand **Message Timeline**.

### Queue & Exchange Binding

- **Named Queue**: Enter a queue name in the **AMQP** tab to consume directly from an existing queue (or declare & bind it when **Auto-Declare Topology** is enabled).
- **Ephemeral Exchange Binding**: Leave the queue name blank and specify an exchange + routing key pattern (e.g., `order.#`). Clapi declares a server-named exclusive, auto-delete queue bound to that exchange and cleans it up automatically when you click **Unsubscribe**.

### Live Timeline & Manual Acknowledgments

- **Live Filtering & Search**: Filter the timeline by payload text, routing key, exchange, or header without interrupting the active consumer.
- **Background Tab Indicator**: Switching to another tab keeps the subscription alive and displays a green active indicator on the tab header.
- **Bounded Buffer & Prefetch**: Configure `prefetchCount` (`basic.qos`) and `maxTimelineMessages` in **Settings** to keep the UI responsive under high-throughput bursts.
- **Ack Modes**:
  - `auto`: Messages are acknowledged automatically upon delivery.
  - `manual`: Each message row displays **Ack**, **Nack**, and **Requeue** controls so you can inspect deliveries before acknowledging or rejecting them.
- **Binary Payloads**: Non-UTF-8 payloads are detected safely and displayed with byte size and Base64 preview.
- **On-Message Scripts & Assertions**: Write a JavaScript test script in the **Scripts** tab to run automatically on every received delivery—validating payloads with `clapi.test` / `clapi.expect` (displaying per-message pass/fail badges in the timeline) or extracting fields into environment variables.
