package io.github.crustack.core.storage.opencollection

import java.io.File

actual fun readTextFile(path: String): String = File(path).readText()

actual fun writeTextFile(path: String, content: String) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

actual fun deleteFile(path: String): Boolean = File(path).delete()

actual fun fileExists(path: String): Boolean = File(path).exists()
