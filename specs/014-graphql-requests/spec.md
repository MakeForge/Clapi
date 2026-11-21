# Feature Specification: GraphQL Requests

**Feature Branch**: `014-graphql-requests`

**Created**: 2026-11-10

**Status**: Draft

**Input**: User description: "Support creating/editing/executing GraphQL requests - Check for existing implementation, I think maybe graphql request are already implemented but simply only if a http request has POST and special options selected. GraphQL requests should now be a first-level request type like HTTP or gRPC requests in the OpenCollection schema - Use OpenCollection GraphQL request types - Check OpenCollection GraphQL fields user could edit in new GraphQLRequestEditor - Ensure import/export works fine - Ensure qa-tests covers this - For GraphQL request Badge in UI, define new color in ClapiColors, use #e10098"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Identify GraphQL Requests in Collection Sidebar (Priority: P1)

Users working with modern GraphQL APIs need to create and organize GraphQL requests as first-class items within their collections and folders, alongside HTTP, gRPC, and WebSocket requests. When a user clicks the "+" action button on a collection root or folder in the sidebar, the menu provides a "New GraphQL Request" action. Selecting this option creates a new first-class GraphQL request in the target folder or collection. In the collection tree and editor tab bar, the item displays a dedicated `GraphQLBadge` showing "GQL" styled with the brand GraphQL color (`#e10098`). Selecting the request opens it in a dedicated GraphQL request editor tab.

**Why this priority**: First-class discovery and creation in the sidebar tree is the primary user entry point for managing GraphQL operations.

**Independent Test**: Click "+" on any collection or folder in the sidebar, choose "New GraphQL Request", verify that a new item appears in the tree with a "GQL" badge styled in `#e10098`, and verify that clicking the item opens a dedicated GraphQL editor tab displaying the "GQL" badge.

**Acceptance Scenarios**:

1. **Given** a collection or folder in the sidebar with an add action button ("+"), **When** the user opens the add menu, **Then** the options include "New HTTP Request", "New gRPC Request", "New WebSocket Request", and "New GraphQL Request".
2. **Given** the add menu is open, **When** the user selects "New GraphQL Request", **Then** a new first-class GraphQL request item is created with default name "New Request" and added to the selected folder or collection.
3. **Given** a GraphQL request in the sidebar tree, **When** rendered in the collection hierarchy, **Then** it displays the request name and a `GraphQLBadge` showing "GQL" with `#e10098` theme styling.
4. **Given** an active or inactive tab for a GraphQL request, **When** displayed in the editor tab bar, **Then** the tab header displays the request title and the `GraphQLBadge`.

---

### User Story 2 - Configure Endpoint, Method, Headers, Query Parameters, Auth, and Settings (Priority: P1)

Users need to target a GraphQL server endpoint, configure HTTP request headers and URL parameters, manage authentication tokens, and configure request execution settings. In the dedicated GraphQL editor, the user enters an endpoint address (e.g. `https://api.example.com/graphql`) in an address bar supporting dynamic variable resolution (e.g. `{{baseUrl}}/graphql`). Users can:
- Select the HTTP method (defaults to `POST`, with support for `GET` when queries are passed via query parameters).
- Configure HTTP headers (name, value, description, enable/disable toggle).
- Configure query parameters (name, value, description, enable/disable toggle).
- Select and configure authentication (Inherit from parent folder/collection, None, Bearer token, Basic Auth, API Key, OAuth2).
- Configure execution settings: encode URL, timeout in milliseconds, follow redirects, forward authorization headers on redirect, and max redirects (each supporting explicit values or "inherit").
- View and edit Markdown documentation in a dedicated "Docs" sub-tab.

**Why this priority**: Targeting endpoints with appropriate headers, query parameters, auth credentials, and timeouts is necessary for all GraphQL interactions.

**Independent Test**: Open a GraphQL request, enter an endpoint URL with variables (e.g. `{{apiBase}}/graphql`), add a custom header, configure Bearer token authentication, update timeout settings, save the request, and verify all values persist in the request state.

**Acceptance Scenarios**:

