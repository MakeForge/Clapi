# Technical Research: Fix Desktop UI High-Severity Violations

**Feature**: `020-fix-desktop-ui-violations`  
**Date**: 2026-11-13  
**Status**: Completed

---

## 1. Composable Modifier Hygiene & Root Propagation

### Context & Problem
In `scratch/composables_evaluation.md`, over 30 primary workspace composables and modal dialogs (`MainScreen`, `RequestEditor`, `BottomPanel`, `ResponseViewer`, `AuthEditor`, `BodyEditor`, `CodeEditor`, `CollectionEditor`, `SettingsDialog`, `EnvironmentEditDialog`, `GitSyncDialog`, etc.) completely omit `modifier: Modifier = Modifier` or violate parameter ordering rules. This prevents host layouts from adjusting paddings, passing size constraints, docking, or applying test tags.

### Decision
1. Every public, internal, or reusable workspace panel and dialog `@Composable` MUST declare `modifier: Modifier = Modifier` as its first optional parameter.
2. In the body, apply `modifier` directly to the outermost layout container (e.g. `Column(modifier = modifier.fillMaxSize())`, `Dialog { Column(modifier = modifier.widthIn(...)) }`).
3. Never use hardcoded sizing defaults like `modifier: Modifier = Modifier.fillMaxWidth()` in parameter declarations. Internal constraints must be chained inside the body (`modifier.fillMaxWidth()`).

### Rationale
- Follows the Compose API Guidelines and Clapi standard checklist (Rule 1).
- Allows parent layouts (such as split panes, tabs, and test harnesses) to control positioning, semantics, and sizing without breaking internal styling.

### Alternatives Considered
- *Leave internal dialogs without modifiers*: Rejected because modal dialogs frequently require external size bounds or accessibility test tags from test runners.
- *Defaulting to `Modifier.fillMaxSize()` in the parameter*: Rejected because caller modifier chains would be overridden or forced into full size even when embedded in custom split layouts.

---

## 2. Redundant `.hoverable` on `IconButton` & Tooltip Consolidation

### Context & Problem
`CodeEditor.kt` (`EditorIconButtonWithTooltip`) and `TopToolbar.kt` (`ToolbarIconButtonWithTooltip`) explicitly attach `.hoverable(interactionSource)` to Material 3 `IconButton`. In Compose Material 3, `IconButton` already internally consumes and handles hover, press, and focus states through its own `clickable` modifier. Attaching an external `.hoverable` with a separate `interactionSource` creates competing gesture detection layers that can swallow or delay pointer click events on nested buttons. In addition, both functions duplicate the centralized `IconButtonWithTooltip` in `SharedTooltip.kt`.

