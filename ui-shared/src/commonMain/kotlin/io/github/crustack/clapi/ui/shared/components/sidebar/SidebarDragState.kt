package io.github.crustack.clapi.ui.shared.components.sidebar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class SidebarDragState {
    var draggedRequestId by mutableStateOf<String?>(null)
    var dragOffsetY by mutableStateOf(0f)
    var dragCursorAbsY by mutableStateOf(0f)
    var dropTargetCollectionId by mutableStateOf<String?>(null)
    var dropTargetRequestId by mutableStateOf<String?>(null)
    var dropInsertAfter by mutableStateOf(false)

    var draggedCollectionId by mutableStateOf<String?>(null)
    var dragCollectionOffsetY by mutableStateOf(0f)
    var draggedFolderParentId by mutableStateOf<String?>(null)
    var draggedFolderParentIdSet by mutableStateOf(false)
    var dropCollectionTargetId by mutableStateOf<String?>(null)
    var dropCollectionInsertAfter by mutableStateOf(false)

    val hitAreas = HashMap<String, TreeNodeHitArea>()
    val collectionHitAreas = HashMap<String, TreeNodeHitArea>()

    fun clearHitAreas() {
        hitAreas.clear()
        collectionHitAreas.clear()
    }
}
