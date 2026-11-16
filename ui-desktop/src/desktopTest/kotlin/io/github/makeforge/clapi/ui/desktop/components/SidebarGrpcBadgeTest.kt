package io.github.makeforge.clapi.ui.desktop.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.MainScreen
import io.github.makeforge.clapi.ui.shared.components.GrpcBadge
import io.github.makeforge.clapi.ui.shared.components.resolveGrpcCallRequest
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import io.github.makeforge.clapi.ui.shared.state.RequestTabState
import io.github.makeforge.clapi.ui.shared.theme.ClapiTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SidebarGrpcBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun grpcBadge_rendersText() {
        composeRule.setContent {
            ClapiTheme {
                GrpcBadge(compact = true)
            }
        }
        composeRule.onNodeWithText("gRPC").assertIsDisplayed()
    }

    @Test
    fun folderAddButton_showsHttpAndGrpcOptionsInContextMenu() {
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
    }

    @Test
    fun selectingNewGrpcRequest_createsGrpcNodeAndDisplaysBadgesInSidebarAndTab() {
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
        composeRule.onNodeWithTag("folder-menu-new-grpc", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(beforeCount + 1, rootFolder.children.size)
        val createdNode: CollectionNode = rootFolder.children.last()
        assertEquals(RequestKind.GRPC, createdNode.kind)
        assertTrue(createdNode.name.startsWith("New Request"))

        // Verify sidebar gRPC badge is displayed
        composeRule.onNodeWithTag("grpc-badge-${createdNode.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("gRPC")

        // Verify tab gRPC badge is displayed
        composeRule.onNodeWithTag("tab-grpc-badge-${createdNode.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("gRPC")

        // Verify GrpcRequestEditor is rendered for the active gRPC tab
        composeRule.onNodeWithTag("grpc-request-editor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("grpc-proto-button", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("grpc-method-dropdown", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("grpc-tab-message", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("grpc-tab-headers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("grpc-tab-scripts", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun grpcProtoDialog_parsesSelectedProtoAndPopulatesMethods() {
        val tempProto = File.createTempFile("ui_proto_test_", ".proto")
        try {
            tempProto.writeText(
                """
                syntax = "proto3";
                package helloworld;

                service Greeter {
                  rpc SayHello (HelloRequest) returns (HelloReply);
                  rpc SayHelloAgain (HelloRequest) returns (HelloReply);
                }

                message HelloRequest { string name = 1; }
                message HelloReply { string message = 1; }
                """.trimIndent()
            )

            val state = AppState(openDefaultTab = false, withDemoData = true)
            val rootFolder = state.collections.first()
            state.addGrpcRequestToCollection(rootFolder.id)

            composeRule.setContent {
                ClapiTheme {
                    MainScreen(state)
                }
            }

            composeRule.onNodeWithTag("grpc-proto-button", useUnmergedTree = true).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("select-proto-file-dialog", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("proto-dialog-path-input", useUnmergedTree = true)
                .performTextInput(tempProto.absolutePath)
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("proto-dialog-save", useUnmergedTree = true).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                state.activeTab?.availableMethods?.size == 2
            }

            val activeTab = state.activeTab!!
            assertEquals(tempProto.absolutePath, activeTab.protoFilePath)
            assertEquals("helloworld.Greeter/SayHello", activeTab.grpcMethod)
            assertEquals(2, activeTab.availableMethods.size)
        } finally {
            tempProto.delete()
        }
    }

    @Test
    fun resolveGrpcCallRequest_substitutesVariablesInUrlMetadataAndMessage() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        state.environments.add(
            EnvState(
                name = "TestEnv",
                variables = listOf(
                    MutableKeyValue("grpcHost", "127.0.0.1"),
                    MutableKeyValue("grpcPort", "50055"),
                    MutableKeyValue("authToken", "secret-jwt"),
                    MutableKeyValue("userName", "Alice"),
                ),
            )
        )
        state.selectedEnvIndex = 0

        val tab = RequestTabState(
            name = "SayHello",
            url = "{{grpcHost}}:{{grpcPort}}",
            kind = RequestKind.GRPC,
        ).apply {
            protoFilePath = "/tmp/greeter.proto"
            grpcMethod = "helloworld.Greeter/SayHello"
            grpcMessageBody = """{"name": "{{userName}}"}"""
            grpcMetadata.add(MutableKeyValue("authorization", "Bearer {{authToken}}"))
        }

        val resolved = resolveGrpcCallRequest(tab, state)
        assertEquals("127.0.0.1", resolved.host)
        assertEquals(50055, resolved.port)
        assertEquals(false, resolved.useTls)
        assertEquals("helloworld.Greeter/SayHello", resolved.fullMethodName)
        assertTrue(resolved.jsonPayload.contains("\"name\":\"Alice\"") || resolved.jsonPayload.contains("\"name\": \"Alice\""))
        assertEquals(listOf("authorization" to "Bearer secret-jwt"), resolved.metadata)
    }
}
