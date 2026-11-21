package io.github.makeforge.clapi.core.storage.importers

import io.github.makeforge.clapi.core.model.importers.ImportScanResult
import io.github.makeforge.clapi.core.model.importers.SpecFormat
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestParamType
import io.github.makeforge.clapi.core.model.opencollection.RawBody
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiHierarchyMappingTest {

    private val mixedSpec = """
        openapi: "3.0.1"
        info:
          title: "Complex Hierarchy API"
          version: "2.1.0"
        servers:
          - url: "https://{environment}.example.com/v1"
            variables:
              environment:
                default: "staging"
        paths:
          /users:
            get:
              tags:
                - "users"
              summary: "List all users"
              parameters:
                - name: "X-Trace-Id"
                  in: "header"
                  example: "trace-1234"
                - name: "limit"
                  in: "query"
                  example: "25"
              responses:
                '200':
                  description: "Users list"
            post:
              tags:
                - "users"
              summary: "Create a new user"
              requestBody:
                content:
                  application/json:
                    example: '{"username":"john_doe"}'
              responses:
                '201':
                  description: "Created"
          /orders/{orderId}:
            get:
              tags:
                - "orders"
              summary: "Get order by ID"
              parameters:
                - name: "orderId"
                  in: "path"
                  example: "order-999"
              responses:
                '200':
                  description: "Order details"
          /health:
            get:
              summary: "Untagged health check"
              responses:
                '200':
                  description: "Service is healthy"
    """.trimIndent()

    @Test
    fun openApiImporter_createsRootFolder_andTagSubfolders_andUntaggedOperations() = runTest {
        val importer = OpenApiImporter()
        val result = importer.scanContent(mixedSpec)

        val success = assertIs<ImportScanResult.Success>(result)
        assertEquals(SpecFormat.OPENAPI_V3, success.format)
        assertEquals("Complex Hierarchy API", success.specTitle)

        assertEquals(1, success.folders.size)
        val rootCandidate = success.folders.first()
        assertEquals("Complex Hierarchy API", rootCandidate.name)
        assertEquals(4, rootCandidate.requestCount)

        val rootFolder = rootCandidate.folder
        val childItems = rootFolder.items

        // We expect 2 subfolders ("users", "orders") and 1 untagged request ("Untagged health check")
        val subfolders = childItems.filterIsInstance<FolderItem>()
        val untaggedRequests = childItems.filterIsInstance<HttpRequestItem>()

        assertEquals(2, subfolders.size, "Should have 2 tag subfolders: users and orders")
        assertEquals(1, untaggedRequests.size, "Should have 1 untagged request directly under root")

        val untagged = untaggedRequests.first()
        assertEquals("Untagged health check", untagged.info.name)
        assertEquals("{{baseUrl}}/health", untagged.http.url)

        val usersFolder = subfolders.find { it.info.name == "users" }
        assertNotNull(usersFolder, "Users tag folder should exist")
        assertEquals(2, usersFolder.items.size)

        val listUsersReq = usersFolder.items.filterIsInstance<HttpRequestItem>().find { it.info.name == "List all users" }
        assertNotNull(listUsersReq)
        assertEquals("GET", listUsersReq.http.method)
        assertEquals("{{baseUrl}}/users", listUsersReq.http.url)
        assertTrue(listUsersReq.http.headers.any { it.name == "X-Trace-Id" && it.value == "trace-1234" })
        assertTrue(listUsersReq.http.params.any { it.name == "limit" && it.value == "25" && it.type == HttpRequestParamType.QUERY })

        val createUserReq = usersFolder.items.filterIsInstance<HttpRequestItem>().find { it.info.name == "Create a new user" }
        assertNotNull(createUserReq)
        assertEquals("POST", createUserReq.http.method)
        val body = assertIs<RawBody>(createUserReq.http.body)
        assertTrue(body.data.contains("john_doe"))

        val ordersFolder = subfolders.find { it.info.name == "orders" }
        assertNotNull(ordersFolder, "Orders tag folder should exist")
        assertEquals(1, ordersFolder.items.size)
        val getOrderReq = ordersFolder.items.filterIsInstance<HttpRequestItem>().first()
        assertEquals("Get order by ID", getOrderReq.info.name)
        assertTrue(getOrderReq.http.params.any { it.name == "orderId" && it.type == HttpRequestParamType.PATH })
    }

    @Test
    fun openApiImporter_extractsServerEnvironment_andVariables() = runTest {
        val importer = OpenApiImporter()
        val result = importer.scanContent(mixedSpec)

        val success = assertIs<ImportScanResult.Success>(result)
        assertEquals(1, success.environments.size)

        val candidateEnv = success.environments.first()
        assertEquals("Complex Hierarchy API Environment", candidateEnv.name)

        val vars = candidateEnv.environment.variables.filterIsInstance<RegularVariable>()
        val baseUrlVar = vars.find { it.name == "baseUrl" }
        assertNotNull(baseUrlVar)
        assertEquals("https://{environment}.example.com/v1", baseUrlVar.value?.asString)

        val envVar = vars.find { it.name == "environment" }
        assertNotNull(envVar)
        assertEquals("staging", envVar.value?.asString)
    }
}
