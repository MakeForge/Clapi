package io.github.crustack.clapi.qa

import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.GraphQlBody
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.GraphQLBody
import io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload
import io.github.crustack.clapi.core.model.opencollection.GraphQLBodyVariant
import io.github.crustack.clapi.core.model.opencollection.GraphQLRequestDetails
import io.github.crustack.clapi.core.model.opencollection.GraphQLRequestInfo
import io.github.crustack.clapi.core.model.opencollection.GraphQLRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader
import io.github.crustack.clapi.core.network.KtorApiClient
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.clapi.core.network.execution.RequestExecutionService
import io.github.crustack.clapi.testsupport.DummyApiServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLRequestE2ETest {

    private lateinit var server: DummyApiServer
    private lateinit var baseUrl: String

    @BeforeTest
    fun setUp() {
        server = DummyApiServer()
        server.start()
        baseUrl = server.baseUrl()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun ktorClient_executesGraphQLPostQueryWithVariables() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val query = "query GetUsers { users { id name } }"
        val variables = "{\"limit\": 5}"

        val request = RequestDefinition(
            id = "gql-post-test",
            name = "GraphQL Post Query",
            method = HttpMethodType.POST,
            url = "$baseUrl/graphql",
            body = RequestBody(
                type = BodyType.GRAPHQL,
                graphQl = GraphQlBody(query = query, variablesJson = variables),
            ),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

        val events = client.execute(request).toList()
        val success = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()

        assertNotNull(success, "Expected successful GraphQL response event")
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("users"), "Expected body to contain users: ${success.response.bodyText}")
        assertTrue(success.response.bodyText.contains(query), "Expected body to echo query: ${success.response.bodyText}")
    }

    @Test
    fun requestExecutionService_executesGraphQLRequestItem_singleBody() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val query = "query SingleQuery { users { id name } }"
        val requestItem = GraphQLRequestItem(
            info = GraphQLRequestInfo(name = "Single Query GQL"),
            graphql = GraphQLRequestDetails(
                method = "POST",
                url = "{{baseUrl}}/graphql",
                headers = listOf(HttpRequestHeader(name = "Accept", value = "application/json")),
                body = GraphQLBodyPayload.Single(GraphQLBody(query = query, variables = "{\"limit\": 3}")),
            ),
        )

        val events = service.execute(
            requestItem = requestItem,
            variableLayers = listOf(mapOf("baseUrl" to baseUrl)),
        ).toList()
        val success = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()

        assertNotNull(success, "Expected successful GraphQL response")
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("users"))
        assertTrue(success.response.bodyText.contains(query))
    }

    @Test
    fun requestExecutionService_executesGraphQLRequestItem_multiVariants() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val queryActive = "query ActiveVariantQuery { users { id } }"
        val requestItem = GraphQLRequestItem(
            info = GraphQLRequestInfo(name = "Multi Variant GQL"),
            graphql = GraphQLRequestDetails(
                method = "POST",
                url = "$baseUrl/graphql",
                body = GraphQLBodyPayload.Variants(
                    listOf(
                        GraphQLBodyVariant(title = "Inactive", selected = false, body = GraphQLBody(query = "query Inactive { none }")),
                        GraphQLBodyVariant(title = "Active", selected = true, body = GraphQLBody(query = queryActive)),
                    )
                ),
            ),
        )

        val events = service.execute(requestItem).toList()
        val success = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()

        assertNotNull(success, "Expected successful GraphQL response")
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains(queryActive))
    }

    @Test
    fun requestExecutionService_executesGraphQLRequestItem_getMethod() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val query = "query GetViaGet { users { id } }"
        val requestItem = GraphQLRequestItem(
            info = GraphQLRequestInfo(name = "GET Query GQL"),
            graphql = GraphQLRequestDetails(
                method = "GET",
                url = "$baseUrl/graphql",
                body = GraphQLBodyPayload.Single(GraphQLBody(query = query)),
            ),
        )

        val events = service.execute(requestItem).toList()
        val success = events.filterIsInstance<NetworkEvent.Success>().firstOrNull()

        assertNotNull(success, "Expected successful GraphQL response")
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("GetViaGet"))
    }
}
