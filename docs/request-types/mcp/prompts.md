---
title: MCP Prompts & LLM Sampling
description: Testing server prompts and handling server-initiated LLM sampling in Clapi.
---

# MCP Prompts & LLM Sampling

<p class="badges">
  <span class="badge badge-protocol">MCP</span>
</p>

## Overview

MCP Prompts provide reusable, server-managed prompt templates with parameterized arguments. MCP Sampling allows servers to request LLM completions back from the client.

## Exploring & Testing Prompts

1. Navigate to the **Prompts** tab.
2. Select any advertised prompt from the list.
3. Fill in required arguments (e.g. `language`, `code_snippet`).
4. Click **Get Prompt** (`prompts/get`).
5. Clapi displays the resolved messages array (user, assistant, and system roles) with rendered text and image attachments.

## Handling Server Sampling (`sampling/createMessage`)

When an MCP server requests LLM assistance from the client:
- **Manual Review**: Clapi presents a modal displaying the requested prompt messages, temperature, and token limits. You can review and manually enter the completion reply.
- **Mock Responses**: Automatically reply with predefined deterministic sample replies during testing.
- **Forward to LLM**: Route sampling requests directly to an OpenAI-compatible endpoint configured in your environment.
