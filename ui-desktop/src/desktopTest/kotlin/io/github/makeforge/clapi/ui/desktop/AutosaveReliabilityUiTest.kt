package io.github.makeforge.clapi.ui.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.persistence.TabsRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AutosaveReliabilityUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun clearStorage() {
        PlatformStorage.remove("clapi.tabs")
    }

    @After
    fun cleanupStorage() {
        PlatformStorage.remove("clapi.tabs")
    }

    @Test
    fun same_length_edit_triggers_real_main_screen_autosave_and_reload() {
        val state = AppState()
        state.settings.autoSaveRequests = true
        val tab = state.activeTab!!
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "before"
        tab.markSaved()
        TabsRepository.save(state)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            tab.bodyContent = "after!"
            tab.markDirty()
        }
        composeRule.waitUntil(5_000) {
            PlatformStorage.getString("clapi.tabs")?.contains("after!") == true
        }

        val restored = AppState(openDefaultTab = false)
        TabsRepository.load(restored)
        assertEquals("after!", restored.activeTab?.bodyContent)
        restored.dispose()
    }
}
