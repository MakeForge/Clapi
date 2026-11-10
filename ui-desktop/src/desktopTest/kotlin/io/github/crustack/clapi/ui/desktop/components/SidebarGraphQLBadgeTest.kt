package io.github.crustack.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.ui.shared.MainScreen
import io.github.crustack.clapi.ui.shared.components.GraphQLBadge
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarGraphQLBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun graphQlBadge_rendersText() {
        composeRule.setContent {
            ClapiTheme {
                GraphQLBadge(compact = true)
            }
        }
        composeRule.onNodeWithText("GQL").assertIsDisplayed()
    }

    @Test
    fun folderAddButton_showsGraphQLOptionInContextMenu() {
        val state = AppState(withDemoData = true)
        val firstCollectionId = state.collections.first().id
        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }

        composeRule.onNodeWithTag("collection-add-$firstCollectionId", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("folder-menu-new-http", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("folder-menu-new-grpc", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("folder-menu-new-websocket", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("folder-menu-new-graphql", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun selectingNewGraphQLRequest_createsGraphQLNodeAndDisplaysBadgesInSidebarAndTab() {
        val state = AppState(withDemoData = true)
        val rootFolder = state.collections.first()
        val beforeCount = rootFolder.children.size

        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }

        composeRule.onNodeWithTag("collection-add-${rootFolder.id}", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder-menu-new-graphql", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(beforeCount + 1, rootFolder.children.size)
        val createdNode: CollectionNode = rootFolder.children.last()
        assertEquals(RequestKind.GRAPHQL, createdNode.kind)
        assertTrue(createdNode.name.startsWith("New Request"))

        // Verify sidebar GraphQL badge is displayed
        composeRule.onNodeWithTag("graphql-badge-${createdNode.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("GQL")

        // Verify tab GraphQL badge is displayed
        composeRule.onNodeWithTag("tab-graphql-badge-${createdNode.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("GQL")
    }
}