1. **Given** an active GraphQL request editor, **When** viewing the address bar, **Then** a URL input field is provided supporting variable resolution (`{{variable}}`) and an HTTP method selector defaulting to `POST`.
2. **Given** the "Headers" tab in the GraphQL editor, **When** the user manages headers, **Then** key-value entries with descriptions can be added, edited, toggled on/off, or deleted.
3. **Given** the "Params" tab in the GraphQL editor, **When** the user manages URL query parameters, **Then** key-value entries with descriptions can be added, edited, toggled on/off, or deleted, and dynamically update the address bar URL.
4. **Given** the "Auth" tab in the GraphQL editor, **When** configuring authentication, **Then** the user can select between Inherit, None, Bearer, Basic, API Key, and OAuth2 authentication modes.
5. **Given** the "Settings" tab in the GraphQL editor, **When** configuring execution options, **Then** fields for URL encoding, timeout, follow redirects, forward authorization header, and max redirects are editable and support inheritance.
6. **Given** the "Docs" tab in the GraphQL editor, **When** writing documentation, **Then** the content is saved as Markdown documentation for the request.

---

### User Story 3 - Author GraphQL Queries, Variables, and Manage Payload Variants (Priority: P1)

GraphQL requests rely on structured operations (queries, mutations, subscriptions) and optional JSON variables. The editor provides a specialized "Body" area containing:
- A query editor with GraphQL syntax highlighting, bracket/brace folding, and query formatting.
- A variables editor with JSON syntax highlighting, formatting, and dynamic variable interpolation (`{{variable}}`).
- Support for GraphQL body variants (multiple saved query/variable combinations with titles and active selection state), allowing users to switch between operations without losing work.

**Why this priority**: Formulating queries and variables with syntax assistance is the primary authoring activity for GraphQL APIs.

**Independent Test**: Compose a GraphQL query with variables in the editor, create a second query/variables variant with a different title, switch between variants, verify that formatting and syntax highlighting function, and verify the selected variant is preserved.

**Acceptance Scenarios**:

1. **Given** the "Body" section of the GraphQL request editor, **When** viewing the query editor, **Then** users can input GraphQL operations with syntax highlighting, line numbers, and brace-based folding.
2. **Given** the variables panel in the GraphQL request editor, **When** editing variables, **Then** users can input JSON-formatted variables with validation and syntax highlighting.
3. **Given** dynamic variables in query or variables text (e.g. `{"id": "{{userId}}"}`), **When** evaluated, **Then** workspace and environment variables are resolved before execution.
4. **Given** multiple body variants configured for a GraphQL request, **When** adding, naming, or switching variants, **Then** each variant retains its independent query and variables payload, and one variant is marked active/selected.

---

### User Story 4 - Execute GraphQL Operations and Inspect Structured Responses (Priority: P1)

Users need to execute GraphQL requests against live servers and inspect the results. Clicking "Send" dispatches the GraphQL request over HTTP (using the standard GraphQL-over-HTTP POST envelope `{"query": "...", "variables": ...}` or GET query parameters). The response pane displays:
- HTTP status code and status text.
- Response time (latency in ms) and payload size.
- Formatted JSON response body with syntax highlighting, search/filtering, line numbering, and copy actions.
- Response headers and cookie details.
- Error indicators if GraphQL returns top-level `errors` or network failures occur.
- Execution of pre-request and post-request/test scripts and assertions defined in the request runtime.

**Why this priority**: Executing operations and verifying responses completes the core API testing workflow.

**Independent Test**: Target the test server `/graphql` endpoint, author a query, click "Send", and verify that the response status 200, execution timing, and formatted JSON data payload are displayed in the response viewer.

**Acceptance Scenarios**:

1. **Given** a configured GraphQL request, **When** the user clicks "Send", **Then** the request is sent with the appropriate Content-Type header (`application/json`) and serialized JSON envelope (`{"query": ..., "variables": ...}`).
2. **Given** a successful GraphQL response, **When** received, **Then** the response pane displays the HTTP status, response time, headers, and formatted JSON body with syntax highlighting.
3. **Given** a response containing a GraphQL `errors` array or a network failure, **When** received, **Then** the response is displayed with clear error indicators while preserving response inspection capabilities.
4. **Given** pre-request or post-request scripts configured in the "Scripts" tab, **When** the request executes, **Then** scripts and assertions execute in accordance with the standard runtime lifecycle.

---

### User Story 5 - OpenCollection v1.0.0 Round-Trip Persistence and Legacy Compatibility (Priority: P2)

