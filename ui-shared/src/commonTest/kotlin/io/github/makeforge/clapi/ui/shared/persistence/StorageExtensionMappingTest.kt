package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StorageExtensionMappingTest {

    @Test
    fun collection_to_standalone_document_preserves_git_storage() {
        val gitConfig = GitStorageConfig(
            remoteUrl = "git@github.com:myorg/myrepo.git",
            branch = "dev",
        )
        val col = CollectionNode(
            id = "col-123",
            name = "Test Git Collection",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            storageExtension = StorageExtension(type = StorageType.GIT, git = gitConfig),
        )

        val doc = col.toOpenCollectionDocument()
        val reloaded = doc.toCollectionNode()

        assertEquals(StorageType.GIT, reloaded.storageType)
        assertEquals("git@github.com:myorg/myrepo.git", reloaded.gitRemoteUrl)
        assertEquals("dev", reloaded.gitBranch)
    }

    @Test
    fun collection_without_storage_defaults_to_local() {
        val col = CollectionNode(
            id = "col-456",
            name = "Local Collection",
            kind = RequestKind.COLLECTION,
            isFolder = true,
        )

        val doc = col.toOpenCollectionDocument()
        val reloaded = doc.toCollectionNode()

        assertEquals(StorageType.LOCAL, reloaded.storageType)
    }

    @Test
    fun global_workspace_roundtrip_preserves_collection_storage() {
        val state = AppState(openDefaultTab = false)
        val col = CollectionNode(
            id = "col-789",
            name = "Git Monorepo API",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            storageExtension = StorageExtension(
                type = StorageType.GIT,
                git = GitStorageConfig(remoteUrl = "https://github.com/org/mono.git", branch = "main"),
            ),
        )
        state.collections.add(col)

        val globalDoc = state.toGlobalOpenCollectionDocument()
        val folderItem = globalDoc.items.first()
        val loaded = folderItem.toCollectionNode()

        assertEquals(StorageType.GIT, loaded.storageType)
        assertEquals("https://github.com/org/mono.git", loaded.gitRemoteUrl)
        assertEquals("main", loaded.gitBranch)
    }
}
