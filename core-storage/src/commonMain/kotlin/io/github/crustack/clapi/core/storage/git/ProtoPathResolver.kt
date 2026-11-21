package io.github.crustack.clapi.core.storage.git

import io.github.crustack.clapi.core.storage.opencollection.copyFileOrDirectory

/**
 * Dual-path resolver and synchronizer for `.proto` schema files and import paths (FR-009, FR-010, FR-011).
 *
 * - Relative paths (`./protobuf/...` or relative to collection root) are resolved against the
 *   collection's Git repository subfolder (`~/.clapi/storage/<repoId>/<collection-name>/`).
 * - Absolute filesystem paths (`/Users/...`, `C:\...`) are preserved as-is.
 */
object ProtoPathResolver {

    private val WINDOWS_DRIVE_REGEX = Regex("^[a-zA-Z]:[/\\\\].*")

    fun isRelativeGitProtoPath(path: String): Boolean {
        val trimmed = path.trim().replace('\\', '/')
        return trimmed.startsWith("./protobuf/") || trimmed.startsWith("protobuf/")
    }

    fun isAbsolutePath(path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.startsWith("/") ||
            trimmed.startsWith("\\\\") ||
            WINDOWS_DRIVE_REGEX.matches(trimmed)
    }

    /**
     * Resolves [rawPath] to an absolute filesystem path when [collectionRepoDir] is provided
     * and [rawPath] is relative (e.g. `./protobuf/collection/service.proto`).
     * Preserves absolute paths unchanged.
     */
    fun resolve(rawPath: String, collectionRepoDir: String?): String {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return ""
        if (isAbsolutePath(trimmed)) return trimmed
        if (collectionRepoDir.isNullOrBlank()) return trimmed

        val normalizedRelative = trimmed
            .replace('\\', '/')
            .removePrefix("./")
            .trimStart('/')

        val normalizedBase = collectionRepoDir.trim().trimEnd('/', '\\')
        return if (normalizedRelative.isEmpty()) {
            normalizedBase
        } else {
            "$normalizedBase/$normalizedRelative"
        }
    }

    fun resolveAll(rawPaths: List<String>, collectionRepoDir: String?): List<String> =
        rawPaths.map { resolve(it, collectionRepoDir) }

    /**
     * Copies a collection-level `.proto` file into `<collectionDir>/protobuf/collection/<fileName>`
     * and returns the portable relative path `./protobuf/collection/<fileName>`.
     */
    fun syncCollectionProtoFile(sourcePath: String, collectionDir: String): String {
        val resolvedSource = resolve(sourcePath, collectionDir)
        val fileName = sourcePath.trim().trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "schema.proto" }
        val relPath = "./protobuf/collection/$fileName"
        val destPath = "${collectionDir.trim().trimEnd('/', '\\')}/protobuf/collection/$fileName"
        copyFileOrDirectory(resolvedSource, destPath)
        return relPath
    }

    /**
     * Copies a collection-level import path (file or directory) into
     * `<collectionDir>/protobuf/collection/imports/<name>` and returns the portable relative path
     * `./protobuf/collection/imports/<name>`.
     */
    fun syncCollectionImportPath(sourcePath: String, collectionDir: String): String {
        val resolvedSource = resolve(sourcePath, collectionDir)
        val entryName = sourcePath.trim().trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "imports" }
        val relPath = "./protobuf/collection/imports/$entryName"
        val destPath = "${collectionDir.trim().trimEnd('/', '\\')}/protobuf/collection/imports/$entryName"
        copyFileOrDirectory(resolvedSource, destPath)
        return relPath
    }

    /**
     * Copies a request-level `.proto` file into `<collectionDir>/protobuf/requests/<requestId>/<fileName>`
     * and returns the portable relative path `./protobuf/requests/<requestId>/<fileName>`.
     */
    fun syncRequestProtoFile(sourcePath: String, requestId: String, collectionDir: String): String {
        val resolvedSource = resolve(sourcePath, collectionDir)
        val fileName = sourcePath.trim().trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "service.proto" }
        val safeReqId = requestId.trim().replace(Regex("[^a-zA-Z0-9_.-]"), "_").ifBlank { "request" }
        val relPath = "./protobuf/requests/$safeReqId/$fileName"
        val destPath = "${collectionDir.trim().trimEnd('/', '\\')}/protobuf/requests/$safeReqId/$fileName"
        copyFileOrDirectory(resolvedSource, destPath)
        return relPath
    }
}
