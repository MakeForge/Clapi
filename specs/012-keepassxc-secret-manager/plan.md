# Implementation Plan: KeePassXC Secret Manager Integration

**Branch**: `012-keepassxc-secret-manager` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-keepassxc-secret-manager/spec.md`

## Summary

This feature adds support for **KeePassXC** as an external secret manager for Clapi collection environments, allowing users to reference secrets stored in a locally running KeePassXC password manager without storing or exposing credentials in collection files.

The technical implementation encompasses:
1. **Model & Schema Extension**: Extend `ExternalSecretType` with enum value `keepassxc` (`KEEPASSXC`) and introduce `KeePassXcExternalSecret` (`name`, `url`, `targetField`, `customFilters`, `description`, `disabled`) into the OpenCollection model in `core-model`, with polymorphic serialization support in `core-storage`.
2. **KeePassXC Proxy Access & Client**: Add `org.purejava:keepassxc-proxy-access:1.3.2` as a desktop runtime dependency. Provide a `KeePassXcClient` interface in `core-network` with desktop implementation connecting over KeePassXC's local native messaging proxy using TweetNaCl encrypted communication. Store client association identities (`associateId`, `idKeyPairPublicKey`) locally in `PlatformStorage`.
3. **Safe Match Count Feedback**: Implement live match count verification in the Environment Editor for KeePassXC secrets (`url` and optional `customFilters` with variable interpolation). Provide real-time match count feedback without leaking or exposing secret values.
4. **Runtime Secret Resolution**: Enhance `SecretResolutionService` to resolve `{{_secret.<name>}}` and secret variables from KeePassXC entries by matching the entry URL, filtering by custom attribute criteria, and extracting the configured target field (`password`, `username`/`login`, `totp`, or custom attribute).
5. **Zero Idle Proxy Lifecycle**: Implement a `KeePassXcClientManager` ensuring that when the active environment does not use KeePassXC, no proxy connections or background processes remain open.

---

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- KeePassXC Proxy Access: `org.purejava:keepassxc-proxy-access:1.3.2`
- UI: Compose Multiplatform 1.8.1 (Desktop)
- Serialization & Schema: `kotlinx.serialization:1.8.1`, Kaml for YAML
- Network & HTTP Client: `io.ktor:ktor-client-core:3.1.2`
- Secure Storage: `eu.anifantakis:ksafe:3.3.0`
- Testing: `kotlin.test`, JUnit 4, Coroutines Test

**Storage**:
- Non-sensitive association IDs: `PlatformStorage` (`clapi.keepassxc.associateId`, `clapi.keepassxc.idKeyPairPublicKey`)
- OpenCollection Document: Environment `externalSecrets` block with type `keepassxc` (no secret values or workstation keys included)
- Secret Values: Ephemeral (in-memory only during request execution)

**Testing**:
- Model and polymorphic serializer tests in `core-model` and `core-storage`
- Unit tests for `KeePassXcClient` filtering and target field extraction with mock proxy responses in `core-network`
- `SecretResolutionService` KeePassXC resolution tests in `core-network`
- UI environment editor secrets and match indicator tests in `ui-shared`
- `RequestExecutor` secret resolution tests in `ui-shared`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Match count feedback responds within 2-3 seconds of entering a URL.
- KeePassXC secret resolution overhead < 100ms per request execution.
- Single query per unique entry URL per request run.

**Constraints**:
- Strictly read-only access to KeePassXC; never create, modify, or delete entries.
- Zero secret leakage: passwords, TOTPs, and custom attribute values are never displayed in match indicators, logs, or persistent files.
- Zero idle proxy footprint: disconnect and terminate proxy connection immediately when KeePassXC is not used by the active environment.

**Scale/Scope**:
- KeePassXC 2.6.0+ on Linux, macOS, and Windows.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**:
   - `core-model`: OpenCollection data model additions (`KEEPASSXC`, `KeePassXcExternalSecret`).
   - `core-network`: `KeePassXcClient`, `KeePassXcClientManager`, and `SecretResolutionService` KeePassXC handling.
   - `core-storage`: Safe serialization omitting secrets and workstation identities.
   - `ui-shared`: Environment editor UI, match indicator, and `RequestExecutor` integration.
2. **Security & Privacy**:
   - Zero credential leakage in UI or logs.
   - Workstation-local association data separated from portable collection files.
3. **Simplicity & Lifecycle Safety**:
   - No daemon process running when KeePassXC is inactive.
   - Clean shutdown upon environment switching.

---

## Project Structure

### Documentation (this feature)

```text
specs/012-keepassxc-secret-manager/
├── plan.md              # Implementation plan (/speckit-plan output)
├── research.md          # Technical research & decisions (/speckit-plan output)
├── data-model.md        # Entities, schemas, state models (/speckit-plan output)
├── quickstart.md        # Runnable validation scenarios (/speckit-plan output)
├── contracts/           # API and OpenCollection contracts (/speckit-plan output)
│   ├── keepassxc-client-api.md
│   └── opencollection-keepassxc-schema.md
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Implementation tasks (/speckit-tasks command)
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/opencollection/
    └── OpenCollectionEnvironment.kt     # ExternalSecretType.KEEPASSXC & KeePassXcExternalSecret

core-storage/
├── src/commonMain/kotlin/...            # OpenCollection serialization mapping
└── src/commonTest/kotlin/...            # JSON and YAML round-trip tests

core-network/
├── build.gradle.kts                     # Dependency: org.purejava:keepassxc-proxy-access:1.3.2
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
│   └── keepassxc/
│       ├── KeePassXcClient.kt           # Client interface, data models, and status types
│       └── KeePassXcClientManager.kt    # Lifecycle manager (lazy connection, zero idle proxy)
│   └── vault/
│       └── SecretResolutionService.kt   # KeePassXC resolution pipeline support
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/
    └── keepassxc/
        └── PureJavaKeePassXcClient.kt   # KeepassProxyAccess implementation

ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── state/
    │   └── AppState.kt                  # MutableExternalSecret (url, targetField, customFilters)
    ├── components/
    │   ├── EnvironmentEditDialog.kt     # KeePassXC provider UI & match count indicator
    │   └── RequestExecutor.kt           # Request execution resolution dispatching
    └── persistence/
        └── OpenCollectionModelMapping.kt # DTO mapping for KeePassXC secrets
```

**Structure Decision**: Multi-project Kotlin Multiplatform architecture cleanly separating core models, desktop proxy client networking, storage serialization, and shared Compose UI components.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations. All design patterns align with existing Clapi architecture.*
