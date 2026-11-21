# Implementation Plan: Folder Settings and Request Defaults Inheritance

**Branch**: `003-folder-request-defaults` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-folder-request-defaults/spec.md`

## Summary

This feature aligns the UI terminology by renaming "Collections" to "Folders", introduces a first-class Folder Settings editor tab accessible by clicking a folder's name in the sidebar, enables configuring folder-level `RequestDefaults` (headers, parameters, authentication, and scripts), and implements visual and runtime inheritance in descendant requests.

The technical approach integrates folder settings tabs directly into `AppState.openTabs` using `RequestKind.FOLDER`. Main editor rendering routes folder tabs to `FolderEditor`, which reuses the existing `EditorTabBar`, `KeyValueEditor`, `AuthEditor`, and `ScriptEditor` composables. In request tabs, inherited headers are marked with `HeaderKind.INHERITED`, authentication includes an `AuthType.INHERIT` option that resolves up the folder hierarchy, and script cascade order is explicitly documented and handled.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 17)

**Primary Dependencies**: Compose Multiplatform Desktop, Compose Foundation, Material 3, `kotlinx.serialization`, `kotlinx.coroutines`

**Storage**: OpenCollection 1.0 JSON/YAML specification (`FolderItem.request`), `PlatformStorage` for tab session persistence

**Testing**: `kotlin.test`, Compose Desktop UI Testing (`desktopTest`), JUnit 5

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Compose Multiplatform Desktop Application

**Performance Goals**: Instantaneous tab switching (<50ms), sub-second resolution of inherited defaults across deep folder hierarchies (50+ nested items)

**Constraints**:
- Strict compatibility with OpenCollection schema (`FolderItem.request`).
- No breaking changes to existing tab session storage (`TabsRepository`).
- Full support for case-insensitive header overrides and hierarchical script chaining.

**Scale/Scope**: Arbitrary folder nesting depth, workspaces with hundreds of folders and requests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

No project-specific constitutional gate violations exist. The architectural design adheres to:
1. Reusing existing modular Compose UI components (`EditorTabBar`, `KeyValueEditor`, `AuthEditor`, `ScriptEditor`).
2. Maintaining strict backward and forward data compatibility with the OpenCollection standard.
3. Separation of presentation logic from core execution and storage.

## Project Structure

### Documentation (this feature)

```text
specs/003-folder-request-defaults/
├── spec.md                     # Feature specification
├── plan.md                     # Implementation plan
├── research.md                 # Technical research & decisions (Phase 0)
├── data-model.md               # Data entities & state transitions (Phase 1)
├── quickstart.md               # Runnable validation guide (Phase 1)
├── contracts/                  # Interface contracts (Phase 1)
│   ├── folder-tab-contract.md
│   └── inheritance-resolution-contract.md
└── checklists/
    └── requirements.md         # Specification quality checklist
```

### Source Code (repository root)

```text
core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/
├── RequestModels.kt                               # Add AuthType.INHERIT
└── McpModels.kt                                   # Extend RequestKind with RequestKind.FOLDER

ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
├── i18n/
│   ├── Strings.kt                                 # Update terminology strings (folders vs collections)
│   └── resources/i18n/*.json                      # Translation bundles
├── state/
│   └── AppState.kt                                # HeaderKind.INHERITED, openFolder(), folder tab state
├── persistence/
│   ├── OpenCollectionModelMapping.kt              # Map FolderItem.request to/from CollectionNode defaults
│   └── TabsRepository.kt                          # Persist/restore RequestKind.FOLDER tabs
└── components/
    ├── Sidebar.kt                                 # Click on folder text opens tab; chevron toggles expand
    ├── RequestTabsBar.kt                          # Render folder icon for RequestKind.FOLDER tabs
    ├── FolderEditor.kt                            # Dedicated Folder Settings editor composable
    ├── RequestEditor.kt                           # Reuse EditorTabBar, wire inherited headers & auth
    ├── AuthEditor.kt                              # AuthType.INHERIT chip & inherited state view
    ├── KeyValueEditor.kt                          # INHERITED badge & read-only lock for inherited rows
    └── RequestExecutor.kt                         # Resolve inherited headers & chain folder pre-scripts
```

**Structure Decision**: Multi-module Kotlin project leveraging `core-model` for shared data structures, `ui-shared` for multiplatform Compose state and composables, and `ui-desktop` for desktop launcher and UI tests.

## Complexity Tracking

*No constitutional violations; no unnecessary complexity introduced.*
