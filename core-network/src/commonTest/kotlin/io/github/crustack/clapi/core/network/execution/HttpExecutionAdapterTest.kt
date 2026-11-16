package io.github.crustack.clapi.core.network.execution

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.McpConnectionConfig
import io.github.crustack.clapi.core.model.McpTransportType
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.ResponseDefinition
import io.github.crustack.clapi.core.model.ResponseMetrics
import io.github.crustack.clapi.core.model.opencollection.AuthApiKey
import io.github.crustack.clapi.core.model.opencollection.AuthBasic
import io.github.crustack.clapi.core.model.opencollection.AuthBearer
import io.github.crustack.clapi.core.model.opencollection.AuthInherit
import io.github.crustack.clapi.core.model.opencollection.ClapiExtensionData
import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.FolderInfo
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.FormParamEntry
import io.github.crustack.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.crustack.clapi.core.model.opencollection.HttpRequestDetails
import io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader
import io.github.crustack.clapi.core.model.opencollection.HttpRequestInfo
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestParam
import io.github.crustack.clapi.core.model.opencollection.McpConnectionConfigDto
import io.github.crustack.clapi.core.model.opencollection.MultipartFormBody
import io.github.crustack.clapi.core.model.opencollection.MultipartPartEntry
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.RawBody
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.RequestDefaults
import io.github.crustack.clapi.core.model.opencollection.StringVariableValue
import io.github.crustack.clapi.core.model.opencollection.withClapiExtensionData
import io.github.crustack.clapi.core.network.ApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpExecutionAdapterTest {

    private val adapter = DefaultHttpExecutionAdapter

    @Test
    fun toExecutableRequest_maps_basic_fields_and_parameters() {
        val requestItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Get Users"),
            http = HttpRequestDetails(
                method = "get",
                url = "https://api.example.com/users",
                headers = listOf(HttpRequestHeader("Accept", "application/json")),
                params = listOf(
                    HttpRequestParam(name = "page", value = "1"),
                    HttpRequestParam(name = "disabled_filter", value = "true", disabled = true)
                ),
            )
        )

        val req = adapter.toExecutableRequest(requestItem)

        assertEquals("Get Users", req.name)
        assertEquals(HttpMethodType.GET, req.method)
        assertEquals("https://api.example.com/users", req.url)
        assertEquals(1, req.headers.size)
        assertEquals("Accept", req.headers[0].key)
        assertEquals("application/json", req.headers[0].value)
        assertEquals(1, req.queryParams.size)
        assertEquals("page", req.queryParams[0].key)
        assertEquals("1", req.queryParams[0].value)
    }

    @Test
    fun toExecutableRequest_resolves_header_inheritance_with_request_override() {
        val collectionDoc = OpenCollectionDocument(
            info = CollectionInfo(name = "Test Collection"),
            request = RequestDefaults(
                headers = listOf(
                    HttpRequestHeader("X-Collection", "col-val"),
                    HttpRequestHeader("X-Common", "col-common")
                )
            )
        )

        val rootFolder = FolderItem(
            info = FolderInfo(name = "Root"),
            request = RequestDefaults(
                headers = listOf(
                    HttpRequestHeader("X-Folder", "root-val"),
                    HttpRequestHeader("X-Common", "root-common")
                )
            )
        )

        val childFolder = FolderItem(
            info = FolderInfo(name = "Child"),
            request = RequestDefaults(
                headers = listOf(
                    HttpRequestHeader("X-Common", "child-common")
                )
            )
        )

        val requestItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Req"),
            http = HttpRequestDetails(
                method = "POST",
                url = "https://example.com",
                headers = listOf(
                    HttpRequestHeader("X-Common", "req-wins"),
                    HttpRequestHeader("X-Disabled", "ignore", disabled = true)
                )
            )
        )

        val req = adapter.toExecutableRequest(
            requestItem = requestItem,
            parentHierarchy = listOf(rootFolder, childFolder),
            collectionRoot = collectionDoc
        )

        val headerMap = req.headers.associate { it.key to it.value }
        assertEquals("col-val", headerMap["X-Collection"])
        assertEquals("root-val", headerMap["X-Folder"])
        assertEquals("req-wins", headerMap["X-Common"])
        assertEquals(null, headerMap["X-Disabled"])
    }

    @Test
    fun toExecutableRequest_resolves_auth_inheritance() {
        val collectionDoc = OpenCollectionDocument(
            info = CollectionInfo(name = "Test"),
            request = RequestDefaults(
                auth = AuthBearer(token = "col-bearer")
            )
        )

        val parentFolder = FolderItem(
            info = FolderInfo(name = "AuthFolder"),
            request = RequestDefaults(
                auth = AuthBasic(username = "admin", password = "secret")
            )
        )

        val childFolder = FolderItem(
            info = FolderInfo(name = "ChildFolder"),
            request = RequestDefaults(auth = AuthInherit)
        )

        val inheritingRequest = HttpRequestItem(
            info = HttpRequestInfo(name = "Req1"),
            http = HttpRequestDetails(
                method = "GET",
                url = "https://example.com",
                auth = AuthInherit
            )
        )

        val req1 = adapter.toExecutableRequest(
            requestItem = inheritingRequest,
            parentHierarchy = listOf(parentFolder, childFolder),
            collectionRoot = collectionDoc
        )

        assertEquals(AuthType.BASIC, req1.auth.type)
        assertEquals("admin", req1.auth.params["username"])
        assertEquals("secret", req1.auth.params["password"])

        val explicitRequest = HttpRequestItem(
            info = HttpRequestInfo(name = "Req2"),
            http = HttpRequestDetails(
                method = "GET",
                url = "https://example.com",
                auth = AuthApiKey(key = "X-Api-Key", value = "key-123", placement = "header")
            )
        )

        val req2 = adapter.toExecutableRequest(
            requestItem = explicitRequest,
            parentHierarchy = listOf(parentFolder),
            collectionRoot = collectionDoc
        )

        assertEquals(AuthType.API_KEY, req2.auth.type)
        assertEquals("X-Api-Key", req2.auth.params["key"])
        assertEquals("key-123", req2.auth.params["value"])

        val req3 = adapter.toExecutableRequest(
            requestItem = inheritingRequest,
            parentHierarchy = emptyList(),
            collectionRoot = collectionDoc
        )

        assertEquals(AuthType.BEARER, req3.auth.type)
        assertEquals("col-bearer", req3.auth.params["token"])
    }

    @Test
    fun variable_layers_precedence_and_resolution() {
        val collectionDoc = OpenCollectionDocument(
            info = CollectionInfo(name = "Test"),
            request = RequestDefaults(
                variables = listOf(
                    RegularVariable("key", StringVariableValue("collection-value")),
                    RegularVariable("colVar", StringVariableValue("col"))
                )
            )
        )

        val folder = FolderItem(
            info = FolderInfo(name = "Folder"),
            request = RequestDefaults(
                variables = listOf(
                    RegularVariable("key", StringVariableValue("folder-value")),
                    RegularVariable("folderVar", StringVariableValue("folder"))
                )
            )
        )

        val requestItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Req"),
            http = HttpRequestDetails(
                method = "GET",
                url = "https://{{key}}.com/{{folderVar}}/{{colVar}}/{{envVar}}/{{runtimeVar}}?g={{globalVar}}"
            )
        )

        val layers = adapter.buildVariableLayers(
            requestItem = requestItem,
            parentHierarchy = listOf(folder),
            collectionRoot = collectionDoc,
            environmentVariables = mapOf("key" to "env-value", "envVar" to "env"),
            globalVariables = mapOf("key" to "global-value", "globalVar" to "global"),
            runtimeVariables = mapOf("key" to "runtime-value", "runtimeVar" to "runtime")
        )

        assertEquals(5, layers.size)
        assertEquals("runtime-value", layers[0]["key"])
        assertEquals("env-value", layers[1]["key"])
        assertEquals("folder-value", layers[2]["key"])
        assertEquals("collection-value", layers[3]["key"])
        assertEquals("global-value", layers[4]["key"])

        val resolved = adapter.resolveVariables(requestItem.http.url, layers)
        assertEquals("https://runtime-value.com/folder/col/env/runtime?g=global", resolved)
    }

    @Test
    fun ignores_mcp_extensions_without_errors() {
        val mcpData = ClapiExtensionData(
            mcp = listOf(
                McpConnectionConfigDto(
                    id = "mcp-1",
                    name = "Local server",
                    config = McpConnectionConfig(
                        transport = McpTransportType.STDIO,
                        command = "npx",
                        args = listOf("-y", "8080", "true"),
                        env = mapOf("PORT" to "8080")
                    )
                )
            )
        )
        val requestItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Req"),
            http = HttpRequestDetails(
                method = "GET",
                url = "https://example.com/api"
            )
        )

        val withMcp = adapter.toExecutableRequest(
            requestItem = requestItem,
            collectionRoot = OpenCollectionDocument(
                info = CollectionInfo(name = "Test"),
                extensions = emptyMap<String, JsonElement>().withClapiExtensionData(mcpData)
            )
        )
        val withoutMcp = adapter.toExecutableRequest(
            requestItem = requestItem,
            collectionRoot = OpenCollectionDocument(info = CollectionInfo(name = "Test"))
        )

        assertEquals(withoutMcp, withMcp)
        assertEquals("Req", withMcp.name)
        assertEquals(HttpMethodType.GET, withMcp.method)
        assertEquals("https://example.com/api", withMcp.url)
    }

    @Test
    fun maps_various_body_types() {
        val jsonItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Json"),
            http = HttpRequestDetails(
                method = "POST",
                url = "https://example.com",
                body = RawBody(type = "json", data = """{"a": 1}""")
            )
        )
        val jsonReq = adapter.toExecutableRequest(jsonItem)
        assertEquals(BodyType.JSON, jsonReq.body.type)
        assertEquals("""{"a": 1}""", jsonReq.body.content)

        val urlEncodedItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Form"),
            http = HttpRequestDetails(
                method = "POST",
                url = "https://example.com",
                body = FormUrlEncodedBody(
                    data = listOf(
                        FormParamEntry("foo", "bar"),
                        FormParamEntry("disabled", "val", disabled = true)
                    )
                )
            )
        )
        val formReq = adapter.toExecutableRequest(urlEncodedItem)
        assertEquals(BodyType.X_WWW_FORM_URLENCODED, formReq.body.type)
        assertEquals(1, formReq.body.formEntries.size)
        assertEquals("foo", formReq.body.formEntries[0].key)
        assertEquals("bar", formReq.body.formEntries[0].value)

        val multipartItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Multipart"),
            http = HttpRequestDetails(
                method = "POST",
                url = "https://example.com",
                body = MultipartFormBody(
                    data = listOf(
                        MultipartPartEntry("field1", type = "text", value = JsonPrimitive("val1")),
                        MultipartPartEntry("field2", type = "file", value = JsonPrimitive("val2"))
                    )
                )
            )
        )
        val multiReq = adapter.toExecutableRequest(multipartItem)
        assertEquals(BodyType.FORM_DATA, multiReq.body.type)
        assertEquals(2, multiReq.body.formEntries.size)
        assertEquals(2, multiReq.body.formDataEntries.size)
    }

    @Test
    fun request_execution_service_executes_http_request_item() = runTest {
        var executedRequest: RequestDefinition? = null
        var executedLayers: List<Map<String, String>>? = null

        val mockClient = object : ApiClient {
            override fun execute(
                request: RequestDefinition,
                variableLayers: List<Map<String, String>>
            ): Flow<NetworkEvent> {
                executedRequest = request
                executedLayers = variableLayers
                return flowOf(
                    NetworkEvent.Success(
                        ResponseDefinition(
                            requestId = "test-req",
                            statusCode = 200,
                            statusText = "OK",
                            headers = emptyList(),
                            cookies = emptyList(),
                            bodyText = "{}",
                            contentType = "application/json",
                            executedAtEpochMillis = 100L,
                            metrics = ResponseMetrics(
                                statusCode = 200,
                                responseTimeMs = 50L,
                                responseSizeBytes = 2L
                            )
                        )
                    )
                )
            }
        }

        val service = RequestExecutionService(apiClient = mockClient, adapter = adapter)

        val requestItem = HttpRequestItem(
            info = HttpRequestInfo(name = "Test Request"),
            http = HttpRequestDetails(
                method = "POST",
                url = "https://api.example.com/test",
                headers = listOf(HttpRequestHeader("X-Custom", "123"))
            )
        )

        val events = service.execute(requestItem).toList()
        assertEquals(1, events.size)
        assertTrue(events[0] is NetworkEvent.Success)
        assertNotNull(executedRequest)
        assertEquals("Test Request", executedRequest?.name)
        assertEquals(HttpMethodType.POST, executedRequest?.method)
        assertEquals("https://api.example.com/test", executedRequest?.url)
        assertNotNull(executedLayers)
    }

    @Test
    fun extractTemplateStrings_and_resolveRequest_cover_all_request_fields() {
        val req = RequestDefinition(
            id = "req-full",
            name = "Full Template Request",
            method = HttpMethodType.POST,
            url = "https://{{host}}/v1",
            queryParams = listOf(
                io.github.crustack.clapi.core.model.KeyValueEntry("{{qKey}}", "{{qVal}}", enabled = true),
                io.github.crustack.clapi.core.model.KeyValueEntry("{{disabledQ}}", "ignore", enabled = false),
            ),
            headers = listOf(
                io.github.crustack.clapi.core.model.KeyValueEntry("{{hKey}}", "{{hVal}}", enabled = true),
            ),
            cookies = listOf(
                io.github.crustack.clapi.core.model.KeyValueEntry("{{cKey}}", "{{cVal}}", enabled = true),
            ),
            auth = io.github.crustack.clapi.core.model.AuthConfig(
                type = AuthType.BEARER,
                params = mapOf("token" to "{{authToken}}"),
            ),
            body = io.github.crustack.clapi.core.model.RequestBody(
                type = BodyType.GRAPHQL,
                content = "{{rawBody}}",
                formEntries = listOf(
                    io.github.crustack.clapi.core.model.KeyValueEntry("{{fKey}}", "{{fVal}}", enabled = true),
                ),
                formDataEntries = listOf(
                    io.github.crustack.clapi.core.model.FormDataEntry(
                        key = "{{fdKey}}",
                        value = "{{fdVal}}",
                        description = "{{fdDesc}}",
                        enabled = true,
                    ),
                ),
                graphQl = io.github.crustack.clapi.core.model.GraphQlBody(
                    query = "query { user(id: \"{{gqlId}}\") { name } }",
                    operationName = "{{gqlOp}}",
                    variablesJson = "{\"id\": \"{{gqlVar}}\"}",
                ),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val extracted = req.extractTemplateStrings()
        assertTrue(extracted.contains("https://{{host}}/v1"))
        assertTrue(extracted.contains("{{qKey}}"))
        assertTrue(extracted.contains("{{qVal}}"))
        assertTrue(!extracted.contains("{{disabledQ}}"))
        assertTrue(extracted.contains("{{hKey}}"))
        assertTrue(extracted.contains("{{hVal}}"))
        assertTrue(extracted.contains("{{cKey}}"))
        assertTrue(extracted.contains("{{cVal}}"))
        assertTrue(extracted.contains("{{authToken}}"))
        assertTrue(extracted.contains("{{rawBody}}"))
        assertTrue(extracted.contains("{{fKey}}"))
        assertTrue(extracted.contains("{{fVal}}"))
        assertTrue(extracted.contains("{{fdKey}}"))
        assertTrue(extracted.contains("{{fdVal}}"))
        assertTrue(extracted.contains("{{fdDesc}}"))
        assertTrue(extracted.contains("query { user(id: \"{{gqlId}}\") { name } }"))
        assertTrue(extracted.contains("{{gqlOp}}"))
        assertTrue(extracted.contains("{\"id\": \"{{gqlVar}}\"}"))

        val layers = listOf(
            mapOf(
                "host" to "api.clapi.io",
                "qKey" to "filter",
                "qVal" to "active",
                "hKey" to "X-Custom-Header",
                "hVal" to "header-123",
                "cKey" to "session_id",
                "cVal" to "cookie-456",
                "authToken" to "bearer-789",
                "rawBody" to "resolved-raw",
                "fKey" to "formKey",
                "fVal" to "formVal",
                "fdKey" to "partKey",
                "fdVal" to "partVal",
                "fdDesc" to "partDesc",
                "gqlId" to "u-1",
                "gqlOp" to "GetUser",
                "gqlVar" to "v-1",
            )
        )

        val resolved = adapter.resolveRequest(req, layers)
        assertEquals("https://api.clapi.io/v1", resolved.url)
        assertEquals("filter", resolved.queryParams[0].key)
        assertEquals("active", resolved.queryParams[0].value)
        assertEquals("X-Custom-Header", resolved.headers[0].key)
        assertEquals("header-123", resolved.headers[0].value)
        assertEquals("session_id", resolved.cookies[0].key)
        assertEquals("cookie-456", resolved.cookies[0].value)
        assertEquals("bearer-789", resolved.auth.params["token"])
        assertEquals("resolved-raw", resolved.body.content)
        assertEquals("formKey", resolved.body.formEntries[0].key)
        assertEquals("formVal", resolved.body.formEntries[0].value)
        assertEquals("partKey", resolved.body.formDataEntries[0].key)
        assertEquals("partVal", resolved.body.formDataEntries[0].value)
        assertEquals("partDesc", resolved.body.formDataEntries[0].description)
        assertEquals("query { user(id: \"u-1\") { name } }", resolved.body.graphQl?.query)
        assertEquals("GetUser", resolved.body.graphQl?.operationName)
        assertEquals("{\"id\": \"v-1\"}", resolved.body.graphQl?.variablesJson)
    }
}
