# Interface Contract: Dialog Escape Dismissal

**Contract ID**: `CTR-DIALOG-ESCAPE-001`  
**Applies to**: All modal dialogs in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/`

---

## 1. Specification

Every modal dialog composable MUST intercept keyboard events to handle the `Escape` key (`Key.Escape`).

### Modifier Implementation Pattern

```kotlin
@Composable
fun MyDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hasDirtyState: Boolean = false,
    onConfirmDiscard: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = {
        if (hasDirtyState && onConfirmDiscard != null) onConfirmDiscard() else onDismiss()
    }) {
        Box(
            modifier = modifier
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        if (hasDirtyState && onConfirmDiscard != null) {
                            onConfirmDiscard()
                        } else {
                            onDismiss()
                        }
                        true
                    } else {
                        false
                    }
                }
                .focusable() // Ensures container can receive key events
        ) {
            // Dialog Content
        }
    }
}
```

## 2. Dialog Inventory & Dismissal Policy

| Dialog | Clean Dismiss Action | Dirty Dismiss Action |
|---|---|---|
| `ConfirmDeleteDialog` | `onDismiss()` | N/A (single prompt) |
| `DirtyCloseDialog` | `onDismiss()` | `onDismiss()` |
| `DirtyMultiCloseDialog` | `onDismiss()` | `onDismiss()` |
| `EnvironmentEditDialog` | `onDismiss()` | Shows dirty confirmation dialog |
| `ErrorMessageDialog` | `onDismiss()` | N/A |
| `ExportCollectionsDialog` | `onDismiss()` | `onDismiss()` |
| `GitSyncDialog` | `onDismiss()` | If uncommitted inputs, prompts or closes |
| `GlobalVariablesDialog` | `onDismiss()` | If modified, prompts or closes |
| `HelpAboutDialog` | `onDismiss()` | `onDismiss()` |
| `ImportSpecificationDialog` | `onDismiss()` | `onDismiss()` |
| `OperationProgressDialog` | Ignored if blocking | Cancels operation if cancellable |
| `SelectProtoFileDialog` | `onDismiss()` | `onDismiss()` |
| `SettingsDialog` | `onDismiss()` | If unsaved settings, prompts or closes |
| `RenameVariantDialog` | `onDismiss()` | `onDismiss()` |

## 3. Compliance Verification
- Pressing `Escape` when dialog is focused MUST dismiss the dialog or trigger discard confirmation.
- Sub-popups or dropdowns inside the dialog MUST consume their own `Escape` event without bubbling to dismiss the entire dialog.
