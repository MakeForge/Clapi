# Implementation Plan: Insomnia v5 Collection & Environment Import

**Branch**: `025-import-insomnia-collection` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/025-import-insomnia-collection/spec.md`

## Summary

This feature adds native support for importing Insomnia v5 exports (`collection.insomnia.rest/5.0`, `spec.insomnia.rest/5.0`, and `environment.insomnia.rest/5.0` in both YAML and JSON) into Clapi collections and environments. The parser is encapsulated in `core-storage` as `InsomniaImporter` and integrated into `CompositeSpecificationImporter` and `ImportExportRepository`. Pre-request and after-response scripts are translated from `insomnia.*` to `clapi.*` namespaces. The Import Dialog is enhanced with format badges (`OpenAPI 3.0+`, `OpenCollection`, `Postman`, `Insomnia`) beneath the file selection area, and comprehensive tests are provided in `core-storage`, `ui-desktop`, and `qa-tests`.

## Technical Context

**Language/Version**: Kotlin 2.1 (Multiplatform, JVM 21 / 17 target)

**Primary Dependencies**:
- Compose Multiplatform 1.8.x (Desktop)
- Material 3 (`androidx.compose.material3`)
- `kaml` (`com.charleskorn.kaml:kaml`) for YAML parsing
- `kotlinx.serialization` (`kotlinx-serialization-json`) for JSON parsing
- `kotlinx.coroutines`

**Storage**:
- Canonical OpenCollection format (`core-storage`)
- Workspace JSON persistence (`WorkspaceRepository`)

**Testing**:
- `kotlin.test`
- Desktop Compose UI testing (`createComposeRule` in `ui-desktop`)
- End-to-end multiplatform tests in `qa-tests`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Desktop Multiplatform Application

**Performance Goals**:
- Scan and preview generation under 2 seconds for Insomnia export files with up to 100 requests.
- Instantaneous (<16ms) UI state updates during item selection in the Import Dialog.

**Constraints**:
- Insomnia schema 5.x compatibility (both YAML and JSON formats).
- Non-destructive import: workspace state is mutated only after user explicitly confirms in Step 2.
- Unsupported Insomnia fields (mock servers, TLS certs, Kong Konnect routing) are gracefully bypassed as documented in `scratch/insomnia-import-unsupported.md`.

**Scale/Scope**:
- Full collection hierarchies with arbitrarily nested folders, HTTP/gRPC/WebSocket requests, authentication configurations, environment variables, and automated test scripts.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist:
1. **Library-First & Modularity**: Core parsing and translation logic reside in `core-storage`, completely decoupled from UI code and independently testable across platforms.
2. **Clear Separation of Concerns**: UI components in `ui-shared` consume clean typed contracts (`SpecificationImporter`, `ImportScanResult`).
3. **Test-First Compliance**: Unit tests for parser and script translator, UI tests for dialog interactions, and QA tests for end-to-end migration.
4. **Data Safety**: Non-destructive preview ensures no workspace overwrites occur without user review and confirmation.

## Project Structure

### Documentation (this feature)

```text
specs/025-import-insomnia-collection/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation & migration guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── insomnia-importer-contract.md
│   └── import-dialog-badges-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/importers/
    └── ImportModels.kt                                  # Add INSOMNIA & POSTMAN to SpecFormat

core-storage/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/
│   ├── InsomniaImporter.kt                              # Insomnia v5 parser & OpenCollection mapper
│   ├── InsomniaScriptTranslator.kt                      # insomnia.* -> clapi.* translator
│   └── SpecificationImporter.kt                         # Integrate InsomniaImporter into CompositeSpecificationImporter
└── src/commonTest/kotlin/io/github/makeforge/clapi/core/storage/importers/
    ├── InsomniaImporterTest.kt                          # Parser & mapping unit tests
    └── InsomniaScriptTranslatorTest.kt                  # Script translation unit tests

ui-shared/
├── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
│   ├── components/
│   │   └── ImportSpecificationDialog.kt                 # Add format badges & handle Insomnia/Postman format
│   ├── persistence/
│   │   └── ImportExportRepository.kt                    # Support Insomnia format in importCollectionFromString
│   └── i18n/
│       └── Strings.kt                                   # Update format badge labels if needed
└── src/commonMain/resources/i18n/
    └── en.json                                          # Format strings & migration labels

ui-desktop/
└── src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/
    └── ImportSpecificationUiTest.kt                     # UI tests for format badges & Insomnia import

qa-tests/
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    └── InsomniaImportE2ETest.kt                         # Automated end-to-end migration QA tests

docs/
└── guide/
    └── import-export.md                                 # Migration documentation from Postman & Insomnia
```

**Structure Decision**:
The core parsing and translation capabilities are placed in `core-storage` adhering to the library-first architectural principle. The UI presentation in `ui-shared` and `ui-desktop` handles visual feedback (badges and preview), while `qa-tests` ensures comprehensive regression and end-to-end coverage.

## Complexity Tracking

> No constitutional violations or unwarranted complexity introduced.
