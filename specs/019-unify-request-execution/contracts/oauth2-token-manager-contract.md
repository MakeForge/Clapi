# Contract: OAuth2TokenManager

**Feature**: `019-unify-request-execution`  
**Module**: `:core-network` (`io.github.makeforge.clapi.core.network.oauth2`)

---

## 1. Interface & Responsibilities

```kotlin
package io.github.makeforge.clapi.core.network.oauth2

import io.github.makeforge.clapi.core.model.AuthConfig
import io.github.makeforge.clapi.core.model.AuthType

class OAuth2TokenManager(
    private val oauth2Client: OAuth2Client,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Ensures that [authConfig] (when `authConfig.type == AuthType.OAUTH2`) has a non-expired
     * `accessToken`.
     *
     * - If `accessToken` is valid and `now < expiresAt - expiryBufferMillis`, returns immediately.
     * - If expired and `refreshToken` + `accessTokenUrl` are present, refreshes the token.
     * - If missing/expired and `autoFetchToken != "false"` with `grantType` in
     *   `{"client_credentials", "password"}`, fetches a new token.
     * - Deduplicates concurrent/repeat calls within the same execution pass using an internal Mutex.
     */
    suspend fun ensureValidToken(
        authConfig: AuthConfig,
        expiryBufferMillis: Long = 30_000L,
        onTokenUpdated: ((AuthConfig) -> Unit)? = null,
    ): OAuth2TokenRefreshResult
}
```

---

## 2. Deduplication & Consumer Contract

1. **`OAuth2DynamicVariableProvider`**:
   - Delegates `{{_auth.oauth2.accessToken}}` and `{{_auth.oauth2.idToken}}` resolution to `OAuth2TokenManager.ensureValidToken(...)` instead of maintaining its own private `acquireToken` implementation.
2. **Pre-Flight Request Auth Preparation (`RequestExecutionService` & UI Executors)**:
   - Delegates `AuthType.OAUTH2` token validation/refresh to `OAuth2TokenManager.ensureValidToken(...)`.
3. **Single-Refresh Guarantee**:
   - When a single request execution uses both `AuthType.OAUTH2` and `{{_auth.oauth2.accessToken}}` in a header/body with an expired token, `OAuth2TokenManager` updates the shared `AuthConfig` state after the first refresh so the second caller sees the fresh `expiresAt` timestamp and performs **zero** redundant HTTP calls.
