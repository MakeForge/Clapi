package io.github.crustack.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.ui.shared.MainScreen
import io.github.crustack.clapi.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionEditorNavigationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opens_collection_tab_when_clicking_collection_header_in_sidebar() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()

        assertTrue(state.openTabs.any { it.kind == RequestKind.COLLECTION })
        assertEquals(RequestKind.COLLECTION, state.activeTab?.kind)
        composeRule.onNodeWithTag("collection-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("collection-tab-protobuf").assertIsDisplayed()
    }

    @Test
    fun clicking_collection_again_focuses_existing_tab_without_duplication() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()
        val countAfterFirst = state.openTabs.count { it.kind == RequestKind.COLLECTION }
        assertEquals(1, countAfterFirst)

        composeRule.onNodeWithTag("collection-node-c1").performClick()
        composeRule.waitForIdle()
        val countAfterSecond = state.openTabs.count { it.kind == RequestKind.COLLECTION }
        assertEquals(1, countAfterSecond)
    }
}
