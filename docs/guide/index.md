---
title: Guide Overview
description: Welcome to Clapi — the local-first, multi-protocol desktop API client and MCP inspection workbench.
---

# Getting Started with Clapi

## Overview

**Clapi** is a bullshit-free open-source API development and debugging desktop application. Unlike traditional API tools that rely heavily on proprietary cloud sync, mandatory user accounts, and remote data storage, Clapi is engineered to work **100% local** (you can however optionally choose to enable git-sync).

### Features

  - **🚀 Quick Start**: Easily import your existing work from other tools like Postman or Insomnia and get up and running with Clapi.
  - **🌐 Multi-Protocol Support**: First-class support for REST, GraphQL, WebSocket, SSE, gRPC unary/streaming, and Model Context Protocol (MCP).
  - **🗂️ Organization & Collaboration**: Organize your requests via Collections and Folders to easily maintain a multitude of service endpoints of any protocol and optionally collaborate with others via git-sync.
  - **🔑 External Secret Managers**: Use secret managers like Azure Vault, AWS Secrets, HashiCorp Vault, GCP Secrets, Keeper Security or KeepassXC to securely provide any type of credentials.
  - **⚡ Scripting Support**: Write pre-request scripts, dynamic variables and post-response assertions powered by an isolated V8 JS runtime with full Chai-compatible assertions.
  - **🔒 Zero Telemetry or Sign-In Required**: Absolute privacy. No background telemetry, no tracking pixels, and no analytics SDKs. All collections and secrets remain strictly on your local disk.

## Guide Index

1. [Installation & Setup](./installation.md): Install native binaries on macOS, Linux, and Windows, or run the standalone runnable JAR.
2. [Quickstart](./quickstart.md): Create your first collection, execute a test request, and inspect timing metrics.
3. [Migrating to Clapi](./migrate-to-clapi.md): Import your existing work from other tools into Clapi.
4. [Core Concepts](./core-concepts.md): Learn about the workspace tree, multi-tab request editor, response inspector, and dynamic variable engine.
5. [Importing & Migrating from Postman & Insomnia](./import-export.md): Migrate your collections, environments, auth configs, and scripts from Postman, Insomnia v5, OpenAPI 3.0+, and OpenCollection.
