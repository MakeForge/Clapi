# Implementation Plan: Support Multiple OpenCollection Collections

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-multiple-collections/spec.md`

## Summary

This feature introduces multi-collection support into Clapi, enabling users to maintain, organize, import, and export multiple OpenCollection documents within a single workspace. In the sidebar, a top-level "Collections" section header with a `+` button replaces the legacy single "Collection" header, listing individual expandable/collapsible collection rows (each with Article icon, double-click inline renaming, and a `+` button to add folders). In the Collection Editor, a new "Info" tab is positioned first on the left to edit `OpenCollectionDocument.info` fields (name, summary, version), accompanied by Request Defaults tabs (Variables, Headers, Auth, Pre-request, Tests) and the Protobuf tab. Folders inherit request defaults from their enclosing collection, and requests inherit defaults through the cascading hierarchy. Global export provides a selection dialog to choose which collections to export, outputting 1 standalone file per collection, while import parses collections and auto-disambiguates name collisions with incremental numbering. Existing single-collection workspaces migrate seamlessly on first load.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 target)

**Primary Dependencies**:
- Compose Multiplatform 1.8.1 (Desktop UI)
- `kotlinx.serialization` (JSON / YAML polymorphic OpenCollection serialization)
- Kotlin Coroutines & Flows
- Desktop Platform Integration (`java.awt.Desktop`, native directory/file dialogs)
- Testing: JUnit 4, `kotlin.test`, Compose Multiplatform UI Test library

**Storage**:
- Canonical OpenCollection format (`core-storage` / `workspace.json` and standalone files) conforming to OpenCollection v1.0.0 JSON schema.
- Workspace state persistence via `WorkspaceRepository` and `CanonicalCollectionRepository`.

**Testing**:
- Unit tests for OpenCollection serialization and model mapping in `core-model` and `core-storage`
- Unit tests for request defaults cascade and inheritance resolution in `ui-shared`
- UI component and interaction tests for sidebar collections hierarchy, inline renaming, and `CollectionEditor` tabs in `ui-desktop`
- End-to-end integration and export/import round-trip tests in `qa-tests`

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Instant sidebar tree rendering and expand/collapse transitions (<16ms, 60fps).
- Inheritance resolution across deep folder hierarchies in <10ms.
- Multi-collection export and import with zero data corruption.

**Constraints**:
- Strict compliance with OpenCollection v1.0.0 JSON schema.
- Disambiguation of duplicate collection names via incremental indexing (e.g. `Collection (1)`) without validation error dialogs.
- Seamless migration of legacy single-collection workspaces.

**Scale/Scope**:
- Workspaces with dozens of independent collections, each with hundreds of nested folders and requests.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No constitutional violations exist. The architectural design adheres to:
1. **Modularity & Layering**: OpenCollection schemas in `core-model`, persistence and serialization in `core-storage`, state and UI components in `ui-shared`, platform bindings in `ui-desktop`.
2. **Standard Interoperability**: Strict adherence to OpenCollection v1.0.0 JSON schema ensures full portability with other OpenCollection-compatible tooling.
3. **Data Integrity & Backward Compatibility**: Legacy single-collection workspaces migrate cleanly into the multi-collection model on first launch without data loss.

---

## Project Structure

### Documentation (this feature)

```text
specs/008-multiple-collections/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: decisions, hierarchy model, inheritance scoping
├── data-model.md        # Phase 1 output: extended CollectionNode, OpenCollectionDocument mapping
├── quickstart.md        # Phase 1 output: end-to-end manual verification & automated test guide
├── contracts/           # Phase 1 output: component & behavior contracts
│   ├── SidebarHierarchyContract.md
│   ├── CollectionEditorContract.md
│   ├── ExportImportContract.md
│   └── InheritanceContract.md
└── checklists/
    └── requirements.md  # Requirements quality checklist
```

### Source Code (repository root)

```text
core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/
├── OpenCollectionDocument.kt             # CollectionInfo, RequestDefaults, CollectionConfig
└── OpenCollectionItem.kt                 # FolderItem, RequestItem

core-storage/src/commonMain/kotlin/io/github/crustack/clapi/core/storage/opencollection/
├── CanonicalCollectionRepository.kt       # Multi-collection persistence & storage operations
├── OpenCollectionSerializer.kt            # JSON/YAML serialization
└── OpenCollectionSchemaValidator.kt       # Schema compliance validation

ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/
├── components/
│   ├── Sidebar.kt                         # Collections header with '+', collection items, inline rename
│   ├── CollectionEditor.kt                # Info tab (leftmost), request defaults tabs, Protobuf tab
│   ├── FolderEditor.kt                    # Folder request defaults with collection inheritance
│   ├── AuthEditor.kt                      # Auth inheritance resolution from parent collection
│   └── ExportCollectionsDialog.kt         # Global multi-collection export selection dialog
├── persistence/
│   ├── OpenCollectionModelMapping.kt      # Mapping between CollectionNode and OpenCollectionDocument
│   ├── ImportExportRepository.kt          # Multi-collection export (1 file/col) & import logic
│   └── WorkspaceRepository.kt             # Workspace persistence & legacy migration
└── state/
    ├── AppState.kt                        # Multi-collection tree, tab state, inheritance cascade
    └── RequestTabState.kt                 # Extended tab state for CollectionEditor

ui-desktop/src/
├── main/kotlin/io/github/crustack/clapi/ui/desktop/
│   └── components/                        # Platform native dialogs (directory chooser)
└── test/kotlin/io/github/crustack/clapi/ui/desktop/
    ├── CollectionEditorNavigationUiTest.kt
    ├── SidebarCollectionHelpersTest.kt
    └── AppStateCollectionTest.kt
```

**Structure Decision**: Leverages existing modular multiplatform project architecture (`core-model`, `core-storage`, `ui-shared`, `ui-desktop`, `qa-tests`), extending `CollectionNode` and `AppState` with collection-level metadata, request defaults, and inheritance resolution while introducing `ExportCollectionsDialog` for selective multi-export.

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified.*
