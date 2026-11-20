package io.github.makeforge.clapi.ui.shared.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
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
import androidx.compose.ui.semantics.semantics
import dev.nucleusframework.window.tao.TaoDragAndDropPayload
import eu.anifantakis.lib.ksafe.KSafe
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.prefs.Preferences

actual fun generateUuid(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

actual fun readFromClipboard(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val data = clipboard.getData(DataFlavor.stringFlavor)
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

/**
 * Optional hook for tests to intercept [saveFileForExport] without opening an OS dialog.
 */
var testSaveFileInterceptor: ((content: String, defaultFilename: String) -> Boolean)? = null

actual fun pickFileForImport(onResult: (fileName: String, content: String) -> Unit) {
    fileIoScope.launch {
        val picked = runCatching {
            FileKit.openFilePicker(
                type = FileKitType.File(extensions = listOf("json", "yaml", "yml")),
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(
                    title = "Import Specification",
                ),
            )
        }.getOrNull() ?: return@launch

        val selected = File(picked.path)
        rememberChooserDirectory(selected)
        runCatching { selected.readText() }
            .onSuccess { content ->
                withContext(Dispatchers.Main) {
                    onResult(selected.name, content)
                }
            }
    }
}

actual fun pickBinaryFileForRequest(onResult: (PickedBinaryFile) -> Unit) {
    fileIoScope.launch {
        val picked = runCatching {
            FileKit.openFilePicker(
                type = FileKitType.File(),
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(
                    title = "Select File",
                ),
            )
        }.getOrNull() ?: return@launch

        val selected = File(picked.path)
        rememberChooserDirectory(selected)
        runCatching {
            val bytes = selected.readBytes()
            Base64.getEncoder().encodeToString(bytes)
        }.onSuccess { base64 ->
            withContext(Dispatchers.Main) {
                onResult(PickedBinaryFile(name = selected.name, base64Content = base64))
            }
        }
    }
}

actual fun pickProtoFile(onResult: (filePath: String) -> Unit) {
    fileIoScope.launch {
        val picked = runCatching {
            FileKit.openFilePicker(
                type = FileKitType.File(extensions = listOf("proto")),
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(
                    title = "Select Proto File",
                ),
            )
        }.getOrNull() ?: return@launch

        val selected = File(picked.path)
        rememberChooserDirectory(selected)
        withContext(Dispatchers.Main) {
            onResult(selected.absolutePath)
        }
    }
}

actual fun pickProtoFiles(onResult: (filePaths: List<String>) -> Unit) {
    fileIoScope.launch {
        val pickedList = runCatching {
            FileKit.openFilePicker(
                type = FileKitType.File(extensions = listOf("proto")),
                mode = FileKitMode.Multiple(),
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(
                    title = "Select Proto Files",
                ),
            )
        }.getOrNull()

        if (!pickedList.isNullOrEmpty()) {
            val files = pickedList.map { File(it.path) }
            rememberChooserDirectory(files.first())
            withContext(Dispatchers.Main) {
                onResult(files.map { it.absolutePath })
            }
        }
    }
}

actual fun pickProtoDirectory(onResult: (dirPath: String) -> Unit) {
    fileIoScope.launch {
        val picked = runCatching {
            FileKit.openDirectoryPicker(
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(
                    title = "Select Proto Folder",
                ),
            )
        }.getOrNull() ?: return@launch

        val selected = File(picked.path)
        rememberChooserDirectory(selected)
        withContext(Dispatchers.Main) {
            onResult(selected.absolutePath)
        }
    }
}

actual fun listProtoFilesInDirectory(dirPath: String): List<DiscoveredProtoFile> {
    val trimmed = dirPath.trim()
    if (trimmed.isEmpty()) return emptyList()
    val root = File(trimmed)
    if (!root.exists() || !root.isDirectory) return emptyList()
    val canonicalRoot = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
    return root.walkTopDown()
        .filter { it.isFile && it.extension.equals("proto", ignoreCase = true) }
        .map { file ->
            val canonicalFile = runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
            val relative = runCatching {
                canonicalFile.relativeTo(canonicalRoot).path.replace('\\', '/')
            }.getOrDefault(file.name)
            DiscoveredProtoFile(
                relativePath = relative,
                absolutePath = file.absolutePath,
            )
        }
        .sortedBy { it.relativePath.lowercase() }
        .toList()
}

actual fun openFileInDefaultApp(filePath: String) {
    runCatching {
        val file = File(filePath)
        if (!file.exists()) return
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.contains("linux") -> ProcessBuilder("xdg-open", file.absolutePath).start()
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) -> {
                Desktop.getDesktop().open(file)
            }
            os.contains("win") -> ProcessBuilder("cmd", "/c", "start", "\"\"", file.absolutePath).start()
            os.contains("mac") -> ProcessBuilder("open", file.absolutePath).start()
            else -> ProcessBuilder("xdg-open", file.absolutePath).start()
        }
    }
}

