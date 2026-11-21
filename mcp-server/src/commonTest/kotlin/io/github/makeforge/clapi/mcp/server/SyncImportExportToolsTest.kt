package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.core.model.McpToolResult
import io.github.makeforge.clapi.core.model.storage.GitStorageConfig
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.model.storage.withStorageExtension
import io.github.makeforge.clapi.core.storage.git.GitCliExecutor
import io.github.makeforge.clapi.core.storage.git.GitCommandResult
import io.github.makeforge.clapi.core.storage.git.GitRepositoryManager
import io.github.makeforge.clapi.mcp.server.backend.ExportCollectionInput
import io.github.makeforge.clapi.mcp.server.backend.GitSyncInput
import io.github.makeforge.clapi.mcp.server.backend.HeadlessWorkspaceBackend
import io.github.makeforge.clapi.mcp.server.backend.ImportSpecInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncImportExportToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `imports OpenAPI 3 and Insomnia specifications rejects Swagger 2 and exports all 4 sanitized formats`() = runTest {
        val backend = HeadlessWorkspaceBackend()
        val server = ClapiMcpServer(backend = backend)

        // 1. Import OpenAPI 3.0 YAML
        val openApiYaml = """
            openapi: 3.0.3
            info:
              title: Petstore API
              version: 1.0.0
            servers:
              - url: https://petstore.example.com/v1
            paths:
              /pets:
                get:
                  summary: List all pets
                  responses:
                    '200':
                      description: A paged array of pets
                post:
                  summary: Create a pet
                  responses:
                    '201':
                      description: Created
        """.trimIndent()

        val importedCol = backend.importSpecification(ImportSpecInput(rawContent = openApiYaml))
        assertEquals("Petstore API", importedCol.name)
        assertTrue(backend.listTree(collectionFilter = "Petstore API").isNotEmpty())

        // 2. Reject Swagger 2.0 with clear diagnostic
        val swagger2 = """
            swagger: "2.0"
            info:
              title: Legacy API
              version: 1.0.0
            paths: {}
        """.trimIndent()
        val ex = assertFailsWith<IllegalArgumentException> {
            backend.importSpecification(ImportSpecInput(rawContent = swagger2))
        }
        assertTrue(ex.message!!.contains("Swagger 2.0 is not supported"))

        // 3. Export in all 4 formats
        for (fmt in listOf("opencollection-yaml", "opencollection-json", "openapi-yaml", "openapi-json")) {
            val exported = backend.exportCollection(
                ExportCollectionInput(collectionId = "Petstore API", format = fmt)
            )
            assertEquals("Petstore API", exported.collectionName)
            assertEquals(fmt, exported.format)
            assertTrue(exported.content.isNotBlank())
        }

        // 4. Test tool dispatch for clapi_import_specification
        val toolCall = server.handleJsonRpc(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 5)
                put("method", "tools/call")
                put("params", buildJsonObject {
                    put("name", "clapi_import_specification")
                    put("arguments", buildJsonObject {
                        put("rawContent", openApiYaml)
                        put("collectionNameOverride", "Petstore Copy")
                    })
                })
            }.toString()
        )
        val res = json.decodeFromJsonElement(McpToolResult.serializer(), toolCall!!["result"]!!)
        assertTrue(!res.isError)
    }

    @Test
    fun `executes git status and commit on git-backed collection`() = runTest {
        val tempDir = Files.createTempDirectory("clapi-mcp-git-test").toFile()
        try {
            val fakeGit = object : GitCliExecutor {
                override suspend fun execute(workingDir: String?, args: List<String>, timeoutMillis: Long): GitCommandResult {
                    return when (args.firstOrNull()) {
                        "rev-parse" -> GitCommandResult(0, "main\n", "")
                        "status" -> GitCommandResult(0, " M GitAPI/collection.opencollection.yaml\n", "")
                        "add" -> GitCommandResult(0, "", "")
                        "commit" -> GitCommandResult(0, "[main abc1234] Update collection\n", "")
                        else -> GitCommandResult(0, "", "")
                    }
                }
            }
            val gitMgr = GitRepositoryManager(executor = fakeGit, baseStorageDir = tempDir.absolutePath)
            val backend = HeadlessWorkspaceBackend(
                baseDirectory = tempDir.absolutePath,
                gitRepositoryManager = gitMgr,
            )

            // Create a git-backed collection document and fake .git marker
            val remoteUrl = "https://github.com/example/git-api.git"
            val repoDir = gitMgr.getRepositoryDirectory(remoteUrl)
            val colDir = gitMgr.getCollectionDirectory(remoteUrl, "GitAPI")
            java.io.File("$repoDir/.git").mkdirs()
            java.io.File(colDir).mkdirs()

            val col = backend.manageCollection(
                io.github.makeforge.clapi.mcp.server.backend.ManageCollectionInput(
                    action = "create",
                    name = "GitAPI",
                )
            )
            // Attach Git storage extension to the collection in repository
            val doc = backend.canonicalRepository.loadGlobalCollection()!!
            val updatedItems = doc.items.map { item ->
                if (item is io.github.makeforge.clapi.core.model.opencollection.FolderItem && item.info.name == "GitAPI") {
                    item.copy(
                        extensions = item.extensions.withStorageExtension(
                            StorageExtension(
                                type = StorageType.GIT,
                                git = GitStorageConfig(remoteUrl = remoteUrl, branch = "main"),
                            )
                        )
                    )
                } else item
            }
            backend.canonicalRepository.saveGlobalCollection(doc.copy(items = updatedItems))

            val statusRes = backend.gitSync(GitSyncInput(collectionId = col.id, action = "status"))
            assertEquals("MODIFIED", statusRes.status)
            assertEquals("main", statusRes.branch)

            val commitRes = backend.gitSync(
                GitSyncInput(collectionId = "GitAPI", action = "commit", commitMessage = "Add endpoint via MCP")
            )
            assertEquals("COMMITTED", commitRes.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
