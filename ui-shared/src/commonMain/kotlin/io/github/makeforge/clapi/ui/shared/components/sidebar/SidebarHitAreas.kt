package io.github.makeforge.clapi.ui.shared.components.sidebar

internal data class TreeNodeHitArea(
    val id: String,
    val isFolder: Boolean,
    val parentCollectionId: String?,
    val top: Float,
    val bottom: Float,
    val label: String,
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

internal fun normalizeSidebarSearchQuery(raw: String): String {
    // Strip invisible formatting characters so visually empty input cannot
    // keep a stale filter active (e.g., zero-width spaces from paste/IME).
    return raw
        .filterNot { ch ->
            ch == '\u200B' || ch == '\u200C' || ch == '\u200D' || ch == '\uFEFF'
        }
        .trim()
}
