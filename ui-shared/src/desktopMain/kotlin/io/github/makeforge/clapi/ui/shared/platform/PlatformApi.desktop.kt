package io.github.makeforge.clapi.ui.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.semantics
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.swing.SwingUtilities
import java.awt.Cursor
import java.awt.Desktop
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

private val fileIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        fileIoScope.launch {
            runCatching { selected.readText() }
                .onSuccess { content ->
                    SwingUtilities.invokeLater {
                        onResult(selected.name, content)
                    }
                }
        }
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
        fileIoScope.launch {
            runCatching {
                val bytes = selected.readBytes()
                Base64.getEncoder().encodeToString(bytes)
            }.onSuccess { base64 ->
                SwingUtilities.invokeLater {
                    onResult(PickedBinaryFile(name = selected.name, base64Content = base64))
                }
            }
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

actual fun pickProtoFiles(onResult: (filePaths: List<String>) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Select Proto Files", FileDialog.LOAD).apply {
        applyRememberedDirectory()
        isMultipleMode = true
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".proto", ignoreCase = true) }
        isVisible = true
    }
    val files = dialog.files
    if (files != null && files.isNotEmpty()) {
        rememberChooserDirectory(files.first())
        onResult(files.map { it.absolutePath })
    } else {
        val selectedDir = dialog.directory
        val selectedFile = dialog.file
        if (selectedDir != null && selectedFile != null) {
            val selected = File(selectedDir, selectedFile)
            rememberChooserDirectory(selected)
            onResult(listOf(selected.absolutePath))
        }
    }
}

actual fun openFileInDefaultApp(filePath: String) {
    runCatching {
        val file = File(filePath)
        if (!file.exists()) return
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("win") -> ProcessBuilder("cmd", "/c", "start", "\"\"", file.absolutePath).start()
                os.contains("mac") -> ProcessBuilder("open", file.absolutePath).start()
                else -> ProcessBuilder("xdg-open", file.absolutePath).start()
            }
        }
    }
}

actual fun openUrlInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(java.net.URI.create(url))
        } else {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("win") -> ProcessBuilder("cmd", "/c", "start", "\"\"", url).start()
                os.contains("mac") -> ProcessBuilder("open", url).start()
                else -> ProcessBuilder("xdg-open", url).start()
            }
        }
    }
}

actual fun isLocalDevMode(): Boolean {
    val devProp = System.getProperty("clapi.dev")?.trim()
    if (devProp.equals("true", ignoreCase = true)) return true
    if (devProp.equals("false", ignoreCase = true)) return false

    val devEnv = System.getenv("CLAPI_DEV")?.trim()
    if (devEnv.equals("true", ignoreCase = true)) return true
    if (devEnv.equals("false", ignoreCase = true)) return false

    val clapiDir = System.getProperty("clapi.dir")
        ?.replace('\\', '/')
        ?.trimEnd('/')
    return clapiDir?.endsWith("/.clapi-dev") == true
}

actual fun platformDocsBaseUrlOverride(): String? {
    return System.getProperty("clapi.docs.baseUrl")?.takeIf { it.isNotBlank() }
        ?: System.getenv("CLAPI_DOCS_BASE_URL")?.takeIf { it.isNotBlank() }
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
        val targetFile = file
        fileIoScope.launch {
            runCatching { targetFile.writeText(content) }
        }
    }
}

actual fun pickDirectory(onResult: (directoryPath: String) -> Unit) {
    javax.swing.SwingUtilities.invokeLater {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Destination Directory"
            val remembered = FileChooserMemory.directoryToOpen(PlatformStorage.getString(FileChooserMemory.LAST_DIR_KEY)) { candidate ->
                File(candidate).isDirectory
            }
            if (remembered != null) currentDirectory = File(remembered)
        }
        val result = chooser.showOpenDialog(null)
        if (result == javax.swing.JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            val dir = chooser.selectedFile
            rememberChooserDirectory(dir)
            onResult(dir.absolutePath)
        }
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
    private val rootPrefs: Preferences =
        Preferences.userNodeForPackage(PlatformStorage::class.java)

    private fun customStorageDir(): String? =
        System.getProperty("clapi.dir")?.takeIf { it.isNotBlank() }
            ?: System.getenv("CLAPI_DIR")?.takeIf { it.isNotBlank() }

    private val prefs: Preferences
        get() {
            val custom = customStorageDir()
            return if (custom != null) {
                val nodeName = custom.hashCode().let { if (it < 0) "dir_${-it}" else "dir_$it" }
                rootPrefs.node(nodeName)
            } else {
                rootPrefs
            }
        }

    private val ksafe by lazy {
        val custom = customStorageDir()
        if (custom != null) {
            val nodeName = custom.hashCode().let { if (it < 0) "dir_${-it}" else "dir_$it" }
            KSafe(fileName = "clapi_secrets_$nodeName")
        } else {
            KSafe(fileName = "clapi_secrets")
        }
    }

    // Values larger than this are written to a file in ~/.clapi/pstore/ (or <clapi.dir>/pstore/)
    // instead of being chunked into Preferences entries. Java Preferences flushes to an XML
    // plist on every write; storing MB-scale data there creates thousands of entries and freezes the UI.
    private const val FILE_THRESHOLD = 65_536  // 64 KB

    val pstoreDirectory: File
        get() {
            val custom = customStorageDir()
            val base = if (custom != null) File(custom) else File(System.getProperty("user.home"), ".clapi")
            return File(base, "pstore").also { it.mkdirs() }
        }

    private val pstoreDir: File
        get() = pstoreDirectory

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

@Composable
actual fun Modifier.fileDropTarget(
    onFileDropped: (fileName: String, content: String) -> Unit,
): Modifier {
    val window = java.awt.Window.getWindows().firstOrNull { it.isShowing }
    val currentOnFileDropped by rememberUpdatedState(onFileDropped)

    DisposableEffect(window) {
        val target = window ?: return@DisposableEffect onDispose {}
        val previousDropTarget = target.dropTarget

        val listener = object : DropTargetAdapter() {
            override fun dragOver(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                        val file = files?.filterIsInstance<File>()?.firstOrNull { f ->
                            val ext = f.extension.lowercase()
                            ext in setOf("yaml", "yml", "json")
                        }
                        if (file != null) {
                            fileIoScope.launch {
                                val text = runCatching { file.readText() }.getOrNull()
                                if (text != null) {
                                    SwingUtilities.invokeLater {
                                        currentOnFileDropped(file.name, text)
                                    }
                                }
                            }
                            dtde.dropComplete(true)
                            return
                        }
                    } else if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val text = dtde.transferable.getTransferData(DataFlavor.stringFlavor) as? String
                        if (!text.isNullOrBlank()) {
                            currentOnFileDropped("dropped-spec.yaml", text)
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.rejectDrop()
                } catch (e: Exception) {
                    dtde.dropComplete(false)
                }
            }
        }

        val newDropTarget = DropTarget(target, DnDConstants.ACTION_COPY, listener, true)
        target.dropTarget = newDropTarget

        onDispose {
            target.dropTarget = previousDropTarget
        }
    }

    return this.semantics {
        set(FileDropSemanticsKey, currentOnFileDropped)
    }
}

