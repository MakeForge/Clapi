package io.github.crustack.clapi.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.ui.shared.components.GrpcRequestEditor
import io.github.crustack.clapi.ui.shared.components.SelectProtoFileDialog
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.ProtoFileItemState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import io.github.crustack.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GrpcProtoSelectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectProtoFileDialog_renders_collection_proto_files_and_selects_on_click() {
        var savedPath: String? = null
        val collectionProtos = listOf(
            "/home/user/protos/greeter.proto",
            "/home/user/protos/echo.proto",
        )

        composeRule.setContent {
            ClapiTheme {
                SelectProtoFileDialog(
                    currentProtoFilePath = null,
                    collectionProtoFiles = collectionProtos,
                    onSave = { savedPath = it },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-collection-list").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-collection-item-/home/user/protos/greeter.proto").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-collection-item-/home/user/protos/echo.proto").assertIsDisplayed()

        // Select greeter.proto
        composeRule.onNodeWithTag("proto-dialog-collection-item-/home/user/protos/greeter.proto").performClick()
        composeRule.waitForIdle()

        // Click Save
        composeRule.onNodeWithTag("proto-dialog-save").performClick()
        composeRule.waitForIdle()

        assertEquals("/home/user/protos/greeter.proto", savedPath)
    }

    @Test
    fun selectProtoFileDialog_supports_manual_picker_when_collection_protos_present() {
        var savedPath: String? = null
        val collectionProtos = listOf("/home/user/protos/greeter.proto")

        composeRule.setContent {
            ClapiTheme {
                SelectProtoFileDialog(
                    currentProtoFilePath = null,
                    collectionProtoFiles = collectionProtos,
                    onSave = { savedPath = it },
                    onDismiss = {},
                    onPickFile = { cb -> cb("/manual/disk/custom.proto") },
                )
            }
        }
        composeRule.waitForIdle()

        // Pick from computer via picker action
        composeRule.onNodeWithTag("proto-dialog-pick-button").performClick()
        composeRule.waitForIdle()

        // Save
        composeRule.onNodeWithTag("proto-dialog-save").performClick()
        composeRule.waitForIdle()

        assertEquals("/manual/disk/custom.proto", savedPath)
    }

    @Test
    fun grpcRequestEditor_dialog_includes_state_collection_proto_files() {
        val state = AppState()
        state.collectionProtoFiles.add(ProtoFileItemState("/shared/protos/greeter.proto"))
        val tab = RequestTabState(
            id = "req-grpc-1",
            name = "Greeter Test",
            kind = RequestKind.GRPC,
        )

        composeRule.setContent {
            ClapiTheme {
                GrpcRequestEditor(
                    tab = tab,
                    state = state,
                    onSend = {},
                    onSave = {},
                )
            }
        }
        composeRule.waitForIdle()

        // Click proto selector button to open dialog
        composeRule.onNodeWithTag("grpc-proto-button").performClick()
        composeRule.waitForIdle()

        // Verify collection proto file is displayed in dialog
        composeRule.onNodeWithTag("proto-dialog-collection-item-/shared/protos/greeter.proto").assertIsDisplayed()
    }

    @Test
    fun selectProtoFileDialog_displays_builtin_types_hint_and_link() {
        composeRule.setContent {
            ClapiTheme {
                SelectProtoFileDialog(
                    currentProtoFilePath = null,
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("proto-dialog-builtin-hint").assertIsDisplayed()
        composeRule.onNodeWithTag("proto-dialog-builtin-link").assertIsDisplayed()
    }
}
