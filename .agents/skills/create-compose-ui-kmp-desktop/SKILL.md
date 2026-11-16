---
name: create-compose-ui-kmp-desktop
description: Create and evaluate Compose Multiplatform UI elements (screens, workspace layouts, components, desktop dialogs) for Kotlin Multiplatform projects targeting Desktop (JVM). Use when creating new desktop Compose UI elements, implementing desktop screens or components, or auditing and refactoring existing @Composable functions targeting desktop.
metadata:
   author: Clapi Contributors
   last-updated: '2026-11-11'
   keywords:
      - Kotlin Multiplatform
      - Compose Multiplatform
      - Desktop
      - JVM
      - UI Development
      - UI Components
      - Code Review
      - Best Practices
      - Modifiers
      - Previews
      - State Hoisting
      - AppState
      - Desktop Ergonomics
      - Scrollbars
      - Keyboard Shortcuts
      - Mouse & Hover
   scope: Compose Multiplatform UI code targeting Desktop (JVM) in Clapi (.kt files containing @Composable)
   primary-goal: Guide the creation of new Compose Multiplatform UI elements for Desktop and audit existing composables for desktop design system adherence, performance, state hoisting, package structure, desktop ergonomics (scrollbars, mouse hover, keyboard shortcuts), and preview standards.
---

# Compose Multiplatform UI Elements: Desktop Creation & Evaluation Guide

## Purpose

Use this skill when **creating new Compose Multiplatform UI elements** (screens, workspace panels, reusable components, desktop dialogs) or when **evaluating, auditing, and refactoring** existing `@Composable` functions targeting **Desktop (JVM)** in Clapi.

This guide targets **ONLY Desktop (JVM)** and standardizes:
1. **Creation Workflow**: Step-by-step process for authoring clean, idiomatic, and testable desktop Compose elements in `ui-shared`.
2. **Desktop Ergonomics**: Integrating desktop scrollbars, mouse hover states, pointer cursor icons (`PointerIcon.Hand`), tooltips, and keyboard shortcuts.
3. **Templates & Patterns**: Canonical Clapi desktop templates for workspace views, leaf components, dialogs with `DialogState`, and previews.
4. **Core Standards Checklist**: The 8 golden rules for desktop modifier hygiene, state hoisting, performance, theming, resources, and previews.
5. **Audit Protocol**: Structured reporting format for evaluating and refactoring desktop Compose code.

Primary reference guides:
* [compose-desktop-best-practices.md](references/compose-desktop-best-practices.md) - Performance, lazy layout keys, desktop scrollbars, hover styling, keyboard navigation, and backwards write avoidance.
* [compose-desktop-state.md](references/compose-desktop-state.md) - State management in desktop JVM, `remember`, Unidirectional Data Flow (UDF), and `AppState` architecture.

---

## Component Architecture for Desktop in Clapi

Clapi organizes Compose UI into four distinct architectural layers across modules:

```
┌────────────────────────────────────────────────────────┐
│ 1. Root Workspace Shell & Coordinators                 │
│    Hosts toolbar, sidebar, tabs, editors, and dialogs. │
│    Interacts with AppState, DialogState, Tabs.         │
│    (e.g., MainScreen, TopToolbar, Sidebar)             │
└───────────────────────────┬────────────────────────────┘
                            │ (State down, events up)
┌───────────────────────────▼────────────────────────────┐
│ 2. Desktop Workspace Panels & Editors                  │
│    Feature layouts; hosts tab-scoped editors, tables,   │
│    panels, and vertical/horizontal desktop scrollbars.  │
│    (e.g., RequestEditor, KeyValueEditor, BottomPanel)  │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 3. Reusable Desktop Components (Leaf Views)            │
│    Cards, chips, method badges, buttons, tooltips;     │
│    accepts Modifier = Modifier, pointer hover icons,   │
│    tooltip integration, and hover background styles.   │
│    (e.g., MethodBadge, IconButtonWithTooltip)          │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 4. Desktop Dialogs & Alerts (Modal Overlays)           │
│    Managed via AppState.dialogs (DialogState) using     │
│    AppDialog (feature dialogs) & AppAlert (alerts).    │
│    (e.g., SettingsDialog, ConfirmDeleteDialog)         │
└────────────────────────────────────────────────────────┘
```

