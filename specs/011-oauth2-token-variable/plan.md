# Implementation Plan: Dynamic OAuth2 Access Token Variable

**Branch**: `011-oauth2-token-variable` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-oauth2-token-variable/spec.md`

## Summary

This feature implements an extensible dynamic runtime variable framework supporting special runtime variables, starting with `{{_auth.oauth2.accessToken}}` across all request types (HTTP and gRPC). When OAuth2 authentication is configured (directly or inherited), referencing `{{_auth.oauth2.accessToken}}` in request URLs, query parameters, headers, bodies, or gRPC metadata triggers runtime validation and token acquisition/refresh prior to request execution. The resolved access token is substituted into the request, and the fresh token state is preserved in UI session memory. A dedicated gRPC integration test verifies that setting `authorization: Bearer {{_auth.oauth2.accessToken}}` in gRPC metadata accurately resolves the token on the wire. The underlying API is built on a modular `DynamicVariableProvider` and `DynamicVariableRegistry` pattern, ensuring additional dynamic runtime variables can be added effortlessly in the future without modifying request execution coordinators.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 target)

**Primary Dependencies**:
- Kotlin Coroutines & Flows
- Ktor Client 3.x (`ktor-client-core`, `OAuth2TokenClient`)
- gRPC dynamic client (`NettyDynamicGrpcClient`, `DynamicGrpcClient`)
- Compose Multiplatform 1.8.1 (Desktop UI)
- `kotlinx.datetime`

**Storage**:
- In-memory runtime caching (`RequestTabState.authOAuth2*`) for active access/refresh/ID tokens and expiration timestamps.
- No new persistent on-disk schema additions needed; dynamic variables adhere to OpenCollection template variable syntax (`{{...}}`).

**Testing**:
- Unit tests for dynamic variable provider and registry in `core-network`
- Component and pipeline tests for HTTP and gRPC token resolution in `ui-shared`
- End-to-end integration tests in `qa-tests` verifying gRPC `authorization` metadata resolution against `DummyApiServer`

**Target Platform**: Multiplatform Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Dynamic variable scanning and matching <2ms per request.
- Token resolution reuses valid cached tokens without redundant HTTP round-trips.
- Token refresh or acquisition completes within standard network latency (<500ms).

**Constraints**:
- Strict failure if `{{_auth.oauth2.accessToken}}` is referenced when OAuth2 is not configured/enabled; zero unauthenticated requests sent.
- Ephemeral tokens must never leak to persisted collection files.
- Thread-safe token acquisition and caching across concurrent request executions.

**Scale/Scope**:
- Protocol-agnostic resolution applicable to any HTTP or gRPC request in the application.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architecture adheres to:
1. **Modularity & Layering**: Core dynamic variable interfaces (`DynamicVariableProvider`, `DynamicVariableRegistry`, `OAuth2DynamicVariableProvider`) reside in `core-network`. UI integration, tab state binding, and request execution logic reside in `ui-shared`. End-to-end integration tests reside in `qa-tests`.
2. **Extensibility & OCP**: New dynamic runtime variables can be added simply by implementing `DynamicVariableProvider` and registering with `DynamicVariableRegistry`.
3. **Data Integrity & Safety**: Fast-fail behavior prevents broken or unauthenticated requests from transmitting over the network if OAuth2 configuration or token acquisition fails.

---

## Project Structure

### Documentation (this feature)

```text
specs/011-oauth2-token-variable/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: dynamic variable provider architecture, gRPC integration
├── data-model.md        # Phase 1 output: DynamicVariableProvider, Context, Registry, and GrpcEditorTab
├── quickstart.md        # Phase 1 output: verification scenarios and test commands
├── contracts/           # Phase 1 output: component & behavior contracts
│   ├── DynamicVariableContract.md
│   └── GrpcExecutionAuthContract.md
└── checklists/
    └── requirements.md  # Requirements quality checklist
```

### Source Code (repository root)

```text
core-network/src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
├── dynamicvar/
│   ├── DynamicVariableProvider.kt       # Interface for dynamic variable providers
│   ├── DynamicVariableContext.kt        # Context carrying effective auth, token client, and state
│   ├── DynamicVariableRegistry.kt       # Registry managing providers and text scanning
│   ├── DynamicVariableException.kt      # Exceptions thrown on dynamic variable resolution failure
│   └── OAuth2DynamicVariableProvider.kt # Provider resolving _auth.oauth2.accessToken
└── VariableResolver.kt                  # Template variable interpolator

core-network/src/commonTest/kotlin/io/github/makeforge/clapi/core/network/
└── dynamicvar/
    ├── DynamicVariableRegistryTest.kt   # Unit tests for registry matching and resolution
    └── OAuth2DynamicVariableProviderTest.kt # Unit tests for OAuth2 token provider

ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
├── components/
│   ├── RequestExecutor.kt               # Dynamic variable pre-execution hook in HTTP & gRPC pipelines
│   ├── GrpcRequestEditor.kt             # Adds Auth tab to GrpcEditorTabBar, renders AuthEditor
│   └── VariableHighlight.kt             # Autocomplete suggestion & syntax highlighting for _auth.
└── state/
    └── AppState.kt                      # Adds AUTH to GrpcEditorTab

ui-shared/src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/
└── DynamicVariableExecutionPipelineTest.kt # Integration test for gRPC & HTTP dynamic variable execution

qa-tests/src/test/kotlin/io/github/makeforge/clapi/qa/
└── GrpcApiE2ETest.kt                    # E2E test verifying authorization header resolution with DummyApiServer
```

**Structure Decision**: Placed core provider interfaces and registry in `core-network` so they remain independent of the UI and reusable across any network clients or execution adapters. Wired the provider into `RequestExecutor.kt` in `ui-shared`, updating both HTTP and gRPC pipelines uniformly.

---

## Complexity Tracking

> No constitutional violations or unjustified complexity introduced.
