package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.CodeEditor
import io.github.makeforge.clapi.ui.shared.components.VariableAwareTextField
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.platform.testDoubleClickActive
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class, ExperimentalTestApi::class)
class GeneralImprovementsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        testDoubleClickActive = false
    }

    // ── 1. Double clicking folder/request renames inline (no dialog) ──

    @Test
    fun double_clicking_folder_triggers_inline_rename_without_dialog() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val folder = CollectionNode(
            id = "f-inline",
            name = "MyFolder",
            isFolder = true,
        )
        state.collections.add(folder)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Verify initial text is displayed
        composeRule.onNodeWithTag("folder-name-f-inline", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("folder-rename-input-f-inline").assertCountEquals(0)

        // Double click on folder name
        testDoubleClickActive = true
        try {
            composeRule.onNodeWithTag("folder-name-f-inline", useUnmergedTree = true).performClick()
        } finally {
            testDoubleClickActive = false
        }
        composeRule.waitForIdle()

        // Inline input should be visible; dialog should NOT exist
        composeRule.onNodeWithTag("folder-rename-input-f-inline").assertIsDisplayed()
        composeRule.onAllNodesWithTag("rename-folder-dialog").assertCountEquals(0)

        // Type new name and commit with Enter
        composeRule.onNodeWithTag("folder-rename-input-f-inline").performTextReplacement("MyFolder Renamed")
        composeRule.onNodeWithTag("folder-rename-input-f-inline").performKeyPress(
            KeyEvent(key = Key.Enter, type = KeyEventType.KeyDown)
        )
        composeRule.waitForIdle()

        // Inline input should be gone and folder node updated
        composeRule.onAllNodesWithTag("folder-rename-input-f-inline").assertCountEquals(0)
        assertEquals("MyFolder Renamed", state.collections.first { it.id == "f-inline" }.name)
    }

    @Test
    fun inline_rename_folder_renames_open_folder_tab_as_well() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val folder = CollectionNode(
            id = "f-open-tab",
            name = "MyFolderTab",
            isFolder = true,
        )
        state.collections.add(folder)
        state.openFolder("f-open-tab")
        assertEquals(1, state.openTabs.size)
        assertEquals("MyFolderTab", state.openTabs[0].name)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Double click on folder name
        testDoubleClickActive = true
        try {
            composeRule.onNodeWithTag("folder-name-f-open-tab", useUnmergedTree = true).performClick()
        } finally {
            testDoubleClickActive = false
        }
        composeRule.waitForIdle()

        // Rename folder inline
        composeRule.onNodeWithTag("folder-rename-input-f-open-tab").performTextReplacement("MyFolderTab Updated")
        composeRule.onNodeWithTag("folder-rename-input-f-open-tab").performKeyPress(
            KeyEvent(key = Key.Enter, type = KeyEventType.KeyDown)
        )
        composeRule.waitForIdle()

        // Open folder tab must also be updated!
        assertEquals("MyFolderTab Updated", state.openTabs[0].name)
        assertEquals("MyFolderTab Updated", state.collections.first { it.id == "f-open-tab" }.name)
    }

    @Test
    fun double_clicking_request_triggers_inline_rename_without_dialog() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val req = CollectionNode(
            id = "r-inline",
            name = "GetUsers",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://example.com/users",
        )
        state.collections.add(req)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Verify initial text is displayed
        composeRule.onNodeWithTag("request-name-r-inline", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("request-rename-input-r-inline").assertCountEquals(0)

        // Double click on request name
        testDoubleClickActive = true
        try {
            composeRule.onNodeWithTag("request-name-r-inline", useUnmergedTree = true).performClick()
        } finally {
            testDoubleClickActive = false
        }
        composeRule.waitForIdle()

        // Inline input should be visible; dialog should NOT exist
        composeRule.onNodeWithTag("request-rename-input-r-inline").assertIsDisplayed()
        composeRule.onAllNodesWithTag("rename-request-dialog").assertCountEquals(0)

        // Type new name and commit with Enter
        composeRule.onNodeWithTag("request-rename-input-r-inline").performTextReplacement("GetUsers V2")
        composeRule.onNodeWithTag("request-rename-input-r-inline").performKeyPress(
            KeyEvent(key = Key.Enter, type = KeyEventType.KeyDown)
        )
        composeRule.waitForIdle()

        // Inline input should be gone and request node updated
        composeRule.onAllNodesWithTag("request-rename-input-r-inline").assertCountEquals(0)
        assertEquals("GetUsers V2", state.collections.first { it.id == "r-inline" }.name)
    }

    // ── 2. Inside editor, Shift+Tab focuses next open tab ──

    @Test
    fun shift_tab_in_editor_focuses_next_open_tab_and_wraps_around() {
        val state = AppState(withDemoData = false, openDefaultTab = false)
        state.addTab(name = "Tab 1", method = HttpMethodType.GET)
        state.addTab(name = "Tab 2", method = HttpMethodType.POST)
        state.addTab(name = "Tab 3", method = HttpMethodType.PUT)
        state.activeTabIndex = 0

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        assertEquals(0, state.activeTabIndex)

        // Focus URL input inside editor and press Shift+Tab
        composeRule.onNodeWithTag("url-input").performClick()
        composeRule.onNodeWithTag("url-input").performKeyPress(
            KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
        )
        composeRule.waitForIdle()

        // Active tab should have moved to Tab 2 (index 1)
        assertEquals(1, state.activeTabIndex)

        // Focus and press Shift+Tab again -> Tab 3 (index 2)
        composeRule.onNodeWithTag("url-input").performClick()
        composeRule.onNodeWithTag("url-input").performKeyPress(
            KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
        )
        composeRule.waitForIdle()
        assertEquals(2, state.activeTabIndex)

        // Focus and press Shift+Tab again -> Tab 1 (index 0)
        composeRule.onNodeWithTag("url-input").performClick()
        composeRule.onNodeWithTag("url-input").performKeyPress(
            KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
        )
        composeRule.waitForIdle()
        assertEquals(0, state.activeTabIndex)
    }

    // ── 3. Ctrl+Space shows variable dropdown without inserting whitespace ──

    @Test
    fun ctrl_space_in_variable_aware_text_field_shows_dropdown_and_does_not_insert_whitespace() {
        var textValue by mutableStateOf("https://example.com/api/")
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            environments.add(EnvState("Dev").apply {
                variables.add(MutableKeyValue("token", "secret123"))
                variables.add(MutableKeyValue("host", "api.example.com"))
            })
            selectedEnvIndex = 0
        }
        val focusRequester = FocusRequester()

        composeRule.setContent {
            VariableAwareTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.focusRequester(focusRequester).testTag("test-input"),
                state = state,
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { focusRequester.requestFocus() }
        composeRule.waitForIdle()

        // Initially dropdown is not visible
        composeRule.onAllNodesWithTag("variable-dropdown").assertCountEquals(0)

        // Press Ctrl+Space to trigger dropdown
        val input = composeRule.onNodeWithTag("test-input")
        input.performKeyPress(
            KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyDown, isCtrlPressed = true)
        )
        input.performKeyPress(
            KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyUp, isCtrlPressed = true)
        )
        composeRule.waitForIdle()

        // Crucial: no whitespace should be added to the field!
        assertEquals("https://example.com/api/", textValue)

        // Dropdown should appear showing available variables
        composeRule.onNodeWithTag("variable-dropdown", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("variable-dropdown-item-token", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("variable-dropdown-item-host", useUnmergedTree = true).assertIsDisplayed()

        // Click on the "token" variable item
        composeRule.onNodeWithTag("variable-dropdown-item-token", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Dropdown should dismiss and {{token}} should be inserted
        composeRule.onAllNodesWithTag("variable-dropdown").assertCountEquals(0)
        assertTrue(textValue.contains("{{token}}"), "Expected text to contain {{token}}, was $textValue")
    }

    @Test
    fun ctrl_space_with_subsequent_space_input_is_suppressed() {
        var textValue by mutableStateOf("https://example.com/api/")
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            environments.add(EnvState("Dev").apply {
                variables.add(MutableKeyValue("token", "secret123"))
            })
            selectedEnvIndex = 0
        }
        val focusRequester = FocusRequester()

        composeRule.setContent {
            VariableAwareTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.focusRequester(focusRequester).testTag("native-input"),
                state = state,
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { focusRequester.requestFocus() }
        composeRule.waitForIdle()

        val input = composeRule.onNodeWithTag("native-input")

        // Trigger Ctrl+Space
        input.performKeyPress(
            KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyDown, isCtrlPressed = true)
        )
        // Simulate IME / text-input pipeline attempting to commit a space right after Ctrl+Space
        input.performTextInput(" ")
        input.performKeyPress(
            KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyUp, isCtrlPressed = true)
        )
        composeRule.waitForIdle()

        // Crucial: no whitespace should be inserted! Space was suppressed by recent Ctrl+Space detection.
        assertEquals("https://example.com/api/", textValue)
        composeRule.onNodeWithTag("variable-dropdown", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun code_editor_ctrl_space_opens_dropdown_without_inserting_whitespace() {
        var textContent by mutableStateOf("{\n  \"field\": 1\n}")
        val variables = listOf("apiKey" to "xyz123")
        composeRule.setContent {
            CodeEditor(
                text = textContent,
                onTextChange = { textContent = it },
                availableVariables = variables,
                testTagPrefix = "test-code-editor",
            )
        }
        composeRule.waitForIdle()
        val editor = composeRule.onNodeWithTag("test-code-editor")
        editor.performKeyPress(
            KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyDown, isCtrlPressed = true)
        )
        editor.performKeyPress(
            KeyEvent(key = Key.Spacebar, type = KeyEventType.KeyUp, isCtrlPressed = true)
        )
        composeRule.waitForIdle()

        assertEquals("{\n  \"field\": 1\n}", textContent)
        composeRule.onNodeWithTag("code-editor-variable-dropdown", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("variable-dropdown-item-apiKey", useUnmergedTree = true).assertIsDisplayed()
    }

    // ── 4. Deleting a folder in sidebar closes tabs for folder and nested requests ──

    @Test
    fun deleting_folder_in_sidebar_closes_folder_tab_and_nested_request_tabs() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
            settings.confirmBeforeDelete = false
        }
        val childReq = CollectionNode(
            id = "child-req-1",
            name = "Nested Request",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://example.com",
        )
        val folder = CollectionNode(
            id = "parent-folder-1",
            name = "Parent Folder",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(childReq),
        )
        state.collections.add(folder)

        // Open both the folder tab and the nested request tab
        state.openFolder("parent-folder-1")
        state.openRequest(requestId = "child-req-1", name = "Nested Request", method = HttpMethodType.GET, url = "https://example.com")
        assertEquals(2, state.openTabs.size)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Open folder action menu and click delete
        composeRule.onNodeWithTag("collection-actions-parent-folder-1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // Both open tabs (the folder tab and the child request tab) must be closed
        assertEquals(0, state.openTabs.size)
        assertEquals(0, state.collections.size)
    }

    // ── 5. Folder single click opens tab and expands folder (stays expanded if already expanded) ──

    @Test
    fun single_click_folder_name_opens_tab_and_expands_without_collapsing() {
        val state = AppState(withDemoData = false, openDefaultTab = false).apply {
            collectionsExpanded = true
        }
        val folder = CollectionNode(
            id = "f-expand-test",
            name = "ExpandFolder",
            isFolder = true,
        )
        state.collections.add(folder)

        // Start with folder collapsed
        state.collectionExpandedState["f-expand-test"] = false

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Click folder name
        composeRule.onNodeWithTag("folder-name-f-expand-test", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Folder tab must be opened and folder must be expanded
        assertEquals(1, state.openTabs.size)
        assertEquals("f-expand-test", state.openTabs[0].id)
        assertEquals(true, state.collectionExpandedState["f-expand-test"])

        // Click folder name again — must stay expanded (not collapse!)
        composeRule.onNodeWithTag("folder-name-f-expand-test", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(true, state.collectionExpandedState["f-expand-test"])
    }

    // ── 6. Restoring tabs does not reopen dangling tabs for deleted items ──

    @Test
    fun tabs_repository_load_only_reopens_existing_items() {
        val originState = AppState(withDemoData = false, openDefaultTab = false)
        val validReq = CollectionNode(
            id = "req-valid",
            name = "Valid Req",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://example.com",
        )
        val validFolder = CollectionNode(
            id = "folder-valid",
            name = "Valid Folder",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(validReq),
        )
        val deletedReq = CollectionNode(
            id = "req-deleted",
            name = "Deleted Req",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://deleted.com",
        )
        val deletedFolder = CollectionNode(
            id = "folder-deleted",
            name = "Deleted Folder",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(deletedReq),
        )
        originState.collections.addAll(listOf(validFolder, deletedFolder))

        // Open tabs for valid folder, valid request, and to-be-deleted request/folder
        originState.openFolder("folder-valid")
        originState.openRequest(requestId = "req-valid", name = "Valid Req", method = HttpMethodType.GET, url = "https://example.com")
        originState.openFolder("folder-deleted")
        originState.openRequest(requestId = "req-deleted", name = "Deleted Req", method = HttpMethodType.GET, url = "https://deleted.com")
        assertEquals(4, originState.openTabs.size)

        // Persist tabs
        TabsRepository.save(originState)

        // New state has ONLY validFolder and validReq (deletedFolder was deleted)
        val restoredState = AppState(withDemoData = false, openDefaultTab = false)
        val restoredReq = CollectionNode(
            id = "req-valid",
            name = "Valid Req",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://example.com",
        )
        val restoredFolder = CollectionNode(
            id = "folder-valid",
            name = "Valid Folder",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(restoredReq),
        )
        restoredState.collections.add(restoredFolder)

        // Load tabs: only 2 valid tabs should be restored; the 2 dangling tabs should be omitted!
        TabsRepository.load(restoredState)
        assertEquals(2, restoredState.openTabs.size)
        assertEquals(listOf("folder-valid", "req-valid"), restoredState.openTabs.map { it.id })
    }
}