| Layer | Responsibility | Module & Package Location | Example |
|---|---|---|---|
| **Root Shell** | Window-level shell, toolbar, sidebar docking, and dialog hosts | `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/` | [`MainScreen.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/MainScreen.kt) |
| **Workspace Panel** | Workspace-mode panels, tab content editors with scrollbars | `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/` | [`RequestEditor.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/RequestEditor.kt), [`ResponseViewer.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ResponseViewer.kt) |
| **Component** | Reusable leaf UI building block with hover & cursor styling | `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/` | [`KeyValueEditor.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/KeyValueEditor.kt), [`SharedTooltip.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt) |
| **Dialog & Alert** | Modals managed via `AppState.dialogs` (`AppDialog` / `AppAlert`) | `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/` | [`ConfirmDeleteDialog.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ConfirmDeleteDialog.kt), [`SettingsDialog.kt`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SettingsDialog.kt) |
| **Previews** | Desktop tool preview functions (for sophisticated views only) | `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/preview` | [`MainScreenPreviews.kt`](/ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/preview/MainScreenPreviews.kt), [`SidebarPreviews.kt`](/ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/preview/SidebarPreviews.kt) |

---

## Step-by-Step Creation Workflow

When creating or modifying desktop UI elements in Clapi:

### Step 1: Determine Component Classification & Location
- **Shared Composable UI**: 95%+ of UI belongs in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/` (or the root package for shell composables).
- **Desktop Launcher**: `ui-desktop/src/desktopMain/kotlin/io/github/makeforge/ui/desktop/Main.kt` is a thin launcher that wires window chrome and invokes `MainScreen`.
- **Previews**: **ONLY** created in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/preview`. Never place `@Preview` annotations inside `ui-shared` (as `ui-shared` is a shared multiplatform module without desktop preview runner dependencies).
- **File Splitting & Subpackages for Long Files/Composables**: If a composable or file gets too long, check if it can be meaningfully split without too much effort into multiple files (e.g., separating leaf views, tree node items, row items, modal dialogs, context menus, and data helpers into sibling files). When splitting, evaluate whether the files should be placed into a dedicated subpackage (e.g., `ui.shared.components.sidebar`, `ui.shared.components.dialogs`, etc.) to establish a cleaner hierarchy instead of cluttering a single flat package. Truly shared/reusable cross-cutting components (such as badges or generic inputs) should remain in the shared parent package or a dedicated common subpackage.

### Step 2: Design State & Event Interface (UDF)
- Keep UI components **stateless** whenever possible by hoisting state to the caller.
- **No Android ViewModels or DI Frameworks**: Clapi does not use Android ViewModels or Koin. State is centralized in [`AppState`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/AppState.kt), slice state holders like [`RequestTabState`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/RequestTabState.kt), and [`DialogState`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/DialogState.kt).
- For leaf/reusable components, accept immutable values and emit explicit event callbacks (e.g. `onClick: () -> Unit`, `onValueChanged: (String) -> Unit`).
- If a component coordinates multiple actions, bundle them or pass high-level domain intents rather than leaking mutable state.

### Step 3: Composable Signature & Modifier Hygiene
- **Mandatory Modifier Parameter**: Every public or reusable `@Composable` function MUST accept `modifier: Modifier = Modifier` as its first optional parameter.
- **Root Application**: Apply the passed `modifier` directly to the top-level layout container inside the function.
- **No Hardcoded Parameter Defaults**: Never supply pre-configured sizing modifiers (such as `Modifier.fillMaxWidth()`) as the parameter default value. Chain internal defaults inside the body if required (`modifier.fillMaxWidth()`).

```kotlin
// BAD
@Composable
fun RequestItemRow(name: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(8.dp)) { ... } // Ignored caller modifier!
}

// GOOD
@Composable
fun RequestItemRow(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) { ... }
}
```

