---
title: RabbitMQ Requests (AMQP 0-9-1)
description: Complete guide to publishing, subscribing, and executing RPC request-reply calls over RabbitMQ in Clapi.
---

# RabbitMQ Requests (AMQP 0-9-1)

<p class="badges">
  <span class="badge badge-protocol">Protocol: RabbitMQ (AMQP 0-9-1)</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenRabbitMqPublishPreviewLight.W1600dp_H800dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenRabbitMqPublishPreviewDark.W1600dp_H800dp.png"
  alt="Clapi RabbitMQ Request Workbench"
/>

Clapi includes first-class **RabbitMQ** support (`RequestKind.RABBITMQ`, `type: "rabbitmq"` in OpenCollection) covering all three core AMQP 0-9-1 messaging patterns in a single dedicated workbench:

- **Publish (`PUB`)**: Send messages to exchanges (direct, topic, fanout, headers, or default) with routing keys, AMQP BasicProperties, custom headers, publisher confirms, and mandatory return detection.
- **Subscribe (`SUB`)**: Attach a live consumer to a named queue or bind an exclusive auto-delete queue to an exchange and routing pattern, inspect incoming messages in real time, and acknowledge or reject/requeue deliveries manually.
- **RPC (`RPC`)**: Execute synchronous request-reply calls over RabbitMQ with automatic `correlation_id` and `reply_to` handling (Direct Reply-to `amq.rabbitmq.reply-to`, temporary exclusive queues, or named reply queues), configurable timeouts, and post-response test assertions.

All RabbitMQ connection, topology, routing, payload, auth, script, and setting fields are stored directly inside your OpenCollection file—no external sidecar files required.

## Quick Start

1. Click the **`+`** button on any collection or folder in the sidebar and choose **RabbitMQ Request**.
2. In the connection bar, select a mode (**PUB**, **SUB**, or **RPC**) and enter your broker details (`host`, `port`, `vhost`, and optional **TLS** toggle for `amqps://`).
3. Configure authentication in the **Auth** tab (defaults to **Inherit** from parent folder/collection).
4. In the **AMQP** tab, set your exchange name/type, routing key (or binding pattern), queue name, and (for RPC) reply strategy.
5. Compose your payload in the **Message** tab (supports `{{variable}}` interpolation and named variants) and click **Publish**, **Subscribe**, or **Send RPC**.

## Detailed Feature Subpages

- [Publish & Subscribe](./publish-subscribe.md): Exchanges, routing keys, queues, topology auto-declaration, publisher confirms, mandatory returns, live timeline filtering, and manual Ack/Nack/Requeue.
- [RPC (Request-Reply)](./rpc.md): Direct Reply-to (`amq.rabbitmq.reply-to`), temporary and named reply queues, `correlation_id` matching, timeouts, and test assertions.
- [Authentication & TLS](./auth.md): Folder/collection auth inheritance, Basic Auth (`PLAIN`), Bearer/OAuth 2.0 JWT, and TLS client certificates (`EXTERNAL` SASL).
