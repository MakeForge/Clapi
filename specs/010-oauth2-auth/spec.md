# Feature Specification: OAuth2 Authentication

**Feature Branch**: `010-oauth2-auth`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "Implement OAuth2 authentication: OpenCollection has defined OAuth2 model for different types of credentials, client, resource owner, all of them should be supported; Ensure in Auth Editor tab you can select and configure OAuth2 properly, ensure that even for secret fields you can use variables/secrets for their value; in auth editor tab also provide small section to test the credentials with a 'Fetch Tokens' button and display current refresh token, access token, identity token in a copyable text field; When a request with oauth2 is executed, check if current access token is still valid, if not use Ktor http client to fetch new access token and set it on the request authorization properly before the request is actually executed; Ensure qa-tests contains a test for this (using @qa-tests/keycloak-oauth2-compose.yml to easily start a keycloak server with @qa-tests/keycloak/test-realm.json that includes examples for the different types of credentials)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure OAuth2 Authentication in Auth Editor (Priority: P1)

API developers need to authenticate against OAuth2-protected APIs using various grant types standard in the OpenCollection specification (including Client Credentials and Resource Owner Password flows). In the request and collection Auth Editor tabs, the user selects "OAuth 2.0", picks their required grant flow, and enters endpoints, scopes, client identifiers, and secret credentials. All fields—including sensitive secret fields such as Client Secret and Password—must support dynamic variables and external secrets so that credentials do not need to be hardcoded in plain text.

**Why this priority**: Without being able to select and configure OAuth2 credentials in the UI and persist them in the collection format, no OAuth2 flows can be configured or executed.

**Independent Test**: Can be tested independently by navigating to the Auth tab of a request or collection, selecting OAuth2, choosing flow types (Client Credentials, Resource Owner Password Credentials), entering parameter values with variables (e.g., `{{client_secret}}`), and verifying that the configuration is preserved, displayed correctly, and serialized into the OpenCollection model.

**Acceptance Scenarios**:

1. **Given** a user is editing authentication for a request or folder, **When** they select the "OAuth 2.0" authentication type, **Then** the Auth Editor displays the OAuth2 configuration panel with options for flow selection and relevant credential inputs.
2. **Given** an OAuth2 configuration with Client Credentials flow selected, **When** the user provides Client ID, Client Secret, Token URL, and Scope, **Then** all values are retained in the request state and saved according to OpenCollection schema.
3. **Given** an OAuth2 configuration with Resource Owner Password Credentials flow selected, **When** the user provides Token URL, Client ID, Client Secret, Username, and Password, **Then** all inputs are retained and saved in the request state.
4. **Given** any OAuth2 field (including Client Secret, Password, Client ID, or URLs), **When** a user enters variable syntax (e.g., `{{my_secret}}` or `@secret{my_key}`), **Then** the system accepts the variable reference without truncation or masking errors and resolves it dynamically during token acquisition.
5. **Given** credentials placement options (Basic Auth Header vs. Request Body), **When** the user configures the placement, **Then** the client credential transmission method is stored and respected during token exchange.

---

### User Story 2 - Interactively Fetch and Inspect OAuth2 Tokens (Priority: P1)

When configuring OAuth2 credentials, developers need immediate verification that their configuration works before dispatching actual API calls. Inside the Auth Editor tab, a dedicated "Fetch Tokens" action allows the user to test credentials on demand. Upon fetching, the resulting Access Token, Refresh Token, and Identity Token (ID Token) are displayed in copyable text boxes so the user can inspect or manually copy them.

**Why this priority**: Testing credentials directly in the editor provides instant feedback, debuggability, and transparency, ensuring developers know whether connection or authentication issues stem from credentials versus the target API.

**Independent Test**: Can be tested independently by entering valid OAuth2 credentials pointing to a token endpoint (e.g., Keycloak), clicking "Fetch Tokens", and observing that the access token, refresh token, and identity token fields populate with copyable token strings.

**Acceptance Scenarios**:

1. **Given** valid OAuth2 parameters entered in the Auth Editor, **When** the user clicks "Fetch Tokens", **Then** the application contacts the token endpoint and populates the Access Token, Refresh Token, and ID Token fields (when provided by the identity provider).
2. **Given** displayed token fields in the Auth Editor, **When** the user clicks the copy button next to any token (Access, Refresh, or ID token), **Then** the token text is copied to the system clipboard and visual confirmation is displayed.
3. **Given** invalid credentials or an unreachable token endpoint, **When** the user clicks "Fetch Tokens", **Then** an informative error message is displayed in the UI indicating the reason for failure (e.g., invalid client, unauthorized, network error) without crashing.
4. **Given** already fetched tokens, **When** the user switches between tabs or reopens the request within the same session, **Then** the current token status and cached tokens remain visible.

---

### User Story 3 - Automatic Token Validation and Refresh on Request Execution (Priority: P1)

When a developer clicks "Send" on an HTTP request configured with OAuth2, the execution pipeline must verify whether a valid, unexpired access token exists. If no token has been fetched yet, or if the current access token has expired (or is about to expire), the system automatically uses the configured OAuth2 flow (or refresh token grant if available) to fetch a fresh access token, attaches it as a Bearer authorization token on the request, and then executes the API request seamlessly.

**Why this priority**: Eliminates manual token management during everyday API testing and automation. Requests should "just work" without requiring developers to manually click "Fetch Tokens" before each request execution.

**Independent Test**: Can be tested independently by creating a request requiring OAuth2 authentication with no active token or an expired token, triggering request execution, and verifying that a fresh token is acquired and the request succeeds with an `Authorization: Bearer <token>` header.

**Acceptance Scenarios**:

1. **Given** an OAuth2-authenticated request with no active access token, **When** the user executes the request, **Then** the system automatically fetches a new access token and executes the request with the valid token in the `Authorization` header.
2. **Given** an OAuth2-authenticated request with an expired access token and a valid refresh token, **When** the user executes the request, **Then** the system uses the refresh token to obtain a fresh access token before executing the request.
3. **Given** an OAuth2-authenticated request with an unexpired access token, **When** the user executes the request, **Then** the existing access token is reused immediately without making an unnecessary token endpoint call.
4. **Given** an OAuth2-authenticated request where token acquisition fails (e.g., token endpoint 401 Unauthorized), **When** the user executes the request, **Then** the request execution halts with a clear error explaining the authentication failure before calling the target URL.

---

### User Story 4 - End-to-End Automated Testing with Keycloak (Priority: P2)

The QA integration test suite must automatically verify the entire OAuth2 flow against a live OpenID Connect / OAuth2 server (Keycloak test realm with Client Credentials and Resource Owner Password clients).

**Why this priority**: Guarantees regressions are prevented and proves real-world compatibility with standard OAuth2 identity providers.

**Independent Test**: Can be tested independently by running the automated QA test suite targeting Keycloak container endpoints, validating token retrieval and subsequent protected API calls for all configured credential flows.

**Acceptance Scenarios**:

1. **Given** a Keycloak instance running with the test realm, **When** running the automated Client Credentials test, **Then** the test acquires an access token and executes an authenticated request successfully.
2. **Given** a Keycloak instance running with the test realm, **When** running the automated Resource Owner Password Credentials test, **Then** the test acquires access, refresh, and identity tokens and executes an authenticated request successfully.
3. **Given** an access token that has expired or is invalidated, **When** running the automatic refresh scenario, **Then** the client automatically refreshes the token and completes the protected request.

---

### Edge Cases