### Step 4: Strings, Resources & Theming
- **Strings & Localization**: All user-facing strings MUST use [`Strings`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/i18n/Strings.kt):
  ```kotlin
  import io.github.makeforge.clapi.ui.shared.i18n.Strings

  Text(text = Strings.save)
  Text(text = Strings.t("add_request"))
  ```
  *Never use Android `R.string`, Android Context, or hardcoded raw strings for user-visible copy.*
- **Icons & Drawables**: Use Compose Multiplatform resources generated in `ui-shared`:
  ```kotlin
  import clapi.ui_shared.generated.resources.Res
  import clapi.ui_shared.generated.resources.ic_add
  import org.jetbrains.compose.resources.painterResource

  Icon(
      painter = painterResource(Res.drawable.ic_add),
      contentDescription = Strings.t("add_request"),
  )
  ```
   If you want to add a new material icon, see skill `add-material-compose-icon`
- **Colors & Theming**: Reference [`ClapiColors`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/theme/ClapiTheme.kt) and MaterialTheme tokens.
  - Backgrounds: `ClapiColors.Background`, `ClapiColors.Surface`, `ClapiColors.SurfaceHigh`
  - Text: `ClapiColors.OnSurface`, `ClapiColors.OnSurfaceDim`, `ClapiColors.OnSurfaceVariant`
  - Accents: `ClapiColors.Primary`, `ClapiColors.Border`, `ClapiColors.HoverOverlay`

### Step 5: Desktop Ergonomics
Desktop applications rely on physical keyboard and mouse interaction:

1. **Scrollbars**: Always pair scrollable containers (`LazyColumn`, `LazyRow`, `verticalScroll`, `horizontalScroll`) with desktop scrollbars:
   ```kotlin
   val listState = rememberLazyListState()
   Box(modifier = modifier.fillMaxSize()) {
       LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) { ... }
       VerticalScrollbar(
           adapter = rememberScrollbarAdapter(listState),
           modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
       )
   }
   ```
2. **Mouse & Hover**:
   - Use `Modifier.pointerHoverIcon(PointerIcon.Hand)` on clickable items, cards, and custom controls.
   - For icon buttons with hover hints, use [`IconButtonWithTooltip`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt) or [`HoverTooltip`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt).
   - > [!WARNING]
     > Do NOT attach redundant `.hoverable(interactionSource)` modifiers to `IconButton`, because Compose Material 3 `IconButton` already handles hover and press interactions via its internal `clickable`. Doing so can swallow pointer click events on nested buttons.
3. **Keyboard Navigation & Shortcuts**:
   - Intercept desktop shortcuts via `Modifier.onKeyEvent { ... }` checking `(it.isCtrlPressed || it.isMetaPressed)`.
   - Modals, dialogs, and popup overlays must handle `Key.Escape` to dismiss cleanly.

### Step 6: Dialogs & Alerts Integration (`DialogState`)
Dialogs in Clapi follow the centralized [`DialogState`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/state/DialogState.kt) architecture:
1. **Feature Dialogs**: Sealed entries in `AppDialog` (e.g. `AppDialog.Settings`, `AppDialog.GitSync`, `AppDialog.EnvironmentEdit(index)`).
   - Opened via `state.showSettingsDialog = true` or `state.dialogs.show(AppDialog.Settings)`.
   - Closed via `state.dialogs.dismiss(AppDialog.Settings)`.
2. **Alerts**: Stacked entries in `AppAlert` (`Confirm`, `Operation`, `Error`).
   - Triggered via `state.showConfirm(title, message, onConfirm)`, `state.startOperation(title, message, job)`, or `state.showError(title, message)`.
   - Handled in `MainScreen` via overlays (`ConfirmDeleteDialog`, `OperationProgressDialog`, `ErrorMessageDialog`).
3. **Dialog Composables**:
   - Use Compose Desktop `Dialog` or `AlertDialog` with width constraints (e.g. `Modifier.widthIn(min = 400.dp, max = 600.dp)`).
   - Support dragging/repositioning where appropriate (as in `ConfirmDeleteDialog`).

