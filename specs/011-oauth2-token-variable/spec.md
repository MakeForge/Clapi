# Feature Specification: Dynamic OAuth2 Access Token Variable

**Feature Branch**: `011-oauth2-token-variable`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "When OAuth2 is enabled, also allow in any request (http/grpc) to add special variable (similar to external secret variables) `{{_auth.oauth2.accessToken}}`. If this is used in a request, when executing the request it will be resolved with a valid access token; Ensure there is a test for a grpc request and set e.g. 'authorization' header with the new special variable and check its correctly resolved when sending the request; Design the api in a way that it will be easy to add further special variables with dynamic runtime values later for usage in requests"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Resolve OAuth2 Access Token Dynamic Variable in HTTP and gRPC Requests (Priority: P1)

API developers frequently need to inject OAuth2 access tokens into custom locations beyond the standard Authorization header (for example, in custom headers like `X-Access-Token`, in query parameters, within request payloads, or in gRPC metadata). When OAuth2 authentication is enabled for a request (either configured directly on the request or inherited from its parent folder or collection), the user can insert the special variable `{{_auth.oauth2.accessToken}}` anywhere variables are supported.

When the request is executed, the runtime variable resolution system detects `{{_auth.oauth2.accessToken}}`, verifies the OAuth2 configuration, ensures a valid access token is obtained (reusing an active token or automatically refreshing/fetching a new one if expired), and substitutes the variable with the valid access token before transmitting the request over the wire.

**Why this priority**: Core value of the feature. It allows flexible token placement in any request protocol (HTTP and gRPC) without forcing rigid authentication header conventions.

**Independent Test**: Can be tested by configuring OAuth2 authentication on a request or parent collection, adding `{{_auth.oauth2.accessToken}}` to a header or parameter, executing the request, and confirming that the sent request contains the resolved token string.

**Acceptance Scenarios**:

1. **Given** a request with OAuth2 authentication configured and an active valid access token, **When** the user executes a request containing `{{_auth.oauth2.accessToken}}` in a header or query parameter, **Then** the variable is resolved and replaced with the active access token value before the request is sent.
2. **Given** a request with OAuth2 authentication configured whose token is expired or not yet fetched, **When** the user executes the request referencing `{{_auth.oauth2.accessToken}}`, **Then** the system automatically retrieves a fresh access token from the token endpoint and replaces the variable with the newly acquired token.
3. **Given** a request that inherits OAuth2 authentication from a parent folder or collection, **When** the request references `{{_auth.oauth2.accessToken}}` and executes, **Then** the inherited OAuth2 settings are resolved, token validity is ensured, and the token is correctly substituted.
4. **Given** a request where OAuth2 authentication is NOT enabled or configured, **When** the request containing `{{_auth.oauth2.accessToken}}` is executed, **Then** the execution halts before network dispatch and displays an informative error explaining that OAuth2 authentication is not configured for the request.
5. **Given** a request where OAuth2 token retrieval fails (e.g., invalid client credentials or network failure), **When** executing a request referencing `{{_auth.oauth2.accessToken}}`, **Then** request execution is aborted with a clear error notification detailing the token acquisition failure.

---

### User Story 2 - Dynamic OAuth2 Token Resolution in gRPC Metadata Headers (Priority: P1)

Developers invoking gRPC services often require bearer token authentication passed via gRPC metadata headers (such as `authorization: Bearer {{_auth.oauth2.accessToken}}` or custom headers like `auth-token: {{_auth.oauth2.accessToken}}`). Prior to this feature, gRPC requests did not process OAuth2 token lifecycle management. With this capability, gRPC calls inspect declared metadata and message payloads for dynamic OAuth2 variables, trigger token verification or fetching against the configured OAuth2 provider, and inject the resolved token into outgoing gRPC metadata.

**Why this priority**: Explicit requirement to ensure gRPC requests have full first-class support for dynamic OAuth2 access token interpolation, validated with comprehensive tests.

**Independent Test**: Can be tested by defining a gRPC request with metadata header `authorization` set to `Bearer {{_auth.oauth2.accessToken}}`, configuring OAuth2 credentials, executing the gRPC call, and asserting that the metadata intercepted at the transport layer contains the valid Bearer token.

