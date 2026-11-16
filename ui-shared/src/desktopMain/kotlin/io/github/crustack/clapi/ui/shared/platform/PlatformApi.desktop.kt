package io.github.crustack.clapi.ui.shared.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FilenameFilter
import java.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.prefs.Preferences
import eu.anifantakis.lib.ksafe.KSafe

actual fun generateUuid(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

actual fun readFromClipboard(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor)
        data as? String
    } catch (_: Exception) {
        null
    }
}

actual fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss")
    return sdf.format(Date(epochMillis))
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual val horizontalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual val verticalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))

actual val nwseResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))

actual val neswResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.SW_RESIZE_CURSOR))

/**
 * H-5: Desktop already applies the correct cursor via `pointerHoverIcon(horizontalResizeCursor)`.
 * This modifier is a no-op on desktop.
 */
actual fun Modifier.platformResizeCursorStyle(isHorizontal: Boolean): Modifier = this

actual fun Modifier.platformDiagonalResizeCursorStyle(isNwSe: Boolean): Modifier = this

actual fun pickFileForImport(onResult: (fileName: String, content: String) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Import Specification", FileDialog.LOAD).apply {
        applyRememberedDirectory()
        filenameFilter = FilenameFilter { _, name ->
            setOf("json", "yaml", "yml").any { ext -> name.endsWith(".$ext", ignoreCase = true) }
        }
        isVisible = true
    }

    val selectedDir = dialog.directory
    val selectedFile = dialog.file

    if (selectedDir != null && selectedFile != null) {
        val selected = File(selectedDir, selectedFile)
        rememberChooserDirectory(selected)
        runCatching { selected.readText() }
            .onSuccess { content -> onResult(selected.name, content) }
    }
}

actual fun pickBinaryFileForRequest(onResult: (PickedBinaryFile) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Select File", FileDialog.LOAD).apply {
        applyRememberedDirectory()
        isVisible = true
    }
    val selectedDir = dialog.directory
    val selectedFile = dialog.file
    if (selectedDir != null && selectedFile != null) {
        val selected = File(selectedDir, selectedFile)
        rememberChooserDirectory(selected)
        runCatching { selected.readBytes() }
            .onSuccess { bytes ->
                val base64 = Base64.getEncoder().encodeToString(bytes)
                onResult(PickedBinaryFile(name = selected.name, base64Content = base64))
            }
    }
}

actual fun pickProtoFile(onResult: (filePath: String) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Select Proto File", FileDialog.LOAD).apply {
        applyRememberedDirectory()
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".proto", ignoreCase = true) }
        isVisible = true
    }
    val selectedDir = dialog.directory
    val selectedFile = dialog.file
    if (selectedDir != null && selectedFile != null) {
        val selected = File(selectedDir, selectedFile)
        rememberChooserDirectory(selected)
        onResult(selected.absolutePath)
    }
}

actual fun saveFileForExport(content: String, defaultFilename: String) {
    val dialog = FileDialog(null as Frame?, "Export", FileDialog.SAVE).apply {
        applyRememberedDirectory()
        file = defaultFilename
        val ext = defaultFilename.substringAfterLast('.', "").lowercase()
        if (ext == "yaml" || ext == "yml") {
            filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".yaml", ignoreCase = true) || name.endsWith(".yml", ignoreCase = true)
            }
        } else if (ext == "json") {
            filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".json", ignoreCase = true)
            }
        }
        isVisible = true
    }
    val selectedDir = dialog.directory
    val selectedFile = dialog.file
    if (selectedDir != null && selectedFile != null) {
        var file = File(selectedDir, selectedFile)
        val ext = defaultFilename.substringAfterLast('.', "").lowercase()
        if (ext == "yaml" || ext == "yml") {
            if (!file.name.endsWith(".yaml", ignoreCase = true) && !file.name.endsWith(".yml", ignoreCase = true)) {
                file = File(file.absolutePath + ".yaml")
            }
        } else if (ext == "json") {
            if (!file.name.endsWith(".json", ignoreCase = true)) {
                file = File(file.absolutePath + ".json")
            }
        }
        rememberChooserDirectory(file)
        runCatching { file.writeText(content) }
    }
}

private fun FileDialog.applyRememberedDirectory() {
    val path = FileChooserMemory.directoryToOpen(PlatformStorage.getString(FileChooserMemory.LAST_DIR_KEY)) { candidate ->
        File(candidate).isDirectory
    }
    if (path != null) directory = path
}

private fun rememberChooserDirectory(file: File) {
    val path = FileChooserMemory.directoryToRemember(file.absolutePath) { candidate ->
        File(candidate).isDirectory
    }
    if (path != null) PlatformStorage.putString(FileChooserMemory.LAST_DIR_KEY, path)
}

actual var testDoubleClickActive: Boolean = false

// ── Key Event Handling ──────────────────────────────────────────

private var lastDesktopCtrlSpaceMillis: Long = 0L

actual fun isRecentCtrlSpaceTriggered(thresholdMs: Long): Boolean {
    val elapsed = System.currentTimeMillis() - lastDesktopCtrlSpaceMillis
    return elapsed in 0..thresholdMs
}

actual fun resetCtrlSpaceTrigger() {
    lastDesktopCtrlSpaceMillis = 0L
}

