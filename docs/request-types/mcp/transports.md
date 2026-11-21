---
title: MCP Transports (HTTP, SSE, Stdio)
description: Configuring Streamable HTTP, Legacy SSE, and Stdio process transports in Clapi.
---

# MCP Transports (HTTP, SSE, Stdio)

<p class="badges">
  <span class="badge badge-protocol">MCP</span>
</p>

## Overview

The Model Context Protocol supports multiple physical transports. Clapi supports all current and legacy official transport specifications.

## Supported Transports

### 1. Streamable HTTP (MCP 2025-06-18)
The modern default transport for remote MCP servers:
- High-performance JSON-RPC over HTTP POST.
- Supports streaming server responses and bidirectional session management via `Mcp-Session-Id`.
- Select **Auto** in Clapi's HTTP transport selector to automatically use Streamable HTTP with fallback to Legacy SSE.

### 2. Legacy HTTP + SSE (MCP 2024-11-05)
For earlier MCP servers that split communication between a GET `/sse` endpoint and a POST `/messages` endpoint. Select **Legacy** to connect to older servers.

### 3. Desktop stdio (Local Processes)
Allows running local CLI agents directly on your machine:
- **Executable & Arguments**: e.g. `npx -y @modelcontextprotocol/server-postgres postgresql://localhost/mydb`
- **Shell PATH Resolution**: Clapi resolves binaries using your interactive login shell (`zsh`/`bash -ilc 'echo $PATH'`), ensuring tools installed via `nvm`, `pnpm`, or `brew` resolve properly.
- **Process Security Confirmation**: For your protection, Clapi prompts for explicit confirmation before spawning any local executable process on your machine.
