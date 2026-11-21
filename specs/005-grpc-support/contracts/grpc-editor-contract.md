# Contract: gRPC UI Components & Interactions

## UI Contracts (`ui-shared`)

### 1. `GrpcBadge` Composable

Renders the visual indicator for gRPC requests across the application.

```kotlin
package io.github.crustack.clapi.ui.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders the "gRPC" badge with MethodPatch theme styling.
 *
 * @param compact When true, renders smaller text/padding for tree nodes and tab headers.
 * @param modifier Modifier applied to the outer layout.
 */
@Composable
fun GrpcBadge(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
)
```

**Styling Rules**:
- Text: `"gRPC"` (all caps or standard casing, bold)
- Color: `ClapiColors.MethodPatch`
- Background: `ClapiColors.MethodPatch.copy(alpha = 0.12f)`
- Corner radius: `4.dp`
- Font size: `10.sp` when compact, `12.sp` standard

---

### 2. `SelectProtoFileDialog` Composable

Modal dialog allowing users to view and update the request's `.proto` file path.

```kotlin
package io.github.crustack.clapi.ui.shared.components

import androidx.compose.runtime.Composable

@Composable
fun SelectProtoFileDialog(
    currentFilePath: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
)
```

**Interaction Flow**:
1. Dialog title: "Select Proto File"
2. Displays currently chosen file path or "No proto file selected"
3. Action button: "Select Proto File" -> Opens desktop file picker filtered to `.proto`
4. Action button: "Save" -> Invokes `onSave(selectedPath)` and dismisses dialog
5. Action button: "Cancel" or "Close (X)" -> Invokes `onDismiss()` without saving

---

### 3. Folder Creation Menu Contract

In `Sidebar.kt`:
- Clicking "+" on a folder opens a menu with:
  - "New HTTP Request" -> Creates HTTP request item in that folder
  - "New gRPC Request" -> Creates gRPC request item in that folder
- Test tags:
  - Add button: `collection-add-${node.id}`
  - Menu option HTTP: `folder-menu-new-http`
  - Menu option gRPC: `folder-menu-new-grpc`
