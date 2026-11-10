# Contract: GraphQL UI & Editor Components

**Feature**: `014-graphql-requests`  
**Date**: 2026-11-10  

## 1. `GraphQLBadge` Composable

### Purpose
Renders a compact, styled badge displaying `"GQL"` for GraphQL request items in the sidebar collection tree, request editor tabs, and editor top bar.

### Signature
```kotlin
@Composable
fun GraphQLBadge(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
)
```

### Visual Specifications
- **Badge Text**: `"GQL"`
- **Typography**: Matches `MethodBadge` and `WebSocketBadge` (`FontWeight.Bold`, 10sp if compact else 12sp).
- **Text Color**: `ClapiColors.MethodGraphQl` (`#e10098`).
- **Background Color**: `ClapiColors.MethodGraphQl.copy(alpha = 0.12f)`.
- **Corner Radius**: `4.dp`.
- **Padding**: Horizontal `4.dp` (compact) or `6.dp` (normal), vertical `2.dp`.

---

## 2. Sidebar Creation Contract

### Purpose
Allows users to create a new first-class GraphQL request from folder or collection "+" menus.

### Menu Items
- Folder context menu: Dropdown item with icon and label `"New GraphQL Request"` (`Strings.t("new_graphql_request")`).
- Collection root context menu: Dropdown item with icon and label `"New GraphQL Request"` (`Strings.t("new_graphql_request")`).
- Action creates a `CollectionNode` with:
  ```kotlin
  CollectionNode(
      id = generateUuid(),
      name = "New Request",
      isFolder = false,
      url = "https://",
      method = HttpMethodType.POST,
      kind = RequestKind.GRAPHQL,
      graphQlMethod = "POST",
      graphQlQuery = "query {\n  \n}",
      graphQlVariables = "{\n  \n}",
  )
  ```

---

## 3. `GraphQLRequestEditor` Composable

### Purpose
Dedicated editor rendered in `RequestEditor.kt` when `tab.kind == RequestKind.GRAPHQL`.

### Signature
```kotlin
@Composable
fun GraphQLRequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSend: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

### Layout Specifications
1. **Top Address Bar**:
   - `GraphQLBadge` ("GQL").
   - Method selector dropdown: `POST` (default) or `GET`.
   - URL text field with variable highlighting/completion.
   - Primary action: "Send" button (Ctrl+Enter / Cmd+Enter) / "Cancel" button when running.
   - Secondary action: "Save" button (Ctrl+S / Cmd+S).
2. **Editor Sub-Tabs**:
   - **Body**:
     - Variant Management Bar: Tab strip or chips displaying variant titles (`"Default"`, `"Variant 2"`, etc.) with active selection indicator, rename, delete, and "+ Variant" action.
     - Operation Query Editor: Left or top pane with line numbers, GraphQL keyword/string/variable syntax highlighting (`highlightGraphql`), brace folding, and a "Prettify" format button.
     - Variables Editor: Right or bottom pane with line numbers, JSON syntax highlighting (`highlightJson`), formatting, and validation.
   - **Headers**: Key-value table for custom HTTP headers with enable/disable checkbox.
   - **Params**: Key-value table for URL query parameters with enable/disable checkbox.
   - **Auth**: Reusable `AuthEditor` with Inherit, None, Bearer, Basic, API Key, and OAuth2 modes.
   - **Scripts**: Script editor for pre-request and post-request/test scripts with access to `clapi` runtime object.
   - **Settings**: Execution settings: URL encoding, timeout (ms), follow redirects, forward auth header, max redirects.
   - **Docs**: Markdown documentation viewer and editor.
3. **Response Viewer**:
   - Status badge (e.g. `200 OK`, response time in ms, response size in KB).
   - Tab strip: Body (JSON syntax highlighting with search/filter/copy), Headers, Cookies.
