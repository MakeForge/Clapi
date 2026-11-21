package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult

expect class OpenApiImporter() : SpecificationImporter {
    override suspend fun scanContent(rawContent: String): ImportScanResult
}
