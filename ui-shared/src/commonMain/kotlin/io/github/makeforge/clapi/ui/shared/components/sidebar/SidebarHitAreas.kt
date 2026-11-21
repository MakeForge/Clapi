package io.github.makeforge.clapi.ui.shared.components.sidebar

internal data class TreeNodeHitArea(
    val id: String,
    val isFolder: Boolean,
    val parentCollectionId: String?,
    val top: Float,
    val bottom: Float,
    val label: String,
)

internal data class RequestDropTarget(
    val collectionId: String?,
    val requestId: String?,
    val insertAfter: Boolean,
)

internal data class FolderDropTarget(
    val targetFolderId: String,
    val insertAfter: Boolean,
)

internal fun resolveClosestHitArea(
    areas: Collection<TreeNodeHitArea>,
    cursorY: Float,
): TreeNodeHitArea? {
    // Prefer rows that currently contain the cursor. If none contain it,
    // fall back to nearest row center to keep drag interactions continuous.
    val inside = areas.filter { cursorY >= it.top && cursorY <= it.bottom }
    if (inside.isNotEmpty()) {
        return inside.minByOrNull {
            kotlin.math.abs(cursorY - ((it.top + it.bottom) / 2f))
        }
    }
    return areas.minByOrNull {
        kotlin.math.abs(cursorY - ((it.top + it.bottom) / 2f))
    }
}

/**
 * Resolves the drop target for dragging one or more requests.
 * Returns `null` if [cursorY] is back over any of the dragged items' own original
 * rows, so releasing there cleanly keeps every item in its original position.
 */
internal fun resolveRequestDropTarget(
    allAreas: Collection<TreeNodeHitArea>,
    movingRequestIds: Collection<String>,
    cursorY: Float,
): RequestDropTarget? {
    val movingSet = movingRequestIds.toSet()
    val overOwnOriginalPosition = allAreas.any {
        it.id in movingSet && cursorY >= it.top && cursorY <= it.bottom
    }
    if (overOwnOriginalPosition) return null

    val target = resolveClosestHitArea(
        areas = allAreas.filter { it.id !in movingSet },
        cursorY = cursorY,
    ) ?: return null

    return if (target.isFolder) {
        RequestDropTarget(
            collectionId = target.id,
            requestId = null,
            insertAfter = false,
        )
    } else {
        val midY = (target.top + target.bottom) / 2f
        RequestDropTarget(
            collectionId = target.parentCollectionId,
            requestId = target.id,
            insertAfter = cursorY >= midY,
        )
    }
}

/**
 * Resolves the drop target for dragging one or more folders at the same parent level.
 * Returns `null` if [cursorY] is back over any of the dragged folders' own original
 * rows, so releasing there cleanly keeps every folder in its original position.
 */
internal fun resolveFolderDropTarget(
    allAreas: Collection<TreeNodeHitArea>,
    movingFolderIds: Collection<String>,
    parentCollectionId: String?,
    cursorY: Float,
    isValidTarget: (String) -> Boolean,
): FolderDropTarget? {
    val movingSet = movingFolderIds.toSet()
    val overOwnOriginalPosition = allAreas.any {
        it.id in movingSet && cursorY >= it.top && cursorY <= it.bottom
    }
    if (overOwnOriginalPosition) return null

    val target = resolveClosestHitArea(
        areas = allAreas.filter {
            it.id !in movingSet &&
                it.parentCollectionId == parentCollectionId &&
                isValidTarget(it.id)
        },
        cursorY = cursorY,
    ) ?: return null

    val midY = (target.top + target.bottom) / 2f
    return FolderDropTarget(
        targetFolderId = target.id,
        insertAfter = cursorY >= midY,
    )
}

internal fun normalizeSidebarSearchQuery(raw: String): String {
    // Strip invisible formatting characters so visually empty input cannot
    // keep a stale filter active (e.g., zero-width spaces from paste/IME).
    return raw
        .filterNot { ch ->
            ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF'
        }
        .trim()
}