**Acceptance Scenarios**:

1. **Given** a gRPC request configured with OAuth2 authentication and metadata header `authorization: Bearer {{_auth.oauth2.accessToken}}`, **When** the gRPC request is executed, **Then** the system obtains or refreshes the access token, substitutes `{{_auth.oauth2.accessToken}}`, and delivers the metadata key `authorization` with value `Bearer <actual_token>` to the gRPC client.
2. **Given** a gRPC request where `{{_auth.oauth2.accessToken}}` is used in custom metadata keys or the JSON message body, **When** executed, **Then** all occurrences of the dynamic variable are substituted with the valid token.
3. **Given** a gRPC request referencing `{{_auth.oauth2.accessToken}}` while OAuth2 credentials are invalid or missing, **When** executed, **Then** execution terminates gracefully with an error message and does not send an unauthenticated RPC call.

---

### User Story 3 - Extensible Dynamic Runtime Variable Architecture (Priority: P2)

To ensure long-term maintainability and prevent ad-hoc variable handling scattered across request execution logic, the variable resolution pipeline must adopt an extensible, provider-based design. The design must decouple variable identification, resolution, and lifecycle hooks so that adding future runtime variables (such as dynamic timestamps, generated UUIDs, hash computations, or additional auth tokens) requires registering a new dynamic variable provider rather than altering core execution flows.

**Why this priority**: Guarantees that the codebase remains clean, modular, and easily extensible for additional dynamic runtime variables without technical debt.

**Independent Test**: Can be tested by registering a mock or stub dynamic runtime variable provider into the resolver registry and verifying that requests containing the registered variable format are correctly resolved through the standardized pipeline.

**Acceptance Scenarios**:

1. **Given** the dynamic variable resolution pipeline, **When** a request string contains a registered dynamic variable prefix or pattern, **Then** the system delegates resolution to the corresponding dynamic variable provider.
2. **Given** multiple dynamic variable providers registered in the system, **When** a request references variables from different providers alongside static and secret variables, **Then** all variables are resolved in predictable order and merged smoothly into the execution context.
3. **Given** a dynamic variable provider requiring asynchronous pre-execution tasks (such as network token fetching), **When** invoked, **Then** the pipeline awaits provider completion before proceeding to request dispatch.

---

### User Story 4 - Autocomplete Suggestions and Visual Highlighting for Dynamic Variables (Priority: P3)

When composing HTTP or gRPC requests in the UI, users benefit from discoverability of available dynamic variables. When entering variable placeholders `{{` in URL, header, metadata, or body inputs, the autocomplete menu should present `_auth.oauth2.accessToken` with a descriptive label when OAuth2 authentication is enabled or available in context. Furthermore, syntax highlighting must recognize `{{_auth.oauth2.accessToken}}` as a valid variable rather than an undefined or broken variable reference.

**Why this priority**: Enhances usability and authoring experience, making users aware that the dynamic variable is available and properly formatted.

**Independent Test**: Open a request editor with OAuth2 enabled, type `{{_auth.` into a header or metadata field, and verify that `_auth.oauth2.accessToken` appears in the autocomplete suggestion list and is highlighted with valid variable coloring.

**Acceptance Scenarios**:

1. **Given** an editor field with OAuth2 authentication enabled, **When** the user triggers autocomplete inside `{{`, **Then** `_auth.oauth2.accessToken` is displayed in the suggestion list with an explanatory description (e.g., "Current OAuth2 Access Token").
2. **Given** a text input containing `{{_auth.oauth2.accessToken}}`, **When** rendered, **Then** the syntax highlighter styles the variable as valid when OAuth2 is configured, distinguishing it from unknown or missing variable references.

---

### Edge Cases