Collections containing first-class GraphQL requests must be persisted to disk, exported, and imported conforming strictly to the OpenCollection v1.0.0 schema (`GraphQLRequest`, `GraphQLRequestInfo`, `GraphQLRequestDetails`, `GraphQLBody`, `GraphQLBodyVariant`, `GraphQLRequestRuntime`, `GraphQLRequestSettings`). Furthermore:
- Saving and loading collections preserves all GraphQL-specific fields without loss or schema violations.
- Existing collections containing HTTP requests with `BodyType.GRAPHQL` are cleanly supported and can be converted or opened without data loss or corruption.

**Why this priority**: Ensures reliable data persistence, team collaboration via Git/file sync, and standards compliance with the OpenCollection ecosystem.

**Independent Test**: Create a collection containing a GraphQL request with custom headers, body variants, auth, runtime scripts, and settings, export it to OpenCollection JSON and YAML files, re-import into a clean session, and assert that 100% of properties match the original request.

**Acceptance Scenarios**:

1. **Given** a collection containing GraphQL requests, **When** saved or exported to OpenCollection JSON or YAML, **Then** each item is serialized as a `GraphQLRequest` with `info.type: "graphql"` matching the OpenCollection v1.0.0 schema.
2. **Given** an OpenCollection file containing `type: "graphql"` items, **When** imported or loaded into Clapi, **Then** polymorphic deserialization parses them as first-class GraphQL request items with all properties preserved.
3. **Given** a legacy collection with an HTTP request having body type `GRAPHQL`, **When** loaded in Clapi, **Then** it is loaded seamlessly and can be executed or migrated to a first-class GraphQL request.

---

### Edge Cases

- What happens if the query string is empty when the user clicks "Send"?
  - The application displays an unobtrusive warning stating that a query is required before sending.
- What happens if the variables JSON contains syntax errors?
  - The editor highlights the JSON syntax error inline and alerts the user when attempting to execute.
- What happens if a GraphQL request uses the `GET` method instead of `POST`?
  - The request encodes `query` and `variables` as URL query parameters in accordance with standard GraphQL-over-HTTP specifications.
- What happens when a user switches between multiple body variants?
  - Changes in the active variant are saved before switching, and the newly selected variant's query and variables are loaded into the editor without losing uncommitted edits in other variants.
- What happens when importing an OpenCollection file where `graphql.body` is an object vs an array of variants?
  - Polymorphic decoding seamlessly accepts either a single `GraphQLBody` or a list of `GraphQLBodyVariant` objects, normalizing them for editing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support GraphQL requests as a first-class `OpenCollectionItem` type (`GraphQLRequestItem`) conforming to OpenCollection v1.0.0 specification with `info.type = "graphql"`.
- **FR-002**: The system MUST implement OpenCollection GraphQL schema structures including `GraphQLRequestInfo`, `GraphQLRequestDetails`, `GraphQLBody`, `GraphQLBodyVariant`, `GraphQLRequestRuntime`, and `GraphQLRequestSettings`.
- **FR-003**: The collection sidebar context menu on folders and collection roots MUST include an action to create a "New GraphQL Request" alongside HTTP, gRPC, and WebSocket request creation.
- **FR-004**: In the sidebar tree and editor tab bar, GraphQL requests MUST display a dedicated `GraphQLBadge` displaying the text "GQL".
- **FR-005**: The system MUST define a new theme color in `ClapiColors` (e.g. `MethodGraphQl` / `MethodGraphQL`) using `#e10098` for GraphQL requests across dark and light themes, used for `GraphQLBadge` text, background tints, and active accents.
- **FR-006**: The system MUST provide a dedicated `GraphQLRequestEditor` component for viewing and editing first-class GraphQL requests.
- **FR-007**: The GraphQL request editor MUST provide an address bar supporting URL entry with dynamic variable resolution (`{{variable}}`) and an HTTP method selector (supporting `POST` as default and `GET`).
- **FR-008**: The GraphQL request editor MUST provide a "Body" tab with dedicated editors for the GraphQL query (with GraphQL syntax highlighting, line numbers, brace folding, and query formatting) and JSON variables (with JSON syntax highlighting and formatting).
- **FR-009**: The GraphQL request editor MUST support multiple GraphQL body variants, allowing users to create, title, select, and switch between named query/variable configurations.
- **FR-010**: The GraphQL request editor MUST provide a "Headers" sub-tab allowing users to configure custom HTTP headers (name, value, description, enabled/disabled state).
- **FR-011**: The GraphQL request editor MUST provide a "Params" sub-tab allowing users to configure URL query parameters (name, value, description, enabled/disabled state).
- **FR-012**: The GraphQL request editor MUST provide an "Auth" sub-tab supporting authentication modes (Inherit, None, Bearer, Basic, API Key, OAuth2).
- **FR-013**: The GraphQL request editor MUST provide a "Scripts" sub-tab for configuring pre-request and post-request/test scripts and assertions.
- **FR-014**: The GraphQL request editor MUST provide a "Settings" sub-tab allowing users to configure execution settings (`encodeUrl`, `timeout`, `followRedirects`, `forwardAuthorizationHeader`, `maxRedirects`) supporting explicit values or "inherit".
- **FR-015**: The GraphQL request editor MUST provide a "Docs" sub-tab for reading and editing Markdown documentation.
- **FR-016**: The GraphQL execution engine MUST serialize the active query and resolved variables into a standard GraphQL-over-HTTP payload, dispatch the HTTP request, and deliver the response into the standard response viewer.
- **FR-017**: The system MUST support full lossless round-trip serialization and deserialization of GraphQL requests in OpenCollection JSON and YAML formats conforming to the `opencollection_v1.0.0.json` schema.
- **FR-018**: The system MUST preserve backward compatibility with existing HTTP requests having `BodyType.GRAPHQL` while enabling smooth workflow transition to first-class GraphQL requests.
- **FR-019**: The test suite in `qa-tests` MUST include automated end-to-end integration tests validating GraphQL request creation, OpenCollection schema compliance, round-trip serialization, and live execution against the dummy test server.

