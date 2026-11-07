package io.github.crustack.core.model.opencollection

import io.github.crustack.core.model.McpConnectionConfig
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
    val authType: String? = null,
    val legacyFields: Map<String, JsonElement> = emptyMap()
)

fun Map<String, JsonElement>.getClapiExtensionData(json: Json = Json): ClapiExtensionData? {
    val element = this["clapi"] ?: return null
    return try {
        json.decodeFromJsonElement<ClapiExtensionData>(element)
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.withClapiExtensionData(
    clapiData: ClapiExtensionData,
    json: Json = Json
): Map<String, JsonElement> {
    val element = json.encodeToJsonElement(clapiData)
    return this + ("clapi" to element)
}
