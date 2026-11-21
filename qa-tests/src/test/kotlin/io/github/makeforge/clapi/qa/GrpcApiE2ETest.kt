package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcMetadata
import io.github.makeforge.clapi.core.model.opencollection.GrpcMethodType
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestDetails
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestInfo
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.network.KtorApiClient
import io.github.makeforge.clapi.core.network.RetryPolicy
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableContext
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableRegistry
import io.github.makeforge.clapi.core.network.dynamicvar.OAuth2ResolvedConfig
import io.github.makeforge.clapi.core.network.grpc.GrpcCallRequest
import io.github.makeforge.clapi.core.network.grpc.NettyDynamicGrpcClient
import io.github.makeforge.clapi.core.network.grpc.ProtocJarParser
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSchemaValidator
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.makeforge.clapi.core.network.execution.RequestExecutionService
import io.github.makeforge.clapi.testsupport.DummyApiServer
import io.grpc.Status
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrpcApiE2ETest {

    private lateinit var server: DummyApiServer
    private lateinit var protoParser: ProtocJarParser
    private lateinit var grpcClient: NettyDynamicGrpcClient
    private lateinit var executionService: RequestExecutionService

    @BeforeTest
    fun setUp() {
        server = DummyApiServer()
        server.start()
        protoParser = ProtocJarParser()
        grpcClient = NettyDynamicGrpcClient(protoParser = protoParser)
        executionService = RequestExecutionService(
            apiClient = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1)),
            grpcClient = grpcClient,
        )
    }

    @AfterTest
    fun tearDown() = runTest {
        if (::grpcClient.isInitialized) {
            grpcClient.shutdown()
        }
        if (::server.isInitialized) {
            server.stop()
        }
    }

    @Test
    fun `parses greeter proto and discovers SayHello and SayHelloAgain unary RPCs`() = runTest {
        val protoFile = File(server.protoFilePath())
        val schema = protoParser.parseProtoFile(
            protoFilePath = protoFile.absolutePath,
            includePaths = listOf(server.metadataProtoFilePath()),
        )

        assertNotNull(schema.fileDescriptorBytes, "Expected compiled descriptor bytes")
        assertEquals(1, schema.services.size)
        val service = schema.services.single()
        assertEquals("helloworld.Greeter", service.fullName)
        assertEquals(
            listOf("helloworld.Greeter/SayHello", "helloworld.Greeter/SayHelloAgain"),
            service.methods.map { it.fullMethodName }
        )
        assertTrue(service.methods.all { !it.isClientStreaming && !it.isServerStreaming })
    }

    @Test
    fun `executes SayHello and SayHelloAgain unary calls with metadata roundtrip against DummyApiServer`() = runTest {
        val response1 = grpcClient.executeUnary(
            GrpcCallRequest(
                requestId = "e2e-say-hello",
                host = "127.0.0.1",
                port = server.grpcPort(),
                useTls = false,
                protoFilePath = server.protoFilePath(),
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": "Clapi QA"}""",
                metadata = listOf("x-custom-meta" to "meta-value-123"),
                includePaths = listOf(server.metadataProtoFilePath()),
            )
        )

        assertEquals(0, response1.statusCode, "Expected 0 OK, got ${response1.statusCode}: ${response1.errorMessage}")
        assertEquals("OK", response1.statusDescription)
        assertTrue(response1.durationMs >= 1L)
        assertNotNull(response1.responseJson)
        assertTrue(response1.responseJson!!.contains("\"message\": \"Hello, Clapi QA!\""))
        assertTrue(
            response1.responseJson!!.contains("\"receivedMeta\": \"meta-value-123\"") ||
                response1.responseJson!!.contains("\"received_meta\": \"meta-value-123\"")
        )
        assertTrue(
            response1.responseJson!!.contains("ackMetadata") ||
                response1.responseJson!!.contains("ack_metadata")
        )
        assertTrue(response1.responseJson!!.contains("trace-Clapi QA"))
        assertTrue(response1.responseMetadata.any { it.first == "x-grpc-server" && it.second == "DummyApiServer" })
        assertTrue(response1.responseMetadata.any { it.first == "x-echo-custom-meta" && it.second == "meta-value-123" })

        val response2 = grpcClient.executeUnary(
            GrpcCallRequest(
                requestId = "e2e-say-hello-again",
                host = "127.0.0.1",
                port = server.grpcPort(),
                useTls = false,
                protoFilePath = server.protoFilePath(),
                fullMethodName = "helloworld.Greeter/SayHelloAgain",
                jsonPayload = """{"name": "Clapi QA"}""",
                includePaths = listOf(server.metadataProtoFilePath()),
            )
        )

        assertEquals(0, response2.statusCode)
        assertEquals("OK", response2.statusDescription)
        assertNotNull(response2.responseJson)
        assertTrue(response2.responseJson!!.contains("\"message\": \"Hello again, Clapi QA!\""))
    }

    @Test
    fun `executes GrpcRequestItem through RequestExecutionService with variable substitution and auth`() = runTest {
        val grpcItem = GrpcRequestItem(
            info = GrpcRequestInfo(name = "Greeter SayHello", type = "grpc"),
            grpc = GrpcRequestDetails(
                url = "grpc://127.0.0.1:${server.grpcPort()}",
                method = "helloworld.Greeter/SayHello",
                methodType = "unary",
                protoFilePath = server.protoFilePath(),
                metadata = listOf(
                    GrpcMetadata(name = "x-custom-meta", value = "{{customMeta}}"),
                    GrpcMetadata(name = "x-custom-meta", value = "ignored-disabled", disabled = true),
                ),
                message = """{"name": "{{targetUser}}"}""",
                auth = AuthBearer(token = "{{bearerToken}}"),
            ),
        )

        val result = executionService.executeGrpc(
            requestItem = grpcItem,
            variableLayers = listOf(
                mapOf(
                    "customMeta" to "resolved-meta",
                    "targetUser" to "Alice",
                    "bearerToken" to "secret-jwt",
                )
            ),
            includePaths = listOf(server.metadataProtoFilePath()),
        )

        assertEquals(0, result.statusCode)
        assertEquals("OK", result.statusDescription)
        assertNotNull(result.responseJson)
        assertTrue(result.responseJson!!.contains("\"message\": \"Hello, Alice!\""))
        assertTrue(result.responseJson!!.contains("resolved-meta;Bearer secret-jwt"))
        assertTrue(result.durationMs >= 1L)
    }

    @Test
    fun `handles gRPC status errors cleanly via DummyApiServer`() = runTest {
        val deniedResponse = grpcClient.executeUnary(
            GrpcCallRequest(
                requestId = "e2e-denied",
                host = "127.0.0.1",
                port = server.grpcPort(),
                useTls = false,
                protoFilePath = server.protoFilePath(),
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": "denied"}""",
                includePaths = listOf(server.metadataProtoFilePath()),
            )
        )

        assertEquals(Status.Code.PERMISSION_DENIED.value(), deniedResponse.statusCode)
        assertEquals("PERMISSION_DENIED", deniedResponse.statusDescription)
        assertNull(deniedResponse.responseJson)
        assertNotNull(deniedResponse.errorMessage)
        assertTrue(deniedResponse.errorMessage!!.contains("Access denied for denied"))
    }

    @Test
    fun `disabling or omitting required collection import path fails gRPC call with descriptive error`() = runTest {
        // Direct test against server.protoFilePath() without include paths:
        val failedDirectResponse = grpcClient.executeUnary(
            GrpcCallRequest(
                requestId = "e2e-disabled-import-direct",
                host = "127.0.0.1",
                port = server.grpcPort(),
                useTls = false,
                protoFilePath = server.protoFilePath(),
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": "Clapi QA"}""",
                includePaths = emptyList(), // Disabled import path
            )
        )

        assertEquals(Status.Code.INVALID_ARGUMENT.value(), failedDirectResponse.statusCode)
        assertNull(failedDirectResponse.responseJson)
        assertNotNull(failedDirectResponse.errorMessage)
        assertTrue(
            failedDirectResponse.errorMessage!!.contains("metadata.proto") ||
                failedDirectResponse.errorMessage!!.contains("Failed to compile"),
            "Expected error message mentioning missing import or compilation failure, but was: ${failedDirectResponse.errorMessage}"
        )
    }

    @Test
    fun `fixture clapi-test-collection contains valid gRPC items and passes schema validation`() {
        val candidates = listOf(
            File("qa-tests/fixtures/clapi-test-collection.json"),
            File("fixtures/clapi-test-collection.json"),
            File("../qa-tests/fixtures/clapi-test-collection.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("clapi-test-collection.json fixture not found")
        val raw = file.readText()

        val validator = OpenCollectionSchemaValidator()
        val issues = validator.validate(raw)
        assertTrue(issues.isEmpty(), "Expected 0 schema validation issues on fixture, got: $issues")

        val doc = OpenCollectionSerializer.deserializeJson(raw)
        val grpcFolder = doc.items.filterIsInstance<FolderItem>().firstOrNull { it.info.name == "gRPC" }
        assertNotNull(grpcFolder, "Expected 'gRPC' folder in clapi-test-collection.json")
        assertTrue(grpcFolder.items.isNotEmpty())
        val sayHello = assertIs<GrpcRequestItem>(grpcFolder.items.first())
        assertEquals("Greeter SayHello", sayHello.info.name)
        assertEquals("helloworld.Greeter/SayHello", sayHello.grpc.method)
        assertEquals(GrpcMethodType.UNARY, sayHello.grpc.methodType)
        assertEquals("greeter.proto", sayHello.grpc.protoFilePath)
    }

    @Test
    fun `resolves oauth2 access token in grpc metadata authorization header when sending request`() = runTest {
        val fakeTokenClient = object : OAuth2TokenClient {
            override suspend fun fetchClientCredentialsToken(
                tokenUrl: String,
                clientId: String,
                clientSecret: String,
                placement: OAuth2CredentialsPlacement,
                scope: String?,
            ): Result<OAuth2TokenResponse> = Result.success(
                OAuth2TokenResponse(
                    accessToken = "mock-grpc-token-xyz-123",
                    expiresIn = 3600,
                )
            )

            override suspend fun fetchPasswordToken(
                tokenUrl: String,
                clientId: String,
                clientSecret: String,
                placement: OAuth2CredentialsPlacement,
                username: String,
                password: String,
                scope: String?,
            ): Result<OAuth2TokenResponse> = error("Not needed")

            override suspend fun refreshToken(
                tokenUrl: String,
                refreshToken: String,
                clientId: String?,
                clientSecret: String?,
                placement: OAuth2CredentialsPlacement,
                scope: String?,
            ): Result<OAuth2TokenResponse> = error("Not needed")
        }

        val registry = DynamicVariableRegistry()
        val context = DynamicVariableContext(
            effectiveAuthType = AuthType.OAUTH2,
            oauth2Config = OAuth2ResolvedConfig(
                tokenUrl = "https://auth.example.com/oauth/token",
                clientId = "grpc-client-id",
                clientSecret = "grpc-client-secret",
            ),
            oauth2TokenState = OAuth2TokenState(accessToken = ""),
            oauth2TokenClient = fakeTokenClient,
        )

        val rawMetadata = listOf("authorization" to "Bearer {{_auth.oauth2.accessToken}}")
        val resolvedDynamicVars = registry.resolveDynamicVariables(
            texts = rawMetadata.flatMap { listOf(it.first, it.second) },
            context = context,
        )

        assertEquals("mock-grpc-token-xyz-123", resolvedDynamicVars["_auth.oauth2.accessToken"])

        val resolvedMetadata = rawMetadata.map { (key, value) ->
            VariableResolver.resolve(key, listOf(resolvedDynamicVars)) to
                VariableResolver.resolve(value, listOf(resolvedDynamicVars))
        }

        val response = grpcClient.executeUnary(
            GrpcCallRequest(
                requestId = "e2e-oauth2-meta-test",
                host = "127.0.0.1",
                port = server.grpcPort(),
                useTls = false,
                protoFilePath = server.protoFilePath(),
                fullMethodName = "helloworld.Greeter/SayHello",
                jsonPayload = """{"name": "Authenticated gRPC User"}""",
                metadata = resolvedMetadata,
                includePaths = listOf(server.metadataProtoFilePath()),
            )
        )

        assertEquals(0, response.statusCode)
        assertEquals("OK", response.statusDescription)
        assertNotNull(response.responseJson)
        assertTrue(response.responseJson!!.contains("Hello, Authenticated gRPC User!"))

        val lastLogged = server.requestsLog.value.lastOrNull { it.path == "helloworld.Greeter/SayHello" }
        assertNotNull(lastLogged, "Expected logged request on DummyApiServer")
        assertEquals("Bearer mock-grpc-token-xyz-123", lastLogged.headers["authorization"])
    }
}
