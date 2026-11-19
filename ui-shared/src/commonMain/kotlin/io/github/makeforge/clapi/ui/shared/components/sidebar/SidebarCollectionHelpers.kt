package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.ui.shared.persistence.ImportExportNaming
import io.github.makeforge.clapi.ui.shared.platform.generateUuid
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode

internal fun duplicateNode(node: CollectionNode, rootNameOverride: String? = null): CollectionNode {
    // Use copy() so every field (including future additions) is guaranteed to be duplicated.
    // Only the id and name are different; children are recursively duplicated with new ids.
    val duplicatedChildren = node.children.map { duplicateNode(it) }
    return node.copy(
        id = generateUuid(),
        requestRef = if (node.isFolder) null else generateUuid(),
        name = rootNameOverride ?: node.name,
        children = mutableStateListOf<CollectionNode>().also { it.addAll(duplicatedChildren) },
    )
}

internal fun findRootCollectionIdForRequest(
    collections: List<CollectionNode>,
    requestId: String,
): String? {
    collections.forEach { root ->
        if (!root.isFolder) return@forEach
        if (containsRequestId(root, requestId)) return root.id
    }
    return null
}

private fun containsRequestId(node: CollectionNode, requestId: String): Boolean {
    if (!node.isFolder) return node.id == requestId
    node.children.forEach { child ->
        if (containsRequestId(child, requestId)) return true
    }
    return false
}

fun filterCollectionNode(node: CollectionNode, query: String): CollectionNode? {
    val loweredQuery = query.trim().lowercase()
    if (loweredQuery.isEmpty()) return node

    val selfMatches = node.name.lowercase().contains(loweredQuery) ||
        (node.url?.lowercase()?.contains(loweredQuery) == true)

    // If the folder/collection name itself matches, show it with ALL of its children
    // so the user can see every request inside the matched container.
    if (selfMatches && node.isFolder) return node

    val filteredChildren = node.children.mapNotNull { filterCollectionNode(it, loweredQuery) }.toMutableList()
    return if (selfMatches || filteredChildren.isNotEmpty()) {
        node.copy(children = filteredChildren)
    } else {
        null
    }
}

fun getVisibleSidebarNodes(
    collections: List<CollectionNode>,
    expandedState: Map<String, Boolean>,
    query: String = "",
): List<CollectionNode> {
    val q = normalizeSidebarSearchQuery(query)
    val rootNodes = if (q.isBlank()) collections else collections.mapNotNull { filterCollectionNode(it, q) }
    val result = mutableListOf<CollectionNode>()
    fun traverse(nodes: List<CollectionNode>) {
        for (node in nodes) {
            result.add(node)
            if (node.isFolder && (expandedState[node.id] ?: true)) {
                traverse(node.children)
            }
        }
    }
    traverse(rootNodes)
    return result
}

fun getVisibleFolderIds(
    collections: List<CollectionNode>,
    expandedState: Map<String, Boolean>,
    query: String = "",
): List<String> = getVisibleSidebarNodes(collections, expandedState, query).filter { it.isFolder }.map { it.id }

fun getVisibleRequestIds(
    collections: List<CollectionNode>,
    expandedState: Map<String, Boolean>,
    query: String = "",
): List<String> = getVisibleSidebarNodes(collections, expandedState, query).filter { !it.isFolder }.map { it.id }

fun duplicateRequestInCollections(collections: MutableList<CollectionNode>, requestId: String): String? {
    return duplicateRequestInNodeList(collections, requestId)
}

private fun duplicateRequestInNodeList(nodes: MutableList<CollectionNode>, requestId: String): String? {
    nodes.indices.forEach { index ->
        val node = nodes[index]
        if (!node.isFolder && node.id == requestId) {
            val siblingNames = nodes.map { it.name }.toSet()
            val duplicateName = generateUniqueNodeName("${node.name} Copy", siblingNames)
            val duplicated = duplicateNode(node, rootNameOverride = duplicateName)
            nodes.add(index + 1, duplicated)
            return duplicateName
        }
        if (node.children.isNotEmpty()) {
            val duplicatedName = duplicateRequestInNodeList(node.children, requestId)
            if (duplicatedName != null) return duplicatedName
        }
    }
    return null
}

private fun generateUniqueNodeName(base: String, existingNames: Set<String>): String {
    if (base !in existingNames) return base
    var index = 2
    while ("$base $index" in existingNames) {
        index++
    }
    return "$base $index"
}

fun deleteRequestFromCollections(collections: MutableList<CollectionNode>, requestId: String): Boolean {
    val iterator = collections.iterator()
    while (iterator.hasNext()) {
        val node = iterator.next()
        if (!node.isFolder && node.id == requestId) {
            iterator.remove()
            return true
        }
        if (node.children.isNotEmpty()) {
            if (deleteRequestFromCollections(node.children, requestId)) return true
        }
    }
    return false
}

