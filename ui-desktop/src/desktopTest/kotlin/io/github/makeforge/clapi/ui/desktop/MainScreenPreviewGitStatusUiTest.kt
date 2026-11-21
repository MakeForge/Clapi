package io.github.makeforge.clapi.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenGraphQlPreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenGrpcPreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenHttpPreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenMcpPreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenRabbitMqPublishPreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenRabbitMqRpcPreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenRabbitMqSubscribePreviewLight
import io.github.makeforge.clapi.ui.desktop.preview.MainScreenWebSocketPreviewLight
import org.junit.Rule
import org.junit.Test

class MainScreenPreviewGitStatusUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun assertPreviewShowsActiveGitStatus(preview: @Composable () -> Unit) {
        composeRule.setContent { preview() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-git-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar-git-branch-label", useUnmergedTree = true).assertTextEquals("main")
        composeRule.onNodeWithTag("sidebar-git-dirty-dot", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("git-sync-dialog").assertDoesNotExist()
    }

    @Test
    fun http_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenHttpPreviewLight()
    }

    @Test
    fun grpc_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenGrpcPreviewLight()
    }

    @Test
    fun websocket_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenWebSocketPreviewLight()
    }

    @Test
    fun graphql_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenGraphQlPreviewLight()
    }

    @Test
    fun mcp_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenMcpPreviewLight()
    }

    @Test
    fun rabbitmq_publish_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenRabbitMqPublishPreviewLight()
    }

    @Test
    fun rabbitmq_subscribe_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenRabbitMqSubscribePreviewLight()
    }

    @Test
    fun rabbitmq_rpc_preview_shows_active_git_sync_status() = assertPreviewShowsActiveGitStatus {
        MainScreenRabbitMqRpcPreviewLight()
    }
}
