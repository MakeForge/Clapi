package io.github.crustack.clapi.core.storage.importers

import io.github.crustack.clapi.core.model.importers.ImportScanResult

expect class OpenApiImporter() : SpecificationImporter {
    override suspend fun scanContent(rawContent: String): ImportScanResult
}
