# Data Model & UI Structural Schema: Fix Desktop UI High-Severity Violations

**Feature**: `020-fix-desktop-ui-violations`  
**Date**: 2026-11-13  
**Status**: Draft

---

## 1. UI Structural Models & Contracts

### 1.1 Composable Modifier Envelope
Defines the standard architectural parameter envelope for all public and internal Clapi UI composables.

```text
┌────────────────────────────────────────────────────────────────┐
│                   Composable Parameter Flow                    │
├────────────────────────────────────────────────────────────────┤
│ 1. Mandatory Domain Inputs (state, value, domain id)           │
│ 2. Mandatory Event Handlers (onClick, onValueChanged)          │
│ 3. FIRST Optional Parameter: modifier: Modifier = Modifier    │
│ 4. Other Optional Parameters (compact: Boolean, etc.)          │
│ 5. Trailing Composable Lambdas (content, slots)                │
└───────────────────────────────┬────────────────────────────────┘
                                │ applied directly to
                                ▼
┌────────────────────────────────────────────────────────────────┐
│               Root Container Layout (Box / Column)             │
│               modifier.fillMaxSize() / modifier.fillMaxWidth() │
└────────────────────────────────────────────────────────────────┘
```

- **Validation Rules**:
  - `modifier` MUST be optional with default `Modifier`.
  - `modifier` MUST be the first optional parameter in the argument list.
  - Sizing constraints (e.g. `fillMaxSize()`, `fillMaxWidth()`, `widthIn()`) MUST NOT be in the parameter default; they must be chained internally inside the composable body on the incoming `modifier`.

---

### 1.2 Desktop Scrollable Container Schema
Represents the structural pattern pairing a scrollable viewport with a desktop scrollbar.

```text
┌────────────────────────────────────────────────────────────────┐
│ Box(modifier = modifier)                                       │
│ ┌───────────────────────────────────────────────┬────────────┐ │
│ │ Scrollable Viewport                           │ Vertical   │ │
│ │ (LazyColumn / Column.verticalScroll)          │ Scrollbar  │ │
│ │ state = listState / scrollState               │ (end edge) │ │
│ │ padding(end = 12.dp)                          │            │ │
│ └───────────────────────────────────────────────┴────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

- **Fields & Attributes**:
  - `viewportState`: Instance of `LazyListState` (for lazy lists) or `ScrollState` (for continuous vertical/horizontal scroll columns/rows).
  - `adapter`: `ScrollbarAdapter` generated via `rememberScrollbarAdapter(viewportState)`.
  - `orientation`: `Orientation.Vertical` (standard) or `Orientation.Horizontal` (for tabs).
  - `scrollbarAlignment`: `Alignment.CenterEnd` (for vertical) or `Alignment.BottomCenter` (for horizontal).
  - `endMargin`: Minimum 10.dp padding on the viewport container to prevent content occlusion by the scrollbar thumb.

---

### 1.3 Tooltip Action Interaction State
Models the debounce and pointer tracking cycle for interactive desktop icon buttons.

- **State Attributes**:
  - `isHovered: Boolean` — Observed from `interactionSource.collectIsHoveredAsState()`.
  - `tooltipVisible: Boolean` — Toggled true after debounce delay, reset to false on pointer exit or click.
  - `delayMillis: Long` — Standard desktop hover delay (default: 200ms).
  - `offset: IntOffset` — Position offset relative to the host icon button (default: `(0, 26)`).

- **State Transitions**:
  ```text
  [Pointer Enters Icon]
         │
         ▼
  `isHovered = true` ──(200ms debounce)──► `tooltipVisible = true` (Popup appears)
         │                                       │
         ├───────────────────────────────────────┤
         ▼                                       ▼
  [Pointer Leaves OR Click Triggered] ──► `tooltipVisible = false` (Popup dismissed immediately)
  ```

- **Validation Rules**:
  - `IconButton` MUST receive `interactionSource = interactionSource` directly.
  - Redundant `.hoverable(interactionSource)` MUST NOT be attached to `IconButton`.
  - Click actions MUST immediately dismiss active tooltips without swallowing the click event.

---

### 1.4 Split Pane Geometry Model
Represents the partition bounds and configuration for `HorizontalSplitPane` and `VerticalSplitPane`.

- **Attributes**:
  - `splitFraction: Float` — Partition ratio between 0.0f and 1.0f (Mandatory).
  - `onSplitChanged: (Float) -> Unit` — Callback emitting updated ratio during dragging (Mandatory).
  - `modifier: Modifier = Modifier` — Container layout modifier (First Optional).
  - `minFraction: Float` — Lower bound constrain (default: `0.15f`).
  - `maxFraction: Float` — Upper bound constrain (default: `0.85f`).
  - `first: @Composable () -> Unit` — Primary pane content.
  - `second: @Composable () -> Unit` — Secondary pane content.
