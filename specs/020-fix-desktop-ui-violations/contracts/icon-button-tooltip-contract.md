# UI Contract: Icon Button Actions & Tooltip Feedback

**Type**: Component Ergonomics & Event Contract  
**Applies to**: Action buttons, toolbar controls, and editor shortcuts in `ui-shared`

---

## 1. Canonical Implementation (`IconButtonWithTooltip`)

All icon buttons with hover tooltips MUST utilize or follow the semantics of [`IconButtonWithTooltip`](file:///home/user/IdeaProjects/Clapi/ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components/SharedTooltip.kt#L163-L227):

```kotlin
@Composable
fun IconButtonWithTooltip(
    onClick: () -> Unit,
    tooltip: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltipTag: String? = null,
    delayMillis: Long = 200L,
    alignment: Alignment = Alignment.BottomCenter,
    offset: IntOffset = IntOffset(0, 26),
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
)
```

---

## 2. Pointer Event Rules & Invariants

1. **No Redundant `.hoverable` on `IconButton`**:
   - `IconButton` already internally tracks hover and press interactions.
   - Do NOT chain `.hoverable(interactionSource)` onto `IconButton(modifier = modifier.hoverable(...))`.
   - Instead, pass `interactionSource` into `IconButton(interactionSource = interactionSource, onClick = onClick)`.

2. **Immediate Click Dispatch**:
   - Clicks MUST execute `onClick()` synchronously on pointer release without being intercepted or blocked by tooltip debounce coroutines.
   - On click, `tooltipVisible` MUST be immediately set to `false`.

3. **Debounced Hover Reveal**:
   - Tooltip visibility must wait `200L` milliseconds of continuous hover before opening to prevent edge-of-icon flicker.
   - Pointer departure must immediately hide the tooltip without lingering.
