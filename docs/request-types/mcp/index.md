---
title: Model Context Protocol (MCP) Client
description: Complete guide to discovering, testing, and debugging MCP servers and AI tool ecosystems in Clapi.
---

# Model Context Protocol (MCP) Client

<p class="badges">
  <span class="badge badge-protocol">Protocol: MCP 2025-06-18 & 2024-11-05</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenMcpPreviewLight.W1920dp_H1080dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenMcpPreviewDark.W1920dp_H1080dp.png"
  alt="Clapi MCP Tools Explorer"
/>

Clapi is a first-class desktop client for Anthropic's [Model Context Protocol (MCP)](https://modelcontextprotocol.io/). It allows you to connect to, inspect, and exercise MCP servers beside the HTTP APIs and databases they depend on—using the same unified collections, environments, <code v-pre>{{variables}}</code>, authentication editors, and response tooling.

Save an MCP connection in your workspace, connect over HTTP or local desktop stdio processes, execute tools, browse resources, run prompts, and analyze raw JSON-RPC exchanges.

---

## Quick Start

1. In the sidebar, click **Add → MCP Connection** (`RequestKind.MCP`).
2. Choose your transport:
   - **Streamable HTTP**: For remote or containerized MCP servers (`https://my-mcp-server.com/mcp`).
   - **Desktop stdio**: For local CLI tools (e.g. `npx -y @modelcontextprotocol/server-filesystem /tmp`).
3. Click **Connect**. Clapi executes the MCP initialization handshake.
4. Once connected, browse the **Tools**, **Resources**, and **Prompts** tabs.
5. Select a tool (e.g. `read_file`), enter arguments in the generated form or JSON editor, and click **Run**.
6. The tool result renders in the response pane, while raw protocol traffic is recorded in **Activity**.

---

## Detailed Feature Subpages

- [Transports (HTTP, SSE, Stdio)](./transports.md): Configuring Streamable HTTP (2025-06-18), Legacy HTTP+SSE (2024-11-05), and desktop stdio process spawning with login shell PATH resolution.
- [Tools Discovery & Execution](./tools.md): Browsing advertised tools, dynamic JSON schema argument form generation, destructive action warnings, and execution controls.
- [Resources & Subscriptions](./resources.md): Reading static and dynamic resource URIs and live resource subscriptions with automatic response panel updates.
- [Prompts & LLM Sampling](./prompts.md): Inspecting prompt templates, arguments, and handling server-initiated `sampling/createMessage` requests.
- [MCP OAuth 2.1 & Authentication](./auth.md): Dynamic client registration, OAuth 2.1 PKCE flows, Bearer tokens, and API key headers.
- [Activity Log & Protocol Diagnostics](./activity-log.md): Real-time JSON-RPC protocol inspector for debugging requests, responses, notifications, and errors.
