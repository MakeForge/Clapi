# UI Contract: Composable Modifier Hygiene

**Type**: Composable API Contract  
**Applies to**: All public, internal, and reusable `@Composable` functions in `ui-shared`

---

## 1. Function Signature Standard

Every UI composable function that produces layout elements MUST adhere to the following signature contract:

```kotlin
@Composable
fun <ComposableName>(
    <mandatoryParam1>: <Type>,
    <mandatoryParam2>: <Type>,
    modifier: Modifier = Modifier,
    <optionalParam1>: <Type> = <default>,
    <optionalParam2>: <Type> = <default>,
    content: @Composable () -> Unit = {},
)
```

### Invariants:
1. **Mandatory First**: All required parameters (such as domain models, IDs, mandatory callbacks) appear before optional parameters.
2. **First Optional**: `modifier: Modifier = Modifier` MUST be the first parameter with a default value.
3. **No Sizing in Parameter**: Never specify `Modifier = Modifier.fillMaxWidth()` or any sizing in the parameter declaration.
4. **Root Element Binding**: The incoming `modifier` MUST be passed directly to the top-level layout element in the composable's body:
   ```kotlin
   @Composable
   fun MyPanel(
       tab: RequestTabState,
       modifier: Modifier = Modifier,
   ) {
       Column(
           modifier = modifier
               .fillMaxSize()
               .background(ClapiColors.Background)
       ) { ... }
   }
   ```

---

## 2. Modal Dialog Modifier Contract

For dialogs that render an overlay (such as `ConfirmDeleteDialog`, `EnvironmentEditDialog`, `SettingsDialog`):

```kotlin
@Composable
fun <DialogName>(
    state: AppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .widthIn(min = 400.dp, max = 800.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ClapiColors.SurfaceHigh)
                .padding(20.dp)
        ) { ... }
    }
}
```

- Callers can provide custom sizing or test tags (`Modifier.testTag("dialog_root")`).
- Default bounds (`widthIn`) are configured internally on the chained modifier.
