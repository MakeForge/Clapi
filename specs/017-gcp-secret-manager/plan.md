# Implementation Plan: GCP Secret Manager Integration

**Branch**: `017-gcp-secret-manager` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-gcp-secret-manager/spec.md`

## Summary

Implement Google Cloud Platform (GCP) Secret Manager secret provider integration (`gcp-secrets-manager`) for Clapi, matching the user experience and architectural pattern of HashiCorp Vault, KeePassXC, AWS Secrets Manager, and Azure Key Vault. Users configure global GCP connection parameters (Project ID, masked Access Token, optional Endpoint URL Override) in Settings -> Secrets Manager with encrypted platform storage. In the Environment Editor, users declare external secrets with Secret ID, Version (defaulting to `"latest"`), Target Field (for JSON property extraction), optional Project ID override, and Custom Filters. During request dispatch, `SecretResolutionService` queries GCP Secret Manager's REST API (`:access`) via Ktor with query deduplication by Secret ID and Version, decodes Base64 data, extracts target fields, evaluates custom filters, and injects secrets into outgoing requests. The QA test suite is extended with automated end-to-end integration tests running against a containerized GCP Secret Manager emulator.

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.1.x / JVM Toolchain 21

**Primary Dependencies**: Ktor Client (`io.ktor:ktor-client-core`, `io.ktor:ktor-client-cio`), Kotlinx Serialization JSON (`kotlinx-serialization-json`), Compose Multiplatform Desktop

**Storage**: In-memory state, Desktop preferences, encrypted secure platform storage (`PlatformStorage.putSecretString` via Java KeyStore / SecretKey)

**Testing**: JUnit 4 / Kotlin Test (`kotlin.test`), Coroutines Test (`kotlinx-coroutines-test`), Ktor Client MockEngine, GCP Secret Manager REST Emulator Docker container in `qa-tests`

**Target Platform**: Desktop (macOS, Linux, Windows on JVM)

**Project Type**: Multi-module desktop application with shared UI and multiplatform core

**Performance Goals**: Secret resolution batching executes in <200ms against local emulator; query deduplication ensures 1 network request per unique `(projectId, secretId, version)` tuple per execution; autocomplete displays available secrets in <50ms

**Constraints**: Zero credential leakage in exports or logs; no heavyweight Google Cloud Java SDK dependencies (lightweight Ktor-only client); direct UI configuration for credentials (environment variable fallback deferred to later); offline local testing via containerized emulator

**Scale/Scope**: 5 core modules (`core-model`, `core-network`, `core-storage`, `feature-requests`, `ui-shared`) plus `qa-tests`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Modular Architecture**: Changes align with established boundaries: models in `core-model`, networking in `core-network`, storage/schema in `core-storage`, execution in `feature-requests`, UI in `ui-shared`, and end-to-end integration tests in `qa-tests`. (PASS)
- **Zero Credential Leakage**: Sensitive credentials (`gcpAccessToken`) are stored encrypted and omitted from all serialized OpenCollection documents and application logs. (PASS)
- **Test-First & Regression Free**: Full unit and integration test coverage across mock and live container emulator environments. (PASS)

## Project Structure

### Documentation (this feature)

```text
specs/017-gcp-secret-manager/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── gcp-secret-manager-client-contract.md
│   ├── opencollection-gcp-secrets-schema.md
│   └── gcp-settings-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code Changes by Module

```text
core-model/
└── src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt     # GcpSecretsManagerExternalSecret & serializer update

core-network/
├── src/commonMain/kotlin/io/github/crustack/clapi/core/network/gcp/
│   ├── GcpSecretManagerConfig.kt        # Configuration data model
│   └── GcpSecretManagerClient.kt        # Interface & KtorGcpSecretManagerClient
├── src/commonMain/kotlin/io/github/crustack/clapi/core/network/vault/
│   └── SecretResolutionService.kt       # resolveGcpSecrets & resolveAllSecrets wiring
└── src/commonTest/kotlin/io/github/crustack/clapi/core/network/gcp/
    ├── GcpSecretManagerClientTest.kt    # MockEngine unit tests
    └── SecretResolutionServiceGcpTest.kt# Resolution & batching tests

core-storage/
├── src/commonMain/resources/opencollection/
│   └── opencollection_v1.0.0.json       # Update schema for GcpSecretsManagerExternalSecret
└── src/commonTest/kotlin/io/github/crustack/clapi/core/storage/opencollection/
    └── OpenCollectionExternalSecretsTest.kt # GCP roundtrip & credential leak check tests

feature-requests/
└── src/commonMain/kotlin/io/github/crustack/clapi/feature/requests/
    └── RequestExecutionService.kt       # GCP parameters & mapping for execution

ui-shared/
├── src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/
│   └── AppSettings.kt                   # Global GCP settings properties (gcpProjectId, gcpAccessToken, gcpEndpointUrl)
├── src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/persistence/
│   ├── SettingsRepository.kt            # Load/save GCP settings with encrypted accessToken
│   ├── OpenCollectionModelMapping.kt    # Bidirectional mapping for GcpSecretsManagerExternalSecret
│   └── ImportExportRepository.kt        # Serialize targetField, version, and customFilters for GCP
├── src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/
│   ├── SettingsDialog.kt                # GCP Secret Manager settings form
│   ├── EnvironmentEditDialog.kt         # gcp-secrets-manager provider row & table headers
│   └── executor/RequestVariableResolution.kt # Wire AppSettings into resolveAllSecrets for GCP
└── src/commonTest/kotlin/io/github/crustack/clapi/ui/shared/components/
    ├── GcpSettingsTest.kt               # Settings persistence & masking tests
    ├── EnvironmentEditorGcpTest.kt      # Environment editor mapping tests
    └── RequestExecutorGcpSecretsTest.kt # Dispatch & error tests

qa-tests/
├── docker/secret-managers-compose.yml   # Add gcp-secretmanager emulator & seeder service
├── fixtures/clapi-test-collection.json  # Add GCP Staging environment
└── src/test/kotlin/io/github/crustack/clapi/qa/
    └── GcpSecretManagerIntegrationTest.kt # E2E GCP Secret Manager integration tests
```

## Structure Decision

Reuses the established multi-module architecture:
- Core data models in `core-model/opencollection/`
- Lightweight Ktor network client in `core-network/gcp/`
- JSON schema and serialization in `core-storage/`
- Request execution integration in `feature-requests/`
- Desktop UI, settings, and autocomplete in `ui-shared/`
- Containerized integration tests in `qa-tests/`

## Complexity Tracking

No constitution violations or architectural exceptions required. Standard patterns from KeePassXC, AWS Secrets Manager, and Azure Key Vault are followed directly.
