# Implementation Plan: OpenAPI & OpenCollection Import Dialog

**Branch**: `004-import-openapi-collection` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-import-openapi-collection/spec.md`

## Summary

This feature introduces an Insomnia-style import dialog accessible via the import folder icon button in the sidebar. It supports importing from a local file (drag-and-drop or file picker for `.json` and `.yaml` files) or directly from the system clipboard. Upon scanning, the dialog validates the input against OpenCollection YAML or OpenAPI v3.x (JSON/YAML) using `io.swagger.parser.v3:swagger-parser`, maps the content to canonical collection models, and presents an interactive preview allowing users to selectively choose folders and environments, rename items before import, and observe overwrite warnings for colliding names.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Compose Multiplatform 1.8.1 (Desktop)
- Material 3 (`androidx.compose.material3`)
- `io.swagger.parser.v3:swagger-parser:2.1.25`
- `kotlinx.serialization` (JSON)
- `kaml` (YAML)
- `kotlinx.coroutines`

**Storage**:
- Canonical OpenCollection format (`core-storage`)
- Workspace JSON persistence (`WorkspaceRepository`)

**Testing**:
- `kotlin.test`
- Compose Desktop UI testing (`createComposeRule` in `ui-desktop`)
- Unit and integration tests for specification parsing and mapping

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Scan and preview generation under 3 seconds for files up to 5 MB
- Instantaneous (<16ms) conflict re-evaluation on inline rename

**Constraints**:
- Strict OpenAPI v3.0+ requirement; Swagger 2.0 or earlier is rejected with helpful user guidance
- Support single-file selection (JSON or YAML)
- Zero workspace mutations until the user explicitly confirms via "Import"
- Full cancel safety (dismissing discards all pending changes)

**Scale/Scope**: Supports schemas with hundreds of endpoints and multiple tag groupings.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Library-First & Modularity**: Core OpenAPI parsing and OpenCollection mapping are isolated in `core-storage`, reusable independently from the UI.
2. **Clear Separation of Concerns**: UI dialog logic in `ui-shared` is decoupled from parsing engines via typed contracts (`SpecificationImporter`, `ImportScanResult`).
3. **Test-First Compliance**: Clear contract definitions, fixture-driven validation, and end-to-end UI tests.
4. **Data Safety**: Non-destructive preview ensures no data overwrites occur without user review and confirmation.

## Project Structure

### Documentation (this feature)

```text
specs/004-import-openapi-collection/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── import-dialog-contract.md
│   └── openapi-importer-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
gradle/
└── libs.versions.toml                               # Add swagger-parser dependency

core-storage/
├── build.gradle.kts                                 # Add swagger-parser implementation
└── src/
    ├── commonMain/kotlin/io/github/makeforge/clapi/core/storage/
    │   └── importers/
    │       ├── SpecificationImporter.kt             # Scan interface & result types
    │       ├── OpenApiImporter.kt                   # OpenAPI v3 parser & mapper
    │       └── OpenCollectionImporter.kt            # OpenCollection YAML mapper
    └── commonTest/kotlin/io/github/makeforge/clapi/core/storage/
        └── importers/
            └── SpecificationImporterTest.kt         # Parser unit tests & fixture validation

ui-shared/
└── src/
    └── commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
        ├── components/
        │   ├── Sidebar.kt                           # Update import button to open ImportSpecificationDialog
        │   └── ImportSpecificationDialog.kt         # Two-step import dialog composable
        └── platform/
            ├── PlatformApi.kt                       # pickImportFile supporting JSON & YAML
            └── PlatformApi.desktop.kt               # Desktop file chooser with JSON & YAML filter

ui-desktop/
└── src/
    └── desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/
        └── ImportSpecificationUiTest.kt             # Compose UI tests for import flow
```

**Structure Decision**: Multi-module architecture keeping OpenAPI parsing in `core-storage`, reusable composable dialog in `ui-shared`, and UI automation tests in `ui-desktop`.

## Complexity Tracking

*No constitutional violations; no unnecessary complexity introduced.*
