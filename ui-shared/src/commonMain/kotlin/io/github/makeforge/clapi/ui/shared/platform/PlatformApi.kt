package io.github.makeforge.clapi.ui.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.CoroutineDispatcher

data class PickedBinaryFile(
    val name: String,
    val base64Content: String,
)

/**
 * Represents a `.proto` file discovered inside a user-selected proto folder.
 */
data class DiscoveredProtoFile(
    val relativePath: String,
    val absolutePath: String,
)

// ── Identity / Time ─────────────────────────────────────────────

/** Platform-safe UUID generation. */
expect fun generateUuid(): String

/** Platform-safe epoch milliseconds. */
expect fun currentTimeMillis(): Long

// ── Clipboard ───────────────────────────────────────────────────

/** Copy [text] to the system clipboard. */
expect fun copyToClipboard(text: String)

/**
 * Read plain text from the system clipboard synchronously.
 * Returns null if the clipboard has no text content or is unavailable.
 * On desktop this accesses the AWT Toolkit clipboard on the calling thread —
 * callers must ensure this is called from a background coroutine dispatcher.
 */
expect fun readFromClipboard(): String?

// ── Formatting ──────────────────────────────────────────────────

/** Format an epoch-millis timestamp as "HH:mm:ss" local time. */
expect fun formatTimestamp(epochMillis: Long): String

// ── Coroutine dispatchers ───────────────────────────────────────

/** IO dispatcher (Dispatchers.IO on JVM). */
expect val ioDispatcher: CoroutineDispatcher

// ── Pointer icons ───────────────────────────────────────────────

/** Horizontal resize (col-resize) cursor. */
expect val horizontalResizeCursor: PointerIcon

/** Vertical resize (row-resize) cursor. */
expect val verticalResizeCursor: PointerIcon

/** Diagonal resize cursor running from north-west to south-east. */
expect val nwseResizeCursor: PointerIcon

/** Diagonal resize cursor running from north-east to south-west. */
expect val neswResizeCursor: PointerIcon

/**
 * H-5 fix: Platform-specific modifier that applies the correct CSS resize cursor
 * where PointerIcon.Default cannot be replaced with a CSS cursor type.
 * On desktop this is a no-op because [horizontalResizeCursor]/[verticalResizeCursor]
 * already map to the correct AWT cursor via [pointerHoverIcon].
 */
expect fun Modifier.platformResizeCursorStyle(isHorizontal: Boolean): Modifier

/** Web fallback for diagonal CSS resize cursors; a no-op on desktop. */
expect fun Modifier.platformDiagonalResizeCursorStyle(isNwSe: Boolean): Modifier

/**
 * Key used by Compose semantics to allow UI tests and platform integrations
 * to drop a file into a dropzone.
 */
val FileDropSemanticsKey = androidx.compose.ui.semantics.SemanticsPropertyKey<(fileName: String, content: String) -> Unit>("FileDropAction")

/**
 * Modifier that attaches native file drag-and-drop listener to the drop target.
 * When files are dropped, supported files (.json, .yaml, .yml) are read and delivered to [onFileDropped].
 */
@Composable
expect fun Modifier.fileDropTarget(
    onFileDropped: (fileName: String, content: String) -> Unit,
): Modifier

// ── File I/O ────────────────────────────────────────────────────

/**
 * Pick a file from the filesystem and deliver its file name and text content to [onResult].
 * On desktop this opens a JFileChooser starting at the last imported/exported
 * folder (user home on first use). On web it triggers an `<input type=file>`;
 * browsers do not allow setting that dialog's start directory.
 */
expect fun pickFileForImport(onResult: (fileName: String, content: String) -> Unit)

fun pickFileForImport(onResult: (String) -> Unit) = pickFileForImport { _, content -> onResult(content) }

/**
 * Pick any binary/text file from the filesystem and deliver filename + base64 bytes.
 */
expect fun pickBinaryFileForRequest(onResult: (PickedBinaryFile) -> Unit)

/**
 * Pick a `.proto` file from the filesystem and deliver its absolute file path to [onResult].
 */
expect fun pickProtoFile(onResult: (filePath: String) -> Unit)

/**
 * Pick one or more `.proto` files from the filesystem and deliver their absolute file paths to [onResult].
 */
expect fun pickProtoFiles(onResult: (filePaths: List<String>) -> Unit)

/**
 * Pick a root directory containing `.proto` files and deliver its absolute path to [onResult].
 */
expect fun pickProtoDirectory(onResult: (dirPath: String) -> Unit)

/**
 * Recursively scan [dirPath] for `.proto` files (case-insensitive) and return them sorted
 * by normalized `/` relative path. Returns an empty list if [dirPath] does not exist or is not a directory.
 */
expect fun listProtoFilesInDirectory(dirPath: String): List<DiscoveredProtoFile>

/**
 * Open a file with the system default viewer or application.
 */
expect fun openFileInDefaultApp(filePath: String)

/**
 * Open a URL in the system default web browser.
 */
expect fun openUrlInBrowser(url: String)

/**
 * Returns true when running in local development mode (e.g. `./gradlew :ui-desktop:jvmDev`).
 */
expect fun isLocalDevMode(): Boolean

/**
 * Optional explicit override for the documentation site base URL (e.g. via system property or env var).
 */
expect fun platformDocsBaseUrlOverride(): String?

/**
 * Save [content] as a file with the given [defaultFilename].
 * On desktop this opens a save dialog; on web it triggers a browser download.
 */
expect fun saveFileForExport(content: String, defaultFilename: String)

/**
 * Prompt user to select a directory and deliver its absolute path to [onResult].
 */
expect fun pickDirectory(onResult: (directoryPath: String) -> Unit)

/**
 * Returns true if [event] on [targetId] corresponds to a double click.
 */
expect fun isPointerDoubleClick(targetId: String, event: androidx.compose.ui.input.pointer.PointerEvent): Boolean

/**
 * Resets synthetic pointer double-click tracking state (e.g. on drag start or screen mount/dispose).
 */
expect fun resetPointerDoubleClickState()

/**
 * Flag used in Compose unit tests to simulate a double-click when AWT mouse events are not present.
 */
expect var testDoubleClickActive: Boolean

// ── Key Event Handling ──────────────────────────────────────────

/**
 * Handles Ctrl+Space / Cmd+Space shortcut for variable dropdown, consuming native AWT
 * events so that no whitespace character is typed into text components.
 *
 * @param event The Compose [androidx.compose.ui.input.key.KeyEvent].
 * @param onTrigger Action to execute when the shortcut is triggered (on key down).
 * @return True if the event was a Ctrl+Space event (down, typed, or up) and was consumed.
 */
expect fun handleCtrlSpaceKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    onTrigger: () -> Unit,
): Boolean

/** Returns true if a Ctrl+Space shortcut was triggered recently within [thresholdMs]. */
expect fun isRecentCtrlSpaceTriggered(thresholdMs: Long = 2500L): Boolean

/** Resets the recent Ctrl+Space trigger tracking. */
expect fun resetCtrlSpaceTrigger()

// ── Storage ─────────────────────────────────────────────────────

/** Simple key-value string storage (Preferences on JVM, localStorage on web). */
expect object PlatformStorage {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)

    /**
     * Stores [value] securely encrypted using AES-GCM and platform-native key custody.
     * Guaranteed safe for tokens, passphrases, and credentials.
     */
    fun putSecretString(key: String, value: String)

    /**
     * Retrieves and decrypts the secret previously stored under [key].
     * Returns null if the key does not exist or was cleared.
     */
    fun getSecretString(key: String): String?
}
