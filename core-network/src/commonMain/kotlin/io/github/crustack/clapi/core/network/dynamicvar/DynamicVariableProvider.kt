package io.github.crustack.clapi.core.network.dynamicvar

interface DynamicVariableProvider {
    /**
     * Human-readable identifier for this provider (e.g., "OAuth2TokenProvider").
     */
    val name: String

    /**
     * Determines whether this provider can resolve the given token (without outer `{{...}}`).
     */
    fun supports(token: String): Boolean

    /**
     * Resolves the supported tokens against the provided execution context.
     * @return Map of token string to resolved runtime value.
     * @throws DynamicVariableResolutionException if resolution fails.
     */
    suspend fun resolve(
        tokens: Set<String>,
        context: DynamicVariableContext,
    ): Map<String, String>
}
