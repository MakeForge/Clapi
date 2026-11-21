# Implementation Plan: Unify Request Execution, Variable & Secret Resolution, and Module Boundaries

**Branch**: `019-unify-request-execution` | **Date**: 2026-11-13 | **Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/019-unify-request-execution/spec.md)  
**Input**: Feature specification from `/specs/019-unify-request-execution/spec.md` and architectural analysis from [http-request-flow-evaluation.md](file:///home/user/IdeaProjects/Clapi/scratch/http-request-flow-evaluation.md)

## Summary

Consolidate the redundant `:feature-requests` module into `:core-network`, unify the request execution and variable/secret resolution pipeline across HTTP, GraphQL, and gRPC in `:core-network` and `:ui-shared`, refactor `SecretResolutionService` to use a pluggable `ExternalSecretProvider` strategy pattern with `SecretResolutionContext`, deduplicate OAuth2 token refresh logic into `OAuth2TokenManager`, and fix all 11 identified execution/variable/secret bugs with comprehensive unit, integration, and E2E regression tests.

## Technical Context

**Language/Version**: Kotlin 2.3.20 (Kotlin Multiplatform — JVM / Desktop target, Java 21 toolchain)  
**Primary Dependencies**:
- Compose Multiplatform `1.10.3` (`:ui-shared`)
- Ktor Client `3.4.2` (`:core-network`)
- Koin `4.2.0` (`:core-network`, `:ui-shared`)
- kotlinx-coroutines `1.10.2`, kotlinx-serialization-json `1.10.0`
- Rhino / GraalJS script engine (`:core-scripting`)
**Storage**: `PlatformStorage` / `SettingsRepository` (`:ui-shared`), OpenCollection YAML workspace files (`:core-storage`)  
**Testing**: `kotlin.test`, `kotlinx-coroutines-test`, JUnit 5, Ktor `MockEngine` (`:core-network`, `:core-scripting`, `:ui-shared`, `:qa-tests`)  
**Target Platform**: Desktop (JVM — Linux, macOS, Windows)  
**Project Type**: Kotlin Multiplatform desktop application  
**Performance Goals**: Zero redundant `HttpClient` allocations when `clapi.sendRequest()` is unused; at most 1 OAuth2 token refresh network call per request execution when token is expired; deterministic two-pass secret resolution without unnecessary provider calls when no new `{{_secret.*}}` tokens are introduced by scripts.  
**Constraints**: Preserve existing KMP module dependency DAG (`:core-network` depends only on `:core-model`, never on `:core-scripting` or `:ui-shared`); maintain 100% backward compatibility with OpenCollection YAML schemas and existing user settings (including fallback migration for legacy `"settings.*"` secret keys).  
**Scale/Scope**: Affects `:core-network`, `:core-scripting`, `:ui-shared`, `:qa-tests`, and removes `:feature-requests`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Clean Module Boundaries (DAG Compliance)**: PASS — Dissolving `:feature-requests` into `:core-network` removes a dead dependency edge (`:ui-shared -> :feature-requests`) while keeping `:core-network` free of `:core-scripting` or `:ui-shared` dependencies.
- **No Unverified Bug Fixes / Test-Driven Verification**: PASS — Every bug and code smell identified in the evaluation (`BUG-01` through `BUG-11`) is paired with explicit unit, integration, and E2E test suites in `:core-network`, `:core-scripting`, `:ui-shared`, and `:qa-tests`.
- **Resource & Memory Safety**: PASS — Per-request `KtorApiClient` instances and lazy `subReqClient` instances are deterministically closed in `finally` blocks.
- **Security & Secret Hygiene**: PASS — Secrets are never flattened as empty strings (`""`) over valid fallback variables, unresolved `{{_secret.*}}` placeholders are preserved for visibility, and masked logging remains enforced.

## Project Structure

### Documentation (this feature)

```text
specs/019-unify-request-execution/
├── plan.md                                      # This file (/speckit-plan output)
├── research.md                                  # Phase 0 architectural decisions & bug-test matrix
├── data-model.md                                # Phase 1 data models, layer precedence & state rules
├── quickstart.md                                # Phase 1 build & test execution guide
├── checklists/
│   └── requirements.md                          # Spec quality checklist (16/16 passed)
└── contracts/
    ├── execution-pipeline-contract.md           # Two-pass pipeline & protocol parity contract
    ├── secret-provider-spi.md                   # ExternalSecretProvider & SecretResolutionContext SPI
    └── oauth2-token-manager-contract.md         # Shared OAuth2TokenManager contract
```

### Source Code (repository root)

```text
# 1. Module Removal (:feature-requests dissolved into :core-network)
settings.gradle.kts                              # MODIFY: Remove include(":feature-requests")
feature-requests/                                # DELETE: Entire module directory removed after migration

# 2. Core Network (:core-network)
core-network/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
│   ├── ApiClient.kt                             # MODIFY: Add close() / AutoCloseable default method
│   ├── KtorApiClient.kt                         # MODIFY: Implement close(), remove WebSocket "hello" stub
│   ├── NetworkDiModule.kt                       # MODIFY: Register ExternalSecretProviders, OAuth2TokenManager, RequestExecutionService
│   ├── dynamicvar/
│   │   └── OAuth2DynamicVariableProvider.kt     # MODIFY: Delegate token acquisition to OAuth2TokenManager
│   ├── oauth2/
│   │   └── OAuth2TokenManager.kt                # NEW: Shared Mutex-backed OAuth2 expiry/refresh/auto-fetch manager
│   ├── execution/
│   │   ├── HttpExecutionAdapter.kt              # MOVE+MODIFY from :feature-requests: Full RequestDefinition resolution
│   │   ├── RequestExecutionService.kt           # MOVE+MODIFY from :feature-requests: Two-pass resolution, extractTemplateStrings(), Layer 0 script vars
│   │   └── RequestTemplateExtractor.kt          # NEW: RequestDefinition.extractTemplateStrings() & GrpcCallRequest.extractTemplateStrings()
│   └── vault/
│       ├── SecretResolutionContext.kt           # NEW: Context + per-provider config data classes
│       ├── ExternalSecretProvider.kt            # NEW: Strategy interface + 6 provider implementations (Vault, AWS, Azure, GCP, Keeper, KeePassXC)
│       └── SecretResolutionService.kt           # MODIFY: Replace 27-param methods with SecretResolutionContext & ExternalSecretProvider list; use VariableResolver in custom filters
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/network/
    ├── execution/
    │   ├── HttpExecutionAdapterTest.kt          # MOVE+EXPAND from :feature-requests (BUG-07)
    │   └── RequestExecutionServiceSecretsTest.kt # MOVE+EXPAND from :feature-requests (BUG-01, BUG-02, BUG-03, BUG-07)
    ├── oauth2/
    │   └── OAuth2TokenManagerTest.kt            # NEW: Deduplication & refresh/auto-fetch tests (BUG-09)
    ├── vault/
    │   └── SecretResolutionServiceTest.kt       # MODIFY/EXPAND: Strategy providers & custom filter whitespace interpolation (BUG-08)
    └── KtorApiClientLifecycleTest.kt            # NEW: close() lifecycle & WebSocket UnsupportedOperationException test (BUG-10)

# 3. Core Scripting (:core-scripting)
core-scripting/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/scripting/
│   └── ClapiScriptEngine.kt                     # MODIFY: Safe JSON string escaping via JsonPrimitive(rawBody).toString() (BUG-11)
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/scripting/
    └── ClapiScriptEngineTest.kt                 # MODIFY: Add sendRequest callback tests with \t, \u2028, \u2029, quotes, and control chars (BUG-11)

# 4. Shared UI & Orchestration (:ui-shared)
ui-shared/
├── build.gradle.kts                             # MODIFY: Remove api(project(":feature-requests"))
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
│   ├── state/
│   │   ├── EnvironmentModels.kt                 # MODIFY: Fix EnvState.toVariableMap() to exclude blank secret:true placeholders (BUG-02)
│   │   └── AppState.kt                          # MODIFY: Add per-collection variable storage & isolation helpers (BUG-04)
│   ├── persistence/
│   │   └── SettingsRepository.kt                # MODIFY: Add SettingsStorageKeys constants with legacy fallback support (BUG-05)
│   └── components/
│       ├── GrpcRequestEditor.kt                 # MODIFY: Apply tab.authType (Basic, Bearer, JWT, API Key, OAuth2, Inherit) to gRPC metadata in resolveGrpcCallRequest (BUG-06d)
│       └── executor/
│           ├── RequestVariableResolution.kt     # MODIFY: Build SecretResolutionContext via SettingsStorageKeys, delegate OAuth2 to OAuth2TokenManager, two-pass helper
│           ├── RequestExecutionPipeline.kt      # NEW: Shared pre/post script + lazy subReqClient + variable layer assembly + setNextRequest helpers
│           ├── RequestExecutor.kt               # MODIFY: Use unified pipeline, include requestScopedScriptVars at Layer 0, close KtorApiClient in finally (BUG-01, BUG-03, BUG-10)
│           ├── GraphQlRequestExecutor.kt        # MODIFY: Use unified pipeline, Layer 0 script vars, honor tab.retryEnabled, fix GET double-resolution, propagate setNextRequest, close client (BUG-01, BUG-06a/b/c, BUG-10)
│           └── GrpcRequestExecutor.kt           # MODIFY: Use unified pipeline, two-pass secrets, lazy subReqClient, propagate setNextRequest (BUG-03, BUG-06c, BUG-10)
└── src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── state/
    │   ├── EnvironmentModelsTest.kt             # NEW: Verify secret:true blank value does not shadow lower layers (BUG-02)
    │   └── CollectionVariablesIsolationTest.kt  # NEW: Verify Collection A vs Collection B isolation (BUG-04)
    ├── persistence/
    │   └── SettingsStorageKeysTest.kt           # NEW: Verify canonical "clapi.settings.*" keys & legacy "settings.*" fallback (BUG-05)
    └── components/executor/
        ├── RequestExecutionPipelineTest.kt      # NEW: Verify Layer 0 requestScopedScriptVars on wire, two-pass post-script secret resolution, lazy subReqClient (BUG-01, BUG-03, BUG-10)
        ├── GraphQlRequestExecutorTest.kt        # NEW: Verify retryEnabled, single-pass GET resolution, setNextRequest, Layer 0 script vars (BUG-01, BUG-06a, BUG-06b, BUG-06c)
        └── GrpcRequestExecutorTest.kt           # NEW: Verify gRPC auth metadata injection (Bearer/Basic/APIKey/OAuth2/Inherit) & setNextRequest (BUG-06c, BUG-06d)

# 5. End-to-End QA Tests (:qa-tests)
qa-tests/
├── build.gradle.kts                             # MODIFY: Remove implementation(project(":feature-requests"))
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    ├── ApiClientE2ETest.kt                      # MODIFY: Update imports to :core-network, add E2E tests for BUG-01, BUG-02, BUG-03, BUG-04
    ├── ExternalSecretsIntegrationTest.kt        # MODIFY: Update imports to :core-network, add E2E tests for BUG-02, BUG-03, BUG-07
    └── [6 other *IntegrationTest / *E2ETest.kt] # MODIFY: Update RequestExecutionService imports to :core-network
```

**Structure Decision**: Consolidate `:feature-requests` into `:core-network.execution` and centralize UI/script protocol orchestration in `:ui-shared.components.RequestExecutionPipeline` so `:core-network` stays strictly transport/resolution-focused and `:ui-shared` eliminates the 3x protocol duplication across HTTP, GraphQL, and gRPC.

## Complexity Tracking

No constitution violations introduced. Module count is **reduced** by 1 (`:feature-requests` removed), parameter count on `SecretResolutionService` drops from 27 to 2 (`texts`, `context`), and duplicated OAuth2 + execution pipeline code is unified.
