package io.github.crustack.ui.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.github.crustack.ui.shared.MainScreen
import io.github.crustack.ui.shared.persistence.SettingsRepository
import io.github.crustack.ui.shared.persistence.TabsRepository
import io.github.crustack.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.ui.shared.state.AppState
import io.github.crustack.ui.shared.theme.ClapiTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Clapi") {
        val state = remember {
            AppState(openDefaultTab = false).also {
                SettingsRepository.load(it.settings)
                it.collectionsExpanded = it.settings.collectionsExpanded
                it.environmentsExpanded = it.settings.environmentsExpanded
                WorkspaceRepository.load(it)   // collections first
                TabsRepository.load(it)         // IDs resolved against loaded collections
                it.syncSidebarToActiveTab()
            }
        }
        ClapiTheme(appTheme = state.settings.theme, language = state.settings.language) {
            MainScreen(state)
        }
    }
}
