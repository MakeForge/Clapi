package io.github.makeforge.clapi.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.CodeEditor
import io.github.makeforge.clapi.ui.shared.components.RequestEditor
import io.github.makeforge.clapi.ui.shared.components.SyntaxLanguage
import io.github.makeforge.clapi.ui.shared.i18n.Strings
import io.github.makeforge.clapi.ui.shared.platform.resetPointerDoubleClickState
import io.github.makeforge.clapi.ui.shared.platform.testDoubleClickActive
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.RequestEditorTab
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class, ExperimentalSerializationApi::class)
class DesktopUxPolishUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        testDoubleClickActive = false
        resetPointerDoubleClickState()
    }

    @After
    fun tearDown() {
        testDoubleClickActive = false
        resetPointerDoubleClickState()
    }

    // ── 1. Dynamic taskbar / window title ─────────────────────────────────────

    @Test
    fun formatWindowTitle_reflectsActiveTabOrDefaultsToClapi() {
        assertEquals("Clapi", formatWindowTitle(null))
        assertEquals("Clapi", formatWindowTitle(""))
        assertEquals("Clapi", formatWindowTitle("   "))
        assertEquals("Get Users - Clapi", formatWindowTitle("Get Users"))
        assertEquals("Users API - Clapi", formatWindowTitle("  Users API  "))
    }

    @Test
    fun formatDetachedResponseTitle_reflectsActiveTabOrDefaults() {
        assertEquals("Clapi - Response", formatDetachedResponseTitle(null))
        assertEquals("Clapi - Response", formatDetachedResponseTitle("  "))
        assertEquals("Get Users - Response - Clapi", formatDetachedResponseTitle("Get Users"))
    }

    @Test
    fun crashDialogWindowIcon_resourceExistsOnClasspath() {
        val stream = this::class.java.classLoader.getResourceAsStream("icons/clapi-icon-64.png")
        assertNotNull(stream, "Clapi taskbar icon resource must exist for main and crash windows")
        stream.close()
    }

    // ── 2. Request Editor tabs horizontal scrolling ───────────────────────────

    @Test
    fun requestEditorTabs_scrollHorizontallyWhenConstrained() {
        val state = AppState(openDefaultTab = true)
        val tab = state.activeTab!!
        tab.selectedEditorTab = RequestEditorTab.PARAMS

        composeRule.setContent {
            ClapiTheme {
                Box(modifier = Modifier.width(220.dp)) {
                    RequestEditor(
                        tab = tab,
                        state = state,
                        onSend = {},
                        onSave = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor-tab-bar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("editor-tabs-scrollbar", useUnmergedTree = true).assertExists()

        // Scroll horizontally via mouse wheel over the tab row to reach the rightmost tab ("Tests")
        composeRule.onNodeWithTag("editor-tab-row", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            scroll(20f)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor-tab-tests", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(RequestEditorTab.TESTS, tab.selectedEditorTab)
    }

    // ── 3. Double-click on open tab to inline rename ──────────────────────────

    @Test
    fun doubleClickOpenRequestTab_inlineRenamesTabAndCollectionNode() {
        val state = AppState(openDefaultTab = false)
        state.collections.clear()
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Users API",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode("r1", "Get Users", method = HttpMethodType.GET, url = "https://example.com/users"),
                ),
            )
        )
        state.openRequest("r1", "Get Users", HttpMethodType.GET, "https://example.com/users")

        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        testDoubleClickActive = true
        try {
            composeRule.onNodeWithTag("tab-chip-r1", useUnmergedTree = true).performMouseInput {
                click()
            }
        } finally {
            testDoubleClickActive = false
        }
        composeRule.waitForIdle()

        val renameInput = composeRule.onNodeWithTag("tab-rename-input-r1", useUnmergedTree = true)
        renameInput.assertIsDisplayed()
        renameInput.performTextClearance()
        renameInput.performTextInput("List All Users")
        renameInput.performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals("List All Users", state.openTabs.first().name)
        assertEquals("List All Users", state.collections[0].children[0].name)
    }

    @Test
    fun doubleClickOpenFolderTab_inlineRenamesFolderEverywhere() {
        val state = AppState(openDefaultTab = false)
        state.collections.clear()
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Users API",
                isFolder = true,
                children = mutableStateListOf(),
            )
        )
        state.openFolder("c1")

        composeRule.setContent {
            ClapiTheme {
                MainScreen(state)
            }
        }
        composeRule.waitForIdle()

        testDoubleClickActive = true
        try {
            composeRule.onNodeWithTag("tab-chip-c1", useUnmergedTree = true).performMouseInput {
                click()
            }
        } finally {
            testDoubleClickActive = false
        }
        composeRule.waitForIdle()

        val renameInput = composeRule.onNodeWithTag("tab-rename-input-c1", useUnmergedTree = true)
        renameInput.assertIsDisplayed()
        renameInput.performTextClearance()
        renameInput.performTextInput("Accounts API")
        renameInput.performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals("Accounts API", state.openTabs.first().name)
        assertEquals("Accounts API", state.collections[0].name)
    }

    // ── 4. Code Editor toolbar buttons hover tooltips ─────────────────────────

    @Test
    fun codeEditorToolbarButtons_showHoverTooltips() {
        val sampleJson = """
            {
              "user": {
                "name": "Alice"
              }
            }
        """.trimIndent()

        var buttonToExpectedTooltip: List<Pair<String, String>> = emptyList()

        composeRule.setContent {
            ClapiTheme {
                buttonToExpectedTooltip = listOf(
                    "test-editor-format-toggle" to Strings.format,
                    "test-editor-word-wrap-toggle" to Strings.disableWordWrap,
                    "test-editor-search-toggle" to Strings.search,
                    "test-editor-fold-all" to Strings.collapseAll,
                    "test-editor-unfold-all" to Strings.expandAll,
                    "test-editor-copy-button" to Strings.copyBody,
                )
                CodeEditor(
                    text = sampleJson,
                    onTextChange = {},
                    language = SyntaxLanguage.JSON,
                    enableFolding = true,
                    enableSearch = true,
                    enableFormat = true,
                    enableWordWrap = true,
                    enableCopy = true,
                    testTagPrefix = "test-editor",
                )
            }
        }
        composeRule.waitForIdle()

        for ((tag, expectedText) in buttonToExpectedTooltip) {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performMouseInput {
                enter(center)
            }
            composeRule.mainClock.advanceTimeBy(300L)
            composeRule.waitForIdle()

            val tooltipNode = composeRule.onNodeWithTag("$tag-tooltip", useUnmergedTree = true)
            tooltipNode.assertIsDisplayed()
            tooltipNode.onChild().assertTextEquals(expectedText)

            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performMouseInput {
                exit(Offset(-50f, -50f))
            }
            composeRule.waitForIdle()
        }
    }
}
