package io.github.makeforge.clapi.core.network

private val variablePattern = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")

object VariableResolver {
    val dynamicVariables: List<DynamicVariable> = listOf(
        TimestampDynamicVariable,
        IsoTimestampDynamicVariable,
        RandomIntDynamicVariable,
        RandomIntRangeDynamicVariable,
        GuidDynamicVariable,
    )

    fun resolve(
        value: String,
        variableLayers: List<Map<String, String>>,
        removeUnresolved: Boolean = false,
    ): String {
        if (value.isBlank()) {
            return value
        }

        return variablePattern.replace(value) { matchResult ->
            val token = matchResult.groupValues[1].trim()
            val fromLayers = variableLayers.firstNotNullOfOrNull { layer -> layer[token] }
            fromLayers ?: resolveDynamicToken(token) ?: if (removeUnresolved) "" else matchResult.value
        }
    }

    fun resolveDynamicToken(token: String): String? {
        val trimmed = token.trim()
        for (handler in dynamicVariables) {
            if (handler.matches(trimmed)) {
                val resolved = handler.resolve(trimmed)
                if (resolved != null) return resolved
            }
        }
        return null
    }

    fun isDynamicToken(token: String): Boolean {
        val trimmed = token.trim()
        return dynamicVariables.any { it.matches(trimmed) }
    }
}
