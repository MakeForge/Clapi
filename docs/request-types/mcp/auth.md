---
title: MCP OAuth 2.1 & Authentication
description: Authenticating to secure remote MCP servers using OAuth 2.1 and API keys in Clapi.
---

# MCP OAuth 2.1 & Authentication

<p class="badges">
  <span class="badge badge-protocol">MCP & Security</span>
</p>

## Overview

Remote MCP servers operating over Streamable HTTP often require enterprise-grade authorization. Clapi supports OAuth 2.1 with PKCE, dynamic client registration, and static tokens.

## Authentication Schemes

### 1. OAuth 2.1 Authorization
- **Discovery**: Automatically discovers authorization endpoints via `/.well-known/oauth-authorization-server`.
- **Dynamic Client Registration**: Registers Clapi with the MCP server dynamically (`/oauth/register`).
- **PKCE Flow**: Executes secure authorization code exchange with S256 code challenges.
- **Encrypted Token Store**: Access and refresh tokens are stored encrypted via KSafe.

### 2. Bearer Tokens & Headers
For API gateways protecting MCP endpoints:
- In the **Headers** tab, configure `Authorization: Bearer {{mcpToken}}` or `X-API-Key: {{apiKey}}`.
- All JSON-RPC HTTP POST requests to the MCP endpoint will transmit these credentials.
