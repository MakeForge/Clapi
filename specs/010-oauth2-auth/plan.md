# Implementation Plan: OAuth2 Authentication

**Branch**: `010-oauth2-auth` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-oauth2-auth/spec.md`

## Summary

This feature implements full OAuth 2.0 authentication support across Clapi, aligning with OpenCollection data models and standard RFC 6749 grant types. Users can select and configure OAuth 2.0 in the Auth Editor for requests, folders, and collections with support for Client Credentials and Resource Owner Password flows, including credential placement in headers vs. body. All credential fields support dynamic template variables and external secrets. An interactive "Fetch Tokens" panel in the Auth Editor enables on-demand token acquisition and displays the Access Token, Refresh Token, and Identity Token in copyable text fields. During HTTP request execution, the pipeline inspects active access token validity (evaluating expiration with clock-skew protection and JWT claim fallback), automatically fetching or refreshing tokens via Ktor HTTP client before injecting `Authorization: Bearer <token>`. An automated end-to-end integration test suite verifies the functionality against a local Keycloak instance.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 target)

**Primary Dependencies**:
- Ktor Client 3.x (`ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`)
- Compose Multiplatform 1.8.1 (Desktop UI)
- `kotlinx.serialization` (JSON polymorphic OpenCollection serialization)
- Kotlin Coroutines & Flows
- Testing: JUnit 4, `kotlin.test`, Ktor Server Netty (for mock/echo endpoints), Keycloak Docker Compose

**Storage**:
- Canonical OpenCollection format (`core-model` / `OpenCollectionAuth.kt`) persisting `AuthOAuth2` configurations.
- In-memory runtime caching for active access/refresh/ID tokens and expiration timestamps during user sessions.

**Testing**:
- Unit tests for token parsing, validity calculation, and credential placement in `core-network`
- Unit tests for OpenCollection mapping and state management in `ui-shared`
- Desktop Compose UI tests for Auth Editor OAuth2 controls, password masking, and token copy actions in `ui-desktop`
- End-to-end integration tests in `qa-tests` validating Client Credentials, Resource Owner Password, and auto-refresh against Keycloak (`qa-tests/keycloak-oauth2-compose.yml`)

**Target Platform**: Multiplatform Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Token acquisition round-trip <500ms under standard network conditions.
- In-flight validity check <1ms per request without unnecessary network round-trips when tokens remain valid.
- Smooth UI transitions with responsive token copy and progress indicators.

**Constraints**:
- Strict adherence to OpenCollection v1.0.0 JSON schema for `AuthOAuth2`.
- Ephemeral tokens (access/refresh/ID tokens) must not leak to persisted collection files on disk.
- Client secret and password fields must support variable templates (`{{secret}}`) while preventing plaintext screen shoulder-surfing via password masking toggles.

**Scale/Scope**:
- Supports arbitrary numbers of requests and collections with independent or inherited OAuth2 configurations.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architecture adheres to:
1. **Modularity & Layering**: OpenCollection definitions in `core-model`, network token client and validity checks in `core-network`, UI state and editor in `ui-shared`, and automated integration tests in `qa-tests`.
2. **Standard Interoperability**: Implements RFC 6749 and OpenCollection v1.0.0 standards, fully interoperable with industry OAuth2 providers (Keycloak, Auth0, Okta, etc.).
3. **Data Integrity & Security**: Sensitive credentials use variable/secret resolution without committing ephemeral tokens to disk.

---

## Project Structure

### Documentation (this feature)

```text
specs/010-oauth2-auth/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: decisions, flow support, validity rules
├── data-model.md        # Phase 1 output: AuthOAuth2, OAuth2TokenState, UI state mapping
├── quickstart.md        # Phase 1 output: UI verification and Keycloak test guide
├── contracts/           # Phase 1 output: component & behavior contracts
│   ├── OAuth2TokenClientContract.md
│   ├── AuthEditorOAuth2Contract.md
│   └── OAuth2ExecutionContract.md
└── checklists/
    └── requirements.md  # Requirements quality checklist
```

### Source Code (repository root)

```text
core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/
└── OpenCollectionAuth.kt                  # Canonical AuthOAuth2, OAuth2Flow, credentials models

core-network/src/commonMain/kotlin/io/github/crustack/clapi/core/network/
├── oauth2/
│   ├── OAuth2TokenClient.kt              # Ktor HTTP client for token requests (client_credentials, password, refresh)
│   ├── OAuth2TokenResponse.kt            # RFC 6749 response model
│   └── OAuth2TokenState.kt               # Token state & validity checking (expires_in & JWT exp claim)
└── KtorApiClient.kt                       # Injects Authorization Bearer header from resolved token

ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/
├── components/
│   ├── AuthEditor.kt                     # OAuth2 flow selector, credential fields, Fetch Tokens button & token displays
│   └── RequestExecutor.kt                # Token pre-flight check, auto-refresh, and execution integration
├── persistence/
│   └── OpenCollectionModelMapping.kt     # Maps AuthOAuth2 to/from RequestTabState and CollectionNode
└── state/
    └── AppState.kt                       # OAuth2 properties on RequestTabState & CollectionNode

qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/
└── OAuth2IntegrationTest.kt              # E2E Keycloak OAuth2 test suite (client_credentials, password, auto-refresh)
```

**Structure Decision**: Standard multi-module layout following existing conventions (`core-model`, `core-network`, `ui-shared`, `qa-tests`).

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