- **Token response without `expires_in`**: If the identity provider does not return an `expires_in` duration, the system checks the JWT `exp` claim if the token is a valid JWT; otherwise, it assigns a safe default expiration window.
- **Clock skew**: A token expiration buffer (e.g., 30 seconds before recorded expiry) is applied so tokens that would expire in-flight are refreshed ahead of time.
- **Concurrent requests**: When multiple requests share the same OAuth2 authentication configuration, token refreshes are synchronized to avoid duplicate simultaneous token endpoint calls.
- **Refresh token expiration or invalidation**: If a refresh token request fails (e.g., invalid grant / expired refresh token), the system falls back to re-executing the primary credential grant (Client Credentials or Resource Owner Password) if credentials are available.
- **Inherited OAuth2 settings**: Requests inheriting authentication from parent folders or collections correctly inherit OAuth2 configuration and share or resolve tokens accordingly.
- **Missing or blank required fields**: If required fields (such as Token URL or Client ID) are empty, the UI clearly identifies missing fields and prevents execution with descriptive validation feedback.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow users to select "OAuth 2.0" in the authentication editor for requests, folders, and collections.
- **FR-002**: The system MUST support configuring OpenCollection OAuth2 flows, specifically Client Credentials (`client_credentials`) and Resource Owner Password Credentials (`resource_owner_password_credentials`), as well as preserving settings for Authorization Code and Implicit flows.
- **FR-003**: The system MUST provide inputs for all relevant flow parameters, including Access Token URL, Refresh Token URL, Authorization URL, Redirect URI, Client ID, Client Secret, Scope, Username, Password, and Credentials Placement (header vs. body).
- **FR-004**: All OAuth2 input fields—including sensitive secret fields (Client Secret, Password)—MUST support dynamic template variable substitution (e.g., `{{variable}}` and `@secret{...}`).
- **FR-005**: The system MUST provide a "Fetch Tokens" action in the Auth Editor that requests tokens directly from the token endpoint using the configured flow parameters and resolved variables.
- **FR-006**: The system MUST display the resulting Access Token, Refresh Token, and Identity Token in copyable text fields in the Auth Editor after fetching tokens.
- **FR-007**: When executing any HTTP request configured with OAuth2, the system MUST verify whether the current access token is valid and unexpired before dispatching the request.
- **FR-008**: If the current access token is missing or expired, the system MUST automatically request a new access token (via refresh token grant if present, or via primary grant credentials) and inject it into the request authorization header before the request is executed.
- **FR-009**: The system MUST gracefully handle token acquisition errors during both manual "Fetch Tokens" and automatic request execution, presenting clear diagnostic messages without crashing.
- **FR-010**: The project QA test suite MUST include automated integration tests validating OAuth2 Client Credentials and Resource Owner Password flows against the provided Keycloak test realm configuration.

### Key Entities *(include if feature involves data)*

- **OAuth2Configuration**: Holds the complete OAuth2 setup for a request or collection, including flow type, endpoint URLs, client credentials, resource owner credentials, token source, credentials placement, and settings.
- **OAuth2TokenState**: Represents the active token session for an OAuth2 configuration, storing the current Access Token, Refresh Token, Identity Token, token type, and expiration timestamp.
- **OAuth2CredentialsPlacement**: Enum defining how client credentials are sent to the token endpoint (`basic_auth_header` or `body`).
- **OAuth2Flow**: Enum defining the grant flow (`client_credentials`, `resource_owner_password_credentials`, `authorization_code`, `implicit`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure OAuth2 credentials and fetch tokens interactively in under 30 seconds from a freshly configured request.
- **SC-002**: 100% of valid OAuth2 credential configurations successfully acquire access tokens and execute authenticated requests in automated tests.
- **SC-003**: In-flight token refresh is fully automated, requiring 0 manual user actions when executing requests with expired access tokens.
- **SC-004**: Token fields in the UI allow one-click clipboard copying with visual confirmation.
- **SC-005**: All credential fields support variable and external secret resolution without exposing secrets in plain text files.

## Assumptions

- Target identity providers adhere to standard RFC 6749 (OAuth 2.0) and OpenID Connect specifications for token requests and JSON responses.
- In-memory token caching is scoped to the application session; persisted collection files store configuration definitions and variable references, not active tokens, unless explicitly configured.
- Automated QA tests use the existing Keycloak Docker Compose environment (`qa-tests/keycloak-oauth2-compose.yml`) and realm definitions (`qa-tests/keycloak/test-realm.json`).
