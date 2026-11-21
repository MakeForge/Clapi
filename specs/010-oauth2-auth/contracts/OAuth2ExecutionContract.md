# Contract: OAuth2 Request Execution Pipeline

## Interceptor / Execution Step in `RequestExecutor.kt`

Prior to sending an HTTP request, the pipeline evaluates the effective authentication configuration.

### Invariants & Sequence

1. **Resolution of Effective Auth**:
   - If `tab.authType == AuthType.INHERIT`, resolve the ancestor collection or folder auth.
   - If effective auth type is `AuthType.OAUTH2`:
2. **Variable Resolution**:
   - All OAuth2 configuration fields (URL, Client ID, Client Secret, Username, Password, Scope) MUST be resolved against `variableLayersWithSecrets`.
3. **Validity Check**:
   - Check if current active token (from tab or token cache) is valid via `OAuth2TokenState.isValid()`.
4. **Auto-Fetch / Auto-Refresh**:
   - If token is missing or expired:
     - If a valid `refreshToken` is available:
       - Attempt `refreshToken(...)`.
     - If refresh token is unavailable or fails:
       - Attempt primary grant (`fetchClientCredentialsToken` or `fetchPasswordToken`).
     - If token fetch fails:
       - Cancel request execution, emit failure event with the OAuth2 error message, and log diagnostic info to console.
     - If token fetch succeeds:
       - Update cached token state and tab UI token fields.
5. **Authorization Header Injection**:
   - Add/overwrite `Authorization: Bearer <validAccessToken>` on the outgoing request headers.
6. **Execution Proceeds**:
   - Network dispatch continues normally to the target endpoint.
