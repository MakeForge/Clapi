# Quickstart & Verification Guide: Unify Request Execution, Variable & Secret Resolution, and Module Boundaries

**Feature**: `019-unify-request-execution`  
**Date**: 2026-11-13  
**Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/019-unify-request-execution/spec.md)

---

## 1. Build & Module Consolidation Verification

Verify that `:feature-requests` has been completely dissolved into `:core-network` and no stale module references remain:

```bash
# 1. Verify no Gradle references to :feature-requests remain
rtk grep "feature-requests" settings.gradle.kts ui-shared/build.gradle.kts qa-tests/build.gradle.kts

# 2. Compile and run tests across all affected modules
rtk gradlew :core-network:test :core-scripting:test :ui-shared:desktopTest :qa-tests:test
```

---

## 2. Targeted Bug-Regression Test Commands

Every bug and architectural issue identified in [http-request-flow-evaluation.md](file:///home/user/IdeaProjects/Clapi/scratch/http-request-flow-evaluation.md) is verified by a dedicated automated test suite:

### A. Core Network Unit Tests (`:core-network`)
Covers:
- **BUG-02 & BUG-03**: Two-pass secret resolution (`secret: true` environment variables + post-script secret tokens).
- **BUG-07**: `RequestDefinition.extractTemplateStrings()` scanning header keys, query param keys, cookies, `auth.params`, `formEntries`, `formDataEntries`, and `graphQl` fields.
- **BUG-08**: Custom secret filter interpolation using `VariableResolver.resolve` (handling `{{ var }}` with whitespace).
- **BUG-09**: `OAuth2TokenManager` deduplicating token refresh when both `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}` are used.
- **BUG-10**: `KtorApiClient.close()` lifecycle and explicit `UnsupportedOperationException` on `ProtocolType.WEBSOCKET`.
- **Strategy Pattern**: All 6 `ExternalSecretProvider` implementations (`Vault`, `AWS`, `Azure`, `GCP`, `Keeper`, `KeePassXC`) via `SecretResolutionContext`.

```bash
rtk gradlew :core-network:test \
  --tests "*HttpExecutionAdapterTest*" \
  --tests "*RequestExecutionServiceSecretsTest*" \
  --tests "*SecretResolutionServiceTest*" \
  --tests "*OAuth2TokenManagerTest*" \
  --tests "*KtorApiClientLifecycleTest*"
```

### B. Scripting Engine Unit Tests (`:core-scripting`)
Covers:
- **BUG-11**: `ClapiScriptEngine` safe JSON string escaping (`JsonPrimitive(rawBody).toString()`) for `clapi.sendRequest()` callbacks containing `\t`, `\u2028`, `\u2029`, quotes, backslashes, and control characters.

```bash
rtk gradlew :core-scripting:test --tests "*ClapiScriptEngineTest*"
```

### C. UI Shared Pipeline, State & Protocol Parity Tests (`:ui-shared`)
Covers:
- **BUG-01**: `clapi.variables.set("ephemeralKey", "val")` (`requestScopedScriptVars`) applied at Layer 0 to the actual outgoing HTTP, GraphQL, and gRPC requests.
- **BUG-02**: `EnvState.toVariableMap()` excluding blank `secret: true` entries so lower-priority folder/collection/global variables are not shadowed by `""`.
- **BUG-04**: `clapi.collectionVariables.set(k, v)` isolated per active collection (`Collection A` vs `Collection B`).
- **BUG-05**: `SettingsStorageKeys` canonical `"clapi.settings.*"` key resolution with legacy `"settings.*"` fallback.
- **BUG-06a**: GraphQL honoring `tab.retryEnabled`.
- **BUG-06b**: GraphQL `GET` single-pass variable resolution (no double-interpolation when variables contain literal `{{...}}`).
- **BUG-06c**: GraphQL and gRPC post-request scripts propagating `clapi.execution.setNextRequest("NextReq")`.
- **BUG-06d**: gRPC UI request execution applying `tab.authType` (`Bearer`, `Basic`, `API Key`, `OAuth2`, `Inherit`) to gRPC call metadata.
- **BUG-10**: Lazy `subReqClient` instantiation only when `clapi.sendRequest()` is invoked by a script.

```bash
rtk gradlew :ui-shared:desktopTest \
  --tests "*RequestExecutionPipelineTest*" \
  --tests "*EnvironmentModelsTest*" \
  --tests "*CollectionVariablesIsolationTest*" \
  --tests "*SettingsStorageKeysTest*" \
  --tests "*GraphQlRequestExecutorTest*" \
  --tests "*GrpcRequestExecutorTest*"
```

### D. End-to-End Pipeline Tests (`:qa-tests`)
Covers:
- Full multi-layer execution pipeline (`RequestScopedScriptVars > DynamicVars > ResolvedSecrets > Environment > Folders > Collection > Global`) with pre-request script secret injection, post-request assertions, and collection variable isolation.

```bash
rtk gradlew :qa-tests:test \
  --tests "*ApiClientE2ETest*" \
  --tests "*ExternalSecretsIntegrationTest*"
```
