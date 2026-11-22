# Implementation Plan: Release-Ready E2E & UI Verification Suite

**Branch**: `034-release-e2e-test-suite` | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/034-release-e2e-test-suite/spec.md`

## Summary

Close all release-blocking test, CI quality-gate, packaged-runtime smoke, and interactive UI E2E gaps identified in [scratch/test-evalution.md](../../scratch/test-evalution.md) to get Clapi 1.0.0 ready for release. Specifically:
1. Expand `.github/workflows/quality-check.yml` and `.github/workflows/release.yml` to run all 10 test modules (including `:ui-desktop:desktopTest` on PRs), fail hard on unreachable Docker services in CI, re-enable `McpClientHandshakeTest`, and verify both the fat JAR and jlink-trimmed native distributables across storage, Javet V8 scripting, embedded MCP HTTP/stdio servers, and legal files.
2. Build a reusable in-process UI E2E harness (`EmbeddedSampleServerRule`, `EmbeddedGrpcTestServerRule`, `IsolatedWorkspaceRule`) inside `ui-desktop/src/desktopTest` and implement full interactive `MainScreen` UI E2E suites covering every protocol (HTTP, SSE/LLM, GraphQL, WebSocket, gRPC, MCP Client, RabbitMQ), all previously untested dialogs/settings/shortcuts/undo/multi-close workflows, security secret-redaction guarantees across all UI surfaces, and resilience/layout/round-trip edge cases.
3. Commit initial Roborazzi Compose Desktop preview visual baselines and enforce `:ui-desktop:verifyRoborazziDesktop` on Linux CI.

## Technical Context

**Language/Version**: Kotlin 2.1.21 (KMP targeting `jvm("desktop")`), JDK 25 (Temurin 25)  
**Primary Dependencies**: Compose Multiplatform 1.8.1, Ktor 3.1.2 (client + embedded Netty server via `:sample-server`), gRPC Netty Shaded + Protobuf (`:core-network`), Javet V8 (`:core-scripting`), RabbitMQ AMQP client, Nucleus 3.0.0, Roborazzi Desktop Preview Scanner  
**Storage**: OpenCollection JSON/YAML files (`DefaultCanonicalCollectionRepository`), `PlatformStorage` (JVM Preferences / isolated temp storage), KSafe encrypted secret storage  
**Testing**: JUnit 4 + `kotlin.test` + `compose-ui-test-junit4-desktop` (`createComposeRule`) + `kotlinx-coroutines-test` + embedded `:sample-server` + embedded gRPC server + Docker Compose (`qa-tests/docker/`) + Roborazzi  
**Target Platform**: Desktop JVM (Linux, macOS, Windows); CI verification on `ubuntu-latest` (`xvfb-run`), packaging & distributable smoke verification across `ubuntu-latest`, `macos-latest`, and `windows-latest`  
**Project Type**: Multi-module Kotlin Multiplatform Desktop Application (11 Gradle modules)  
**Performance Goals**: New UI E2E suite executes deterministically in <60s locally using shared per-class embedded servers (`@ClassRule`); packaged smoke check finishes in <15s per OS runner  
**Constraints**: Zero external internet dependencies; offline-capable deterministic local test servers; zero leaked preferences/temp files between tests; zero regressions in existing 2,115+ tests  
**Scale/Scope**: 10 tested Gradle modules, 7 protocol workspaces (HTTP, SSE/LLM, GraphQL, WebSocket, gRPC, MCP, RabbitMQ), 7 new UI E2E test suites, 2 CI workflow updates, 1 expanded packaged smoke runner, 1 Roborazzi baseline set

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Note: `.specify/memory/constitution.md` currently contains only the default placeholder template; applying project governance from `AGENTS.md`, `DEVELOPMENT.md`, and `.github/copilot-instructions.md`:
- **Module Architecture Rule** (`DEVELOPMENT.md`): `ui-desktop` depends on `ui-shared` (and `sample-server` in `desktopTest` only); `ui-shared` depends on core modules; core modules have no UI dependencies. **PASS** — all new UI E2E tests and harnesses live in `ui-desktop/src/desktopTest` without altering production module boundaries.
- **UI Development Guidelines** (`DEVELOPMENT.md`): Any added `testTag` modifiers or bug fixes in `ui-shared` preserve modifier hygiene and desktop ergonomics. **PASS**.
- **Build & Test Command Rule** (`.github/copilot-instructions.md`): Use `rtk gradlew ...` for all local Gradle runs. **PASS**.
- **Changelog & Versioning Policy** (`AGENTS.md`): Update `CHANGELOG.md` under `## [1.0.0] - TBD` upon implementing the feature/fixes (no version bump needed since `1.0.0` is unreleased `TBD`). **PASS**.
- **Post-Design Re-Check**: All Phase 1 contracts (`ci-and-smoke-contracts.md`, `ui-e2e-test-contracts.md`), `data-model.md`, and `quickstart.md` adhere strictly to module boundaries, offline determinism, and existing Gradle/Compose conventions. **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/034-release-e2e-test-suite/
├── plan.md                                # This file (/speckit-plan output)
├── research.md                            # Phase 0 research & technical decisions
├── data-model.md                          # Phase 1 harness models, redaction & baseline matrices
├── quickstart.md                          # Phase 1 end-to-end validation guide
├── contracts/
│   ├── ci-and-smoke-contracts.md          # CI matrix + packaged/distributable smoke CLI contracts
│   └── ui-e2e-test-contracts.md           # Complete UI E2E suite & scenario contracts
└── tasks.md                               # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
.github/workflows/
├── quality-check.yml                      # Update: run all 10 modules + Roborazzi + Docker lifecycle
└── release.yml                            # Update: run all 10 modules + Roborazzi + verifyDistributableRuntime on all 3 OSes

