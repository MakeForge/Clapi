package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.components.GraphQLRequestEditor
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.GraphQLEditorTab
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQLRequestEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun graphqlEditor_rendersAddressBarAndTabs() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        )

        composeRule.setContent {
            ClapiTheme {
                GraphQLRequestEditor(tab = tab, state = state, onSend = {}, onSave = {})
            }
        }

        composeRule.onNodeWithTag("graphql-method-selector", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-url-input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-send-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-body", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-headers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-params", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-auth", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-scripts", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-settings", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("graphql-tab-docs", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun graphqlEditor_switchTabs() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        )

        composeRule.setContent {
            ClapiTheme {
                GraphQLRequestEditor(tab = tab, state = state, onSend = {}, onSave = {})
            }
        }

        composeRule.onNodeWithTag("graphql-tab-settings", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(GraphQLEditorTab.SETTINGS, tab.selectedGraphQLTab)

        composeRule.onNodeWithTag("graphql-tab-headers", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(GraphQLEditorTab.HEADERS, tab.selectedGraphQLTab)

        composeRule.onNodeWithTag("graphql-tab-docs", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(GraphQLEditorTab.DOCS, tab.selectedGraphQLTab)
    }

    @Test
    fun graphqlEditor_methodSwitch() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        )

        composeRule.setContent {
            ClapiTheme {
                GraphQLRequestEditor(tab = tab, state = state, onSend = {}, onSave = {})
            }
        }

        assertEquals("POST", tab.graphQlMethod)

        composeRule.onNodeWithTag("graphql-method-selector", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("graphql-method-option-GET", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals("GET", tab.graphQlMethod)
    }

    @Test
    fun graphqlEditor_variantsManagement() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        )

        composeRule.setContent {
            ClapiTheme {
                GraphQLRequestEditor(tab = tab, state = state, onSend = {}, onSave = {})
            }
        }

        assertEquals(0, tab.graphQlVariants.size)

        // Click Add Variant
        composeRule.onNodeWithTag("graphql-add-variant", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Initializes Default + Variant 2
        assertEquals(2, tab.graphQlVariants.size)
        assertEquals("Default", tab.graphQlVariants[0].title)
        assertEquals("Variant 2", tab.graphQlVariants[1].title)
        assertEquals(1, tab.activeGraphQLVariantIndex)

        // Switch back to Default variant (chip 0)
        composeRule.onNodeWithTag("graphql-variant-chip-0", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, tab.activeGraphQLVariantIndex)
    }

    @Test
    fun graphqlEditor_prettifyQueryAction() {
        val state = AppState(withDemoData = false)
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        )
        tab.graphQlQuery = "query{user{id name}}"

        composeRule.setContent {
            ClapiTheme {
                GraphQLRequestEditor(tab = tab, state = state, onSend = {}, onSave = {})
            }
        }

        composeRule.onNodeWithTag("graphql-prettify-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(tab.effectiveGraphQlQuery.contains("user {"), "Prettified query should have formatted indentation")
    }
}