fun renameRequestInCollections(collections: MutableList<CollectionNode>, requestId: String, newName: String) {
    collections.forEachIndexed { index, node ->
        if (!node.isFolder && node.id == requestId) {
            collections[index] = node.copy(name = newName)
            return
        }
        if (node.children.isNotEmpty()) {
            renameRequestInCollections(node.children, requestId, newName)
        }
    }
}

fun addSubfolderInCollections(
    collections: MutableList<CollectionNode>,
    parentFolderId: String,
    folderName: String,
): CollectionNode? {
    val parent = findCollectionById(collections, parentFolderId) ?: return null
    if (!parent.isFolder) return null
    val siblingNames = parent.children.filter { it.isFolder }.map { it.name }.toSet()
    val uniqueName = ImportExportNaming.generateUniqueCollectionName(folderName, siblingNames)
    val node = CollectionNode(
        id = generateUuid(),
        name = uniqueName,
        isFolder = true,
        children = mutableStateListOf(),
    )
    parent.children.add(node)
    return node
}

fun renameFolderInCollections(
    collections: MutableList<CollectionNode>,
    folderId: String,
    newName: String,
): Boolean {
    return renameFolderInNodeList(collections, folderId, newName)
}

private fun renameFolderInNodeList(
    nodes: MutableList<CollectionNode>,
    folderId: String,
    newName: String,
): Boolean {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) {
        val siblings = nodes.filterIndexed { i, n -> i != index && n.isFolder }.map { it.name }.toSet()
        val uniqueName = ImportExportNaming.generateUniqueCollectionName(newName, siblings)
        nodes[index] = nodes[index].copy(name = uniqueName)
        return true
    }
    nodes.forEach { node ->
        if (node.isFolder && node.children.isNotEmpty()) {
            if (renameFolderInNodeList(node.children, folderId, newName)) return true
        }
    }
    return false
}

fun duplicateFolderInCollections(collections: MutableList<CollectionNode>, folderId: String): String? {
    return duplicateFolderInNodeList(collections, folderId)
}

private fun duplicateFolderInNodeList(nodes: MutableList<CollectionNode>, folderId: String): String? {
    nodes.indices.forEach { index ->
        val node = nodes[index]
        if (node.isFolder && node.id == folderId) {
            val siblingNames = nodes.filter { it.isFolder }.map { it.name }.toSet()
            val duplicateName = ImportExportNaming.generateUniqueCollectionName(node.name, siblingNames)
            val duplicated = duplicateNode(node, rootNameOverride = duplicateName)
            nodes.add(index + 1, duplicated)
            return duplicateName
        }
        if (node.children.isNotEmpty()) {
            val duplicatedName = duplicateFolderInNodeList(node.children, folderId)
            if (duplicatedName != null) return duplicatedName
        }
    }
    return null
}

fun deleteFolderFromCollections(
    collections: MutableList<CollectionNode>,
    folderId: String,
): CollectionNode? = deleteFolderFromNodeList(collections, folderId)

/** Recursively collects all node IDs (both folder and request) from a [CollectionNode] subtree. */
fun collectAllRequestIds(node: CollectionNode): List<String> {
    val result = mutableListOf(node.id)
    node.children.forEach { result.addAll(collectAllRequestIds(it)) }
    return result
}

private fun deleteFolderFromNodeList(
    nodes: MutableList<CollectionNode>,
    folderId: String,
): CollectionNode? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes.removeAt(index)
    nodes.forEach { node ->
        if (node.isFolder && node.children.isNotEmpty()) {
            val deleted = deleteFolderFromNodeList(node.children, folderId)
            if (deleted != null) return deleted
        }
    }
    return null
}

fun countRequestsInFolder(folder: CollectionNode): Int {
    if (!folder.isFolder) return 0
    return folder.children.sumOf { child ->
        when {
            child.isFolder -> countRequestsInFolder(child)
            else -> 1
        }
    }
}

/** Move a request up (direction = -1) or down (direction = 1) within its parent list. */
fun moveRequestInCollections(collections: MutableList<CollectionNode>, requestId: String, direction: Int): Boolean {
    val index = collections.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) {
        val targetIndex = index + direction
        if (targetIndex in collections.indices) {
            val item = collections.removeAt(index)
            collections.add(targetIndex, item)
            return true
        }
        return false
    }
    for (node in collections) {
        if (node.children.isNotEmpty()) {
            if (moveRequestInCollections(node.children, requestId, direction)) return true
        }
    }
    return false
}

