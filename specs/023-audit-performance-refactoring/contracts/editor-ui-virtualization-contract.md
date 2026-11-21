# Contract: Off-Main-Thread Execution, Stream Assembly & UI Virtualization

**Feature**: `023-audit-performance-refactoring`  
**Covers**: `FR-007`, `FR-008`, `FR-009`, `FR-012`, `FR-013`, `FR-014`, `FR-017`

---

## 1. Off-Main-Thread Script & File Picker Execution (`core-scripting`, `ui-shared`)

1. **`PlatformJsRuntime.desktop.kt`**:
   - `actual suspend fun evaluateJs(script: String): String` MUST execute the GraalJS `Context` creation and `context.eval("js", script)` inside `withContext(Dispatchers.Default)` so the UI thread is never blocked during script evaluation or timeout enforcement.
2. **`PlatformApi.desktop.kt`**:
   - `pickFileForImport`, `pickBinaryFileForRequest`, and `saveFileForExport` MUST perform file reads (`readText`, `readBytes`), Base64 encoding, and file writes (`writeText`) on `Dispatchers.IO` and deliver completion callbacks on `Dispatchers.Main`.

---

## 2. Incremental Stream Assembly & Zero-Allocation Response Size (`core-network`, `ui-shared`)

1. **Incremental Stream Assembly (`StreamSupport.kt`)**:
   - `LlmTextAssembler.extractChunkDelta(rawChunk: String): String?` extracts the text delta from a single SSE/JSON stream chunk in $O(1)$ time.
   - During streaming in `RequestExecutor` and `GraphQlRequestExecutor`, each `NetworkEvent.Chunk` appends only the new delta (falling back to full `LlmTextAssembler.assemble(...)` only once when `NetworkEvent.Completed` arrives if needed).
2. **Zero-Allocation UTF-8 Size & Guarded LLM Parsing (`KtorApiClient.kt`)**:
   - `HttpResponse.toResponseDefinition` computes `bodyText.utf8ByteLength()` only when `Content-Length` is absent or negative, without calling `bodyText.encodeToByteArray()`.
   - `LlmTextAssembler.assembleFromBody(bodyText)` is invoked only when `bodyText.length <= 512_000` and `bodyText` contains an LLM envelope marker (`"choices"`, `"content"`, `"response"`, `"delta"`, or `"message"`).

---

## 3. UI Virtualization & Editor Indexing (`ui-shared`, `editor-ui`)

1. **Virtualized `ResponseRawView` (`ResponseViewer.kt`)**:
   - `ResponseRawView` MUST render lines inside a `LazyColumn` (with `SelectionContainer` and `PlatformLazyVerticalScrollbar`) rather than passing the entire response string to a single `Text` node.
2. **Pre-Indexed Fold Regions & Diagnostics (`EditorRenderer.kt`)**:
   - `foldableStartLines: Set<Int>` and `diagnosticsByLine: Map<Int, List<InlineEditorError>>` MUST be computed once via `remember` outside `LazyColumn`'s `items` block so each visible line performs $O(1)$ map/set lookups instead of $O(F + D)$ linear scans.
   - `listState` and `hScrollState` MUST be remembered per `viewModel` (`remember(viewModel)`) without retaining unbounded `mutableMapOf<EditorViewModel, ...>()` caches.
3. **Memoized Variable Highlighting (`BodyEditor.kt`, `VariableHighlight.kt`)**:
   - `definedVarNames` and `variableHighlightProvider` MUST be memoized via `remember` / `derivedStateOf` so `LineView` does not re-run variable regexes on unrelated recompositions.
