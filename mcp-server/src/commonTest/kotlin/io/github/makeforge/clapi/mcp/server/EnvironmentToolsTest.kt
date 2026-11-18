package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.McpReadResourceResult
import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.mcp.server.backend.EnvironmentsOverviewDto
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.ManageFolderInput
import io.github.makeforge.clapi.mcp.server.backend.SanitizedEnvironmentDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedExternalSecretDeclarationDto
import io.github.makeforge.clapi.mcp.server.backend.SetVariableInput
import io.github.makeforge.clapi.mcp.server.backend.UpsertEnvironmentInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `manages environments inheritance cycle detection and typed variables across all 4 scopes`() = runTest {
        val backend = HeadlessWorkspaceBackend()
        val server = ClapiMcpServer(backend = backend)

        // 1. Create Base and Staging environments (Staging extends Base)
        backend.upsertEnvironment(
            UpsertEnvironmentInput(
                name = "Base",
                color = "#2196F3",
                description = "Root shared environment",
            )
        )
        backend.setVariable(
            SetVariableInput(
                scope = "environment",
                target = "Base",
                key = "baseUrl",
                kind = "regular",
                variableType = "string",
                value = "https://base.example.com",
            )
        )
        backend.setVariable(
            SetVariableInput(
                scope = "environment",
                target = "Base",
                key = "timeoutMs",
                kind = "regular",
                variableType = "number",
                value = "5000",
            )
        )
        backend.setVariable(
            SetVariableInput(
                scope = "environment",
                target = "Base",
                key = "sharedSecret",
                kind = "secret",
                variableType = "string",
                value = "base-secret-value-1234",
            )
        )

        backend.upsertEnvironment(
            UpsertEnvironmentInput(
                name = "Staging",
                color = "#4CAF50",
                extends = "Base",
                externalSecretsProvider = "aws-secrets-manager",
                externalSecrets = listOf(
                    SanitizedExternalSecretDeclarationDto(
                        name = "rdsSecret",
                        providerType = "aws-secrets-manager",
                        path = "staging/db/credentials",
                        targetField = "password",
                    )
                ),
            )
        )
        // Override baseUrl in Staging and add a dynamic variable
        backend.setVariable(
            SetVariableInput(
                scope = "environment",
                target = "Staging",
                key = "baseUrl",
                kind = "regular",
                variableType = "string",
                value = "https://staging.example.com",
            )
        )
        backend.setVariable(
            SetVariableInput(
                scope = "environment",
                target = "Staging",
                key = "nextId",
                kind = "dynamic",
                variableType = "number",
                dynamicScript = "return 42;",
                dynamicParameters = listOf(DynamicVariableParameter(name = "offset", defaultValue = "0")),
            )
        )

        // 2. Verify circular inheritance is rejected (Base cannot extend Staging, and Base cannot extend Base)
        assertFailsWith<IllegalArgumentException> {
            backend.upsertEnvironment(UpsertEnvironmentInput(name = "Base", extends = "Base"))
        }
        assertFailsWith<IllegalArgumentException> {
            backend.upsertEnvironment(UpsertEnvironmentInput(name = "Base", extends = "Staging"))
        }

        // 3. Activate Staging and verify projection via clapi://environments/Staging
        backend.setActiveEnvironment("Staging")
        val envRes = server.handleJsonRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "resources/read")
                put("params", buildJsonObject {
                    put("uri", "clapi://environments/Staging")
                })
            }.toString()
        )
        val contents = json.decodeFromJsonElement(McpReadResourceResult.serializer(), envRes!!["result"]!!)
        val stagingDto = json.decodeFromString(SanitizedEnvironmentDto.serializer(), contents.contents.first().text!!)
        assertTrue(stagingDto.isActive)
        assertEquals("Base", stagingDto.extends)
        assertEquals(1, stagingDto.externalSecrets.size)
        assertEquals("staging/db/credentials", stagingDto.externalSecrets.first().path)

        val baseUrlVar = stagingDto.variables.first { it.key == "baseUrl" }
        assertEquals("https://staging.example.com", baseUrlVar.value)
        assertFalse(baseUrlVar.isInherited)
        assertTrue(baseUrlVar.isOverridden)

        val timeoutVar = stagingDto.variables.first { it.key == "timeoutMs" }
        assertEquals("5000", timeoutVar.value)
        assertEquals("number", timeoutVar.variableType)
        assertTrue(timeoutVar.isInherited)
        assertEquals("Base", timeoutVar.sourceEnvName)

        val secretVar = stagingDto.variables.first { it.key == "sharedSecret" }
        assertEquals(MASKED_SECRET_PLACEHOLDER, secretVar.value)
        assertTrue(secretVar.secret)
        assertTrue(secretVar.isSet)
        assertTrue(secretVar.isInherited)

        val dynVar = stagingDto.variables.first { it.key == "nextId" }
        assertEquals("dynamic", dynVar.kind)
        assertEquals("number", dynVar.variableType)
        assertEquals("return 42;", dynVar.dynamicScript)
        assertEquals(1, dynVar.dynamicParameters.size)

        // 4. Test variables across Global, Collection, and Folder scopes
        val globalBool = backend.setVariable(
            SetVariableInput(
                scope = "global",
                key = "featureFlag",
                kind = "regular",
                variableType = "boolean",
                value = "true",
            )
        )
        assertEquals("boolean", globalBool.variableType)
        assertEquals("true", globalBool.value)

        backend.manageCollection(ManageCollectionInput(action = "create", name = "Orders"))
        backend.manageFolder(ManageFolderInput(action = "create", parentId = "Orders", name = "Admin"))

        val colObjVar = backend.setVariable(
            SetVariableInput(
                scope = "collection",
                target = "Orders",
                key = "defaultMeta",
                kind = "regular",
                variableType = "object",
                value = """{"region":"eu"}""",
            )
        )
        assertEquals("object", colObjVar.variableType)

        val folderSecretVar = backend.setVariable(
            SetVariableInput(
                scope = "folder",
                target = "Orders/Admin",
                key = "adminKey",
                kind = "secret",
                variableType = "string",
                value = "admin-secret-9999",
            )
        )
        assertEquals(MASKED_SECRET_PLACEHOLDER, folderSecretVar.value)
        assertTrue(folderSecretVar.isSet)

        // 5. Verify destructive confirmation on delete_environment
        val unconfirmed = server.handleJsonRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "tools/call")
                put("params", buildJsonObject {
                    put("name", "clapi_delete_environment")
                    put("arguments", buildJsonObject {
                        put("name", "Staging")
                        put("confirm", false)
                    })
                })
            }.toString()
        )
        val unconfirmedRes = json.decodeFromJsonElement(McpToolResult.serializer(), unconfirmed!!["result"]!!)
        assertTrue(unconfirmedRes.isError)
        assertEquals(2, backend.listEnvironments().environments.size)

        val delRes = backend.deleteEnvironment("Staging", confirm = true)
        assertTrue(delRes.deleted)
        val afterDel = backend.listEnvironments()
        assertEquals(1, afterDel.environments.size)
        assertEquals(null, afterDel.activeEnvironment)
    }
}
