# Compose Multiplatform Desktop Best Practices

Follow these performance, ergonomics, and optimization rules for Compose Multiplatform on Desktop (JVM) in Clapi.

## 1. Use `remember` to Minimize Expensive Calculations

Composable functions can execute on every frame during window resizing or animations. Minimize CPU calculations inside the body of a composable. Cache calculation results with `remember(keys)`.

```kotlin
@Composable
fun FilteredCollectionsList(
    collections: List<CollectionNode>,
    query: String,
    modifier: Modifier = Modifier,
) {
    val filteredCollections = remember(collections, query) {
        if (query.isBlank()) collections
        else collections.filter { it.name.contains(query, ignoreCase = true) }
    }

    LazyColumn(modifier = modifier) {
        items(filteredCollections, key = { it.id }) { node ->
            CollectionItemRow(node)
        }
    }
}
```

*Note: For heavy parsing, disk I/O, or network requests, move computations outside the composables into background coroutines or repositories (`Dispatchers.Default` or `Dispatchers.IO`).*

## 2. Use Stable Keys in Lazy Layouts

Lazy layouts (`LazyColumn`, `LazyRow`, `LazyVerticalGrid`) recycle and reuse layout nodes. Always provide a stable, unique key for each item so Compose can track reordered or deleted items across recompositions without reconstructing unmodified items.

```kotlin
@Composable
fun CollectionsList(
    collections: List<CollectionNode>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(
            items = collections,
            key = { node -> node.id },
        ) { node ->
            CollectionRow(node = node)
        }
    }
}
```

## 3. Desktop Scrollbar Integration

Unlike mobile touch screens, desktop users expect visible, interactive scrollbars for mouse wheel scrolling and click-and-drag scroll tracking.

Always pair scrollable columns, rows, and lazy lists with desktop scrollbars:

```kotlin
@Composable
fun ScrollableCollectionsContent(
    collections: List<CollectionNode>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
        ) {
            items(collections, key = { it.id }) { node ->
                CollectionRow(node = node)
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
```

## 4. Mouse Hover, Pointer Cursor & Tooltip Ergonomics

Desktop applications rely on mouse hover indicators, tooltips, and appropriate cursor shapes to communicate interactivity.

* **Pointer Hover Icon**: Use `Modifier.pointerHoverIcon(PointerIcon.Hand)` on clickable cards, buttons, or custom interactable items.
* **Hover States**: Apply visual feedback (subtle container color shifts, `ClapiColors.HoverOverlay`, or outline highlights) when the mouse hovers over interactive components.
* **Tooltips**: Use [`IconButtonWithTooltip`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt) or [`HoverTooltip`](/ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/SharedTooltip.kt) for action buttons.
  > [!WARNING]
  > Never add `.hoverable(interactionSource)` directly onto an `IconButton` modifier. `IconButton` already handles hover and press interactions via its internal `clickable`. Doing so intercepts pointer press events and prevents button clicks from firing when nested inside clickable rows.

```kotlin
@Composable
fun DesktopInteractiveItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isHovered) ClapiColors.HoverOverlay else ClapiColors.Surface)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = ClapiColors.OnSurface)
    }
}
```

## 5. Keyboard Navigation & Shortcuts

Desktop applications must be operable via physical keyboards.

* **Focusability**: Add `Modifier.focusable()` and `focusRequester(requester)` to key interactive zones.
* **Key Event Interception**: Intercept standard desktop shortcuts (`Ctrl`/`Cmd` key combinations, `Escape` to go back or dismiss dialogs) using `Modifier.onKeyEvent`:

```kotlin
val focusRequester = remember { FocusRequester() }

Box(
    modifier = Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.N -> {
                        onCreateNewRequest()
                        true
                    }
                    keyEvent.key == Key.Escape -> {
                        onDismissOrBack()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
)
```

## 6. Use `derivedStateOf` to Limit Recompositions

When state changes at a high frequency (such as scroll position offsets during mouse wheel scrolling), wrap derived state thresholds in `derivedStateOf` to trigger recomposition only when the condition's boolean or bucketed result changes.

```kotlin
val listState = rememberLazyListState()

val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 2 }
}

AnimatedVisibility(visible = showScrollToTop) {
    ScrollToTopButton(onClick = { scope.launch { listState.animateScrollToItem(0) } })
}
```

## 7. Defer State Reads as Long as Possible

Defer reading state to the layout or draw phase whenever values change continuously (e.g. during animations or window resizing). Use lambda modifiers like `Modifier.offset { ... }` or `Modifier.drawBehind { ... }` to avoid composition-phase re-evaluations.

```kotlin
@Composable
fun AnimatedHeader(offsetProvider: () -> Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = offsetProvider()) }
    ) {
        // ...
    }
}
```

## 8. Avoid Backwards Writes

Compose assumes state is never mutated during a composition pass. Writing to state inside the body of a `@Composable` creates recomposition loops and unpredictable rendering passes.

* **Rule**: Never mutate state variables directly in the body of a `@Composable`. State writes belong strictly inside event lambdas (`onClick`, `onValueChange`) or side-effect handlers (`LaunchedEffect`, `DisposableEffect`).
