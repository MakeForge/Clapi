package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

interface SpecificationImporter {
    /**
     * Inspects and parses [rawContent] as OpenCollection (JSON/YAML), OpenAPI v3 (JSON/YAML), or Insomnia v5.
     * Returns [ImportScanResult.Success] on valid content or [ImportScanResult.Failure] with diagnostic messages.
     */
    suspend fun scanContent(rawContent: String): ImportScanResult

    companion object {
        fun default(): SpecificationImporter = CompositeSpecificationImporter()
    }
}

class CompositeSpecificationImporter(
    private val openApiImporter: OpenApiImporter = OpenApiImporter(),
    private val openCollectionImporter: OpenCollectionImporter = OpenCollectionImporter(),
    private val insomniaImporter: InsomniaImporter = InsomniaImporter.default(),
) : SpecificationImporter {

    private companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
        private val OPENCOLLECTION_YAML_REGEX = Regex("""(?m)^\s*['"]?opencollection['"]?\s*:""")
        private val OPENCOLLECTION_JSON_REGEX = Regex(""""opencollection"\s*:""")
        private val OPENAPI_YAML_REGEX = Regex("""(?m)^\s*['"]?openapi['"]?\s*:""")
        private val OPENAPI_JSON_REGEX = Regex(""""openapi"\s*:""")
    }

    override suspend fun scanContent(rawContent: String): ImportScanResult {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return ImportScanResult.Failure("Input content is empty.")
        }

        if (insomniaImporter.canHandle(trimmed)) {
            return insomniaImporter.scanContent(rawContent)
        }

        val legacyClapiType = detectLegacyClapiType(trimmed)
        if (legacyClapiType != null) {
            return ImportScanResult.Failure(
                "Legacy format '$legacyClapiType' is no longer supported. Please import an OpenCollection (v1.0.0), OpenAPI v3, or Insomnia v5 file."
            )
        }

        if (trimmed.contains("swagger:") || trimmed.contains("\"swagger\":") || trimmed.contains("'swagger':")) {
            return ImportScanResult.Failure("Swagger 2.0 is not supported. Please provide an OpenAPI v3.0+ specification.")
        }

        if (OPENCOLLECTION_YAML_REGEX.containsMatchIn(trimmed) || OPENCOLLECTION_JSON_REGEX.containsMatchIn(trimmed)) {
            return openCollectionImporter.scanContent(rawContent)
        }

        val looksLikeOpenApi = OPENAPI_YAML_REGEX.containsMatchIn(trimmed) || OPENAPI_JSON_REGEX.containsMatchIn(trimmed)
        val openApiResult = openApiImporter.scanContent(rawContent)
        if (openApiResult is ImportScanResult.Success) {
            return openApiResult
        }

        if (trimmed.contains("opencollection")) {
            return openCollectionImporter.scanContent(rawContent)
        }

        if (looksLikeOpenApi) {
            return openApiResult
        }

        return ImportScanResult.Failure(
            message = "Unrecognized specification format. Expected an OpenCollection (v1.0.0), OpenAPI v3, or Insomnia v5 file.",
            details = (openApiResult as? ImportScanResult.Failure)?.details ?: emptyList(),
        )
    }

    private fun detectLegacyClapiType(trimmed: String): String? {
        if (!trimmed.startsWith("{")) return null
        val root = runCatching { lenientJson.parseToJsonElement(trimmed) as? JsonObject }.getOrNull() ?: return null
        val rootType = root["type"]?.jsonPrimitive?.contentOrNull
        return if (rootType == "clapiCollection" || rootType == "clapiWorkspace") rootType else null
    }
}
