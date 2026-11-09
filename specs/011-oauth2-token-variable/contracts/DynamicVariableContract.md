# Contract: Dynamic Variable Provider Interface & Resolution

**Package**: `io.github.crustack.clapi.core.network.dynamicvar`

---

## 1. Provider Registration Contract

```kotlin
interface DynamicVariableProvider {
    val name: String
    fun supports(token: String): Boolean
    suspend fun resolve(tokens: Set<String>, context: DynamicVariableContext): Map<String, String>
}
```

### Invariants & Guarantees
1. **Token Identification**:
   - `supports(token)` must return `true` if and only if the provider handles the exact variable token name (or token prefix).
   - Dynamic variable tokens omit outer double curly braces `{{...}}`. For example, for template `{{_auth.oauth2.accessToken}}`, the candidate token is `_auth.oauth2.accessToken`.
2. **Context Resolution**:
   - Providers receive a read-only `DynamicVariableContext` carrying effective authentication state and access to active variable layers for nested template expansion.
   - If a provider encounters an unrecoverable condition (e.g. required auth is disabled, token acquisition returns network 401/500, or required credentials are empty), it MUST throw a `DynamicVariableResolutionException`.
   - The caller MUST catch `DynamicVariableResolutionException`, halt execution, populate `tab.lastError`, and cancel the dispatch of the underlying request.
3. **State Side Effects**:
   - When a provider mutates or retrieves cached session state (such as fetching a new OAuth2 access token and refresh token), it invokes `context.onOAuth2TokenUpdated(...)` so the host application UI and request tab stay synchronized.

---

## 2. Dynamic Variable Registry Contract

```kotlin
class DynamicVariableRegistry(
    providers: List<DynamicVariableProvider> = listOf(OAuth2DynamicVariableProvider())
) {
    fun register(provider: DynamicVariableProvider)
    fun unregister(provider: DynamicVariableProvider)
    suspend fun resolveDynamicVariables(
        texts: Iterable<String>,
        context: DynamicVariableContext,
    ): Map<String, String>
}
```

### Resolution Flow
1. **Scan**: Extracts all variable placeholders from the provided `texts` using the standard token pattern `\{\{\s*([^{}]+?)\s*\}\}`.
2. **Match**: Maps each extracted token to the first registered provider where `provider.supports(token) == true`.
3. **Resolve**: For each provider with matched tokens, asynchronously calls `provider.resolve(matchedTokens, context)`.
4. **Aggregate**: Returns an aggregated map of `token -> resolvedValue`. If no dynamic tokens match registered providers, returns an empty map immediately without delay.
