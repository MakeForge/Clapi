# UI Contract: Desktop Scrollbars

**Type**: Layout & Ergonomics Contract  
**Applies to**: All scrollable panels, editors, lists, and dialogs in `ui-shared`

---

## 1. Lazy Layout Scrollbar Contract (`LazyColumn`)

When rendering collections with dynamic or large sets of rows, use `LazyColumn` paired with `PlatformLazyVerticalScrollbar` (or `VerticalScrollbar`):

```kotlin
@Composable
fun <ListPanel>(
    items: List<Item>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp),
        ) {
            items(items = items, key = { it.id }) { item ->
                ItemRow(item)
            }
        }

        PlatformLazyVerticalScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            testTag = "panel_vertical_scrollbar",
        )
    }
}
```

### Invariants:
1. `listState` must be shared between `LazyColumn` and the scrollbar adapter.
2. The list must have end padding (`end = 12.dp`) to avoid thumb overlap.
3. The scrollbar must be aligned to `Alignment.CenterEnd` with `fillMaxHeight()`.

---

## 2. Scrollable Column Contract (`verticalScroll`)

When rendering vertical layouts with fixed but overflowing content:

```kotlin
@Composable
fun <FormPanel>(
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 12.dp),
        ) {
            // Form sections and fields
        }

        PlatformColumnVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            testTag = "form_vertical_scrollbar",
        )
    }
}
```

---

## 3. Horizontal Scrollbar Contract (`horizontalScroll`)

For scrollable tab bars or wide code lines:

```kotlin
@Composable
fun RequestTabsBar(
    tabs: List<RequestTabState>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(bottom = 4.dp),
        ) {
            // Tab chips
        }

        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}
```
