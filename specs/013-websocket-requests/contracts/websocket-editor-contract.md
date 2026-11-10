# Contract: WebSocket UI & Editor Components

**Feature**: `013-websocket-requests`
**Date**: 2026-11-10

## 1. `WebSocketBadge` Composable

### Purpose
Renders a compact, styled badge displaying `"WS"` for WebSocket items in the sidebar tree, editor tabs, and top request bar.

### Signature
```kotlin
@Composable
fun WebSocketBadge(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
)
```

### Visual Specifications
- **Text**: `"WS"`
- **Typography**: Same font weight and size as `MethodBadge` (SemiBold / Medium 10sp-11sp).
- **Text Color**: `ClapiColors.MethodWebSocket`
- **Background Color**: `ClapiColors.MethodWebSocket.copy(alpha = 0.12f)`
- **Border**: 1.dp border with `ClapiColors.MethodWebSocket.copy(alpha = 0.25f)` (or matching `MethodBadge` corner shape 4.dp).

---

## 2. Sidebar Folder/Collection Add Menu Contract

### Purpose
Allows users to instantiate a new first-class WebSocket request.

### Invocations
- Folder context menu: DropdownMenuItem with label `Strings.t("new_websocket_request")` ("New WebSocket Request")
- Root collection context menu: DropdownMenuItem with label `Strings.t("new_websocket_request")`
- Action creates a `CollectionNode` with:
  - `name = "New Request"`
  - `kind = RequestKind.WEBSOCKET`
  - `url = "ws://"`

---

## 3. `WebSocketRequestEditor` Composable

### Purpose
Top-level editor rendered in `RequestEditor.kt` when `tab.kind == RequestKind.WEBSOCKET`.

### Signature
```kotlin
@Composable
fun WebSocketRequestEditor(
    tab: RequestTabState,
    state: AppState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
)
```

### Layout Structure
- **Top Bar**:
  - `WebSocketBadge`
  - URL text field with variable interpolation preview (`ws://...`)
  - Status pill (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `ERROR`)
  - Primary button: "Connect" (when disconnected/error) or "Disconnect" (when connected/connecting)
  - Secondary action: "Save" (hotkey Ctrl+S / Cmd+S)
- **Split Pane (Left = Configuration, Right = Timeline)**:
  - **Left Pane Tabs**:
    1. **Message**:
       - Variant selector bar: Dropdown/Row of saved variants + "Add Variant" action
       - Message Format selector: `TEXT`, `JSON`, `XML`, `BINARY`
       - Code editor with syntax highlighting for the active payload
       - "Send" button (enabled if `connectionState == CONNECTED`)
    2. **Headers**: Handshake key-value editor (`tab.headers`)
    3. **Auth**: Standard `AuthEditor` (Inherit, Bearer, Basic, API Key)
    4. **Settings**: Timeout (ms or "inherit"), Keep-alive interval (ms or "inherit")
    5. **Scripts**: Pre-request handshake script editor
    6. **Variables**: Request-scoped variables grid
    7. **Docs**: Markdown documentation viewer/editor
  - **Right Pane (Message Stream & Timeline)**:
    - Action bar: "Clear", message count summary, search/filter text input
    - Scrollable chronological timeline:
      - Each entry displays: Direction chip (`SENT` in Primary tint, `RECEIVED` in Secondary/Success tint), timestamp, payload size, preview.
      - Expandable full payload viewer with copy-to-clipboard button.
