# Implementation Plan: Azure Key Vault Integration

**Branch**: `016-azure-key-vault` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-azure-key-vault/spec.md`

## Summary

Implement Azure Key Vault secret provider integration (`azure-key-vault`) for Clapi, matching the user experience of HashiCorp Vault, KeePassXC, and AWS Secrets Manager. Users configure global Azure connection parameters (Vault Name/URL, Tenant ID, Client ID, masked Client Secret, Authority Host, API Version) in Settings -> Secrets Manager with encrypted platform storage. In the Environment Editor, users declare external secrets with Secret Name, Target Field (for JSON key extraction), and Custom Filters. During request dispatch, `SecretResolutionService` acquires an Entra ID OAuth2 token (or bypasses for local testing), queries Azure Key Vault via HTTP GET with query deduplication by Secret Name, extracts target fields, evaluates custom filters, and injects secrets into outgoing requests.

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.1.x / JVM Toolchain 21

**Primary Dependencies**: Ktor Client (`io.ktor:ktor-client-core`, `io.ktor:ktor-client-cio`), Kotlinx Serialization JSON (`kotlinx-serialization-json`), Compose Multiplatform Desktop

**Storage**: In-memory state, Desktop preferences, encrypted secure platform storage (`PlatformStorage.putSecretString` via Java KeyStore / SecretKey)

**Testing**: JUnit 4 / Kotlin Test (`kotlin.test`), Coroutines Test (`kotlinx-coroutines-test`), Ktor Client MockEngine, Lowkey Vault Docker container in `qa-tests`

**Target Platform**: Desktop (macOS, Linux, Windows on JVM)

**Project Type**: Multi-module desktop application with shared UI and multiplatform core

**Performance Goals**: Secret resolution batching executes in <200ms against local emulator; query deduplication ensures 1 network request per unique secret name per execution; autocomplete displays available secrets in <50ms

**Constraints**: Zero credential leakage in exports or logs; no heavyweight Azure Java SDK dependencies (Ktor-only); offline testing via Lowkey Vault with `authorityHost = "bypass"`

**Scale/Scope**: 5 core modules (`core-model`, `core-network`, `core-storage`, `feature-requests`, `ui-shared`) plus `qa-tests`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Modular Architecture**: Changes align with established boundaries: models in `core-model`, networking in `core-network`, storage/schema in `core-storage`, execution in `feature-requests`, UI in `ui-shared`, and end-to-end integration tests in `qa-tests`. (PASS)
- **Zero Credential Leakage**: Sensitive credentials (`azureClientSecret`) are stored encrypted and omitted from all serialized OpenCollection documents. (PASS)
- **Test-First & Regression Free**: Full unit and integration test coverage across mock and live container environments. (PASS)

## Project Structure

### Documentation (this feature)

```text
specs/016-azure-key-vault/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── azure-key-vault-client-contract.md
│   ├── opencollection-azure-secrets-schema.md
│   └── azure-settings-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code Changes by Module

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt     # AzureKeyVaultExternalSecret, serializer update

core-network/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/azure/
│   ├── AzureKeyVaultConfig.kt           # Configuration data model
│   └── AzureKeyVaultClient.kt           # Interface & KtorAzureKeyVaultClient
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/vault/
│   └── SecretResolutionService.kt       # resolveAzureSecrets & resolveAllSecrets wiring
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/network/azure/
    ├── AzureKeyVaultClientTest.kt       # MockEngine unit tests
    └── SecretResolutionServiceAzureTest.kt # Resolution & batching tests

core-storage/
├── src/commonMain/resources/opencollection/
│   └── opencollection_v1.0.0.json       # Update schema for AzureKeyVaultExternalSecret
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/opencollection/
    └── OpenCollectionExternalSecretsTest.kt # Azure roundtrip & leak check tests

feature-requests/
└── src/commonMain/kotlin/io/github/makeforge/clapi/feature/requests/
    └── RequestExecutionService.kt       # Azure parameters & mapping for execution

ui-shared/
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/
│   └── AppSettings.kt                   # Global Azure settings properties
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/persistence/
│   ├── SettingsRepository.kt            # Load/save Azure settings with encrypted clientSecret
│   ├── OpenCollectionModelMapping.kt    # Bidirectional mapping for AzureKeyVaultExternalSecret
│   └── ImportExportRepository.kt        # Serialize targetField and customFilters
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/
│   ├── SettingsDialog.kt                # Azure Key Vault settings form
│   ├── EnvironmentEditDialog.kt         # azure-key-vault provider row & table headers
│   └── executor/RequestVariableResolution.kt # Wire AppState.settings into resolveAllSecrets
└── src/commonTest/kotlin/io/github/makeforge/clapi/ui/shared/components/
    ├── AzureSettingsTest.kt             # Settings persistence & masking tests
    ├── EnvironmentEditorAzureTest.kt    # Environment editor mapping tests
    └── RequestExecutorAzureSecretsTest.kt # Dispatch & error tests

qa-tests/
├── docker/secret-managers-compose.yml   # Add local-azure-vault (Lowkey Vault) & seeder
├── fixtures/clapi-test-collection.json  # Add Azure Staging environment
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── AzureKeyVaultIntegrationTest.kt  # E2E Lowkey Vault integration test
```

## Structure Decision

Reuses existing modular architecture:
- Network client logic placed in `core-network/azure/`
- Data models placed in `core-model/opencollection/`
- Schema and serialization in `core-storage/`
- UI forms in `ui-shared/components/`
- Integration tests in `qa-tests/`
