# Contract: Import Dialog Format Badges UI

**Feature**: Insomnia v5 Collection & Environment Import
**Path**: `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/ImportSpecificationDialog.kt`

---

## 1. UI Layout & Component Contract

Below the "Choose Files" button in the dropzone area of Step 1 (`SOURCE_SELECTION`), the dialog displays a row of badges representing the supported formats:

```kotlin
@Composable
fun SupportedFormatBadges(modifier: Modifier = Modifier)
```

### Visual Specifications:
- **Container**: `Row` with `horizontalArrangement = Arrangement.spacedBy(6.dp)` and `verticalAlignment = Alignment.CenterVertically`.
- **Badges**:
  1. `OpenAPI 3.0+`
  2. `OpenCollection`
  3. `Postman`
  4. `Insomnia`
- **Badge Styling**:
  - Background: `ClapiColors.SurfaceContainerHigh`
  - Border: `1.dp` solid `ClapiColors.Border.copy(alpha = 0.5f)`
  - Shape: `RoundedCornerShape(4.dp)`
  - Padding: `horizontal = 8.dp, vertical = 3.dp`
  - Text color: `ClapiColors.OnSurfaceDim`
  - Font size: `11.sp`
  - Font weight: `FontWeight.Medium`

### Test Tags:
- Container: `Modifier.testTag("import-supported-formats-badges")`
- OpenAPI badge: `Modifier.testTag("import-format-badge-openapi")`
- OpenCollection badge: `Modifier.testTag("import-format-badge-opencollection")`
- Postman badge: `Modifier.testTag("import-format-badge-postman")`
- Insomnia badge: `Modifier.testTag("import-format-badge-insomnia")`

---

## 2. Format Badge in Step 2 (`ITEM_SELECTION`)

In the dialog header next to the title when `currentStep == ImportDialogStep.ITEM_SELECTION`:
- If `scanResult.format == SpecFormat.OPENAPI_V3` $\rightarrow$ "OpenAPI 3.0"
- If `scanResult.format == SpecFormat.OPENCOLLECTION` $\rightarrow$ "OpenCollection"
- If `scanResult.format == SpecFormat.POSTMAN` $\rightarrow$ "Postman"
- If `scanResult.format == SpecFormat.INSOMNIA` $\rightarrow$ "Insomnia v5"
- Test tag: `Modifier.testTag("import-format-badge")`