### Key Entities

- **GraphQLRequestItem**: Represents a complete GraphQL request item in an OpenCollection document, containing `info: GraphQLRequestInfo`, `graphql: GraphQLRequestDetails`, optional `runtime: GraphQLRequestRuntime`, optional `settings: GraphQLRequestSettings`, and optional `docs: String`.
- **GraphQLRequestDetails**: Encapsulates protocol-specific configuration: `method: String` (defaults to "POST"), `url: String`, `headers: List<HttpRequestHeader>`, `params: List<HttpRequestParam>`, `body: GraphQLBodyPayload` (representing either a single `GraphQLBody` or list of `GraphQLBodyVariant`), and optional `auth: AuthConfig`.
- **GraphQLBody**: Represents the GraphQL operation payload containing `query: String` and optional `variables: String` (JSON string).
- **GraphQLBodyVariant**: Represents a named variation of a GraphQL body with `title: String`, `selected: Boolean`, and `body: GraphQLBody`.
- **GraphQLRequestSettings**: Encapsulates execution parameters: `encodeUrl: BooleanSetting`, `timeout: TimeoutSetting`, `followRedirects: BooleanSetting`, `forwardAuthorizationHeader: BooleanSetting`, and `maxRedirects: NumberSetting`.
- **GraphQLRequestRuntime**: Encapsulates runtime metadata including variables, scripts (pre-request and test), assertions, and actions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new GraphQL request, enter a query, and execute it against an endpoint in under 15 seconds.
- **SC-002**: 100% of GraphQL requests exported to OpenCollection JSON or YAML strictly validate against the official OpenCollection v1.0.0 JSON schema.
- **SC-003**: Round-trip import and export of collections with GraphQL requests preserves 100% of configured fields (URL, method, headers, params, body query/variables/variants, auth, scripts, settings, and documentation).
- **SC-004**: GraphQL query formatting and syntax highlighting render without visible latency (< 16ms frame render time) on typical queries under 1,000 lines.
- **SC-005**: All automated GraphQL QA integration and round-trip tests pass consistently across supported operating systems.

## Assumptions

- GraphQL requests are transmitted over HTTP/HTTPS using standard GraphQL-over-HTTP transport specifications (POST with JSON body `{"query": "...", "variables": ...}` by default; GET with URL query parameters when GET is chosen).
- Response visualization uses the existing high-performance JSON response viewer, which already supports syntax highlighting, line numbers, search, and folding.
- Body variants use the OpenCollection `GraphQLBodyVariant` schema, allowing users to save and toggle between alternative queries or test variable configurations within the same request.
- The default HTTP method for newly created GraphQL requests is POST.
- The GraphQL badge color `#e10098` applies uniformly across both dark and light themes, with appropriate opacity/tint adjustments for background badges.