actual fun handleCtrlSpaceKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    onTrigger: () -> Unit,
): Boolean {
    val awt = event.awtEventOrNull
    val isCtrlOrMeta = event.isCtrlPressed || event.isMetaPressed ||
            (awt != null && (awt.isControlDown || awt.isMetaDown))

    val isSpaceKey = event.key == androidx.compose.ui.input.key.Key.Spacebar ||
            awt?.keyCode == java.awt.event.KeyEvent.VK_SPACE

    // 1. KeyDown / KEY_PRESSED with Spacebar and Ctrl/Meta
    if (isCtrlOrMeta && !event.isAltPressed && !event.isShiftPressed && isSpaceKey) {
        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown ||
            awt?.id == java.awt.event.KeyEvent.KEY_PRESSED
        ) {
            awt?.consume()
            lastDesktopCtrlSpaceMillis = System.currentTimeMillis()
            onTrigger()
            return true
        }
        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyUp ||
            awt?.id == java.awt.event.KeyEvent.KEY_RELEASED
        ) {
            awt?.consume()
            return true
        }
    }

    // 2. AWT KEY_TYPED or Compose Unknown event with space character
    val isSpaceChar = event.utf16CodePoint == ' '.code || awt?.keyChar == ' '
    val recent = isRecentCtrlSpaceTriggered()

    if (isSpaceChar && (isCtrlOrMeta || recent)) {
        awt?.consume()
        resetCtrlSpaceTrigger()
        return true
    }

    return false
}

actual fun isPointerDoubleClick(targetId: String, event: PointerEvent): Boolean {
    val mouse = event.awtEventOrNull
    if (mouse != null) {
        return mouse.clickCount >= 2
    }
    return testDoubleClickActive
}

actual object PlatformStorage {
    private val prefs: Preferences =
        Preferences.userNodeForPackage(PlatformStorage::class.java)

    private val ksafe by lazy { KSafe(fileName = "clapi_secrets") }

    // Values larger than this are written to a file in ~/.clapi/pstore/ instead of being
    // chunked into Preferences entries.  Java Preferences flushes to an XML plist on every
    // write; storing MB-scale data there creates thousands of entries and freezes the UI.
    private const val FILE_THRESHOLD = 65_536  // 64 KB
    private val pstoreDir: File
        get() = File(System.getProperty("user.home"), ".clapi/pstore").also { it.mkdirs() }

    private fun fileForKey(key: String): File {
        val safeName = key.replace(".", "_").replace("/", "_")
        return File(pstoreDir, safeName)
    }

    actual fun putString(key: String, value: String) {
        if (value.length > FILE_THRESHOLD) {
            // Write to a plain file; mark it in Preferences so getString knows where to look.
            fileForKey(key).writeText(value)
            prefs.putBoolean("${key}__isFile", true)
            // Remove any stale chunked or direct entry.
            val oldChunks = prefs.getInt("${key}__chunks", -1)
            if (oldChunks > 0) {
                for (i in 0 until oldChunks) prefs.remove("${key}__$i")
                prefs.remove("${key}__chunks")
            }
            prefs.remove(key)
        } else {
            // Remove any file-backed entry that may have existed before.
            if (prefs.getBoolean("${key}__isFile", false)) {
                fileForKey(key).delete()
                prefs.remove("${key}__isFile")
            }
            // Preferences has a max value size of ~8 KB, so split moderate values.
            if (value.length > 7000) {
                val chunks = value.chunked(7000)
                prefs.putInt("${key}__chunks", chunks.size)
                chunks.forEachIndexed { i, chunk -> prefs.put("${key}__$i", chunk) }
            } else {
                val oldChunks = prefs.getInt("${key}__chunks", -1)
                if (oldChunks > 0) {
                    for (i in 0 until oldChunks) prefs.remove("${key}__$i")
                    prefs.remove("${key}__chunks")
                }
                prefs.put(key, value)
            }
        }
        prefs.flush()
    }

    actual fun getString(key: String): String? {
        // File-backed large value?
        if (prefs.getBoolean("${key}__isFile", false)) {
            val f = fileForKey(key)
            return if (f.exists()) f.readText() else null
        }
        // Chunked moderate value?
        val chunkCount = prefs.getInt("${key}__chunks", -1)
        return if (chunkCount > 0) {
            buildString { for (i in 0 until chunkCount) append(prefs.get("${key}__$i", "")) }
        } else {
            prefs.get(key, null)
        }
    }

    actual fun remove(key: String) {
        if (prefs.getBoolean("${key}__isFile", false)) {
            fileForKey(key).delete()
            prefs.remove("${key}__isFile")
        }
        prefs.remove(key)
        val chunkCount = prefs.getInt("${key}__chunks", -1)
        if (chunkCount > 0) {
            for (i in 0 until chunkCount) prefs.remove("${key}__$i")
            prefs.remove("${key}__chunks")
        }
        prefs.flush()
        runCatching { ksafe.deleteDirect(key) }
    }

    actual fun putSecretString(key: String, value: String) {
        ksafe.putDirect(key, value)
    }

    actual fun getSecretString(key: String): String? {
        return ksafe.getDirect<String?>(key, null)
    }
}
