package io.github.makeforge.clapi.core.model.importers

import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.FolderItem

/**
 * Indicates the origin of the import payload.
 */
enum class ImportSourceType {
    FILE,
    CLIPBOARD,
}

/**
 * Encapsulates the staged source before scanning.
 */
data class ImportSourceState(
    val type: ImportSourceType = ImportSourceType.FILE,
    val fileName: String? = null,
    val rawContent: String? = null,
) {
    /**
     * True if FILE mode has loaded non-blank content or CLIPBOARD mode has non-empty text.
     */
    val isValidScanSource: Boolean
        get() = when (type) {
            ImportSourceType.FILE -> !rawContent.isNullOrBlank()
            ImportSourceType.CLIPBOARD -> !rawContent.isNullOrBlank()
        }
}

/**
 * Supported specification formats.
 */
enum class SpecFormat {
    OPENAPI_V3,
    OPENCOLLECTION,
}

/**
 * Controls the current step within the import dialog workflow.
 */
enum class ImportDialogStep {
    SOURCE_SELECTION,
    ITEM_SELECTION,
}

/**
 * Represents a parsed folder candidate discovered during the scanning process.
 */
data class ImportCandidateFolder(
    val id: String,
    val originalName: String,
    val name: String,
    val folder: FolderItem,
    val requestCount: Int,
    val selected: Boolean = true,
    val hasConflict: Boolean = false,
)

/**
 * Represents a parsed environment candidate discovered during scanning.
 */
data class ImportCandidateEnvironment(
    val id: String,
    val originalName: String,
    val name: String,
    val environment: Environment,
    val variableCount: Int,
    val selected: Boolean = true,
    val hasConflict: Boolean = false,
)

/**
 * Structured result returned after parsing and validating a raw specification.
 */
sealed interface ImportScanResult {
    data class Success(
        val format: SpecFormat,
        val specTitle: String,
        val folders: List<ImportCandidateFolder>,
        val environments: List<ImportCandidateEnvironment> = emptyList(),
    ) : ImportScanResult

    data class Failure(
        val message: String,
        val details: List<String> = emptyList(),
    ) : ImportScanResult
}
