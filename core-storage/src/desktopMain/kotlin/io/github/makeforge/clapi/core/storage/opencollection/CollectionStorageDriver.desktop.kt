package io.github.makeforge.clapi.core.storage.opencollection

import java.io.File

actual fun readTextFile(path: String): String = File(path).readText()

actual fun writeTextFile(path: String, content: String) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

actual fun deleteFile(path: String): Boolean = File(path).delete()

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun fileStamp(path: String): Pair<Long, Long>? {
    val file = File(path)
    return if (file.exists()) file.lastModified() to file.length() else null
}

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

actual fun copyProtoFilesFromDirectory(sourceDir: String, destinationDir: String): Boolean {
    return try {
        val src = File(sourceDir)
        if (!src.exists() || !src.isDirectory) return false
        val dst = File(destinationDir)
        if (src.canonicalFile == dst.canonicalFile) return true

        if (dst.exists()) {
            dst.deleteRecursively()
        }
        val canonicalSrc = src.canonicalFile
        var copiedAny = false
        src.walkTopDown()
            .filter { it.isFile && it.extension.equals("proto", ignoreCase = true) }
            .forEach { file ->
                val relPath = file.canonicalFile.relativeTo(canonicalSrc).path
                val targetFile = File(dst, relPath)
                targetFile.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
                copiedAny = true
            }
        if (!copiedAny) {
            dst.mkdirs()
        }
        true
    } catch (_: Exception) {
        false
    }
}
