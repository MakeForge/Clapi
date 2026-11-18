package io.github.makeforge.clapi.mcp.server.backend

class NodeNotFoundException(
    val identifier: String,
    val expectedKind: String? = null,
) : IllegalArgumentException(
    if (expectedKind != null) {
        "No $expectedKind found matching identifier '$identifier'."
    } else {
        "No workspace item found matching identifier '$identifier'."
    }
)

class AmbiguousIdentifierException(
    val identifier: String,
    val candidates: List<WorkspaceTreeNodeDto>,
) : IllegalArgumentException(
    buildString {
        append("Identifier '$identifier' is ambiguous and matches ${candidates.size} items. ")
        append("Please specify the exact UUID from: ")
        append(
            candidates.joinToString(", ") { c ->
                "[id=${c.id}, name='${c.name}', kind=${c.kind}${c.method?.let { ", method=$it" }.orEmpty()}, path='${c.slashPath}']"
            }
        )
    }
)

class DestructiveConfirmationRequiredException(
    val operation: String,
    val target: String,
) : IllegalArgumentException(
    "Destructive operation '$operation' on '$target' requires explicit confirmation (`confirm: true`)."
)

/**
 * Resolves workspace items (collections, folders, requests) by exact UUID or human-readable
 * slash-delimited paths (`"CollectionName/FolderName/RequestName"`), supporting `\/` escaped slashes.
 */
object NodeLookupResolver {

    /**
     * Splits a slash path into unescaped path segments, honoring `\/` as a literal slash within a segment name.
     */
    fun parseSlashPath(rawPath: String): List<String> {
        val trimmed = rawPath.trim().removePrefix("/").removeSuffix("/")
        if (trimmed.isEmpty()) return emptyList()
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < trimmed.length) {
            val ch = trimmed[i]
            if (ch == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '/') {
                current.append('/')
                i += 2
                continue
            }
            if (ch == '/') {
                val seg = current.toString().trim()
                if (seg.isNotEmpty()) segments.add(seg)
                current.clear()
            } else {
                current.append(ch)
            }
            i++
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) segments.add(last)
        return segments
    }

    /**
     * Escapes literal `/` characters in a node name with `\/` before joining into a canonical `slashPath`.
     */
    fun escapeSegment(name: String): String = name.replace("/", "\\/")

    fun buildSlashPath(segments: List<String>): String =
        segments.joinToString("/") { escapeSegment(it) }

    /**
     * Flattens a tree of [WorkspaceTreeNodeDto] into all nodes (pre-order traversal).
     */
    fun flatten(roots: List<WorkspaceTreeNodeDto>): List<WorkspaceTreeNodeDto> {
        val result = mutableListOf<WorkspaceTreeNodeDto>()
        fun walk(node: WorkspaceTreeNodeDto) {
            result.add(node)
            node.children.forEach(::walk)
        }
        roots.forEach(::walk)
        return result
    }

    /**
     * Resolves a single node from [roots] matching [idOrPath] and optional [allowedKinds].
     *
     * Resolution precedence:
     * 1. Exact `id` match (case-sensitive, then case-insensitive UUID match).
     * 2. Exact `slashPath` match (or segment-by-segment hierarchical traversal).
     * 3. For single-segment queries (no unescaped `/`), exact `name` match within [allowedKinds].
     *
     * Throws [AmbiguousIdentifierException] if multiple candidates match a path or name query.
     * Throws [NodeNotFoundException] if zero candidates match.
     */
    fun resolve(
        roots: List<WorkspaceTreeNodeDto>,
        idOrPath: String,
        allowedKinds: Set<String>? = null,
    ): WorkspaceTreeNodeDto {
        val query = idOrPath.trim()
        if (query.isEmpty()) {
            throw NodeNotFoundException(idOrPath, allowedKinds?.joinToString("/"))
        }

        val allNodes = flatten(roots)
        val kindFiltered = if (allowedKinds != null) {
            allNodes.filter { it.kind.lowercase() in allowedKinds }
        } else {
            allNodes
        }

        // 1. Exact ID match
        val exactIdMatches = kindFiltered.filter { it.id == query }
        if (exactIdMatches.size == 1) return exactIdMatches.first()
        val caseInsensitiveIdMatches = kindFiltered.filter { it.id.equals(query, ignoreCase = true) }
        if (caseInsensitiveIdMatches.size == 1) return caseInsensitiveIdMatches.first()

        // 2. Hierarchical segment match
        val segments = parseSlashPath(query)
        if (segments.isEmpty()) {
            throw NodeNotFoundException(idOrPath, allowedKinds?.joinToString("/"))
        }

        val segmentMatches = matchBySegments(roots, segments, allowedKinds)
        if (segmentMatches.size == 1) return segmentMatches.first()
        if (segmentMatches.size > 1) {
            throw AmbiguousIdentifierException(idOrPath, segmentMatches)
        }

        // 3. Single-segment name fallback across filtered nodes
        if (segments.size == 1) {
            val targetName = segments.first()
            val nameMatches = kindFiltered.filter { it.name.equals(targetName, ignoreCase = false) }
                .ifEmpty { kindFiltered.filter { it.name.equals(targetName, ignoreCase = true) } }
            if (nameMatches.size == 1) return nameMatches.first()
            if (nameMatches.size > 1) {
                throw AmbiguousIdentifierException(idOrPath, nameMatches)
            }
        }

        throw NodeNotFoundException(idOrPath, allowedKinds?.joinToString("/"))
    }

    private fun matchBySegments(
        roots: List<WorkspaceTreeNodeDto>,
        segments: List<String>,
        allowedKinds: Set<String>?,
    ): List<WorkspaceTreeNodeDto> {
        var currentLevel = roots
        for (i in segments.indices) {
            val seg = segments[i]
            val isLast = i == segments.lastIndex
            val exactMatches = currentLevel.filter { node ->
                node.name == seg && (!isLast || allowedKinds == null || node.kind.lowercase() in allowedKinds)
            }
            val matches = exactMatches.ifEmpty {
                currentLevel.filter { node ->
                    node.name.equals(seg, ignoreCase = true) &&
                        (!isLast || allowedKinds == null || node.kind.lowercase() in allowedKinds)
                }
            }
            if (matches.isEmpty()) return emptyList()
            if (isLast) return matches
            currentLevel = matches.flatMap { it.children }
        }
        return emptyList()
    }
}
