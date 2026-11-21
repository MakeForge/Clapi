package io.github.makeforge.clapi.core.model.opencollection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenCollectionModelTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testDocumentSerializationRoundTrip() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "Test API",
                version = "1.0.0",
                summary = "A test collection"
            ),
            bundled = true,
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Production",
                        variables = listOf(
                            RegularVariable(name = "baseUrl", value = VariableValue.of("https://api.example.com")),
                            SecretVariable(name = "apiKey", type = "string")
                        )
                    )
                )
            ),
            items = listOf(
                FolderItem(
                    info = FolderInfo(name = "Users"),
                    items = listOf(
                        HttpRequestItem(
                            info = HttpRequestInfo(name = "Get Users"),
                            http = HttpRequestDetails(
                                method = "GET",
                                url = "{{baseUrl}}/users",
                                headers = listOf(HttpRequestHeader(name = "Accept", value = "application/json")),
                                params = listOf(HttpRequestParam(name = "limit", value = "10", type = "query")),
                                auth = AuthBearer(token = "test-token")
                            )
                        )
                    )
                )
            ),
            extensions = mapOf(
                "clapi" to JsonObject(mapOf("authType" to JsonPrimitive("jwt")))
            )
        )

        val encoded = json.encodeToString(OpenCollectionDocument.serializer(), doc)
        assertTrue(encoded.contains("\"opencollection\":\"1.0.0\""))
        assertTrue(encoded.contains("\"secret\":true"))
        // Secret variable must NOT have a value field
        assertFalse(encoded.contains("\"apiKey\":{\"value\""))
        assertFalse(encoded.contains("\"apiKey\",\"value\""))

        val decoded = json.decodeFromString(OpenCollectionDocument.serializer(), encoded)
        assertEquals("1.0.0", decoded.opencollection)
        assertEquals("Test API", decoded.info.name)
        assertEquals(1, decoded.items.size)

        val folder = assertIs<FolderItem>(decoded.items[0])
        assertEquals("Users", folder.info.name)
        assertEquals(ItemType.FOLDER, folder.info.type)
        assertEquals(1, folder.items.size)

        val request = assertIs<HttpRequestItem>(folder.items[0])
        assertEquals("Get Users", request.info.name)
        assertEquals(ItemType.HTTP, request.info.type)
        assertEquals("GET", request.http.method)
        assertEquals("{{baseUrl}}/users", request.http.url)

        val bearerAuth = assertIs<AuthBearer>(request.http.auth)
        assertEquals("test-token", bearerAuth.token)

        val env = decoded.config?.environments?.firstOrNull()
        assertNotNull(env)
        assertEquals(2, env.variables.size)
        val regular = assertIs<RegularVariable>(env.variables[0])
        assertEquals("https://api.example.com", regular.value?.asString)
        val secret = assertIs<SecretVariable>(env.variables[1])
        assertEquals("apiKey", secret.name)
        assertTrue(secret.secret)
    }

    @Test
    fun testAuthInheritSerialization() {
        val details = HttpRequestDetails(
            method = "GET",
            url = "https://api.example.com",
            auth = AuthInherit
        )
        val encoded = json.encodeToString(HttpRequestDetails.serializer(), details)
        assertTrue(encoded.contains("\"auth\":\"inherit\""))

        val decoded = json.decodeFromString(HttpRequestDetails.serializer(), encoded)
        assertEquals(AuthInherit, decoded.auth)
    }

    @Test
    fun testDescriptionAndDocumentationPolymorphism() {
        // String format
        val jsonStr = """{"content":"Simple desc","type":"text/plain"}"""
        val descObj = json.decodeFromString(Description.serializer(), jsonStr)
        assertEquals("Simple desc", descObj.content)

        val primitiveJson = "\"Simple desc string\""
        val descPrimitive = json.decodeFromString(Description.serializer(), primitiveJson)
        assertEquals("Simple desc string", descPrimitive.content)

        val docPrimitive = json.decodeFromString(Documentation.serializer(), "\"Markdown docs\"")
        assertEquals("Markdown docs", docPrimitive.content)
    }

    @Test
    fun testSecretVariableNeverSerializesValue() {
        val secret = SecretVariable(name = "token", type = "string")
        val encoded = json.encodeToString(VariableEntrySerializer, secret)
        assertFalse(encoded.contains("\"value\""))
        assertTrue(encoded.contains("\"secret\":true"))
    }

    @Test
    fun testCollectionProtobufConfigSerializationRoundTrip() {
        val config = CollectionConfig(
            protobuf = CollectionProtobufConfig(
                protoFiles = listOf(
                    ProtoFile(type = "file", path = "/path/to/service.proto")
                ),
                importPaths = listOf(
                    ProtoFileImportPath(path = "/path/to/imports", disabled = false),
                    ProtoFileImportPath(path = "/path/to/other.proto", disabled = true),
                )
            )
        )
        val encoded = json.encodeToString(CollectionConfig.serializer(), config)
        assertTrue(encoded.contains("\"protobuf\""))
        assertTrue(encoded.contains("\"protoFiles\""))
        assertTrue(encoded.contains("\"/path/to/service.proto\""))
        assertTrue(encoded.contains("\"importPaths\""))
        assertTrue(encoded.contains("\"disabled\":true"))

        val decoded = json.decodeFromString(CollectionConfig.serializer(), encoded)
        assertNotNull(decoded.protobuf)
        assertEquals(1, decoded.protobuf!!.protoFiles.size)
        assertEquals("/path/to/service.proto", decoded.protobuf!!.protoFiles[0].path)
        assertEquals(ProtoFileType.FILE, decoded.protobuf!!.protoFiles[0].type)
        assertEquals(2, decoded.protobuf!!.importPaths.size)
        assertEquals("/path/to/imports", decoded.protobuf!!.importPaths[0].path)
        assertEquals(false, decoded.protobuf!!.importPaths[0].disabled)
        assertEquals(true, decoded.protobuf!!.importPaths[1].disabled)
    }

    @Test
    fun testOpenCollectionEnumSerializationRoundTrip() {
        val env = Environment(
            name = "TestEnv",
            externalSecrets = ExternalSecrets(
                type = ExternalSecretType.HASHICORP_VAULT_SERVER,
                variables = listOf(
                    HashicorpVaultExternalSecret(name = "secret1", path = "secret/data/app")
                )
            ),
            clientCertificates = listOf(
                PemCertificate(
                    domain = "api.example.com",
                    type = CertificateType.PEM,
                    certificateFilePath = "/path/to/cert.pem",
                    privateKeyFilePath = "/path/to/key.pem"
                )
            ),
            variables = listOf(
                SecretVariable(name = "token", type = VariableType.STRING),
                RegularVariable(name = "num", value = VariableValue.of(VariableType.NUMBER, "42"))
            )
        )

        val encodedEnv = json.encodeToString(Environment.serializer(), env)
        assertTrue(encodedEnv.contains("\"type\":\"hashicorp-vault-server\""))
        assertTrue(encodedEnv.contains("\"type\":\"pem\""))
        assertTrue(encodedEnv.contains("\"type\":\"string\""))
        assertTrue(encodedEnv.contains("\"type\":\"number\""))

        val decodedEnv = json.decodeFromString(Environment.serializer(), encodedEnv)
        assertEquals(ExternalSecretType.HASHICORP_VAULT_SERVER, decodedEnv.externalSecrets?.type)
        val cert = assertIs<PemCertificate>(decodedEnv.clientCertificates[0])
        assertEquals(CertificateType.PEM, cert.type)
        val secretVar = assertIs<SecretVariable>(decodedEnv.variables[0])
        assertEquals(VariableType.STRING, secretVar.type)
        val numVar = assertIs<RegularVariable>(decodedEnv.variables[1])
        val typedNum = assertIs<TypedVariableValue>(numVar.value)
        assertEquals(VariableType.NUMBER, typedNum.type)

        val requestDetails = HttpRequestDetails(
            method = "POST",
            url = "https://api.example.com/data",
            params = listOf(
                HttpRequestParam(name = "q", value = "search", type = HttpRequestParamType.QUERY)
            ),
            body = RawBody(type = RequestBodyType.JSON, data = "{}"),
            auth = AuthApiKey(key = "X-Api-Key", value = "abc", placement = ApiKeyPlacement.HEADER)
        )
        val encodedReq = json.encodeToString(HttpRequestDetails.serializer(), requestDetails)
        assertTrue(encodedReq.contains("\"type\":\"query\""))
        assertTrue(encodedReq.contains("\"type\":\"json\""))
        assertTrue(encodedReq.contains("\"type\":\"apikey\""))
        assertTrue(encodedReq.contains("\"placement\":\"header\""))

        val decodedReq = json.decodeFromString(HttpRequestDetails.serializer(), encodedReq)
        assertEquals(HttpRequestParamType.QUERY, decodedReq.params[0].type)
        val rawBody = assertIs<RawBody>(decodedReq.body)
        assertEquals(RequestBodyType.JSON, rawBody.type)
        val apiKeyAuth = assertIs<AuthApiKey>(decodedReq.auth)
        assertEquals(AuthType.APIKEY, apiKeyAuth.type)
        assertEquals(ApiKeyPlacement.HEADER, apiKeyAuth.placement)

        val runtime = HttpRequestRuntime(
            scripts = listOf(
                Script(type = ScriptType.BEFORE_REQUEST, code = "console.log('before');"),
                Script(type = ScriptType.AFTER_RESPONSE, code = "console.log('after');")
            ),
            actions = listOf(
                ActionSetVariable(
                    type = ActionType.SET_VARIABLE,
                    phase = ActionPhase.AFTER_RESPONSE,
                    selector = ActionSelector(expression = "data.token", method = ActionSelectorMethod.JSONQ),
                    variable = ActionTargetVariable(name = "authToken", scope = ActionVariableScope.ENVIRONMENT)
                )
            )
        )
        val encodedRuntime = json.encodeToString(HttpRequestRuntime.serializer(), runtime)
        assertTrue(encodedRuntime.contains("\"type\":\"before-request\""))
        assertTrue(encodedRuntime.contains("\"type\":\"after-response\""))
        assertTrue(encodedRuntime.contains("\"type\":\"set-variable\""))
        assertTrue(encodedRuntime.contains("\"phase\":\"after-response\""))
        assertTrue(encodedRuntime.contains("\"method\":\"jsonq\""))
        assertTrue(encodedRuntime.contains("\"scope\":\"environment\""))

        val decodedRuntime = json.decodeFromString(HttpRequestRuntime.serializer(), encodedRuntime)
        assertEquals(ScriptType.BEFORE_REQUEST, decodedRuntime.scripts[0].type)
        assertEquals(ScriptType.AFTER_RESPONSE, decodedRuntime.scripts[1].type)
        assertEquals(ActionType.SET_VARIABLE, decodedRuntime.actions[0].type)
        assertEquals(ActionPhase.AFTER_RESPONSE, decodedRuntime.actions[0].phase)
        assertEquals(ActionSelectorMethod.JSONQ, decodedRuntime.actions[0].selector.method)
        assertEquals(ActionVariableScope.ENVIRONMENT, decodedRuntime.actions[0].variable.scope)
    }
}