core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/mcp/
└── McpClientHandshakeTest.kt              # Update: remove @Ignore & fix coroutine/teardown determinism

qa-tests/src/test/kotlin/io/github/makeforge/clapi/
├── testsupport/CiTestGuard.kt             # New: fail hard on CI=true, Assume.assumeTrue locally
├── qa/OAuth2IntegrationTest.kt            # Update: use CiTestGuard
└── rabbitmq/RabbitMqEndToEndTest.kt       # Update: use CiTestGuard

ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
├── components/
│   ├── executor/SecretLogRedactor.kt      # Audit & harden: include inherited env & global secrets
│   ├── executor/RequestCommandExporter.kt # Audit: verify/enforce secret redaction contract
│   ├── CrashReportDialog.kt               # Audit: ensure secret redaction in details/URL if needed
│   ├── RabbitMqRequestEditor.kt           # Add minimal missing testTags if needed
│   ├── ResponseViewer.kt                  # Add minimal missing testTags if needed
│   └── dialogs/settings/ThemeSettingsPane.kt # Add minimal missing testTags if needed

ui-desktop/
├── build.gradle.kts                       # Update: strengthen verifyPackagedRuntime + add verifyDistributableRuntime + Roborazzi snapshot dir
├── src/desktopMain/kotlin/io/github/makeforge/clapi/ui/desktop/
│   └── Main.kt                            # Update: add --packaged-smoke-test mode (storage + Javet V8 + dispose)
└── src/desktopTest/
    ├── snapshots/roborazzi/               # New: committed golden visual baseline PNGs
    └── kotlin/io/github/makeforge/clapi/ui/desktop/e2e/
        ├── support/
        │   ├── EmbeddedSampleServerRule.kt
        │   ├── EmbeddedGrpcTestServerRule.kt
        │   └── IsolatedWorkspaceRule.kt
        ├── HttpRequestUiE2ETest.kt
        ├── ScriptingAndVariablesUiE2ETest.kt
        ├── StreamingAndRealtimeUiE2ETest.kt
        ├── McpAndGrpcUiE2ETest.kt
        ├── RabbitMqUiE2ETest.kt
        ├── DialogsSettingsAndShortcutsUiTest.kt
        └── ResilienceLayoutAndRoundTripUiTest.kt
```

**Structure Decision**: Consolidate all new Compose UI E2E tests and shared in-process server/workspace JUnit rules under `ui-desktop/src/desktopTest/.../e2e/` because `ui-desktop` already owns all Compose Desktop UI tests, Roborazzi configuration, and the `:sample-server` + Netty `desktopTest` dependency. Keep container fail-fast guards in `qa-tests` and packaged runtime verification in `ui-desktop/build.gradle.kts` + `Main.kt`.

### Documentation Impact (`docs/`)

- **Impact**: None — Inspected `docs/.vitepress/config.mts`; the VitePress site is strictly an end-user product guide (Getting Started, Request Types, Organization, Automation, Settings, About) and does not cover internal test suites, CI quality gates, or build-time smoke tests. No end-user behavior or feature surface is added or altered by this verification suite (any developer test-command notes belong in `DEVELOPMENT.md` if needed, not `docs/`).

## Complexity Tracking

No constitution or architectural violations. All additions live strictly in test source sets, CI workflows, build verification tasks, and minimal non-breaking smoke flags / `testTag` hooks.
