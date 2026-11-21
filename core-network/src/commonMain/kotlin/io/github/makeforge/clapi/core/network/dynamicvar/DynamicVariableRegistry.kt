package io.github.makeforge.clapi.core.network.dynamicvar

class DynamicVariableRegistry(
    providers: List<DynamicVariableProvider> = defaultProviders(),
) {
    private val _providers = mutableListOf<DynamicVariableProvider>().apply {
        addAll(providers)
    }

    val providers: List<DynamicVariableProvider>
        get() = _providers.toList()

    fun register(provider: DynamicVariableProvider) {
        synchronized(_providers) {
            _providers.removeAll { it.name == provider.name }
            _providers.add(provider)
        }
    }

    fun unregister(provider: DynamicVariableProvider) {
        synchronized(_providers) {
            _providers.removeAll { it.name == provider.name }
        }
    }

    fun unregisterByName(name: String) {
        synchronized(_providers) {
            _providers.removeAll { it.name == name }
        }
    }

    fun extractTokens(texts: Iterable<String>): Set<String> {
        val tokens = mutableSetOf<String>()
        for (text in texts) {
            if (text.isBlank()) continue
            TOKEN_REGEX.findAll(text).forEach { match ->
                val token = match.groupValues[1].trim()
                if (token.isNotEmpty()) {
                    tokens.add(token)
                }
            }
        }
        return tokens
    }

    suspend fun resolveDynamicVariables(
        texts: Iterable<String>,
        context: DynamicVariableContext,
    ): Map<String, String> {
        val tokens = extractTokens(texts)
        if (tokens.isEmpty()) return emptyMap()

        val resolved = mutableMapOf<String, String>()
        val currentProviders = providers

        for (provider in currentProviders) {
            val supportedTokens = tokens.filter { provider.supports(it) }.toSet()
            if (supportedTokens.isNotEmpty()) {
                val providerResults = provider.resolve(supportedTokens, context)
                resolved.putAll(providerResults)
            }
        }

        return resolved
    }

    companion object {
        fun defaultProviders(): List<DynamicVariableProvider> = listOf(
            OAuth2DynamicVariableProvider(),
            UserScriptDynamicVariableProvider(),
        )

        private val TOKEN_REGEX = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")
    }
}
