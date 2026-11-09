package io.github.crustack.clapi.core.storage.opencollection

import java.io.File

actual fun readTextFile(path: String): String = File(path).readText()

actual fun writeTextFile(path: String, content: String) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

actual fun deleteFile(path: String): Boolean = File(path).delete()

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun getDefaultStorageBaseDirectory(): String = File(System.getProperty("user.home"), ".clapi/storage").absolutePath

actual fun copyFileOrDirectory(sourcePath: String, destinationPath: String): Boolean {
    return try {
        val src = File(sourcePath)
        if (!src.exists()) return false
        val dst = File(destinationPath)
        if (src.canonicalFile == dst.canonicalFile) return true
        if (src.isDirectory) {
            dst.mkdirs()
            src.copyRecursively(dst, overwrite = true)
        } else {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
            true
        }
    } catch (_: Exception) {
        false
    }
}
