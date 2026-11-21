# Phase 0 Research: OAuth2 Authentication

## Research Topics & Decisions

### 1. OAuth2 Flow Support & Token Acquisition Architecture

- **Context**: The feature requires supporting standard OAuth2 flows defined in OpenCollection (`client_credentials`, `resource_owner_password_credentials`, `authorization_code`, `implicit`). Automated execution and token testing primarily focus on Client Credentials and Resource Owner Password flows, with ability to refresh via Refresh Token grant.
- **Decision**: Implement an `OAuth2TokenClient` in `core-network` built on top of Ktor's `HttpClient`. It will perform standard RFC 6749 POST requests to the OAuth2 `accessTokenUrl` (or `refreshTokenUrl`).
  - **Client Credentials Flow**: Sends `grant_type=client_credentials`, optional `scope`. Authenticates client via HTTP Basic Auth header (`Authorization: Basic base64(clientId:clientSecret)`) or via request body form parameters (`client_id`, `client_secret`) according to `OAuth2CredentialsPlacement`.
  - **Resource Owner Password Credentials Flow**: Sends `grant_type=password`, `username`, `password`, optional `scope`. Client authentication applied via header or body according to `placement`.
  - **Refresh Token Grant**: Sends `grant_type=refresh_token`, `refresh_token`, optional `scope`, along with client credentials if configured.
- **Rationale**: Ktor `HttpClient` is already the foundational HTTP engine in `core-network` with ContentNegotiation and CIO engine. Creating a dedicated `OAuth2TokenClient` keeps network logic pure, cross-platform, and independently testable without UI dependencies.
- **Alternatives Considered**:
  - *Ktor's built-in `Auth` feature plugin*: Ktor client's `bearer {}` auth plugin handles automatic bearer refreshing, but it is tightly coupled to the static client configuration, whereas Clapi dynamically executes requests with heterogeneous auth configs, custom variable layers, folder/collection inheritance, and manual "Fetch Tokens" button triggers in the UI. A dedicated `OAuth2TokenClient` provides full control and introspection for both interactive UI and dispatch pipelines.

---

### 2. Variable and Secret Substitution in OAuth2 Fields

- **Context**: The user requires that "even for secret fields you can use variables/secrets for their value". Fields such as `clientSecret`, `password`, `clientId`, `accessTokenUrl`, and `scope` may contain `{{variable}}` templates or `@secret{name}` references.
- **Decision**:
  - Ensure all OAuth2 fields in the UI state are scanned during secret resolution (`resolveExternalSecretsForTab` in `RequestExecutor.kt`).
  - Prior to invoking `OAuth2TokenClient` (whether via "Fetch Tokens" button or in-flight request dispatch), resolve all string parameters against the active `variableLayers` using `VariableResolver.resolve(value, variableLayers)`.
  - In the UI, render secret fields (`clientSecret`, `password`) with visibility toggles (masked/unmasked) while retaining full string values, preventing accidental exposure of sensitive variables or raw secrets on screen.
- **Rationale**: Clapi already uses `VariableResolver` and `SecretResolutionService` for request URLs, headers, body, and basic auth credentials. Extending this to OAuth2 fields provides unified variable handling across the entire app.
- **Alternatives Considered**:
  - *Resolving variables only at request execution time*: Rejected because clicking "Fetch Tokens" in the Auth Editor also needs access to the active environment variables and secrets to succeed.

---

### 3. Token Expiration & Validity Checking

- **Context**: When a request with OAuth2 is executed, the system must check whether the current access token is still valid. If not, it fetches a new access token before executing the request.
- **Decision**:
  - Track token state using an `OAuth2TokenState` model containing:
    - `accessToken: String`
    - `refreshToken: String?`
    - `idToken: String?`
    - `tokenType: String` (defaults to "Bearer")
    - `expiresAtEpochMs: Long?` (computed from `now + expires_in * 1000L`)
  - **Validity Rule**: A token is considered **valid** if:
    1. `accessToken.isNotBlank()`
    2. Expiration check: If `expiresAtEpochMs != null`, `expiresAtEpochMs > currentTimeMillis() + SKEW_BUFFER_MS` (where `SKEW_BUFFER_MS = 30_000L` / 30 seconds).
    3. If `expiresAtEpochMs == null`, attempt a lightweight parse of the JWT payload (decoding payload base64 JSON). If an `exp` numeric claim exists, check `exp * 1000L > currentTimeMillis() + SKEW_BUFFER_MS`.
    4. If no expiration data is available (opaque token without `expires_in`), treat as valid once fetched until an HTTP 401 is encountered or user triggers a manual refresh.
- **Rationale**: A 30-second skew buffer prevents edge-case token expiration in-flight during high latency or slow network conditions. Checking both `expires_in` and JWT `exp` guarantees compatibility with OAuth2 and OpenID Connect identity providers (e.g. Keycloak).
- **Alternatives Considered**:
  - *No buffer (checking `expiresAt > now`)*: Risk of token expiring between the pre-flight check and the actual upstream API receipt.
  - *Parsing JWT signatures*: Unnecessary and heavyweight for client-side validity; standard token endpoints already sign and validate tokens.

