package io.github.makeforge.clapi.editor.core

enum class InlineErrorSeverity {
    ERROR,
    WARNING,
}

data class InlineEditorError(
    val line: Int,
    val col: Int,
    val message: String,
    val severity: InlineErrorSeverity = InlineErrorSeverity.ERROR,
)
