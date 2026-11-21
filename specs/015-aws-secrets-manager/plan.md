# Implementation Plan: AWS Secrets Manager Integration

**Branch**: `015-aws-secrets-manager` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/015-aws-secrets-manager/spec.md`

## Summary

This feature adds support for **AWS Secrets Manager** as an external secret manager for Clapi collection environments, allowing users to reference secrets stored in AWS Secrets Manager without storing or exposing credentials in collection files.

The technical implementation encompasses:
1. **Model & Schema Extension**: Extend `ExternalSecretVariable` with `AwsSecretsManagerExternalSecret` (`name`, `secretName`, `targetField`, `customFilters`, `description`, `disabled`) in `core-model` (`OpenCollectionEnvironment.kt`) and update polymorphic serialization in `core-model` and roundtrip tests in `core-storage`.
2. **Lightweight AWS SigV4 Signer & Client**: Provide `AwsSigV4Signer` and `KtorAwsSecretsManagerClient` in `core-network` using standard JVM cryptography (`javax.crypto.Mac` and `java.security.MessageDigest`) and Ktor `HttpClient`, avoiding heavyweight AWS Java SDK dependencies.
3. **Global Settings Configuration**: Add AWS connection settings in `AppSettings` (Region, Access Key ID, Secret Access Key, Session Token, Endpoint URL Override, Version Stage) and `SettingsDialog`, persisting sensitive keys in encrypted platform storage (`KSafe`) via `PlatformStorage.putSecretString`.
4. **Environment Editor UI & Mapping**: Enable `aws-secrets-manager` provider in `EnvironmentEditDialog` with a table layout matching KeePassXC (Name, Secret ID / Name, Target Field, Filters, Description, Enabled toggle). Map between `EnvironmentState` and `AwsSecretsManagerExternalSecret` in `OpenCollectionModelMapping.kt`.
5. **Runtime Secret Resolution**: Enhance `SecretResolutionService` to resolve `aws-secrets-manager` secrets: batch queries by Secret ID, parse JSON payloads (or fallback to plain strings), evaluate custom filters with environment variable interpolation, extract target fields, and substitute into `{{_secret.<name>}}` placeholders during request execution.
6. **Automated QA LocalStack E2E Test**: Add Docker LocalStack Secrets Manager orchestration and an end-to-end integration test in `qa-tests` verifying client resolution and HTTP request execution.

---

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Network & HTTP Client: `io.ktor:ktor-client-core:3.1.2`, `io.ktor:ktor-client-cio:3.1.2`
- UI: Compose Multiplatform 1.8.1 (Desktop)
- Serialization & Schema: `kotlinx.serialization:1.8.1`, Kaml for YAML
- Secure Storage: `eu.anifantakis:ksafe:3.3.0`
- Cryptography: Standard JVM `javax.crypto.Mac` and `java.security.MessageDigest` for AWS SigV4
- Testing: `kotlin.test`, JUnit 4 / 5, Coroutines Test, Docker Compose LocalStack

**Storage**:
- Non-sensitive settings: `PlatformStorage` (`settings.awsRegion`, `settings.awsAccessKeyId`, `settings.awsEndpointUrl`, `settings.awsVersionStage`)
- Sensitive credentials: `PlatformStorage.putSecretString` (`settings.awsSecretAccessKey`, `settings.awsSessionToken`) backed by encrypted `KSafe`
- OpenCollection Document: Environment `externalSecrets` block with type `aws-secrets-manager` (zero credentials or secret payloads stored)
- Secret Values: Ephemeral (in-memory only during request execution)

**Testing**:
- Model and polymorphic serializer tests in `core-model` and `core-storage`
- Unit tests for `AwsSigV4Signer` and `KtorAwsSecretsManagerClient` in `core-network`
- `SecretResolutionService` AWS resolution and batching tests in `core-network`
- UI Settings dialog and Environment Editor secrets tests in `ui-shared`
- End-to-end integration tests with Docker LocalStack in `qa-tests`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- AWS secret resolution overhead < 200ms per request execution.
- Single network round-trip per unique Secret ID per request run.

**Constraints**:
- Strictly read-only access to AWS Secrets Manager; never create, modify, or delete secrets.
- Zero secret / credential leakage: AWS access keys, secret keys, session tokens, and secret payloads are never exported to collection files or logged.
- Lightweight: No heavyweight AWS Java SDK dependencies.
- Fallback to environment variables is deferred to a future iteration per requirements.

**Scale/Scope**:
- All standard AWS regions + custom endpoint overrides (LocalStack, VPC endpoints).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**:
   - `core-model`: OpenCollection data model additions (`AwsSecretsManagerExternalSecret`).
   - `core-network`: `AwsSigV4Signer`, `AwsSecretsManagerConfig`, `KtorAwsSecretsManagerClient`, and `SecretResolutionService` AWS resolution.
   - `core-storage`: Safe serialization omitting secrets and workstation identities.
   - `ui-shared`: Settings UI, Environment Editor UI, and Request Execution integration.
   - `qa-tests`: LocalStack E2E test suite.
2. **Security & Privacy**:
   - Sensitive credentials encrypted with `KSafe`.
   - Workstation-local credentials completely isolated from portable collection files.
3. **Simplicity & Dependency Hygiene**:
   - Clean, zero-external-dependency AWS SigV4 signing utility using standard JVM crypto.

---

## Project Structure

### Documentation (this feature)

```text
specs/015-aws-secrets-manager/
├── plan.md              # Implementation plan (/speckit-plan output)
├── research.md          # Technical research & decisions (/speckit-plan output)
├── data-model.md        # Entities, schemas, state models (/speckit-plan output)
├── quickstart.md        # Runnable validation scenarios (/speckit-plan output)
├── contracts/           # API and OpenCollection contracts (/speckit-plan output)
│   ├── aws-secrets-manager-client-contract.md
│   ├── opencollection-aws-secrets-schema.md
│   └── aws-settings-contract.md
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Implementation tasks (/speckit-tasks command)
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt        # AwsSecretsManagerExternalSecret & polymorphic serializer

core-network/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
    ├── aws/
    │   ├── AwsSigV4Signer.kt               # AWS SigV4 request signer
    │   ├── AwsSecretsManagerConfig.kt      # Configuration data class
    │   └── AwsSecretsManagerClient.kt      # Ktor-based AWS Secrets Manager client
    └── vault/
        └── SecretResolutionService.kt      # AWS secret resolution & query batching

core-storage/
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/opencollection/
    └── OpenCollectionExternalSecretsTest.kt # AWS OpenCollection JSON/YAML roundtrip & zero leakage tests

ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── state/
    │   └── AppSettings.kt                  # AWS settings properties
    ├── persistence/
    │   ├── SettingsRepository.kt           # Load/save AWS settings
    │   └── OpenCollectionModelMapping.kt   # EnvironmentState <-> AwsSecretsManagerExternalSecret mapping
    └── components/
        ├── SettingsDialog.kt               # Secrets Manager tab AWS inputs
        ├── EnvironmentEditDialog.kt        # External Secrets tab AWS provider & table
        └── executor/
            └── RequestVariableResolution.kt # Resolution pipeline wiring for AWS

qa-tests/
├── docker/
│   └── secret-managers-compose.yml         # LocalStack Secrets Manager service & seeder
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── AwsSecretsManagerIntegrationTest.kt # Live E2E test with LocalStack
```

**Structure Decision**: Standard multi-module architecture following existing patterns from `006-external-secret-variables` and `012-keepassxc-secret-manager`.

## Complexity Tracking

> *None. No constitutional violations or unjustified complexity.*
