# Research: GraphQL Requests

**Feature**: `014-graphql-requests`  
**Date**: 2026-11-10  
**Status**: Completed  

## 1. Existing GraphQL Implementation in Clapi

### Context & Discovery
We conducted an audit of the current codebase to understand how GraphQL is presently treated:
1. **HTTP Body Type**: GraphQL is currently supported only as a sub-type of HTTP request body: `BodyType.GRAPHQL` in `RequestModels.kt`.
2. **Execution & Enveloping**: `core-network` contains `encodeGraphQlEnvelope(body: GraphQlBody, ...)` which encodes queries and variables into `{"query": "...", "operationName": "...", "variables": ...}` and converts JSON5 variable comments to strict JSON.
3. **WorkspaceMode.GRAPHQL**: `GraphQLPanel.kt` exists as an experimental/scratch tool in `WorkspaceMode`, but it is detached from collections, cannot be saved in OpenCollection documents, does not support multiple requests, and lacks full request lifecycle features (auth inheritance, environments, scripts, settings, export/import).
4. **OpenCollection Schema**: The canonical OpenCollection schema (`opencollection_v1.0.0.json`) already specifies `GraphQLRequest`, `GraphQLRequestInfo`, `GraphQLRequestDetails`, `GraphQLBody`, `GraphQLBodyVariant`, `GraphQLRequestRuntime`, and `GraphQLRequestSettings`. However, `core-model` lacked the Kotlin data classes and polymorphic deserializer wiring for `GraphQLRequestItem`.

### Decisions
- **Decision**: Elevate GraphQL to a first-class citizen alongside `HTTP`, `gRPC`, and `WEBSOCKET`.
- **Rationale**: Elevating GraphQL to `RequestKind.GRAPHQL` and `ItemType.GRAPHQL` aligns Clapi directly with the OpenCollection v1.0.0 specification, allows saving and syncing GraphQL requests in collections, provides a dedicated `GraphQLRequestEditor`, and retains backward compatibility with legacy HTTP requests having `BodyType.GRAPHQL`.

---

## 2. OpenCollection Schema Compliance & Modeling

### Schema Analysis
OpenCollection v1.0.0 defines `GraphQLRequest` with `additionalProperties: false`:
```json
{
  "info": { "name": "...", "type": "graphql", "description": "...", "seq": ..., "tags": [...] },
  "graphql": {
    "method": "POST",
    "url": "https://api.example.com/graphql",
    "headers": [...],
    "params": [...],
    "body": { "query": "...", "variables": "..." } | [ { "title": "...", "selected": true, "body": { ... } } ],
    "auth": { ... }
  },
  "runtime": { "variables": [...], "scripts": [...], "assertions": [...], "actions": [...] },
  "settings": {
    "encodeUrl": true | false | "inherit",
    "timeout": 5000 | "inherit",
    "followRedirects": true | false | "inherit",
    "forwardAuthorizationHeader": true | false | "inherit",
    "maxRedirects": 5 | "inherit"
  },
  "docs": "Markdown..."
}
```

### Decisions
- **Decision**: Mirror the exact polymorphic pattern used by `WebSocketRequestItem` and `WebSocketMessagePayload`:
  - Define `GraphQLBody(val query: String = "", val variables: String? = null)`.
  - Define `GraphQLBodyVariant(val title: String, val selected: Boolean? = null, val body: GraphQLBody)`.
  - Define sealed interface `GraphQLBodyPayload` with `Single(val body: GraphQLBody)` and `Variants(val variants: List<GraphQLBodyVariant>)` with a custom polymorphic serializer `GraphQLBodyPayloadSerializer`.
  - Add `GraphQLRequestInfo`, `GraphQLRequestDetails`, `GraphQLRequestSettings`, `GraphQLRequestRuntime`, and `GraphQLRequestItem` implementing `OpenCollectionItem`.
  - In `OpenCollectionItemSerializer`, select `GraphQLRequestItem.serializer()` when `element.jsonObject.containsKey("graphql")` or `type == ItemType.GRAPHQL`.
- **Rationale**: This guarantees lossless serialization and deserialization in JSON and YAML, ensures compatibility with OpenCollection tools, and supports single-query requests and multi-variant requests cleanly.

---

## 3. UI, Branding & Badge Design