actual fun openUrlInBrowser(url: String) {
    runCatching {
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.contains("linux") -> ProcessBuilder("xdg-open", url).start()
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) -> {
                Desktop.getDesktop().browse(java.net.URI.create(url))
            }
            os.contains("win") -> ProcessBuilder("cmd", "/c", "start", "\"\"", url).start()
            os.contains("mac") -> ProcessBuilder("open", url).start()
            else -> ProcessBuilder("xdg-open", url).start()
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
    if (testSaveFileInterceptor?.invoke(content, defaultFilename) == true) {
        return
    }
    fileIoScope.launch {
        val suggestedName = defaultFilename.substringBeforeLast('.', defaultFilename)
        val ext = defaultFilename.substringAfterLast('.', "").lowercase().ifBlank { null }
        val picked = runCatching {
            FileKit.openFileSaver(
                suggestedName = suggestedName,
                extension = ext,
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(title = "Export"),
            )
        }.getOrNull() ?: return@launch

        var file = File(picked.path)
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

actual fun pickDirectory(onResult: (directoryPath: String) -> Unit) {
    fileIoScope.launch {
        val picked = runCatching {
            FileKit.openDirectoryPicker(
                directory = rememberedPlatformDirectory(),
                dialogSettings = FileKitDialogSettings(title = "Select Destination Directory"),
            )
        }.getOrNull() ?: return@launch

        val dir = File(picked.path)
        rememberChooserDirectory(dir)
        withContext(Dispatchers.Main) {
            onResult(dir.absolutePath)
        }
    }
}

private fun rememberedPlatformDirectory(): PlatformFile? {
    val path = FileChooserMemory.directoryToOpen(PlatformStorage.getString(FileChooserMemory.LAST_DIR_KEY)) { candidate ->
        File(candidate).isDirectory
    }
    return path?.let { PlatformFile(File(it)) }
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
    event: KeyEvent,
    onTrigger: () -> Unit,
): Boolean {
    val awt = event.awtEventOrNull
    val isCtrlOrMeta = event.isCtrlPressed || event.isMetaPressed ||
            (awt != null && (awt.isControlDown || awt.isMetaDown))

    val isSpaceKey = event.key == Key.Spacebar ||
            awt?.keyCode == java.awt.event.KeyEvent.VK_SPACE

    // 1. KeyDown / KEY_PRESSED with Spacebar and Ctrl/Meta
    if (isCtrlOrMeta && !event.isAltPressed && !event.isShiftPressed && isSpaceKey) {
        if (event.type == KeyEventType.KeyDown ||
            awt?.id == java.awt.event.KeyEvent.KEY_PRESSED
        ) {
            awt?.consume()
            lastDesktopCtrlSpaceMillis = System.currentTimeMillis()
            onTrigger()
            return true
        }
        if (event.type == KeyEventType.KeyUp ||
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

private var lastClickTargetId: String? = null
private var lastClickTimestampMs: Long = 0L
private const val DOUBLE_CLICK_TIMEOUT_MS = 400L

fun resetPointerDoubleClickState() {
    lastClickTargetId = null
    lastClickTimestampMs = 0L
}

actual fun isPointerDoubleClick(targetId: String, event: PointerEvent): Boolean {
    val mouse = event.awtEventOrNull
    if (mouse != null) {
        return mouse.clickCount >= 2
    }
    if (testDoubleClickActive) {
        return true
    }
    val now = System.currentTimeMillis()
    val isDouble = (targetId == lastClickTargetId) && (now - lastClickTimestampMs in 1..DOUBLE_CLICK_TIMEOUT_MS)
    if (isDouble) {
        lastClickTargetId = null
        lastClickTimestampMs = 0L
    } else {
        lastClickTargetId = targetId
        lastClickTimestampMs = now
    }
    return isDouble
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
            KSafe(fileName = "clapi_secrets_$nodeName", lazyLoad = true)
        } else {
            KSafe(fileName = "clapi_secrets", lazyLoad = true)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.fileDropTarget(
    onFileDropped: (fileName: String, content: String) -> Unit,
): Modifier {
    val currentOnFileDropped by rememberUpdatedState(onFileDropped)

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                try {
                    val payload = event.nativeEvent as? TaoDragAndDropPayload
                    val files: List<File> = when {
                        payload != null -> payload.files.map { File(it) }
                        else -> {
                            val transferable = runCatching { event.awtTransferable }.getOrNull()
                            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                                    ?.filterIsInstance<File>()
                                    .orEmpty()
                            } else {
                                emptyList()
                            }
                        }
                    }

                    val matchingFile = files.firstOrNull { f ->
                        f.extension.lowercase() in setOf("yaml", "yml", "json")
                    }
                    if (matchingFile != null) {
                        fileIoScope.launch {
                            val text = runCatching { matchingFile.readText() }.getOrNull()
                            if (text != null) {
                                withContext(Dispatchers.Main) {
                                    currentOnFileDropped(matchingFile.name, text)
                                }
                            }
                        }
                        return true
                    }

                    val transferable = runCatching { event.awtTransferable }.getOrNull()
                    if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String
                        if (!text.isNullOrBlank()) {
                            currentOnFileDropped("dropped-spec.yaml", text)
                            return true
                        }
                    }
                } catch (_: Exception) {
                    return false
                }
                return false
            }
        }
    }

    return this
        .dragAndDropTarget(
            shouldStartDragAndDrop = { true },
            target = dropTarget,
        )
        .semantics {
            set(FileDropSemanticsKey, currentOnFileDropped)
        }
}

