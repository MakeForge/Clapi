# Data Model: Dynamic Runtime Variables & OAuth2 Token Resolution

**Feature**: Dynamic OAuth2 Access Token Variable (`011-oauth2-token-variable`)

**Date**: 2026-11-09

---

## 1. Domain Entities & Interfaces (`core-network`)

### `DynamicVariableProvider`
Interface implemented by dynamic variable providers to participate in the pre-execution resolution lifecycle.

```kotlin
package io.github.makeforge.clapi.core.network.dynamicvar

interface DynamicVariableProvider {
    /**
     * Human-readable identifier for this provider (e.g., "OAuth2TokenProvider").
     */
    val name: String

    /**
     * Determines whether this provider is capable of resolving the specified token.
     * @param token The variable token extracted from `{{token}}` (e.g., `_auth.oauth2.accessToken`).
     */
    fun supports(token: String): Boolean

    /**
     * Resolves the requested tokens against the provided execution context.
     * @param tokens The subset of tokens supported by this provider found in the request.
     * @param context The execution context containing auth config, state, and token client.
     * @return Map of token string to resolved runtime value.
     * @throws DynamicVariableResolutionException on failure (halting request execution).
     */
    suspend fun resolve(
        tokens: Set<String>,
        context: DynamicVariableContext,
    ): Map<String, String>
}
```

---

### `DynamicVariableContext`
Encapsulates contextual data and services required by providers during variable resolution.

```kotlin
package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState

data class DynamicVariableContext(
    val effectiveAuthType: AuthType,
    val oauth2Config: OAuth2ResolvedConfig? = null,
    val oauth2TokenState: OAuth2TokenState = OAuth2TokenState(),
    val variableLayers: List<Map<String, String>> = emptyList(),
    val oauth2TokenClient: OAuth2TokenClient? = null,
    val onOAuth2TokenUpdated: ((accessToken: String, refreshToken: String?, idToken: String?, expiresAtEpochMs: Long?) -> Unit)? = null,
    val log: ((message: String, isError: Boolean) -> Unit)? = null,
)
```

---

### `OAuth2ResolvedConfig`
Resolved OAuth2 parameters required for token fetching or refresh.

```kotlin
package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow

data class OAuth2ResolvedConfig(
    val flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val tokenUrl: String = "",
    val refreshTokenUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val credentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
    val username: String = "",
    val password: String = "",
    val scope: String? = null,
)
```

---

### `OAuth2DynamicVariableProvider`
Concrete provider handling `_auth.oauth2.accessToken`.

```kotlin
package io.github.makeforge.clapi.core.network.dynamicvar

class OAuth2DynamicVariableProvider : DynamicVariableProvider {
    override val name: String = "OAuth2TokenProvider"

    override fun supports(token: String): Boolean =
        token == ACCESS_TOKEN_VARIABLE

    override suspend fun resolve(
        tokens: Set<String>,
        context: DynamicVariableContext,
    ): Map<String, String> {
        // 1. Verify effective auth is OAuth2
        if (context.effectiveAuthType != AuthType.OAUTH2) {
            throw DynamicVariableResolutionException(
                "Variable '{{$ACCESS_TOKEN_VARIABLE}}' requires OAuth2 authentication to be enabled on the request or parent collection."
            )
        }

        // 2. Reuse active valid token if available
        if (context.oauth2TokenState.isValid()) {
            return mapOf(ACCESS_TOKEN_VARIABLE to context.oauth2TokenState.accessToken.orEmpty())
        }

        // 3. Otherwise fetch or refresh using oauth2TokenClient and oauth2Config
        // 4. Invoke onOAuth2TokenUpdated callback with newly acquired token details
        // 5. Return mapOf(ACCESS_TOKEN_VARIABLE to newAccessToken)
    }

    companion object {
        const val ACCESS_TOKEN_VARIABLE = "_auth.oauth2.accessToken"
    }
}
```

---

### `DynamicVariableRegistry`
Central coordinator for dynamic variable resolution.

```kotlin
package io.github.makeforge.clapi.core.network.dynamicvar

class DynamicVariableRegistry(
    providers: List<DynamicVariableProvider> = listOf(OAuth2DynamicVariableProvider()),
) {
    private val _providers = providers.toMutableList()
    val providers: List<DynamicVariableProvider> get() = _providers

    fun register(provider: DynamicVariableProvider) {
        _providers.add(provider)
    }

    suspend fun resolveDynamicVariables(
        texts: Iterable<String>,
        context: DynamicVariableContext,
    ): Map<String, String> {
        // Extracts all {{ token }} occurrences across texts
        // Groups tokens by supporting provider
        // Calls provider.resolve(...) and aggregates returned maps
    }
}
```

---

## 2. UI State Extensions (`ui-shared`)

### `GrpcEditorTab`
Updated to include the `AUTH` tab for gRPC requests:

```kotlin
enum class GrpcEditorTab(val label: String) {
    MESSAGE("Message"),
    HEADERS("Headers"),
    AUTH("Auth"),
    SCRIPTS("Scripts"),
}
```

### State Mapping & Lifetime
1. When `tab.authType == AuthType.INHERIT`, `state.resolveInheritedAuth(tab)` inspects the parent folder and collection nodes to resolve effective OAuth2 settings for both HTTP and gRPC tabs.
2. When tokens are acquired via `OAuth2DynamicVariableProvider`, `context.onOAuth2TokenUpdated` writes `accessToken`, `refreshToken`, `idToken`, and `expiresAtEpochMs` directly to `tab.authOAuth2*` fields.
3. The resolved values are purely runtime in-memory session states and are not written to serialized collection files on disk.
