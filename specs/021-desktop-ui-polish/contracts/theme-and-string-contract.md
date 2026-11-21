# Interface Contract: Theme Colors and String Localization

**Contract ID**: `CTR-THEME-STRING-004`  
**Applies to**: All UI presentation code in `ui-shared`

---

## 1. Theming Specification

No `@Composable` function may declare hardcoded hex literals (`Color(0x...)`) or static colors (`Color.White`, `Color.Black`) for visual styling. All colors must resolve from `ClapiColors` (or `MaterialTheme.colorScheme`).

### Semantic Color Token Additions

In `ClapiTheme.kt`:
```kotlin
// Additional Semantic Tokens in ClapiColors
val StatusWarning: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusWarning
val StatusInfo: Color    @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusInfo
```

### Color Mapping Table

| Hardcoded Source | Required Semantic Replacement |
|---|---|
| `Color.White` (text/icons on dark surfaces) | `ClapiColors.OnPrimary` or `ClapiColors.OnSurface` |
| `Color(0xFF49CC90)` (GraphQL success green) | `ClapiColors.StatusSuccess` |
| `Color(0xFFE535AB)` (GraphQL magenta / type) | `ClapiColors.Primary` / `ClapiColors.Tertiary` |
| `Color(0xFFFFA500)` / `Color(0xFFFFA726)` (Warning orange) | `ClapiColors.StatusWarning` |
| `Color(0xFFD32F2F)` / `Color(0xFFC62828)` (Error red) | `ClapiColors.Error` / `ClapiColors.StatusServerError` |
| `Color(0xFF42A5F5)` / timing phase blues | `ClapiColors.Secondary` / `ClapiColors.PrimaryContainer` |
| `Color(0xFF4EC9B0)` / `Color(0xFFE5C07B)` (Realtime) | Semantic syntax & status tokens in `ClapiColors` |

---

## 2. Localization Specification

All user-visible text (labels, placeholders, dialog titles, button captions, and tooltips) MUST be resolved via `Strings`.

### Pattern

```kotlin
// Strings.kt accessor
val myLabel: String @Composable @ReadOnlyComposable get() = t("my_label")

// In UI Composable
Text(text = Strings.myLabel)
// or dynamic key:
Text(text = Strings.t("my_label"))
```

### Prohibited Patterns
- `Text("Save")` ❌
- `TextField(placeholder = { Text("Search...") })` ❌
- Hardcoded interpolated strings with English-only templates ❌

## 3. Compliance Verification
- Automated code search confirms 0 occurrences of `Color(0x` or `Color.White` in presentation composables.
- Automated code search confirms no unlocalized raw text strings in button labels or dialog headers.
