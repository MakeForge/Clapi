---
title: Testing Strategy & Verification
description: Automated test architecture, unit tests, JVM integration tests, and UI screenshot tests in Clapi.
---

# Testing Strategy & Verification

<p class="badges">
  <span class="badge badge-protocol">Quality Assurance</span>
  <span class="badge badge-version">JUnit4 & Roborazzi</span>
</p>

## Overview

Clapi maintains a rigorous testing strategy spanning unit, integration, and visual regression layers.

---

## Testing Matrix

| Test Suite | Module | Role & Scope | Execution Command |
|---|---|---|---|
| **Core Protocol Tests** | `core-network` | Validates HTTP verbs, dynamic variable interpolation, WebSocket streaming, and OAuth flows. | `./gradlew :core-network:allTests` |
| **Storage Persistence** | `core-storage` | Validates OpenCollection JSON/YAML serialization and repository roundtrips. | `./gradlew :core-storage:allTests` |
| **JVM Integration & E2E**| `qa-tests` | Runs full live requests against an embedded sample Ktor server with zero external dependencies. | `./gradlew :qa-tests:jvmTest` |
| **Desktop UI Tests** | `ui-desktop` | Compose Desktop smoke tests validating tab management, layouts, and dialogs. | `./gradlew :ui-desktop:desktopTest` |
| **Screenshot Verification**| `ui-desktop` | Roborazzi visual regression tests verifying pixel-perfect UI rendering. | `./gradlew :ui-desktop:recordRoborazziDesktop` |

---

## Running the Sample Server Locally

For manual exploratory testing, the `sample-server` module starts an embedded Ktor server with dozens of test endpoints:

```bash
./gradlew :sample-server:run
```
Default URL: `http://localhost:8080`. Supports mock REST endpoints, OpenAI-compatible chat completions streams, MCP streamable endpoints, and WebSocket echo.
