---
title: RabbitMQ RPC (Request-Reply)
description: Executing correlated RPC calls over RabbitMQ with Direct Reply-to, temporary queues, or named reply queues in Clapi.
---

# RabbitMQ RPC (Request-Reply)

<p class="badges">
  <span class="badge badge-protocol">RabbitMQ &bull; RPC</span>
</p>

## Overview

In **RPC** mode (`RPC`), Clapi treats a RabbitMQ request-reply exchange like a synchronous API call:

1. Prepares the reply consumer *before* publishing so no reply can be missed.
2. Generates a unique UUID `correlation_id` (unless you set an explicit value in **Headers & Props**).
3. Publishes the request with `reply_to` and `correlation_id` properties.
4. Awaits the matching correlated reply within the configured **RPC Timeout** (default `15000 ms`, clamped to `100..300000 ms`).
5. Displays the formatted reply payload, reply headers, AMQP properties, and round-trip latency—and runs any configured **Post-response / Test Script** assertions.

---

## Reply Strategies

Choose the reply strategy in the **AMQP** tab:

| Strategy | Behavior | Best For |
| :--- | :--- | :--- |
| **`direct_reply_to`** *(Default)* | Consumes from RabbitMQ's built-in `amq.rabbitmq.reply-to` pseudo-queue (`autoAck = true`) with zero extra queue declarations. | Standard RabbitMQ RPC services (lowest latency & broker overhead). |
| **`temporary_queue`** | Declares a server-named exclusive, auto-delete reply queue for the duration of the call. | Brokers or services that require a concrete queue name in `reply_to`. |
| **`named_queue`** | Consumes from a user-specified **Named Reply Queue** and matches deliveries by `correlation_id` (unrelated messages on the shared queue are requeued cleanly). | Legacy or fixed-topology RPC responders that reply to a dedicated queue. |

---

## Timeouts, Disconnects & Assertions

- **Timeout Handling**: If no correlated reply arrives before `rpcTimeoutMs`, Clapi cancels the reply consumer, closes the channel, and displays a `TIMEOUT` diagnostic.
- **Mid-Flight Disconnects**: If the broker connection drops while waiting, Clapi fails fast immediately rather than hanging until the timeout expires.
- **Scripting & Test Suites**: RabbitMQ **Publish** and **RPC** requests support `clapi.test` / `clapi.expect` post-response assertions and run sequentially in collection/folder test suites (both in the Desktop UI and via MCP `clapi_run_test_suite`).
