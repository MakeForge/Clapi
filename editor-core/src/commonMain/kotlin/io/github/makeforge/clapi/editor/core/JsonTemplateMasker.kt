package io.github.makeforge.clapi.editor.core

import io.github.makeforge.clapi.core.model.opencollection.VariableType

internal object JsonTemplateMasker {

    private val BUILT_IN_STRING_VARS = setOf(
        "isotimestamp",
        "guid",
        "uuid",
        "randomalphanumeric",
    )

    data class MaskedJsonResult(
        val maskedText: String,
        val templateDiagnostics: List<InlineEditorError>,
    )

    /**
     * Scans [text] for unquoted `{{...}}` template tokens outside string literals and comments.
     *
     * Replaces each unquoted token spanning `[start, endExclusive)` with `'0'` followed by spaces
     * of the exact same length so character offsets, line numbers, and column positions for the
     * rest of the document remain 100% unchanged.
     *
     * Also emits an [InlineEditorError] if an unquoted variable is known to have [VariableType.STRING].
     */
    fun maskAndCheckVariables(
        text: String,
        allowJson5: Boolean,
        variableTypes: Map<String, VariableType> = emptyMap(),
    ): MaskedJsonResult {
        if (!text.contains("{{")) {
            return MaskedJsonResult(text, emptyList())
        }

        val chars = text.toCharArray()
        val diagnostics = mutableListOf<InlineEditorError>()

        var i = 0
        var line = 1
        var col = 1
        var inString: Char? = null
        var escaped = false
        var inLineComment = false
        var inBlockComment = false

        while (i < chars.size) {
            val ch = chars[i]

            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false
                    line++
                    col = 1
                } else {
                    col++
                }
                i++
                continue
            }

            if (inBlockComment) {
                if (ch == '*' && i + 1 < chars.size && chars[i + 1] == '/') {
                    inBlockComment = false
                    col += 2
                    i += 2
                    continue
                }
                if (ch == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
                i++
                continue
            }

            if (inString != null) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == inString) {
                    inString = null
                }
                if (ch == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
                i++
                continue
            }

            if (allowJson5 && ch == '/' && i + 1 < chars.size) {
                if (chars[i + 1] == '/') {
                    inLineComment = true
                    col += 2
                    i += 2
                    continue
                } else if (chars[i + 1] == '*') {
                    inBlockComment = true
                    col += 2
                    i += 2
                    continue
                }
            }

            if (ch == '"' || (allowJson5 && ch == '\'')) {
                inString = ch
                col++
                i++
                continue
            }

            if (ch == '{' && i + 1 < chars.size && chars[i + 1] == '{') {
                val closeIdx = findTemplateTokenEnd(text, i + 2)
                if (closeIdx > i + 2) {
                    val endExclusive = closeIdx + 2
                    val rawToken = text.substring(i + 2, closeIdx).trim()
                    if (rawToken.isNotEmpty()) {
                        chars[i] = '0'
                        for (k in (i + 1) until endExclusive) {
                            chars[k] = ' '
                        }

                        val varName = rawToken.substringBefore('(').trim()
                        if (isStringTypedVariable(varName, rawToken, variableTypes)) {
                            diagnostics += InlineEditorError(
                                line = line,
                                col = col,
                                message = "Variable '$varName' is typed as String; unquoted syntax generates invalid JSON. Wrap in quotes: \"{{$rawToken}}\"",
                                severity = InlineErrorSeverity.WARNING,
                            )
                        }

                        val tokenLen = endExclusive - i
                        col += tokenLen
                        i = endExclusive
                        continue
                    }
                }
            }

            if (ch == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            i++
        }

        return MaskedJsonResult(chars.concatToString(), diagnostics)
    }

    /**
     * Finds the index of the closing `}}` on the same line starting from [contentStart],
     * or returns -1 if none is found or if nested braces/newlines are encountered.
     */
    internal fun findTemplateTokenEnd(text: String, contentStart: Int): Int {
        var j = contentStart
        while (j + 1 < text.length) {
            val c = text[j]
            if (c == '\n' || c == '\r' || c == '{') return -1
            if (c == '}') {
                return if (text[j + 1] == '}') j else -1
            }
            j++
        }
        return -1
    }

    private fun isStringTypedVariable(
        varName: String,
        rawToken: String,
        variableTypes: Map<String, VariableType>,
    ): Boolean {
        val normBuiltIn = varName.removePrefix("$").lowercase()
        if (normBuiltIn in BUILT_IN_STRING_VARS || varName == "_auth.oauth2.accessToken" || varName.startsWith("_secret.")) {
            return true
        }
        val declared = variableTypes[varName] ?: variableTypes[rawToken]
        return declared == VariableType.STRING
    }
}
