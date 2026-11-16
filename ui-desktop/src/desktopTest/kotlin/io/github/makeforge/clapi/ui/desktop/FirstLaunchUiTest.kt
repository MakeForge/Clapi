package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.executor.saveRequest
import io.github.makeforge.clapi.ui.shared.persistence.SettingsRepository
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstLaunchUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        PlatformStorage.remove("clapi.tabs")
        PlatformStorage.remove("clapi.workspace")
    }

    @After
    fun tearDown() {
        PlatformStorage.remove("clapi.tabs")
        PlatformStorage.remove("clapi.workspace")
    }

    @Test
    fun first_launch_has_clean_workspace_and_no_open_request() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("empty-workspace-view").assertIsDisplayed()
        composeRule.onAllNodesWithText("Untitled").assertCountEquals(0)
        assertTrue(state.openTabs.isEmpty())
        assertTrue(state.activeTabIndex == -1)
    }

    @Test
    fun first_launch_has_no_environments() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        assertTrue(state.environments.isEmpty())
    }

    @Test
    fun collections_and_environments_start_collapsed() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        assertFalse(state.collectionsExpanded)
        assertFalse(state.environmentsExpanded)
    }

    @Test
    fun import_icons_exist_for_collections_and_environments() {
        val state = AppState(openDefaultTab = false).apply {
            collectionsExpanded = true
            environmentsExpanded = true
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-import-icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("environment-import-icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun create_request_from_empty_state_creates_default_collection_and_request() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("empty-create-request").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.collections.size)
        assertEquals("Default Collection", state.collections.first().name)
        assertEquals(1, state.collections.first().children.size)
        assertEquals(1, state.openTabs.size)
        composeRule.onNodeWithTag("request-editor").assertIsDisplayed()
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun add_collection_add_request_enter_random_url_and_save_succeeds() {
        val tempDir = java.nio.file.Files.createTempDirectory("clapi-test").toFile()
        try {
            WorkspaceRepository.canonicalRepository = io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository(baseDirectory = tempDir.absolutePath)
            val state = AppState(openDefaultTab = false).also {
                SettingsRepository.load(it.settings)
                it.collectionsExpanded = it.settings.collectionsExpanded
                it.environmentsExpanded = it.settings.environmentsExpanded
                WorkspaceRepository.load(it)
                TabsRepository.load(it)
                it.sidebarSearchQuery = ""
                it.syncSidebarToActiveTab()
            }
            composeRule.setContent { MainScreen(state) }
            composeRule.waitForIdle()

            // 1. Add new collection
            composeRule.onNodeWithTag("sidebar-add-collection-button").performClick()
            composeRule.waitForIdle()
            assertEquals(1, state.collections.size)
            assertEquals("New Collection", state.collections.first().name)

            // 2. Add new request in that collection via tab bar '+' button
            composeRule.onNodeWithContentDescription("New tab").performClick()
            composeRule.waitForIdle()
            assertEquals(1, state.openTabs.size)
            val tab = state.openTabs.first()
            assertEquals("New Request", tab.name)

            // 3. Enter random url
            val testUrl = "https://example.com/random-url-test"
            composeRule.onNodeWithTag("url-input").performClick()
            composeRule.onNodeWithTag("url-input").performTextInput(testUrl)
            composeRule.waitForIdle()
            assertTrue(tab.isDirty)

            // 4. Save request
            composeRule.onNodeWithTag("save-button").performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(10_000) {
                state.consoleLogs.any { it.message.contains("Request saved") || it.message.contains("Failed to save") }
            }

            val logs = state.consoleLogs.map { it.message }
            assertFalse(logs.any { it.contains("Failed to save") }, "Save failed with logs: $logs")
            assertTrue(logs.any { it.contains("Request saved") }, "Expected success log, got: $logs")
            assertFalse(tab.isDirty, "Tab should be clean after saving")

            val savedNode = state.collections.first().children.first()
            assertEquals(testUrl, savedNode.url)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun add_collection_add_request_enter_url_save_and_reload_roundtrip() {
        val tempDir = java.nio.file.Files.createTempDirectory("clapi-test-roundtrip").toFile()
        try {
            val repo = io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository(baseDirectory = tempDir.absolutePath)
            WorkspaceRepository.canonicalRepository = repo

            val state = AppState(openDefaultTab = false)
            WorkspaceRepository.load(state)
            TabsRepository.load(state)

            // Add collection & request
            state.createNewCollection("My API Collection")
            val col = state.collections.first()
            state.addRequestToCollection(col.id)

            val tab = state.openTabs.first()
            val initialReqId = tab.id
            val testUrl = "https://api.example.com/v1/users"
            tab.url = testUrl
            tab.isDirty = true

            // Save
            val savedLatch = kotlinx.coroutines.CompletableDeferred<Unit>()
            kotlinx.coroutines.runBlocking {
                saveRequest(this, state, tab) { savedLatch.complete(Unit) }
                savedLatch.await()
            }
            assertFalse(tab.isDirty)
            assertFalse(state.consoleLogs.any { it.message.contains("Failed to save") })

            // Reload into fresh AppState
            val reloadedState = AppState(openDefaultTab = false)
            WorkspaceRepository.load(reloadedState)
            TabsRepository.load(reloadedState)

            assertEquals(1, reloadedState.collections.size)
            val reloadedCol = reloadedState.collections.first()
            assertEquals("My API Collection", reloadedCol.name)
            assertTrue(reloadedCol.isCollection)
            assertEquals(io.github.makeforge.clapi.core.model.RequestKind.COLLECTION, reloadedCol.kind)
            assertEquals(1, reloadedCol.children.size)

            val reloadedReq = reloadedCol.children.first()
            assertEquals("New Request", reloadedReq.name)
            assertEquals(testUrl, reloadedReq.url)
            assertEquals(initialReqId, reloadedReq.id, "Request ID must be preserved across saves and reloads")

            assertEquals(1, reloadedState.openTabs.size)
            val reloadedTab = reloadedState.openTabs.first()
            assertEquals(initialReqId, reloadedTab.id)
            assertEquals(testUrl, reloadedTab.url)

            // Edit and save again on the reloaded state
            reloadedTab.url = "https://api.example.com/v2/users"
            reloadedTab.isDirty = true
            val reloadedLatch = kotlinx.coroutines.CompletableDeferred<Unit>()
            kotlinx.coroutines.runBlocking {
                saveRequest(this, reloadedState, reloadedTab) { reloadedLatch.complete(Unit) }
                reloadedLatch.await()
            }

            assertFalse(reloadedTab.isDirty)
            assertFalse(reloadedState.consoleLogs.any { it.message.contains("Failed to save") })
            assertTrue(reloadedState.consoleLogs.any { it.message.contains("Request saved") })
            assertEquals("https://api.example.com/v2/users", reloadedCol.children.first().url)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
