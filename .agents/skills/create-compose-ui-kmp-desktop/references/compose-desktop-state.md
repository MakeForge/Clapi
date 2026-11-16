# State in Compose Multiplatform (Desktop Target)

State management in Compose Multiplatform for Desktop follows declarative Unidirectional Data Flow (UDF) principles adapted to desktop runtime and windowing lifecycles in Clapi.

## 1. Composition and Recomposition in Desktop JVM

* **Composition**: The tree of composables built by Compose Multiplatform when rendering the desktop window content (`MainScreen`).
* **Initial Composition**: Executed when the desktop window is created and composables first evaluate.
* **Recomposition**: Triggered when observed Compose `State<T>` values change, re-executing only the affected nodes.
* **Desktop Window Lifecycle**: Unlike Android, desktop windows do not undergo configuration restarts (such as screen rotations or process death recreation). Desktop windows can be resized, minimized, or restored without destroying the underlying JVM state.

## 2. Managing State in Desktop Composables

Use `remember` combined with `mutableStateOf` to retain transient UI state across recompositions in memory:

```kotlin
var searchText by remember { mutableStateOf("") }
```

### Observable Collections
> [!CAUTION]
> Do not use standard mutable collections like `ArrayList` or `mutableListOf()` directly as Compose state. Changes to standard JVM collections are non-observable and will not trigger recomposition.
> Use immutable lists (`List<T>`) stored in `MutableState<List<T>>` or use `mutableStateListOf<T>()` / `mutableStateMapOf<K, V>()`.

```kotlin
// RECOMMENDED: Compose snapshot collection
val items = remember { mutableStateListOf<String>() }

// OR: Immutable list in MutableState
val selectedItemIds = remember { mutableStateOf(setOf<String>()) }
```

## 3. Clapi State Architecture & Unidirectional Data Flow (UDF)

In Clapi, application state is organized in clear, observable state containers in `ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/state/`:

* **`AppState`**: The root application state holder. Contains observable lists (`collections`, `environments`, `openTabs`, `history`), active tab indices, sidebar configuration, and sub-states.
* **`RequestTabState`**: Per-tab mutable state holding request definitions, response state, active sub-tabs, and dirty state.
* **`DialogState`**: Centralized dialog and alert stack (`AppDialog` for feature dialogs like `Settings` and `GitSync`, `AppAlert` for alerts like `Confirm`, `Operation`, `Error`).

```
          ┌────────────────────────────────────────┐
          │               AppState                 │
          │  (collections, openTabs, dialogs, ...) │
          └──────────────────┬─────────────────────┘
                             │ passes state slices
                             ▼
          ┌────────────────────────────────────────┐
          │             Workspace / Screen         │
          │      (MainScreen, RequestEditor)       │
          └──────────────────┬─────────────────────┘
                             │ passes values / lambdas
                             ▼
          ┌────────────────────────────────────────┐
          │             Leaf Component             │
          │  (stateless; emits onClick/onChanged)  │
          └────────────────────────────────────────┘
```

### State Hoisting Rules for Components
1. **Stateless Leaves**: Reusable components (badges, rows, buttons, inputs) accept pure state values and emit event callbacks (`onClick`, `onValueChanged`). They should never hold direct mutable state references to whole application singletons.
2. **Action Callbacks**: Keep component interfaces clean by exposing specific callbacks rather than passing broad mutable containers down the tree.

```kotlin
@Composable
fun DesktopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
    )
}
```

## 4. Converting External Observable Streams

Compose on Desktop observes reactive streams by converting them into Compose `State<T>`:

* **Kotlin Coroutines `StateFlow` / `SharedFlow`**:
  ```kotlin
  val searchResult by searchFlow.collectAsState(initial = emptyList())
  ```
  *(Note: Desktop Compose uses `collectAsState()`; Android-only APIs like `collectAsStateWithLifecycle()` are not used.)*

## 5. Re-evaluating `remember` with Input Keys

Pass dependent keys into `remember(key1, key2)` so that cached objects or state holders are recomputed when their source inputs change.

```kotlin
val formattedDate = remember(timestamp, dateFormat) {
    formatTimestamp(timestamp, dateFormat)
}
```

## 6. Desktop Window & Persistence Retention

Because desktop applications do not experience Android Activity destruction on window resize, transient UI states in `remember` remain stable throughout the window session.
For state that must persist across application restarts:
* **Settings & Preferences**: Persisted via `SettingsRepository` backed by `PlatformStorage`.
* **Workspace Data**: Persisted via `WorkspaceRepository` and `TabsRepository`.
* Never use Android-specific `SavedStateHandle` or Android `Bundle`s in Clapi.
