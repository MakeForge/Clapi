package io.github.makeforge.clapi.ui.shared.mcp.server

import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ManageFolderInput
import io.github.makeforge.clapi.mcp.server.backend.SetVariableInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertEnvironmentInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertRequestInput
import io.github.makeforge.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateWorkspaceBackendTest {

    @Test
    fun `synchronizes MCP mutations bidirectionally with live AppState`() = runTest {
        val tempDir = Files.createTempDirectory("clapi-appstate-mcp-test").toFile()
        val previousRepo = WorkspaceRepository.canonicalRepository
        try {
            WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository(baseDirectory = tempDir.absolutePath)
            val appState = AppState(openDefaultTab = false)
            appState.collections.clear()
            appState.environments.clear()
            appState.globalVariables.clear()

            // 1. Add a collection directly in AppState (simulating UI creation)
            appState.collections.add(
                CollectionNode(
                    id = "ui-col-1",
                    name = "UI Created API",
                    isFolder = true,
                    kind = RequestKind.COLLECTION,
                )
            )
            WorkspaceRepository.save(appState)

            val backend = AppStateWorkspaceBackend(appState)

            // 2. MCP reads the UI-created collection
            val tree = backend.listTree()
            assertEquals(1, tree.size)
            assertEquals("UI Created API", tree.first().name)

            // 3. MCP creates a new collection, folder, request, environment, and variables
            backend.manageCollection(ManageCollectionInput(action = "create", name = "Agent Created API"))
            backend.manageFolder(ManageFolderInput(action = "create", parentId = "Agent Created API", name = "Users"))
            val createdReq = backend.upsertRequest(
                UpsertRequestInput(
                    parentId = "Agent Created API/Users",
                    name = "List Users",
                    kind = "http",
                    method = "GET",
                    url = "https://api.example.com/users",
                )
            )
            assertEquals("Agent Created API/Users/List Users", createdReq.slashPath)

            // 4. Verify live AppState immediately reflects the MCP-created collection, folder, and request
            assertEquals(2, appState.collections.size)
            val agentCol = appState.collections.first { it.name == "Agent Created API" }
            assertEquals(1, agentCol.children.size)
            assertEquals("Users", agentCol.children.first().name)
            assertEquals("List Users", agentCol.children.first().children.first().name)

            // 5. MCP creates and activates an environment and sets global & environment secret variables
            backend.upsertEnvironment(UpsertEnvironmentInput(name = "Staging", color = "#00FF00"))
            backend.setActiveEnvironment("Staging")
            assertEquals("Staging", appState.environments.getOrNull(appState.selectedEnvIndex)?.name)

            val secretVar = backend.setVariable(
                SetVariableInput(
                    scope = "environment",
                    target = "Staging",
                    key = "stagingSecret",
                    kind = "secret",
                    variableType = "string",
                    value = "top-secret-staging-key",
                )
            )
            assertEquals(MASKED_SECRET_PLACEHOLDER, secretVar.value)
            assertTrue(secretVar.isSet)

            val globalNumVar = backend.setVariable(
                SetVariableInput(
                    scope = "global",
                    key = "maxRetries",
                    kind = "regular",
                    variableType = "number",
                    value = "3",
                )
            )
            assertEquals("number", globalNumVar.variableType)
            assertTrue(appState.globalVariables.any { it.key == "maxRetries" && it.value == "3" })

            // 6. Verify EmbeddedMcpServerController start/stop lifecycle
            val controller = appState.embeddedMcpServerController
            assertFalse(controller.isRunning)
            val started = controller.start(port = 0)
            assertTrue(started)
            assertTrue(controller.isRunning)
            assertTrue(controller.activePort > 0)

            appState.disposeAndAwait()
            assertFalse(controller.isRunning)
        } finally {
            WorkspaceRepository.canonicalRepository = previousRepo
            tempDir.deleteRecursively()
        }
    }
}