---

### 4. Token Caching & Re-use Lifecycle

- **Context**: Requests within a collection or folder may inherit OAuth2 configuration. Also, a single request tab can be executed multiple times.
- **Decision**:
  - Maintain session token state in `AppState` / `OAuth2TokenCache`.
  - When tokens are acquired (via "Fetch Tokens" button or auto-refresh on send), store the token in the active tab state (`RequestTabState`) and also in an in-memory `OAuth2TokenCache` keyed by a stable hash of the resolved OAuth2 configuration (or request/folder ID).
  - When executing a request:
    1. Look up cached token state for the request/auth config.
    2. If valid, inject `Authorization: Bearer <accessToken>`.
    3. If invalid/expired:
       - If `refreshToken` is present and valid, call `OAuth2TokenClient.refreshToken(...)`.
       - If refresh fails or no refresh token exists, call `OAuth2TokenClient.fetchToken(...)` using primary credentials (`client_credentials` or `password`).
       - On success, update cached token state and tab UI fields, then inject `Authorization: Bearer <newAccessToken>`.
       - On failure, abort request dispatch and log a clear error to the console.
- **Rationale**: Prevents requesting a new token on every single HTTP request execution, vastly improving performance while ensuring tokens are transparently refreshed when they expire.
- **Alternatives Considered**:
  - *Persisting tokens to OpenCollection JSON files on disk*: Rejected per OpenCollection best practices; credentials and environment variables belong on disk, but ephemeral bearer tokens should remain in-memory session state to prevent committing credentials or stale tokens to git repositories.

---

### 5. UI Design in Auth Editor Tab

- **Context**: The user requires selecting OAuth2 in the Auth Editor tab, configuring credentials (supporting variables/secrets), a "Fetch Tokens" button, and copyable text fields displaying the Refresh Token, Access Token, and Identity Token.
- **Decision**:
  - In `AuthEditor.kt`:
    - Remove the exclusion of `AuthType.OAUTH2` from `selectableAuthTypes`.
    - Replace the "coming soon" placeholder with the complete OAuth2 configuration UI:
      - **Grant Type Selector**: Dropdown / segmented chips for `Client Credentials` and `Password (Resource Owner)`.
      - **Endpoints & Common Config**: Access Token URL (with variable support), Scope.
      - **Client Credentials Section**: Client ID, Client Secret (with variable support and password mask toggle), Credentials Placement (Basic Auth Header vs. Request Body).
      - **Resource Owner Section** (visible when Password flow selected): Username, Password (with variable support and mask toggle).
      - **Token Testing & Inspection Section**:
        - "Fetch Tokens" button with loading indicator (`CircularProgressIndicator`) and error message display.
        - Copyable read-only text fields for:
          - **Access Token** (with Copy icon button and one-click clipboard copy).
          - **Refresh Token** (with Copy icon button).
          - **Identity Token (ID Token)** (with Copy icon button).
        - Expiration status indicator (e.g. "Expires in Xm Ys" or "Expired" or "No token").
- **Rationale**: Follows the existing design system (`ClapiColors`, `LabeledTextField`, `PasswordLabeledTextField`, `Strings.t`) while providing an intuitive developer experience matching Postman/Insomnia.
- **Alternatives Considered**:
  - *Modal popup for fetching tokens*: Rejected; having token fields inline in the Auth tab keeps everything visible in context.

---

### 6. QA Integration Testing with Keycloak

- **Context**: Keycloak compose (`qa-tests/keycloak-oauth2-compose.yml`) and realm config (`qa-tests/keycloak/test-realm.json`) are provided. Automated tests must verify the OAuth2 implementation.
- **Decision**:
  - Add an integration test class `OAuth2IntegrationTest.kt` in `qa-tests`.
  - Test suite will:
    1. Check if Keycloak is reachable at `http://localhost:8080/realms/test-realm` (or run against an embedded/mock Keycloak endpoint or test container if Keycloak is started).
    2. Test Client Credentials flow: Fetch token from Keycloak for `client-credentials-client` (`client-credentials-secret`). Assert access token received and valid.
    3. Test Resource Owner Password flow: Fetch token from Keycloak for `password-flow-client` (`password-flow-secret`), user `testuser` (`password123`). Assert access token, refresh token, and id token received.
    4. Test Refresh Token flow: Use refresh token from step 3 to obtain a refreshed access token.
    5. Test End-to-End Request Execution: Execute an HTTP request with OAuth2 auth against a mock/echo server or Keycloak userinfo endpoint (`http://localhost:8080/realms/test-realm/protocol/openid-connect/userinfo`), validating that `Authorization: Bearer <token>` is sent and auto-refreshed upon simulated expiry.
- **Rationale**: Aligns directly with existing QA test patterns (`ExternalSecretsIntegrationTest`, `ApiClientE2ETest`) and provides full regression protection.