### Step 7: Previews (Only for Sophisticated Views, in `ui-desktop`)
> [!IMPORTANT]
> **Do NOT create a preview for every composable.**
> Small leaf elements, buttons, badges, and minor components do not need previews. Previews are reserved exclusively for **sophisticated views** (such as `MainScreen`, `RequestEditor`, `Sidebar`, or complex dialogs) to make them more easily maintainable and debuggable.
>
> **Previews MUST ONLY be placed in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/preview`**, using:
> `import androidx.compose.desktop.ui.tooling.preview.Preview`
> Never add `@Preview` in `ui-shared`.

---

## Canonical Clapi Desktop Templates

### 1. Workspace Panel / Editor View Template (`ui-shared`)

*Place in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/`.*

```kotlin
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun RequestSummaryPanel(
    items: List<SummaryItemState>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize().background(ClapiColors.Background)) {
        Text(
            text = Strings.t("summary"),
            color = ClapiColors.OnSurface,
            fontSize = 14.sp,
            modifier = Modifier.padding(16.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp, start = 16.dp),
            ) {
                items(items = items, key = { it.id }) { item ->
                    SummaryItemRow(
                        item = item,
                        onClick = { onItemClick(item.id) },
                    )
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
            )
        }
    }
}
```

### 2. Reusable Leaf Component with Hover & Pointer Icon (`ui-shared`)

*Place in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/`.*

```kotlin
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun DesktopItemRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isHovered && enabled) ClapiColors.HoverOverlay else ClapiColors.Surface)
            .hoverable(interactionSource)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = ClapiColors.OnSurface,
            fontSize = 13.sp,
        )
    }
}
```

### 3. Desktop Modal Dialog (`ui-shared`)

*Place in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/`.*

```kotlin
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.theme.ClapiColors

@Composable
fun DesktopCustomDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .widthIn(min = 380.dp, max = 520.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceHigh)
                .border(1.dp, ClapiColors.Border, RoundedCornerShape(8.dp))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = ClapiColors.OnSurface,
                fontSize = 15.sp,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(Strings.cancel, color = ClapiColors.OnSurface)
                }
                Spacer(Modifier.padding(end = 8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = ClapiColors.Primary),
                ) {
                    Text(Strings.save)
                }
            }
        }
    }
}
```

### 4. Desktop Preview for Sophisticated Views (`ui-desktop`)

*Place in `ui-desktop/src/desktopTest/kotlin/io/github/makeforge/clapi/ui/desktop/preview`.*

```kotlin
package io.github.makeforge.clapi.ui.desktop.preview

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.AppTheme
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme

@Preview
@Composable
fun MainScreenSophisticatedPreview() {
    ClapiTheme(appTheme = AppTheme.DARK) {
        MainScreen(state = AppState(withDemoData = true))
    }
}
```

---

## Evaluation & Audit Protocol

When evaluating existing or newly created desktop `@Composable` functions, review against these 8 core criteria:

### The 8 Core Standards for Desktop Compose in Clapi

1. **Modifiers**:
   - Accepts `modifier: Modifier = Modifier` as the first optional parameter.
   - Passed `modifier` is applied directly to the root layout element.
   - Never supplies hardcoded sizing modifiers (e.g. `Modifier.fillMaxWidth()`) as default values.
2. **Targeted Desktop Previews**:
   - Previews are created **only for sophisticated views** (e.g. full workspace screens, primary editor tabs, sidebar), **not** for individual small buttons or leaf widgets.
   - Previews reside strictly in `ui-desktop` under package `io.github.makeforge.clapi.ui.desktop.preview`.
   - Never add `@Preview` inside `ui-shared`.
   - Previews wrap content in `ClapiTheme`.
3. **State Hoisting & Clapi State Models**:
   - Stateless leaf components separated from stateful coordinators.
   - Coordinates with `AppState`, `RequestTabState`, or `DialogState` at panel levels; no Android ViewModels or Koin.
   - Reusable components accept explicit values and emit callbacks (`UDF`).
