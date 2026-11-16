# Implementation Plan: Keeper Security Cloud Secret Manager Integration

**Branch**: `018-keeper-secret-manager` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/018-keeper-secret-manager/spec.md`

## Summary

Implement Keeper Security Cloud (`keeper-security-cloud`) as an external secret manager provider in Clapi, matching the architecture of HashiCorp Vault, KeePassXC, AWS Secrets Manager, Azure Key Vault, and GCP Secret Manager. Users configure Keeper in Settings -> Secrets Manager by inputting a one-time token (OTT) and clicking "Connect", which redeems the token via Keeper Secrets Manager (KSM) SDK, binds the storage, and persists the resulting client configuration encrypted in KSafe (`PlatformStorage.putSecretString`). In the Environment Editor, users define external secrets with Record Title/UID, Target Field (defaulting to `"password"`), Status indicator, and custom key-value filters with default key hint `'url'`. At runtime, `SecretResolutionService` resolves secrets dynamically, supporting standard types (`login`, `serverCredentials`), custom types (`OAuth Client Credentials`), dotted host properties, and Keeper Notation. Memory and performance optimizations ensure zero static overhead when Keeper is unused and automatic client cleanup upon environment switching (`KeeperClientManager`). Offline end-to-end testing in `qa-tests` leverages Kotlin compiler `friendPaths` to access the SDK's internal `TestStubs` and validates all record types against `test-records.json`.

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.1.21 / JVM Toolchain 21

**Primary Dependencies**: Keeper Secrets Manager Core SDK (`com.keepersecurity.secrets-manager:core:17.2.0`), Ktor Client (`io.ktor:ktor-client-core`), Kotlinx Serialization JSON (`kotlinx-serialization-json`), KSafe (`eu.anifantakis:ksafe:3.3.0`), Compose Multiplatform 1.8.1

**Storage**: Encrypted secure storage via KSafe (`PlatformStorage.putSecretString` / `getSecretString`)

**Testing**: JUnit 4 / Kotlin Test (`kotlin.test`), Coroutines Test (`kotlinx-coroutines-test`), Ktor Client MockEngine, Kotlin compiler friend paths in `qa-tests` for Keeper SDK `TestStubs`

**Target Platform**: Desktop JVM (macOS, Linux, Windows)

**Project Type**: Multi-module desktop application with shared Compose UI and Kotlin Multiplatform core

**Performance Goals**: Secret resolution batching completes in <250ms; zero static crypto loading on startup; client teardown in <10ms on environment switch

**Constraints**: Zero credential or one-time token leakage in files or logs; one-time token is never persisted; no static BouncyCastle requirement (standard JVM JCE); offline deterministic E2E testing without external network or Docker containers

**Scale/Scope**: 5 core modules (`core-model`, `core-network`, `core-storage`, `feature-requests`, `ui-shared`) plus `qa-tests`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Modular Architecture**: Changes align with established boundaries: models in `core-model`, networking & KSM SDK logic in `core-network`, schema in `core-storage`, execution in `feature-requests`, UI in `ui-shared`, integration tests in `qa-tests`. (PASS)
- **Zero Credential Leakage**: One-time token is discarded immediately after binding and never stored; client configuration is encrypted with KSafe and excluded from OpenCollection exports and logs. (PASS)
- **Resource Discipline**: Zero static crypto loading when Keeper is not used; active client lifecycle managed by `KeeperClientManager` with automatic cleanup on environment switch. (PASS)
- **Test-First & Regression Free**: Full unit test coverage and offline E2E integration tests in `qa-tests` covering all record types. (PASS)

## Project Structure

### Documentation (this feature)

```text
specs/018-keeper-secret-manager/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── keeper-client-api.md
│   ├── opencollection-keeper-schema.md
│   └── keeper-settings-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code Changes by Module

```text
gradle/
└── libs.versions.toml                   # Add keeper-secrets-manager core library

core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt     # ExternalSecretType.KEEPER_SECURITY_CLOUD, KeeperSecurityExternalSecret & serializer

core-network/
├── build.gradle.kts                     # Add keeper-secrets-manager dependency under desktopMain
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/keeper/
│   ├── KeeperClient.kt                  # KeeperClient interface
│   ├── KeeperClientConfig.kt            # Serialized client config model
│   ├── KeeperClientManager.kt           # Lifecycle manager (lazy getClient & onEnvironmentSwitched)
│   └── KeeperRecordFilter.kt            # Dynamic field matching and target field extraction
├── src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/keeper/
│   ├── KeeperSecretsManagerClient.kt    # KSM SDK implementation
│   └── KeeperStorageInitializer.kt      # OTT initialization & binding logic
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/vault/
│   └── SecretResolutionService.kt       # resolveKeeperSecrets and resolveAllSecrets wiring
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/network/keeper/
    ├── KeeperClientManagerTest.kt       # Lifecycle and cleanup unit tests
    └── KeeperRecordFilterTest.kt        # Dynamic matching across login, serverCredentials, custom OAuth records

core-storage/
├── src/commonMain/resources/opencollection/
│   └── opencollection_v1.0.0.json       # Update schema for keeper-security-cloud & KeeperSecurityExternalSecret
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/opencollection/
    └── OpenCollectionExternalSecretsTest.kt # Keeper roundtrip & serialization tests

ui-shared/
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/
│   └── PlatformApi.kt                   # openUrlInBrowser declaration
├── src/desktopMain/kotlin/io/github/makeforge/clapi/ui/shared/platform/
│   └── PlatformApi.desktop.kt           # openUrlInBrowser implementation via Desktop.browse
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/
│   ├── AppSettings.kt                   # keeperConfigJson property & status enum
│   └── AppState.kt                      # onEnvironmentSwitched hook for KeeperClientManager
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/
│   ├── SettingsRepository.kt            # Save/load keeperConfig via PlatformStorage.putSecretString
│   ├── OpenCollectionModelMapping.kt    # Mapping for KeeperSecurityExternalSecret
│   └── ImportExportRepository.kt        # Import/export handling for Keeper
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/
│   ├── SettingsDialog.kt                # Keeper settings with OTT input, Connect, status check & help link
│   ├── EnvironmentEditDialog.kt         # Keeper provider row, table headers, and custom filters panel
│   └── executor/RequestVariableResolution.kt # Wire Keeper config into resolveAllSecrets
└── src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/
    ├── KeeperSettingsTest.kt            # Settings dialog OTT binding & KSafe storage tests
    └── EnvironmentEditorKeeperTest.kt   # Environment editor Keeper mapping tests

qa-tests/
├── build.gradle.kts                     # Add compileTestKotlin friendPaths configuration for Keeper SDK
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── KeeperSecurityCloudIntegrationTest.kt # Offline E2E test using test-records.json for login, serverCredentials, OAuth
```

---

## Phases

### Phase 0: Outline & Research
- Resolved all unknowns: KSM SDK usage, OTT redemption, KSafe persistence, memory lifecycle, dynamic field extraction, and offline E2E testing with friend paths.
- Documented findings in [research.md](research.md).

### Phase 1: Design & Contracts
- Documented domain model in [data-model.md](data-model.md).
- Documented interfaces and schemas in [contracts/](contracts/):
  - [contracts/keeper-client-api.md](contracts/keeper-client-api.md)
  - [contracts/opencollection-keeper-schema.md](contracts/opencollection-keeper-schema.md)
  - [contracts/keeper-settings-contract.md](contracts/keeper-settings-contract.md)
- Documented end-to-end validation scenarios in [quickstart.md](quickstart.md).
- Re-evaluated Constitution Check: All architectural gates PASS.