### Decision
1. Eliminate redundant `.hoverable` modifiers on `IconButton` instances.
2. Replace `ToolbarIconButtonWithTooltip` and `EditorIconButtonWithTooltip` with the centralized [`IconButtonWithTooltip`](file:///home/user/IdeaProjects/Clapi/ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SharedTooltip.kt#L163-L227) component from `SharedTooltip.kt`.
3. In `IconButtonWithTooltip`, pass `interactionSource` directly to `IconButton(interactionSource = interactionSource, onClick = onClick)` without `.hoverable`.

### Rationale
- Completely removes click-swallowing race conditions on desktop.
- Consolidates tooltip hover delay (200ms debounce), tooltip popup styling, and border/background tokens in a single source of truth.

### Alternatives Considered
- *Keep separate tooltip functions and only remove `.hoverable`*: Rejected because maintaining duplicate 50-line tooltip functions with different delay constants in three different files causes drift and bug replication.

---

## 3. Desktop Scrollbar Ergonomics & Layout Patterns

### Context & Problem
Desktop platforms require visual and interactive scrollbars for scrollable content. Users cannot rely solely on mouse wheels or touchpads to know whether content overflows or to navigate long lists (e.g., responses, timing phases, headers, cookies, console logs, settings). Currently, only 4 components in `ui-shared` implement scrollbars.

### Decision
1. For lazy lists (`LazyColumn`, `LazyRow`):
   - Wrap the list and scrollbar in a `Box(modifier = modifier)`.
   - Use `val listState = rememberLazyListState()`.
   - Place `PlatformLazyVerticalScrollbar` (or `VerticalScrollbar(adapter = rememberScrollbarAdapter(listState))`) aligned at `Alignment.CenterEnd`.
   - Add end padding to the lazy list (`padding(end = 10.dp)`) so content does not render underneath the scrollbar thumb.
2. For scrollable columns (`Column(Modifier.verticalScroll(scrollState))`):
   - Wrap in a `Box(modifier = modifier)`.
   - Place `PlatformColumnVerticalScrollbar(scrollState = scrollState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())`.
3. For horizontal request tabs (`Row(Modifier.horizontalScroll(scrollState))`):
   - Pair with `HorizontalScrollbar(adapter = rememberScrollbarAdapter(scrollState))` along the bottom edge if tabs overflow.

### Rationale
- Leverages the existing abstraction [`PlatformScrollbar.kt`](file:///home/user/IdeaProjects/Clapi/ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/platform/PlatformScrollbar.kt), which already wraps Compose Desktop scrollbars cleanly with test tags and proper alignment.
- Matches native desktop expectations across Linux, macOS, and Windows.

### Alternatives Considered
- *Custom scrollbar implementation*: Unnecessary; Compose Multiplatform Desktop provides first-class `VerticalScrollbar` and `HorizontalScrollbar` with native physics and theme colors.

---

## 4. Parameter Ordering Convention Standardization

### Context & Problem
In `SplitPane.kt`, `HorizontalSplitPane` and `VerticalSplitPane` declare `modifier: Modifier = Modifier` as the *first parameter*, ahead of required arguments `splitFraction: Float` and `onSplitChanged: (Float) -> Unit`. In `MethodBadge.kt`, `modifier: Modifier = Modifier` is placed after boolean flags (`compact`, `sse`), rather than as the first optional parameter.

### Decision
1. Reorder `HorizontalSplitPane` and `VerticalSplitPane`:
   - Mandatory parameters first: `splitFraction: Float`, `onSplitChanged: (Float) -> Unit`.
   - First optional parameter: `modifier: Modifier = Modifier`.
   - Trailing lambda / slot content: `first: @Composable () -> Unit`, `second: @Composable () -> Unit`.
2. Reorder `MethodBadge`:
   - Mandatory parameters first: `method: HttpMethodType`.
   - First optional parameter: `modifier: Modifier = Modifier`.
   - Subsequent optional parameters: `compact: Boolean = false`, `sse: Boolean = false`.

### Rationale
- Adheres to Jetpack Compose and Kotlin Multiplatform coding standards:
  `fun MyComponent(required1: Type, required2: Type, modifier: Modifier = Modifier, optionalFlag: Boolean = false, content: @Composable () -> Unit)`
- Eliminates named-argument friction at call sites when styling containers.

---

## Summary of Decisions

| Violation Category | Affected Components | Selected Solution |
|---|---|---|
| **Redundant `.hoverable`** | `CodeEditor.kt`, `TopToolbar.kt` | Remove `.hoverable`; replace with `SharedTooltip.kt:IconButtonWithTooltip` |
| **Missing Modifiers** | 17 Workspace Panels, 14 Dialogs | Add `modifier: Modifier = Modifier` as first optional param; chain on root layout |
| **Missing Scrollbars** | 16 Scrollable Panels/Views | Wrap in `Box` + pair with `PlatformScrollbar` / `rememberScrollbarAdapter` |
| **Parameter Ordering** | `SplitPane.kt`, `MethodBadge.kt` | Move required params before `modifier`, ensure `modifier` is first optional param |
