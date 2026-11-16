# Quickstart & Validation Guide: OAuth2 Authentication

This guide describes how to validate the OAuth2 feature both interactively in the Clapi desktop UI and via automated integration tests using the local Keycloak server.

---

## 1. Prerequisites & Environment Setup

Start the Keycloak container with pre-configured `test-realm`:
```bash
docker compose -f qa-tests/keycloak-oauth2-compose.yml up -d
```

Verify Keycloak is healthy and realm is available:
```bash
curl -s http://localhost:8080/realms/test-realm | grep '"realm":"test-realm"'
```

Keycloak Configuration Summary:
- **Token URL**: `http://localhost:8080/realms/test-realm/protocol/openid-connect/token`
- **Client Credentials Client**:
  - Client ID: `client-credentials-client`
  - Client Secret: `client-credentials-secret`
- **Resource Owner Password Client**:
  - Client ID: `password-flow-client`
  - Client Secret: `password-flow-secret`
  - Username: `testuser`
  - Password: `password123`

---

## 2. Interactive UI Verification (Auth Editor)

### Scenario A: Client Credentials Flow & Manual Token Fetch
1. Launch Clapi (`./gradlew :ui-desktop:run`).
2. Open or create a request.
3. In the request editor, click the **Auth** tab.
4. Click the **OAuth 2.0** chip selector.
5. Select flow: **Client Credentials**.
6. Enter configuration:
   - **Access Token URL**: `http://localhost:8080/realms/test-realm/protocol/openid-connect/token`
   - **Client ID**: `client-credentials-client`
   - **Client Secret**: `client-credentials-secret`
   - **Credentials Placement**: `Basic Auth Header`
7. Click **Fetch Tokens**.
8. **Expected Outcome**:
   - The button shows a loading spinner briefly.
   - The **Access Token** field populates with a JWT string.
   - Clicking the copy icon next to Access Token copies the string to clipboard.
   - Token status shows "Active" with expiration countdown.

### Scenario B: Resource Owner Password Flow
1. In the Auth tab, switch flow to **Password**.
2. Enter configuration:
   - **Access Token URL**: `http://localhost:8080/realms/test-realm/protocol/openid-connect/token`
   - **Client ID**: `password-flow-client`
   - **Client Secret**: `password-flow-secret`
   - **Username**: `testuser`
   - **Password**: `password123`
3. Click **Fetch Tokens**.
4. **Expected Outcome**:
   - **Access Token**, **Refresh Token**, and **Identity Token** fields all populate with valid JWTs.
   - Copy buttons for each field successfully copy respective tokens.

### Scenario C: Variables and External Secrets in Credentials
1. In the Environment editor, define variable `KEYCLOAK_SECRET = client-credentials-secret`.
2. In the Client Secret field of Auth Editor, enter `{{KEYCLOAK_SECRET}}`.
3. Click **Fetch Tokens**.
4. **Expected Outcome**:
   - Variable resolves properly during token fetch; token is successfully acquired.

### Scenario D: Auto-Fetch & Auto-Refresh on Request Execution
1. Configure request URL to `http://localhost:8080/realms/test-realm/protocol/openid-connect/userinfo`.
2. Clear any active access token in the UI.
3. Click **Send**.
4. **Expected Outcome**:
   - Clapi detects missing/expired access token.
   - Automatically issues token request to Keycloak.
   - Attaches `Authorization: Bearer <token>` to request.
   - Userinfo endpoint returns 200 OK with user profile details (`testuser@example.com`).

---

## 3. Automated QA Integration Tests

Run the OAuth2 integration test suite:
```bash
./gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.OAuth2IntegrationTest"
```

**Expected Outcome**:
- `test_client_credentials_flow()` passes.
- `test_resource_owner_password_flow()` passes.
- `test_refresh_token_grant()` passes.
- `test_request_execution_auto_fetches_and_refreshes_token()` passes.

---

## 4. Teardown

Stop the Keycloak test server:
```bash
docker compose -f qa-tests/keycloak-oauth2-compose.yml down
```
