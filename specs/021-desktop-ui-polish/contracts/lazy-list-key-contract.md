# Interface Contract: Virtualized List Item Keys

**Contract ID**: `CTR-LAZY-KEY-003`  
**Applies to**: All `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` layouts in `ui-shared`

---

## 1. Specification

All calls to `items` and `itemsIndexed` MUST provide a non-null, unique, and deterministic `key` parameter.

### Required Key Selection Rules

1. **Entity-Backed Lists**: If domain objects contain a stable unique identifier (`id`, `uuid`, `key`), the list MUST use it directly:
   ```kotlin
   items(items = collections, key = { it.id }) { node -> ... }
   ```
2. **Log Streams & Chronological Feeds**: For real-time logs where messages might repeat, combine timestamp, hash, and index:
   ```kotlin
   itemsIndexed(items = logs, key = { index, log -> "${log.timestamp}_${log.message.hashCode()}_$index" }) { ... }
   ```
3. **Key-Value & Header Tables**: For HTTP headers and cookies where duplicate header names may exist, combine key, value, and index:
   ```kotlin
   itemsIndexed(items = response.headers, key = { index, header -> "${header.name}:${header.value}:$index" }) { ... }
   ```
4. **Editable Tables**: For mutable rows (e.g. environment variables), maintain stable row IDs on the working copy models rather than using raw indices.

## 2. Inventory of Remediated Lists

| Component | List Target | Key Formula |
|---|---|---|
| `BottomPanel.kt:278` | Console log stream | `"${it.timestamp}_${it.message.hashCode()}_$index"` |
| `BottomPanel.kt:337` | Test results | `"${it.id ?: it.name}_$index"` |
| `EnvironmentEditDialog.kt:919` | Working secrets | `"${secret.id ?: secret.key}_$sIdx"` |
| `McpPanel.kt:949` | MCP server logs | `"mcp_log_${logs[i].timestamp}_$i"` |
| `ResponseViewer.kt:341` | Response headers | `"${header.name}_${header.value}_$index"` |
| `ResponseViewer.kt:369` | Response cookies | `"${cookie.name}_${cookie.domain}_$index"` |
| `VariableHighlight.kt:658` | Variable suggestions | `"${name}_$index"` |

## 3. Compliance Verification
- No `LazyListScope.items` or `itemsIndexed` without `key` parameter across `ui-shared`.
- Filtering or updating list items does not trigger item state corruption or un-mount of preserved item state.
