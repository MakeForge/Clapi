# Contract: Debounced Persistence, Secret Redaction & Coroutine Cancellation

**Feature**: `023-audit-performance-refactoring`  
**Covers**: `FR-003`, `FR-004`, `FR-005`, `FR-006`, `FR-015`, `FR-016`

---

## 1. Debounced Main-Thread Snapshot Persistence Contract (`ui-shared`)

1. **Debounce Window**:
   - `MainScreen` `snapshotFlow` observers for `SettingsRepository.save`, `TabsRepository.save`, and `WorkspaceRepository.save` MUST apply `.drop(1).debounce(500).collectLatest { ... }`.
2. **Thread-Safe Snapshot Capture**:
   - `TabsRepository.save(state, ioDispatcher)` and `WorkspaceRepository.save(state, ioDispatcher)` MUST read Compose state (`state.openTabs`, `state.collections`, `tab.params`, `tab.headers`, `tab.bodyContents`, etc.) synchronously before switching to `withContext(ioDispatcher)`.
   - Only JSON serialization (`json.encodeToString`) and file/preferences disk writes (`PlatformStorage.putString`, `driver.saveCollection`) execute on `ioDispatcher`.
   - Any UI state updates (`state.showError`, `state.refreshAllGitStatusesAsync`) MUST run on the calling/main dispatcher outside `withContext(ioDispatcher)`.

---

## 2. Secret Log Redaction Contract (`ui-shared`)

```kotlin
object SecretLogRedactor {
    const val MASK = "••••••••"

    fun collectSecretValues(
        state: AppState,
        tab: RequestTabState,
        resolvedSecrets: Map<String, String>?,
    ): Set<String>

    fun redact(text: String, secretValues: Set<String>): String
}
```

### Invariants
1. Every secret value with `length >= 3` from `resolvedSecrets` (Vault, AWS, GCP, Azure, KeePassXC, Keeper) or active environment/collection variables with `secret == true && enabled` MUST be replaced with `"••••••••"` in `state.logNetworkEvent(...)` and `state.logConsole(...)`.
2. Redaction sorts secrets by descending length so longer secrets containing shorter secret prefixes are masked as a single token.

---

## 3. Structured Concurrency & Tab Cancellation Contract

1. **Tab Close Cancellation**:
   - `AppState.closeTab(index)` and `AppState.closeTabsByIds(ids)` MUST invoke `tab.currentJob?.cancel()`, set `tab.currentJob = null`, and set `tab.isLoading = false`.
2. **`CancellationException` Preservation**:
   - Every `try / catch (e: Exception)` block wrapping a `suspend` call in `RequestVariableResolution.kt`, `ClapiScriptEngine.kt`, `DesktopGitCliExecutor.kt`, `McpSessionState.kt`, and `McpClient.kt` MUST precede `catch (e: Exception)` with `catch (e: CancellationException) { throw e }`.
3. **Per-Tab Test Results & Ring-Buffer Caps**:
   - `RequestTabState.testResults` holds each tab's assertion results independently.
   - `AppState.testResults` delegates to `activeTab?.testResults ?: fallbackTestResults` so existing callers and tests remain 100% compatible while switching tabs displays each tab's own results.
   - `consoleLogs` (max 1,000), `networkEventLogs` (max 1,000), `historyItems` (max 500), `webSocketLogMessages` (max 1,000), and `streamChunks` (max 5,000) evict oldest entries when capacity is exceeded.
