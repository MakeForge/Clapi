package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.HttpBadge
import io.github.makeforge.clapi.ui.shared.state.AppStateExamples
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test

class SidebarHttpBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun httpBadge_rendersText() {
        composeRule.setContent {
            ClapiTheme {
                HttpBadge(compact = true)
            }
        }
        composeRule.onNodeWithText("HTTP").assertIsDisplayed()
    }

    @Test
    fun folderAddMenu_showsRequestTypeBadges() {
        val state = AppStateExamples.demo()
        val firstCollectionId = state.collections.first().id
        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }

        composeRule.onNodeWithTag("collection-add-$firstCollectionId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("folder-menu-new-http", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("HTTP").assertIsDisplayed()
        composeRule.onNodeWithText("gRPC").assertIsDisplayed()
        composeRule.onNodeWithText("WS").assertIsDisplayed()
        composeRule.onNodeWithText("GQL").assertIsDisplayed()
    }

    @Test
    fun folderContextMenu_showsRequestTypeBadges() {
        val state = AppStateExamples.demo()
        val firstCollectionId = state.collections.first().id
        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }

        composeRule.onNodeWithTag("collection-actions-$firstCollectionId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collection-menu-add-request", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("HTTP").assertIsDisplayed()
        composeRule.onNodeWithText("gRPC").assertIsDisplayed()
        composeRule.onNodeWithText("WS").assertIsDisplayed()
        composeRule.onNodeWithText("GQL").assertIsDisplayed()
        composeRule.onNodeWithText("MCP").assertIsDisplayed()
        composeRule.onNodeWithText("SSE").assertIsDisplayed()
    }
}
