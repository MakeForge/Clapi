package io.github.crustack.core.model

import kotlinx.serialization.Serializable

@Deprecated("Legacy workspace model. Replaced by OpenCollection canonical model.")
@Serializable
data class WorkspaceDefinition(
    val id: String,
    val name: String,
    val description: String? = null,
    val collectionIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Deprecated("Legacy collection model. Use io.github.crustack.core.model.opencollection.OpenCollectionDocument instead.")
@Serializable
data class CollectionDefinition(
    val id: String,
    val workspaceId: String,
    val name: String,
    val description: String? = null,
    val folderIds: List<String> = emptyList(),
    val requestIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Deprecated("Legacy folder model. Use io.github.crustack.core.model.opencollection.FolderItem instead.")
@Serializable
data class FolderDefinition(
    val id: String,
    val collectionId: String,
    val parentFolderId: String? = null,
    val name: String,
    val folderIds: List<String> = emptyList(),
    val requestIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
