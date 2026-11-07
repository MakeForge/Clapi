package io.github.crustack.clapi.editor.ui

import io.github.crustack.clapi.editor.core.JsonMode
import io.github.crustack.clapi.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSyntaxHighlighterTest {

    @Test
    fun mode_always_matches_the_provider() {
        val highlighter = DefaultSyntaxHighlighter(JsonMode)
        assertEquals(LanguageMode.JSON, highlighter.mode)
    }
}
