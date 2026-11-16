package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult

interface SpecificationImporter {
    /**
     * Inspects and parses [rawContent] as either OpenCollection YAML or OpenAPI v3 (JSON/YAML).
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

    override suspend fun scanContent(rawContent: String): ImportScanResult {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return ImportScanResult.Failure("Input content is empty.")
        }

        if (insomniaImporter.canHandle(trimmed)) {
            return insomniaImporter.scanContent(rawContent)
        }

        if (trimmed.contains("swagger:") || trimmed.contains("\"swagger\":") || trimmed.contains("'swagger':")) {
            return ImportScanResult.Failure("Swagger 2.0 is not supported. Please provide an OpenAPI v3.0+ specification.")
        }

        if (trimmed.contains("opencollection:") || trimmed.contains("\"opencollection\":")) {
            val result = openCollectionImporter.scanContent(rawContent)
            if (result is ImportScanResult.Success) {
                return result
            }
        }

        val openApiResult = openApiImporter.scanContent(rawContent)
        if (openApiResult is ImportScanResult.Success) {
            return openApiResult
        }

        if (trimmed.contains("opencollection")) {
            return openCollectionImporter.scanContent(rawContent)
        }

        return openApiResult
    }
}
