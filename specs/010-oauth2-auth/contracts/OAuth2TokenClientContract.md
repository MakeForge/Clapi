# Contract: OAuth2 Token Client

## Component: `OAuth2TokenClient` (`core-network`)

The `OAuth2TokenClient` manages low-level HTTP communication with OAuth 2.0 token endpoints using Ktor's `HttpClient`.

### Public Interface

```kotlin
interface OAuth2TokenClient {
    /**
     * Executes Client Credentials grant:
     * POST to [tokenUrl] with grant_type=client_credentials.
     */
    suspend fun fetchClientCredentialsToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement,
        scope: String? = null
    ): Result<OAuth2TokenResponse>

    /**
     * Executes Resource Owner Password Credentials grant:
     * POST to [tokenUrl] with grant_type=password.
     */
    suspend fun fetchPasswordToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement,
        username: String,
        password: String,
        scope: String? = null
    ): Result<OAuth2TokenResponse>

    /**
     * Executes Refresh Token grant:
     * POST to [tokenUrl] with grant_type=refresh_token.
     */
    suspend fun refreshToken(
        tokenUrl: String,
        refreshToken: String,
        clientId: String? = null,
        clientSecret: String? = null,
        placement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
        scope: String? = null
    ): Result<OAuth2TokenResponse>
}
```

### Protocol Invariants

1. **Content-Type**: Requests MUST use `application/x-www-form-urlencoded`.
2. **Credentials Placement**:
   - `OAuth2CredentialsPlacement.BASIC_AUTH_HEADER`: Client credentials MUST be formatted as `Authorization: Basic base64(urlEncode(clientId) + ":" + urlEncode(clientSecret))`.
   - `OAuth2CredentialsPlacement.BODY`: Client credentials MUST be passed as form parameters `client_id` and `client_secret`.
3. **Error Handling**: Non-2xx responses MUST be parsed as RFC 6749 error responses (`error`, `error_description`) and returned as `Result.failure(OAuth2Exception(error, description))`.
