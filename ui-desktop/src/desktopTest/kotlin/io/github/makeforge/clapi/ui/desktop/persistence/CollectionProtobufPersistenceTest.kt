package io.github.makeforge.clapi.ui.desktop.persistence

import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.ui.shared.persistence.PrivateStateRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.ProtoFileItemState
import io.github.makeforge.clapi.ui.shared.state.ProtoImportPathState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectionProtobufPersistenceTest {

    @Before
    fun setUp() {
        resetStorage()
    }

    @After
    fun tearDown() {
        resetStorage()
    }

    private fun resetStorage() {
        WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository()
        WorkspaceRepository.privateStateRepository = PrivateStateRepository()
    }

    @Test
    fun collection_proto_files_and_import_paths_persist_and_restore_across_workspace_reload() = runTest {
        val sourceState = AppState(openDefaultTab = false, withDemoData = false)

        sourceState.collectionProtoFiles.add(ProtoFileItemState("/shared/protos/user.proto"))
        sourceState.collectionProtoFiles.add(ProtoFileItemState("/shared/protos/order.proto"))

        sourceState.collectionImportPaths.add(ProtoImportPathState("/shared/protos/common/meta.proto", enabled = true))
        sourceState.collectionImportPaths.add(ProtoImportPathState("/shared/protos/external", enabled = false))

        WorkspaceRepository.save(sourceState)

        val canonicalDoc = WorkspaceRepository.canonicalRepository.loadGlobalCollection()
        assertNotNull(canonicalDoc, "Saved collection document should exist")
        assertNotNull(canonicalDoc.config?.protobuf, "Protobuf config should be saved")

        val protoConfig = canonicalDoc.config!!.protobuf!!
        assertEquals(2, protoConfig.protoFiles.size)
        assertEquals("/shared/protos/user.proto", protoConfig.protoFiles[0].path)
        assertEquals("/shared/protos/order.proto", protoConfig.protoFiles[1].path)

        assertEquals(2, protoConfig.importPaths.size)
        assertEquals("/shared/protos/common/meta.proto", protoConfig.importPaths[0].path)
        assertEquals(null, protoConfig.importPaths[0].disabled)
        assertEquals("/shared/protos/external", protoConfig.importPaths[1].path)
        assertEquals(true, protoConfig.importPaths[1].disabled)

        // Restore into new AppState
        val restoredState = AppState(openDefaultTab = false, withDemoData = false)
        WorkspaceRepository.load(restoredState)

        assertEquals(2, restoredState.collectionProtoFiles.size)
        assertEquals("/shared/protos/user.proto", restoredState.collectionProtoFiles[0].path)
        assertEquals("/shared/protos/order.proto", restoredState.collectionProtoFiles[1].path)

        assertEquals(2, restoredState.collectionImportPaths.size)
        assertEquals("/shared/protos/common/meta.proto", restoredState.collectionImportPaths[0].path)
        assertTrue(restoredState.collectionImportPaths[0].enabled)
        assertEquals("/shared/protos/external", restoredState.collectionImportPaths[1].path)
        assertFalse(restoredState.collectionImportPaths[1].enabled)
    }
}