fun moveRequestsBeforeRequest(
    collections: MutableList<CollectionNode>,
    requestIds: List<String>,
    targetRequestId: String,
): Boolean {
    val uniqueIds = requestIds.distinct()
    if (uniqueIds.isEmpty() || targetRequestId in uniqueIds) return false
    if (findRequestParentAndIndex(collections, targetRequestId) == null) return false
    if (uniqueIds.any { findRequestParentAndIndex(collections, it) == null }) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in uniqueIds) {
        val node = extractRequestNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findRequestParentAndIndex(collections, targetRequestId) ?: return false
    val insertIndex = target.second.coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveRequestBeforeRequest(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetRequestId: String,
): Boolean = moveRequestsBeforeRequest(collections, listOf(requestId), targetRequestId)

fun moveRequestsAfterRequest(
    collections: MutableList<CollectionNode>,
    requestIds: List<String>,
    targetRequestId: String,
): Boolean {
    val uniqueIds = requestIds.distinct()
    if (uniqueIds.isEmpty() || targetRequestId in uniqueIds) return false
    if (findRequestParentAndIndex(collections, targetRequestId) == null) return false
    if (uniqueIds.any { findRequestParentAndIndex(collections, it) == null }) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in uniqueIds) {
        val node = extractRequestNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findRequestParentAndIndex(collections, targetRequestId) ?: return false
    val insertIndex = (target.second + 1).coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveRequestAfterRequest(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetRequestId: String,
): Boolean = moveRequestsAfterRequest(collections, listOf(requestId), targetRequestId)

fun moveRequestsToCollection(
    collections: MutableList<CollectionNode>,
    requestIds: List<String>,
    targetCollectionId: String,
): Boolean {
    val uniqueIds = requestIds.distinct()
    if (uniqueIds.isEmpty()) return false
    val targetCollection = findCollectionById(collections, targetCollectionId) ?: return false
    if (uniqueIds.any { findRequestParentAndIndex(collections, it) == null }) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in uniqueIds) {
        val node = extractRequestNode(collections, id) ?: return false
        extracted.add(node)
    }
    targetCollection.children.addAll(extracted)
    return true
}

fun moveRequestToCollection(
    collections: MutableList<CollectionNode>,
    requestId: String,
    targetCollectionId: String,
): Boolean = moveRequestsToCollection(collections, listOf(requestId), targetCollectionId)

private fun extractRequestNode(nodes: MutableList<CollectionNode>, requestId: String): CollectionNode? {
    val index = nodes.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) {
        return nodes.removeAt(index)
    }
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            val extracted = extractRequestNode(node.children, requestId)
            if (extracted != null) return extracted
        }
    }
    return null
}

private fun findRequestParentAndIndex(
    nodes: MutableList<CollectionNode>,
    requestId: String,
): Pair<MutableList<CollectionNode>, Int>? {
    val index = nodes.indexOfFirst { !it.isFolder && it.id == requestId }
    if (index >= 0) return nodes to index
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            val found = findRequestParentAndIndex(node.children, requestId)
            if (found != null) return found
        }
    }
    return null
}

internal fun findCollectionById(nodes: List<CollectionNode>, collectionId: String): CollectionNode? {
    nodes.forEach { node ->
        if (node.isFolder && node.id == collectionId) {
            return node
        }
        if (node.children.isNotEmpty()) {
            val child = findCollectionById(node.children, collectionId)
            if (child != null) return child
        }
    }
    return null
}

fun isValidFolderDropTarget(
    collections: List<CollectionNode>,
    sourceFolderId: String,
    targetFolderId: String,
): Boolean {
    if (sourceFolderId == targetFolderId) return false
    val sourceNode = findCollectionById(collections, sourceFolderId) ?: return false
    if (!sourceNode.isFolder) return false
    return !containsFolderId(sourceNode, targetFolderId)
}

/**
 * Recursively sets the expanded state to false for a node and all its
 * folder children, so imported collections appear fully collapsed.
 */
internal fun collapseNodeRecursively(node: CollectionNode, state: AppState) {
    if (node.isFolder) {
        state.collectionExpandedState[node.id] = false
        for (child in node.children) {
            collapseNodeRecursively(child, state)
        }
    }
}

// ── Collection-level drag-to-reorder helpers (Bug 3) ──────────────────────────

/**
 * Moves the collection identified by [collectionId] to be immediately BEFORE
 * the collection identified by [targetCollectionId] in [collections].
 * Returns true if the move was performed.
 */
