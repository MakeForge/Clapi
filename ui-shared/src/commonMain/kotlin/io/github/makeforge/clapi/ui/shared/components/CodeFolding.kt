// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components

import androidx.compose.runtime.Composable
import io.github.makeforge.clapi.editor.core.LanguageMode
import io.github.makeforge.clapi.editor.ui.FoldRegion as EditorFoldRegion
import io.github.makeforge.clapi.editor.ui.VisibleLine as EditorVisibleLine
import io.github.makeforge.clapi.editor.ui.FoldState as EditorFoldState
import io.github.makeforge.clapi.editor.ui.rememberFoldState as editorRememberFoldState
import io.github.makeforge.clapi.editor.ui.detectFoldRegions as editorDetectFoldRegions
import io.github.makeforge.clapi.editor.ui.detectBraceFoldRegions as editorDetectBraceFoldRegions
import io.github.makeforge.clapi.editor.ui.detectXmlFoldRegions as editorDetectXmlFoldRegions
import io.github.makeforge.clapi.editor.ui.detectCommentFoldRegions as editorDetectCommentFoldRegions
import io.github.makeforge.clapi.editor.ui.computeVisibleLines as editorComputeVisibleLines

// ── Data structures (backward-compatible re-exports) ────────────

/**
 * Backward-compatible type alias for [EditorFoldRegion].
 * New code should use [io.github.makeforge.clapi.editor.ui.FoldRegion] directly.
 */
typealias FoldRegion = EditorFoldRegion

/**
 * Backward-compatible type alias for [EditorVisibleLine].
 * New code should use [io.github.makeforge.clapi.editor.ui.VisibleLine] directly.
 */
typealias VisibleLine = EditorVisibleLine

/**
 * Backward-compatible type alias for [EditorFoldState].
 * New code should use [io.github.makeforge.clapi.editor.ui.FoldState] directly.
 */
typealias FoldState = EditorFoldState

@Composable
fun rememberFoldState(): FoldState = editorRememberFoldState()

// ── Region detection (delegates to editor-ui) ───────────────────

fun detectBraceFoldRegions(lines: List<String>): List<FoldRegion> =
    editorDetectBraceFoldRegions(lines)

fun detectXmlFoldRegions(lines: List<String>): List<FoldRegion> =
    editorDetectXmlFoldRegions(lines)

fun detectCommentFoldRegions(lines: List<String>): List<FoldRegion> =
    editorDetectCommentFoldRegions(lines)

fun detectFoldRegions(lines: List<String>, language: SyntaxLanguage): List<FoldRegion> =
    editorDetectFoldRegions(lines, language.toLanguageMode())

// ── Visible-line computation (delegates to editor-ui) ───────────

fun computeVisibleLines(
    lines: List<String>,
    foldRegions: List<FoldRegion>,
    foldState: FoldState,
): List<VisibleLine> = editorComputeVisibleLines(lines, foldRegions, foldState)
