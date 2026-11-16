# Interface Contract: Desktop Pointer Affordance

**Contract ID**: `CTR-POINTER-HOVER-002`  
**Applies to**: All clickable components, custom buttons, table rows, badges, tabs, and chips in `ui-shared`

---

## 1. Specification

On desktop operating systems, all interactive clickable surfaces that do not inherently set cursor shape (such as custom `Row`, `Box`, `Surface`, or `Text` using `Modifier.clickable`) MUST explicitly declare `Modifier.pointerHoverIcon(PointerIcon.Hand)`.

### Implementation Pattern

```kotlin
// Direct Modifier Chaining
Modifier
    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
    .clickable(enabled = enabled, onClick = onClick)
```

## 2. Component Target Matrix

| Component | Target Element | Cursor Behavior |
|---|---|---|
| `AuthEditor.kt` | Copyable token fields, view buttons | `PointerIcon.Hand` |
| `BodyEditor.kt` | `FormDataRowItem`, `TypeDropdown`, `FilePickerCell`, `AddRowButton` | `PointerIcon.Hand` |
| `BottomPanel.kt` | `ConsoleRow`, Test result rows, action icons | `PointerIcon.Hand` |
| `CollectionEditor.kt` | `ProtoFileRow`, `ProtoImportPathRow`, picker buttons | `PointerIcon.Hand` |
| `GlobalVariablesDialog.kt` | `GlobalVariableRow`, action buttons | `PointerIcon.Hand` |
| `KeyValueEditor.kt` | `KeyValueRow`, type dropdown, delete icon | `PointerIcon.Hand` |
| `MethodBadge.kt` | Clickable badge variants | `PointerIcon.Hand` |
| `RequestBar.kt` | `MethodDropdown`, `SendButton`, `SaveButton`, `CopyCurlButton` | `PointerIcon.Hand` |
| `ResponseViewer.kt` | `MetricChip`, `ResponseTabBar` tab buttons | `PointerIcon.Hand` |
| `SettingsDialog.kt` | `SettingToggle`, `SettingAction`, category tabs | `PointerIcon.Hand` |
| `TopToolbar.kt` | `EnvironmentChip`, action buttons | `PointerIcon.Hand` |
| `WebSocketRequestEditor.kt` | `FormatDropdown`, `TimelineMessageItem` | `PointerIcon.Hand` |

## 3. Compliance Verification
- Mouse hover over any clickable element switches cursor to pointing hand.
- Disabled elements display standard arrow pointer (`PointerIcon.Default`).