4. **Strings, Resources & Theming**:
   - All text comes from `io.github.makeforge.clapi.ui.shared.i18n.Strings` (e.g. `Strings.save`, `Strings.t("...")`). Zero raw user-visible strings.
   - Drawables loaded from `clapi.ui_shared.generated.resources.Res`. Zero Android `R.*` imports.
   - Colors and styling use `ClapiColors` and `ClapiTheme`.
5. **Desktop Ergonomics**:
   - Scrollable areas (`LazyColumn`, `LazyRow`, `verticalScroll`) have desktop scrollbars (`VerticalScrollbar` / `rememberScrollbarAdapter`).
   - Clickable interactive items have `Modifier.pointerHoverIcon(PointerIcon.Hand)`.
   - Keyboard shortcuts (`Escape` to dismiss dialogs, `Ctrl`/`Cmd` combos) supported.
   - Tooltips use `IconButtonWithTooltip` (without redundant `.hoverable` on `IconButton`).
6. **Performance & Memory**:
   - Lazy layout items have unique, stable `key` expressions (`items(items, key = { it.id })`).
   - Heavy computations wrapped in `remember(keys)`.
   - `derivedStateOf` used for rapid state change thresholds (e.g. scroll offsets).
   - Zero backwards writes during composition passes.
7. **Module & Function Locality & File Sizing**:
   - Shared UI code in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/`.
   - Launcher and desktop previews in `ui-desktop/src/desktopMain/`.
   - Large composables (>100 lines) decomposed into focused private helper composables.
   - **Meaningful File Splitting & Subpackages**: If composables or UI files become excessively long, check if they can be meaningfully split without too much effort into multiple files. Evaluate whether to organize the split files into a dedicated subpackage (e.g., `components.sidebar`, `components.editor`, `components.dialogs`) for cleaner architectural hierarchy. Extract leaf widgets, tree node items, row components, modal dialogs, context menus, and pure state/tree manipulation helper functions into dedicated sibling files under the subpackage to keep file sizes manageable, readable, and maintainable, while keeping generic cross-cutting widgets accessible.
8. **Reactive State Observation**:
   - State mutations use Compose `mutableStateOf`, `mutableStateListOf`, or `derivedStateOf`.
   - Avoid standard non-observable collections (`ArrayList`, `mutableListOf`) as direct state.

### Audit Report Structure

When performing a dedicated audit or code review, compile findings into this report format:

```markdown
# Compose Multiplatform Desktop Evaluation Report

**Target Scope**: `[Evaluated Files / Functions / Packages]`
**Date**: `[YYYY-MM-DD]`

---

## Executive Summary
- **Total Composables Audited**: `X`
- **Errors / Best Practice Violations**: `X`
- **Warnings**: `X`
- **Suggestions / Optimizations**: `X`

---

## 🚨 Errors / Best Practice Violations (High Severity)
*Issues impacting correctness, missing scrollbars, Android leaks, missing modifiers, or backwards writes.*

### 1. `[ComposableName]` - `[File.kt:Line]`
- **Category**: `[Modifiers / Android Leak / Performance / State Hoisting]`
- **Issue**: Description of violation.
- **Recommendation**:
```kotlin
// BEFORE
...

// AFTER
...
```

---

## ⚠️ Warnings (Medium Severity)
*Un-keyed lazy lists, missing pointer hover icons, preview in wrong module, or hardcoded strings.*

### 1. `[ComposableName]` - `[File.kt:Line]`
- **Category**: `[Desktop Ergonomics / Keys / Localization / Previews]`
- **Issue**: Description.
- **Recommendation**: Suggested fix.

---

## 💡 Suggestions & Optimizations (Low Severity)
*Opportunities for derivedStateOf, deferred state reads, keyboard shortcuts, or function splitting.*

---

## Next Steps
- [ ] Fix high-severity issues
- [ ] Add missing desktop scrollbars and pointer hover icons
- [ ] Ensure any required previews for sophisticated views are in `ui-desktop.preview`
```
