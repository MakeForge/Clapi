package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import io.github.makeforge.clapi.core.model.opencollection.ApiKeyPlacement
import io.github.makeforge.clapi.core.model.opencollection.AuthApiKey
import io.github.makeforge.clapi.core.model.opencollection.AuthAwsV4
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.MultipartFormBody
import io.github.makeforge.clapi.core.model.opencollection.MultipartPartType
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.model.opencollection.RawBody
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.RequestBodyType
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InsomniaImporterTest {

    private fun loadResource(path: String): String {
        val stream = InsomniaImporterTest::class.java.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `canHandle detects Insomnia v5 YAML and JSON exports`() {
        val importer = InsomniaImporter.default()

        val colYaml = loadResource("/fixtures/insomnia/sample-collection-v5.yaml")
        val specYaml = loadResource("/fixtures/insomnia/sample-spec-v5.yaml")
        val envJson = loadResource("/fixtures/insomnia/sample-environment-v5.json")

        assertTrue(importer.canHandle(colYaml))
        assertTrue(importer.canHandle(specYaml))
        assertTrue(importer.canHandle(envJson))
        assertTrue(importer.canHandle("type: mock.insomnia.rest/5.0\nname: Mock"))

        val openApiYaml = loadResource("/fixtures/petstore-openapi-v3.yaml")
        assertFalse(importer.canHandle(openApiYaml))
        assertFalse(importer.canHandle("{\"openapi\": \"3.0.0\"}"))
    }

    @Test
    fun `scanContent parses Insomnia v5 collection YAML with folders, requests, auth, and environments`() = runTest {
        val importer = InsomniaImporter.default()
        val colYaml = loadResource("/fixtures/insomnia/sample-collection-v5.yaml")

        val result = importer.scanContent(colYaml)
        val success = assertIs<ImportScanResult.Success>(result)

        assertEquals(SpecFormat.INSOMNIA, success.format)
        assertEquals("Sample Insomnia Collection", success.specTitle)
        assertEquals(1, success.folders.size)

        val rootFolder = success.folders.first()
        assertEquals("Sample Insomnia Collection", rootFolder.name)
        // 5 HTTP + 1 Multipart + 1 gRPC + 1 WebSocket = 8 requests
        assertEquals(8, rootFolder.requestCount)

        // Check environments (Base Environment + Staging + Production = 3)
        assertEquals(3, success.environments.size)
        val baseEnv = success.environments.first { it.name == "Base Environment" }
        assertEquals(2, baseEnv.variableCount)

        val stagingEnv = success.environments.first { it.name == "Staging" }
        // Inherits baseUrl (overridden), apiVersion (from base), stageToken = 3 variables
        assertTrue(stagingEnv.environment.variables.any { it.name == "baseUrl" && (it as? RegularVariable)?.value?.asString == "https://staging.api.example.com" })
        assertTrue(stagingEnv.environment.variables.any { it.name == "apiVersion" && (it as? RegularVariable)?.value?.asString == "v1" })
        assertTrue(stagingEnv.environment.variables.any { it.name == "stageToken" && (it as? RegularVariable)?.value?.asString == "stg-secret-token" })

        val prodEnv = success.environments.first { it.name == "Production" }
        assertTrue(prodEnv.environment.variables.any { it.name == "apiKey" && it is SecretVariable })
    }

    @Test
    fun `importToOpenCollection preserves folder hierarchy, HTTP bodies, auth types, gRPC, and WebSocket`() {
        val importer = InsomniaImporter.default()
        val colYaml = loadResource("/fixtures/insomnia/sample-collection-v5.yaml")

        val doc = importer.importToOpenCollection(colYaml)
        assertEquals("Sample Insomnia Collection", doc.info.name)

        // Root items: Users Folder, Form Multipart Upload, User Service gRPC, Live Events WebSocket
        assertEquals(4, doc.items.size)

        val usersFolder = assertIs<FolderItem>(doc.items[0])
        assertEquals("Users Folder", usersFolder.info.name)
        assertEquals("Operations for managing users", usersFolder.info.description?.content)
        assertEquals(1, usersFolder.request?.headers?.size)
        assertEquals("X-Folder-Header", usersFolder.request?.headers?.first()?.name)
        val folderAuth = assertIs<AuthBearer>(usersFolder.request?.auth)
        assertEquals("{{stageToken}}", folderAuth.token)

        // Users Folder children: List Users, Create User, Nested Auth Folder
        assertEquals(3, usersFolder.items.size)

        val listUsers = assertIs<HttpRequestItem>(usersFolder.items[0])
        assertEquals("List Users", listUsers.info.name)
        assertEquals("GET", listUsers.http.method)
        assertEquals("{{baseUrl}}/{{apiVersion}}/users", listUsers.http.url)
        assertEquals(3, listUsers.http.params.size)
        assertEquals(true, listUsers.http.params.first { it.name == "debug" }.disabled)

        val createUser = assertIs<HttpRequestItem>(usersFolder.items[1])
        assertEquals("POST", createUser.http.method)
        val jsonBody = assertIs<RawBody>(createUser.http.body)
        assertEquals(RequestBodyType.JSON, jsonBody.type)
        assertTrue(jsonBody.data.contains("alice@example.com"))
        val basicAuth = assertIs<AuthBasic>(createUser.http.auth)
        assertEquals("admin", basicAuth.username)
        assertEquals("secret", basicAuth.password)

        val nestedFolder = assertIs<FolderItem>(usersFolder.items[2])
        assertEquals("Nested Auth Folder", nestedFolder.info.name)
        assertEquals(3, nestedFolder.items.size)

        val apiKeyReq = assertIs<HttpRequestItem>(nestedFolder.items[0])
        val apiKeyAuth = assertIs<AuthApiKey>(apiKeyReq.http.auth)
        assertEquals("X-API-Key", apiKeyAuth.key)
        assertEquals("{{apiKey}}", apiKeyAuth.value)
        assertEquals(ApiKeyPlacement.HEADER, apiKeyAuth.placement)

        val oauth2Req = assertIs<HttpRequestItem>(nestedFolder.items[1])
        val oauth2Auth = assertIs<AuthOAuth2>(oauth2Req.http.auth)
        assertEquals(OAuth2Flow.CLIENT_CREDENTIALS, oauth2Auth.flow)
        assertEquals("https://auth.example.com/oauth/token", oauth2Auth.accessTokenUrl)
        assertEquals("my-client-id", oauth2Auth.credentials?.clientId)
        assertEquals("read:users", oauth2Auth.scope)

        val iamReq = assertIs<HttpRequestItem>(nestedFolder.items[2])
        val awsAuth = assertIs<AuthAwsV4>(iamReq.http.auth)
        assertEquals("AKIAIOSFODNN7EXAMPLE", awsAuth.accessKeyId)
        assertEquals("us-east-1", awsAuth.region)
        assertEquals("execute-api", awsAuth.service)
        val urlEncodedBody = assertIs<FormUrlEncodedBody>(iamReq.http.body)
        assertEquals(2, urlEncodedBody.data.size)

        // Form Multipart Upload
        val multipartReq = assertIs<HttpRequestItem>(doc.items[1])
        val multipartBody = assertIs<MultipartFormBody>(multipartReq.http.body)
        assertEquals(2, multipartBody.data.size)
        assertEquals(MultipartPartType.TEXT, multipartBody.data[0].type)
        assertEquals(MultipartPartType.FILE, multipartBody.data[1].type)

        // gRPC request
        val grpcReq = assertIs<GrpcRequestItem>(doc.items[2])
        assertEquals("User Service gRPC", grpcReq.info.name)
        assertEquals("localhost:50051", grpcReq.grpc.url)
        assertEquals("/user.UserService/GetUser", grpcReq.grpc.method)
        assertEquals("{\"userId\": \"42\"}", grpcReq.grpc.message)
        assertEquals(1, grpcReq.grpc.metadata.size)

        // WebSocket request
        val wsReq = assertIs<WebSocketRequestItem>(doc.items[3])
        assertEquals("Live Events WebSocket", wsReq.info.name)
        assertTrue(wsReq.websocket.url.startsWith("wss://echo.example.com/ws"))
        assertEquals(1, wsReq.websocket.headers.size)
        assertNotNull(wsReq.websocket.message)
    }

    @Test
    fun `scanContent parses standalone Insomnia v5 environment JSON`() = runTest {
        val importer = InsomniaImporter.default()
        val envJson = loadResource("/fixtures/insomnia/sample-environment-v5.json")

        val result = importer.scanContent(envJson)
        val success = assertIs<ImportScanResult.Success>(result)

        assertEquals(SpecFormat.INSOMNIA, success.format)
        assertEquals("Global Cloud Environments", success.specTitle)
        assertTrue(success.folders.isEmpty())
        assertEquals(3, success.environments.size)
        assertTrue(success.environments.any { it.name == "Base Global Env" })
        assertTrue(success.environments.any { it.name == "Development" })
        assertTrue(success.environments.any { it.name == "Production" })
    }

    @Test
    fun `scanContent parses Insomnia v5 spec with embedded OpenAPI contents`() = runTest {
        val importer = InsomniaImporter.default()
        val specYaml = loadResource("/fixtures/insomnia/sample-spec-v5.yaml")

        val result = importer.scanContent(specYaml)
        val success = assertIs<ImportScanResult.Success>(result)

        assertEquals(SpecFormat.INSOMNIA, success.format)
        assertEquals("Petstore Design Spec", success.specTitle)
        assertTrue(success.folders.isNotEmpty())
        assertTrue(success.folders.first().requestCount >= 2)
        assertTrue(success.environments.isNotEmpty())
    }

    @Test
    fun `scanContent rejects unsupported Insomnia v5 mock server document`() = runTest {
        val importer = InsomniaImporter.default()
        val mockYaml = """
            type: mock.insomnia.rest/5.0
            schema_version: "5.1"
            name: My Mock Server
        """.trimIndent()

        val result = importer.scanContent(mockYaml)
        val failure = assertIs<ImportScanResult.Failure>(result)
        assertTrue(failure.message.contains("mock", ignoreCase = true) || failure.message.contains("not supported", ignoreCase = true))
    }
}