- **Token Expiry During Execution**: If an access token expires right before request dispatch, the dynamic variable resolver detects the expiration (using existing expiry buffers), triggers a token refresh or re-fetch, and resolves the fresh token.
- **Multiple Occurrences in Single Request**: If `{{_auth.oauth2.accessToken}}` appears multiple times in the same request (e.g., in both a header and the request body), the token is fetched/validated once and substituted consistently across all occurrences.
- **OAuth2 Disabled or Set to None**: If a request has auth set to None or another type (e.g., Basic Auth, Bearer Token) but includes `{{_auth.oauth2.accessToken}}`, resolution fails with a clear message indicating OAuth2 is not active for the request.
- **Inherited vs. Direct Auth**: If parent collection defines OAuth2 and the request uses "Inherit Auth", `{{_auth.oauth2.accessToken}}` resolves using the parent collection's OAuth2 credentials. If the request explicitly overrides auth with a non-OAuth2 type, resolution fails.
- **Concurrent Request Executions**: If multiple requests referencing `{{_auth.oauth2.accessToken}}` are triggered concurrently, token acquisition is thread-safe and reuses cached valid tokens to avoid redundant token exchange calls.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support the special dynamic variable `{{_auth.oauth2.accessToken}}` within HTTP request URLs, query parameters, headers, and request bodies.
- **FR-002**: The system MUST support the special dynamic variable `{{_auth.oauth2.accessToken}}` within gRPC endpoint URLs, metadata headers, and message payloads.
- **FR-003**: When `{{_auth.oauth2.accessToken}}` is present in an executing request, the system MUST verify that OAuth2 authentication is configured either directly on the request or inherited from an ancestor folder or collection.
- **FR-004**: If OAuth2 authentication is not configured when `{{_auth.oauth2.accessToken}}` is referenced, the system MUST abort request execution with an explicit error explaining that OAuth2 authentication is required.
- **FR-005**: If OAuth2 authentication is configured, the system MUST ensure a valid, unexpired access token exists (fetching a new token or refreshing via refresh token if necessary) before resolving the variable.
- **FR-006**: If token acquisition fails, the system MUST abort request execution and provide a descriptive error message indicating token acquisition failure.
- **FR-007**: The system MUST provide an extensible dynamic runtime variable registry or provider interface that allows registering variable resolvers by namespace or pattern (e.g., `_auth.*`).
- **FR-008**: The dynamic runtime variable system MUST allow new variable providers to be added without modifying the core request execution coordinator loop.
- **FR-009**: The editor autocomplete system MUST suggest `_auth.oauth2.accessToken` when the user enters variable interpolation syntax (`{{`) in supported request fields when OAuth2 is active.
- **FR-010**: The variable syntax highlighting system MUST recognize `_auth.oauth2.accessToken` as a valid variable reference when OAuth2 is active.
- **FR-011**: The system MUST include automated integration tests demonstrating that a gRPC request configured with metadata header `authorization: Bearer {{_auth.oauth2.accessToken}}` correctly resolves the access token upon invocation.

### Key Entities *(include if feature involves data)*

- **DynamicVariableProvider**: An interface/abstraction responsible for recognizing supported dynamic variable patterns (e.g., `_auth.oauth2.*`), resolving their dynamic runtime values asynchronously, and performing any required pre-resolution side effects (such as token exchange).
- **DynamicVariableContext**: Contextual information passed to dynamic variable providers during request preparation, including the active request tab, application state, resolved authentication configuration, and variable layers.
- **OAuth2TokenResolution**: Represents the resolved state of an OAuth2 access token, containing the token value, expiration metadata, and validation status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of HTTP and gRPC requests utilizing `{{_auth.oauth2.accessToken}}` with valid OAuth2 configuration resolve the access token accurately before transmission.
- **SC-002**: Requests referencing `{{_auth.oauth2.accessToken}}` without OAuth2 configured are stopped with zero accidental unauthenticated network transmissions.
- **SC-003**: Adding a new dynamic runtime variable provider in future increments requires zero modifications to individual HTTP or gRPC execution handlers.
- **SC-004**: Automated tests for gRPC metadata token resolution pass reliably across execution targets.

## Assumptions

- Users have configured OAuth2 endpoints, credentials, and grant types according to the OpenCollection OAuth2 specification established in feature `010-oauth2-auth`.
- Token caching and expiration buffer conventions (e.g., refreshing if expiring within 30 seconds) established for OAuth2 requests apply to dynamic variable token resolution.
- Variable syntax adheres to standard double curly braces `{{...}}` consistent with existing environment and secret variables.
- Dynamic variable resolution happens at request execution time, immediately after pre-request scripts and external secret resolution.