### Color & Badge Specification
- **Requirement**: Use `#e10098` for the GraphQL badge in `ClapiColors`.
- **Decision**:
  - In `AppColorPalette.kt`: Add `val methodGraphQl: Color = Color(0xFFE10098)`.
  - In `ClapiTheme.kt`: Expose `val MethodGraphQl: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.methodGraphQl`.
  - Define `GraphQLBadge(compact: Boolean = false, modifier: Modifier = Modifier)` in `Sidebar.kt`:
    - Text: `"GQL"`
    - Color: `ClapiColors.MethodGraphQl`
    - Background: `ClapiColors.MethodGraphQl.copy(alpha = 0.12f)`
    - Corner radius: `4.dp`
  - In the sidebar tree and tab bar (`RequestTabsBar.kt`), display `GraphQLBadge` for items with `kind == RequestKind.GRAPHQL`.
  - In collection and folder "+" menus, add "New GraphQL Request" with localized string `Strings.t("new_graphql_request")`.

---

## 4. GraphQL Request Editor UX & Layout

### Editor Architecture
- **Decision**: Create `GraphQLRequestEditor` following the ergonomics of `HttpRequestEditor` and `WebSocketRequestEditor`:
  - **Top Address Bar**:
    - Method selector: Dropdown with `POST` (default) and `GET`.
    - URL input field supporting dynamic variable syntax (`{{baseUrl}}/graphql`) and variable auto-completion.
    - Action buttons: "Send" (Ctrl+Enter / Cmd+Enter), "Save" (Ctrl+S / Cmd+S), and "Cancel" when in-flight.
  - **Main Area Tabs**:
    1. **Body**:
       - Variant bar: List/chips of saved variants with title, active selection radio/checkbox, and "+ Variant" action.
       - Query pane: GraphQL syntax-highlighted editor with line numbers, bracket/brace folding, and "Prettify" format action.
       - Variables pane: JSON editor with line numbers, formatting, and validation.
    2. **Headers**: Key-value table for custom headers (Authorization, X-Custom-Header, etc.).
    3. **Params**: Key-value table for query parameters (useful especially for GET requests or endpoint query flags).
    4. **Auth**: Inherit, None, Bearer, Basic, API Key, and OAuth2.
    5. **Scripts**: Pre-request and Post-request/Tests scripts.
    6. **Settings**: URL encoding, Timeout, Follow redirects, Forward auth header, Max redirects.
    7. **Docs**: Markdown documentation viewer and editor.
  - **Response Pane**:
    - Uses the proven `ResponseViewer`: status badge (200 OK, latency ms, size KB), response headers, and formatted JSON body with syntax highlighting, search/filter, and copy actions.

---

## 5. Network Execution & GraphQL Transport

### Execution Mechanics
- **Decision**: Reuse `KtorApiClient` and `encodeGraphQlEnvelope`:
  - When `method == "POST"`:
    - Encode query and variables using `encodeGraphQlEnvelope`.
    - Inject `Content-Type: application/json` (if not explicitly overridden by user).
    - Send as HTTP POST with body.
  - When `method == "GET"`:
    - Encode `query` as URL parameter `?query=...`.
    - If variables are provided, encode as `?variables=...`.
  - Resolve dynamic environment and collection variables in URL, headers, query parameters, query text, and variables JSON before execution.
  - Run pre-request and test scripts in `ClapiScriptEngine`.
- **Rationale**: GraphQL over HTTP standardizes on POST JSON envelopes and GET query parameters. This leverages existing rock-solid network client infrastructure without needing a separate GraphQL network library.

---

## 6. Migration and Backward Compatibility

### Handling Existing HTTP Requests with GraphQL Body
- **Decision**:
  - Keep `BodyType.GRAPHQL` in HTTP requests functional so existing saved HTTP requests continue to work without disruption.
  - Provide automatic recognition or a migration action if a user wishes to convert an HTTP request with GraphQL body to a first-class GraphQL request.
  - Ensure OpenCollection serialization recognizes first-class GraphQL requests and serializes them as `GraphQLRequest` with `info.type = "graphql"`.

---

## 7. QA & Testing Strategy

### Test Plan
1. **Model & Serialization Unit Tests**:
   - Verify `GraphQLRequestItem` round-trip in JSON and YAML adhering to `opencollection_v1.0.0.json`.
   - Verify polymorphic deserialization for both single `GraphQLBody` and array of `GraphQLBodyVariant`.
2. **Persistence & Mapping Unit Tests**:
   - Verify `OpenCollectionModelMapping` converts between `CollectionNode`/`RequestTabState` and `GraphQLRequestItem` losslessly.
3. **Execution Integration Tests**:
   - Verify executing queries and mutations against `DummyApiServer`'s `/graphql` endpoint with variables, headers, and auth.
4. **E2E Collection Tests**:
   - Add a GraphQL request to `OpenCollectionRoundTripE2ETest` to verify complete workspace save, export, and reload without loss.