fun moveCollectionBeforeCollection(
    collections: MutableList<CollectionNode>,
    collectionId: String,
    targetCollectionId: String,
): Boolean {
    val fromIndex = collections.indexOfFirst { it.id == collectionId }
    val toIndex   = collections.indexOfFirst { it.id == targetCollectionId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    val item = collections.removeAt(fromIndex)
    val insertIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
    collections.add(insertIndex, item)
    return true
}

/**
 * Moves the collection identified by [collectionId] to be immediately AFTER
 * the collection identified by [targetCollectionId] in [collections].
 * Returns true if the move was performed.
 */
fun moveCollectionAfterCollection(
    collections: MutableList<CollectionNode>,
    collectionId: String,
    targetCollectionId: String,
): Boolean {
    val fromIndex = collections.indexOfFirst { it.id == collectionId }
    val toIndex   = collections.indexOfFirst { it.id == targetCollectionId }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    val item = collections.removeAt(fromIndex)
    val insertIndex = if (fromIndex < toIndex) toIndex else toIndex + 1
    collections.add(insertIndex, item)
    return true
}

// ── Folder drag-to-reorder helpers (roots + nested folders) ─────────────────

fun isValidFolderGroupDropTarget(
    collections: List<CollectionNode>,
    movingFolderIds: List<String>,
    targetFolderId: String,
): Boolean {
    if (targetFolderId in movingFolderIds) return false
    for (id in movingFolderIds) {
        val node = findCollectionById(collections, id) ?: return false
        if (!node.isFolder) return false
        if (containsFolderId(node, targetFolderId)) return false
    }
    return true
}

/**
 * Removes duplicates and any folder ID whose ancestor folder is also present in [folderIds],
 * preserving order. Prevents double-duplication and data loss when moving or deleting a folder
 * together with its descendants.
 */
fun pruneRedundantDescendantFolders(
    collections: List<CollectionNode>,
    folderIds: List<String>,
): List<String> {
    val unique = folderIds.distinct()
    if (unique.size <= 1) return unique
    val nodesById = unique.mapNotNull { id -> findCollectionById(collections, id)?.let { id to it } }.toMap()
    return unique.filter { candidateId ->
        nodesById.none { (otherId, otherNode) ->
            otherId != candidateId && containsFolderId(otherNode, candidateId)
        }
    }
}

fun moveFoldersBeforeFolder(
    collections: MutableList<CollectionNode>,
    folderIds: List<String>,
    targetFolderId: String,
): Boolean {
    val effectiveIds = pruneRedundantDescendantFolders(collections, folderIds)
    if (effectiveIds.isEmpty() || targetFolderId in effectiveIds) return false
    if (!isValidFolderGroupDropTarget(collections, effectiveIds, targetFolderId)) return false
    if (findFolderParentAndIndex(collections, targetFolderId) == null) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in effectiveIds) {
        val node = extractFolderNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findFolderParentAndIndex(collections, targetFolderId) ?: return false
    val insertIndex = target.second.coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveFolderBeforeFolder(
    collections: MutableList<CollectionNode>,
    folderId: String,
    targetFolderId: String,
): Boolean = moveFoldersBeforeFolder(collections, listOf(folderId), targetFolderId)

fun moveFoldersAfterFolder(
    collections: MutableList<CollectionNode>,
    folderIds: List<String>,
    targetFolderId: String,
): Boolean {
    val effectiveIds = pruneRedundantDescendantFolders(collections, folderIds)
    if (effectiveIds.isEmpty() || targetFolderId in effectiveIds) return false
    if (!isValidFolderGroupDropTarget(collections, effectiveIds, targetFolderId)) return false
    if (findFolderParentAndIndex(collections, targetFolderId) == null) return false
    val extracted = mutableListOf<CollectionNode>()
    for (id in effectiveIds) {
        val node = extractFolderNode(collections, id) ?: return false
        extracted.add(node)
    }
    val target = findFolderParentAndIndex(collections, targetFolderId) ?: return false
    val insertIndex = (target.second + 1).coerceIn(0, target.first.size)
    target.first.addAll(insertIndex, extracted)
    return true
}

fun moveFolderAfterFolder(
    collections: MutableList<CollectionNode>,
    folderId: String,
    targetFolderId: String,
): Boolean = moveFoldersAfterFolder(collections, listOf(folderId), targetFolderId)

private fun extractFolderNode(nodes: MutableList<CollectionNode>, folderId: String): CollectionNode? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes.removeAt(index)
    for (node in nodes) {
        if (node.isFolder && node.children.isNotEmpty()) {
            val extracted = extractFolderNode(node.children, folderId)
            if (extracted != null) return extracted
        }
    }
    return null
}

private fun findFolderParentAndIndex(
    nodes: MutableList<CollectionNode>,
    folderId: String,
): Pair<MutableList<CollectionNode>, Int>? {
    val index = nodes.indexOfFirst { it.isFolder && it.id == folderId }
    if (index >= 0) return nodes to index
    for (node in nodes) {
        if (node.isFolder && node.children.isNotEmpty()) {
            val found = findFolderParentAndIndex(node.children, folderId)
            if (found != null) return found
        }
    }
    return null
}

private fun containsFolderId(node: CollectionNode, folderId: String): Boolean {
    if (node.id == folderId) return true
    node.children.forEach { child ->
        if (child.isFolder && containsFolderId(child, folderId)) return true
    }
    return false
}
