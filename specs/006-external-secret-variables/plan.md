# Implementation Plan: External Secret Variables

**Branch**: `006-external-secret-variables` | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-external-secret-variables/spec.md`

## Summary

This feature introduces external secret resolution for Clapi, enabling environments to reference secrets stored in HashiCorp Vault Server without committing sensitive credentials into collection files.

The implementation comprises four primary areas:
1. **Encrypted Token Storage**: Extend `PlatformStorage` with `putSecretString` and `getSecretString` backed by `eu.anifantakis:ksafe:3.3.0` using platform-native key custody (Keychain / DPAPI / Secret Service).
2. **Global Provider Configuration**: Add a new "Secrets Manager" tab in the global Settings dialog to configure HashiCorp Vault Server credentials (URL, mount path, namespace, encrypted token).
3. **Environment Secret Declarations**: Extend the OpenCollection v1.0.0 data model with `ExternalSecrets` and `HashicorpVaultExternalSecret`. Add an "External Secrets" tab in `EnvironmentEditDialog` to declare secrets (`name`, `path`, `disabled`, `description`).
4. **Resolution & Autocomplete**: Support `{{_secret.<name>}}` template interpolation across request URLs, headers, bodies, and auth. Provide `Ctrl+Space` suggestions showing available secrets. Execute read-only HTTP GET queries against Vault KV v2 API before request execution, caching by path to eliminate redundant network round-trips.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Encrypted Storage: `eu.anifantakis:ksafe:3.3.0`
- Network & HTTP Client: `io.ktor:ktor-client-core:3.1.2`, `io.ktor:ktor-client-content-negotiation`, `io.ktor:ktor-serialization-kotlinx-json`
- Serialization & Schema: `kotlinx.serialization:1.8.1`, OpenCollection v1.0.0
- UI: Compose Multiplatform 1.8.1 (Desktop)
- Testing: `kotlin.test`, JUnit 4, Docker Compose for HashiCorp Vault service fixture

**Storage**:
- Non-sensitive provider settings: Plain `PlatformStorage` (Java Preferences / ~/.clapi/pstore)
- Sensitive tokens: Encrypted `PlatformStorage` (`ksafe`)
- OpenCollection document: Environment `externalSecrets` block (declarations only; credentials excluded)

**Testing**:
- Unit tests for encrypted `PlatformStorage` in `ui-desktop` / `ui-shared`
- Unit tests for `HashiCorpVaultClient` with Ktor MockEngine in `core-network`
- OpenCollection serialization/deserialization tests in `core-storage`
- Variable resolution tests in `feature-requests`
- End-to-end integration tests in `qa-tests` against `qa-tests/secret-managers-compose.yml`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Autocomplete suggestions render in < 50ms.
- Vault secret resolution overhead < 100ms when server is local/responsive.
- Grouping by path ensures at most 1 network call per unique Vault path per request run.

**Constraints**:
- Strictly read-only interactions with Vault; no modifying/creating secrets on the remote provider.
- Zero credential leakage: Vault tokens and server URLs are NEVER exported into OpenCollection files.
- Masked password input for Vault tokens with show/hide toggle.

**Scale/Scope**:
- HashiCorp Vault Server (KV v2). Extensible architecture for future secret managers (AWS, Azure, GCP, 1Password).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**:
   - `core-model`: OpenCollection schema extensions (`ExternalSecrets`, `HashicorpVaultExternalSecret`).
   - `core-network`: Vault client interface, KV v2 wire models, and Ktor implementation.
   - `core-storage`: Serialization/deserialization ensuring credential-free collection export.
   - `feature-requests`: Asynchronous secret resolution pipeline integrated with `VariableResolver`.
   - `ui-shared`: Settings tab, Environment Editor tab, and `VariableHighlight` autocomplete suggestions.
2. **Security & Zero Leakage**:
   - Vault tokens stored strictly via `ksafe` encrypted platform storage.
   - Exported collections strictly omit machine-level provider credentials.
3. **Test-First Compliance**:
   - Automated tests with Ktor mock engine and Docker Compose integration fixture (`secret-managers-compose.yml`).

---

## Project Structure

### Documentation (this feature)

```text
specs/006-external-secret-variables/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── vault-client-contract.md
│   ├── platform-storage-secret-contract.md
│   └── opencollection-external-secrets-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt                 # Add ExternalSecrets and HashicorpVaultExternalSecret

core-network/
└── src/commonMain/kotlin/io/github/crustack/clapi/core/network/
    └── vault/
        ├── VaultModels.kt                           # VaultKvV2Response, VaultKvV2Data
        ├── HashiCorpVaultClient.kt                  # Interface & Ktor implementation
        └── SecretResolutionService.kt               # Path-batched secret retrieval & map extraction

core-storage/
└── src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/
    └── OpenCollectionSerializer.kt                  # Ensure externalSecrets serialization and validation

feature-requests/
└── src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/
    ├── HttpExecutionAdapter.kt                      # Support _secret. variable resolution layer
    └── RequestExecutionService.kt                   # Pre-fetch secrets before dispatching requests

ui-shared/
└── src/
    ├── commonMain/kotlin/io/github/crustack/clapi/ui/shared/
    │   ├── components/
    │   │   ├── SettingsDialog.kt                    # New Secrets Manager section & Vault fields
    │   │   ├── EnvironmentEditDialog.kt             # New External Secrets tab & table
    │   │   ├── VariableHighlight.kt                 # Ctrl+Space suggestions with _secret.*
    │   │   └── RequestExecutor.kt                   # Invoke secret resolution before send
    │   ├── persistence/
    │   │   ├── SettingsRepository.kt                # Load/save Vault settings & encrypted token
    │   │   └── OpenCollectionModelMapping.kt        # Map externalSecrets to/from EnvState
    │   ├── platform/
    │   │   └── PlatformApi.kt                       # putSecretString / getSecretString expectations
    │   └── state/
    │       └── AppState.kt                          # AppSettings vault fields & EnvState externalSecrets
    └── desktopMain/kotlin/io/github/crustack/clapi/ui/shared/platform/
        └── PlatformApi.desktop.kt                   # PlatformStorage actual implementation using ksafe

qa-tests/
└── src/test/kotlin/io/github/crustack/clapi/qa/
    └── ExternalSecretsIntegrationTest.kt            # Integration tests against local Vault container
```

---

## Complexity Tracking

*No constitutional violations; no complex justifications required.*
