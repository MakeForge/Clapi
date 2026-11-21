package io.github.makeforge.clapi.core.model.opencollection

import io.github.makeforge.clapi.core.model.McpConnectionConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class McpConnectionConfigDto(
    val id: String = "",
    val name: String = "",
    val config: McpConnectionConfig = McpConnectionConfig()
)

@Serializable
data class ClapiExtensionData(
    val mcp: List<McpConnectionConfigDto> = emptyList(),
    val authType: String? = null
)

// YAML scalars such as "8080" or "true" come back as numbers/booleans after a round-trip,
// so the typed decode must accept them as strings.
private val clapiExtensionJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

fun Map<String, JsonElement>.getClapiExtensionData(json: Json = clapiExtensionJson): ClapiExtensionData? {
    val element = this["clapi"] ?: return null
    return try {
        json.decodeFromJsonElement<ClapiExtensionData>(element)
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.withClapiExtensionData(
    clapiData: ClapiExtensionData,
    json: Json = clapiExtensionJson
): Map<String, JsonElement> {
    val element = json.encodeToJsonElement(clapiData)
    return this + ("clapi" to element)
}
