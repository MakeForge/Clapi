---
title: Guide Overview
description: Welcome to Clapi — the local-first, multi-protocol desktop API client and MCP inspection workbench.
---

# Getting Started with Clapi

## Overview

**Clapi** is a bullshit-free open-source API development and debugging desktop application. Unlike traditional API tools that rely heavily on proprietary cloud sync, mandatory user accounts, and remote data storage, Clapi is engineered to work **100% local** (you can however optionally choose to enable git-sync).

### Features

  -  **🌐 Multi-Protocol Support**: First-class support for REST, GraphQL, WebSocket, SSE, gRPC unary/streaming, and Model Context Protocol (MCP).
  - **🗂️ Organization**: Organize your requests via Collections and Folders to easily maintain a multitude of service endpoints of any protocol.
  - **🔑 External Secret Managers**: Use secret managers like Azure Vault, AWS Secrets, HashiCorp Vault, GCP Secrets, Keeper Security or KeepassXC to securely provide any type of credentials.
  - **⚡ Scripting Support**: Write pre-request scripts and post-response assertions powered by an isolated, high-performance V8 JS runtime with full Chai-compatible assertions.
  - **<svg class="git-feature-icon" viewBox="0 0 256 256" width="20" height="20" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMinYMin meet"><path d="M251.172 116.594L139.4 4.828c-6.433-6.437-16.873-6.437-23.314 0l-23.21 23.21 29.443 29.443c6.842-2.312 14.688-.761 20.142 4.693 5.48 5.489 7.02 13.402 4.652 20.266l28.375 28.376c6.865-2.365 14.786-.835 20.269 4.657 7.663 7.66 7.663 20.075 0 27.74-7.665 7.666-20.08 7.666-27.749 0-5.764-5.77-7.188-14.235-4.27-21.336l-26.462-26.462-.003 69.637a19.82 19.82 0 0 1 5.188 3.71c7.663 7.66 7.663 20.076 0 27.747-7.665 7.662-20.086 7.662-27.74 0-7.663-7.671-7.663-20.086 0-27.746a19.654 19.654 0 0 1 6.421-4.281V94.196a19.378 19.378 0 0 1-6.421-4.281c-5.806-5.798-7.202-14.317-4.227-21.446L81.47 39.442l-76.64 76.635c-6.44 6.443-6.44 16.884 0 23.322l111.774 111.768c6.435 6.438 16.873 6.438 23.316 0l111.251-111.249c6.438-6.44 6.438-16.887 0-23.324" fill="#DE4C36"/></svg> Collaborate via Git Sync**: Synchronize API collections directly through standard Git repositories with branch switching, conflict reviews, and zero SaaS cloud lock-in.
  - **🔒 Zero Telemetry or Sign-In Required**: Absolute privacy. No background telemetry, no tracking pixels, and no analytics SDKs. All collections and secrets remain strictly on your local disk.

---

## Guide Index

1. [Installation & Setup](./installation.md): Install native binaries on macOS, Linux, and Windows, or run the standalone runnable JAR.
2. [Quickstart](./quickstart.md): Create your first collection, execute a test request, and inspect timing metrics.
3. [Core Concepts & UI Anatomy](./core-concepts.md): Learn about the workspace tree, multi-tab request editor, response inspector, and dynamic variable engine.
4. [Importing & Migrating from Postman & Insomnia](./import-export.md): Migrate your collections, environments, auth configs, and scripts from Postman, Insomnia v5, OpenAPI 3.0+, and OpenCollection.
