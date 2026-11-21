---
title: MCP Tools Discovery & Execution
description: Discovering advertised tools, generating argument input forms, and executing MCP tools in Clapi.
---

# MCP Tools Discovery & Execution

<p class="badges">
  <span class="badge badge-protocol">MCP</span>
</p>

## Overview

MCP Tools allow AI models and clients to execute computational actions (e.g. running SQL queries, reading git history, calling external APIs). Clapi provides an intuitive UI to test these tools independently.

## Tool Exploration

<ThemedScreenshot
  light="/screenshots/mcp-tools.png"
  dark="/screenshots/mcp-tools.png"
  alt="Clapi MCP Tools Discovery and Execution"
  :crop="false"
/>

When connected to an MCP server, Clapi sends `tools/list`:
- **Searchable Tool Directory**: Filter tools by name or description.
- **Destructive Operation Warnings**: Tools flagged as mutating or destructive display a visual warning badge.
- **Read-Only Chips**: Non-mutating discovery tools are badged as `Read-Only`.

## Invocation Modes

### 1. Dynamic Form Mode
Clapi parses the tool's JSON Schema (`inputSchema`) and renders native inputs:
- Text inputs for strings
- Number spinners for integers and floats
- Toggles for booleans
- Select dropdowns for enums
- Required fields are highlighted with validation gating

### 2. Raw JSON Mode
Switch to the **JSON** tab to supply or paste the tool arguments as a raw JSON object.
