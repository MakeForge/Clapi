package io.github.crustack.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.ui.shared.components.CollectionEditor
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionProtobufEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun adding_and_removing_proto_files_marks_dirty_and_save_button_enables() {
        val state = AppState()
        val tab = RequestTabState(id = "collection_settings", name = "Collection", kind = RequestKind.COLLECTION)
        tab.collectionSnapshotProvider = { state.collectionProtobufSnapshot() }
        tab.markSaved()

        var saveCallCount = 0
        var openedPath: String? = null

        composeRule.setContent {
            CollectionEditor(
                tab = tab,
                state = state,
                onSave = {
                    saveCallCount++
                    tab.markSaved()
                },
                onPickProtoFiles = { cb ->
                    cb(listOf("/protos/service_a.proto", "/protos/service_b.proto"))
                },
                onOpenFile = { openedPath = it },
            )
        }
        composeRule.waitForIdle()

        // Initially no changes -> Save button disabled
        composeRule.onNodeWithTag("collection-save-button").assertIsNotEnabled()
        assertFalse(tab.isDirty)

        // Click Add Proto Files
        composeRule.onNodeWithTag("add-proto-files-button").performClick()
        composeRule.waitForIdle()

        assertEquals(2, state.collectionProtoFiles.size)
        assertTrue(tab.isDirty)
        // Now Save button must be enabled
        composeRule.onNodeWithTag("collection-save-button").assertIsEnabled()

        // Click Save button
        composeRule.onNodeWithTag("collection-save-button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, saveCallCount)
        assertFalse(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsNotEnabled()

        composeRule.onNodeWithTag("proto-file-row-/protos/service_a.proto").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-file-row-/protos/service_b.proto").assertIsDisplayed()

        // Click Open file on service_a
        composeRule.onNodeWithTag("open-file-btn-/protos/service_a.proto").performClick()
        composeRule.waitForIdle()
        assertEquals("/protos/service_a.proto", openedPath)

        // Delete service_b -> dirty again, Save button enabled
        composeRule.onNodeWithTag("delete-proto-btn-/protos/service_b.proto").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.collectionProtoFiles.size)
        assertEquals("/protos/service_a.proto", state.collectionProtoFiles.first().path)
        assertTrue(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsEnabled()
    }

    @Test
    fun adding_and_toggling_and_removing_import_paths() {
        val state = AppState()
        val tab = RequestTabState(id = "collection_settings", name = "Collection", kind = RequestKind.COLLECTION)
        tab.collectionSnapshotProvider = { state.collectionProtobufSnapshot() }
        tab.markSaved()

        var saveCallCount = 0

        composeRule.setContent {
            CollectionEditor(
                tab = tab,
                state = state,
                onSave = {
                    saveCallCount++
                    tab.markSaved()
                },
                onPickImportPaths = { cb ->
                    cb(listOf("/protos/common/meta.proto"))
                },
            )
        }
        composeRule.waitForIdle()

        // Initially clean
        composeRule.onNodeWithTag("collection-save-button").assertIsNotEnabled()

        // Click Add Import Paths
        composeRule.onNodeWithTag("add-import-paths-button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.collectionImportPaths.size)
        val importItem = state.collectionImportPaths.first()
        assertTrue(importItem.enabled)
        assertTrue(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsEnabled()

        // Save
        composeRule.onNodeWithTag("collection-save-button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, saveCallCount)
        assertFalse(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsNotEnabled()

        // Toggle disabled -> becomes dirty
        composeRule.onNodeWithTag("import-path-toggle-/protos/common/meta.proto").performClick()
        composeRule.waitForIdle()
        assertFalse(importItem.enabled)
        assertTrue(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsEnabled()

        // Toggle enabled back -> reverts to saved snapshot, so becomes clean again!
        composeRule.onNodeWithTag("import-path-toggle-/protos/common/meta.proto").performClick()
        composeRule.waitForIdle()
        assertTrue(importItem.enabled)
        assertFalse(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsNotEnabled()

        // Delete import path -> becomes dirty
        composeRule.onNodeWithTag("delete-import-btn-/protos/common/meta.proto").performClick()
        composeRule.waitForIdle()
        assertTrue(state.collectionImportPaths.isEmpty())
        assertTrue(tab.isDirty)
        composeRule.onNodeWithTag("collection-save-button").assertIsEnabled()
    }
}
