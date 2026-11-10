package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testGraphQLRequestItemWithSingleBodyRoundTrip() {
        val item = GraphQLRequestItem(
            info = GraphQLRequestInfo(name = "Get User"),
            graphql = GraphQLRequestDetails(
                method = "POST",
                url = "https://api.example.com/graphql",
                headers = listOf(
                    HttpRequestHeader(name = "X-Api-Key", value = "secret")
                ),
                body = GraphQLBodyPayload.Single(
                    GraphQLBody(query = "query { user { id name } }", variables = "{\"id\":\"1\"}")
                ),
                auth = AuthBearer(token = "bearer-token")
            ),
            settings = GraphQLRequestSettings(
                timeout = JsonPrimitive(5000),
                followRedirects = JsonPrimitive(true)
            ),
            docs = "Documentation for Get User query"
        )

        val serialized = json.encodeToString(GraphQLRequestItem.serializer(), item)
        assertTrue(serialized.contains("\"graphql\":"))
        assertTrue(serialized.contains(""""query":"query { user { id name } }""""))
        assertTrue(serialized.contains(""""variables":"{\"id\":\"1\"}""""))

        val deserialized = json.decodeFromString(GraphQLRequestItem.serializer(), serialized)
        assertEquals("Get User", deserialized.info.name)
        assertEquals(ItemType.GRAPHQL, deserialized.info.type)
        assertEquals("https://api.example.com/graphql", deserialized.graphql.url)
        assertEquals("POST", deserialized.graphql.method)
        assertEquals(1, deserialized.graphql.headers.size)
        assertEquals("X-Api-Key", deserialized.graphql.headers[0].name)
        val payload = deserialized.graphql.body
        assertIs<GraphQLBodyPayload.Single>(payload)
        assertEquals("query { user { id name } }", payload.body.query)
        assertEquals("{\"id\":\"1\"}", payload.body.variables)
        assertIs<AuthBearer>(deserialized.graphql.auth)
        assertEquals("bearer-token", (deserialized.graphql.auth as AuthBearer).token)
    }

    @Test
    fun testGraphQLRequestItemWithVariantsBodyRoundTrip() {
        val item = GraphQLRequestItem(
            info = GraphQLRequestInfo(name = "User Operations"),
            graphql = GraphQLRequestDetails(
                url = "https://api.example.com/graphql",
                body = GraphQLBodyPayload.Variants(
                    listOf(
                        GraphQLBodyVariant(
                            title = "List Users",
                            selected = true,
                            body = GraphQLBody(query = "query { users { id } }")
                        ),
                        GraphQLBodyVariant(
                            title = "User Details",
                            selected = false,
                            body = GraphQLBody(query = "query (\$id: ID!) { user(id: \$id) { name } }", variables = "{\"id\":\"42\"}")
                        )
                    )
                )
            )
        )

        val serialized = json.encodeToString(GraphQLRequestItem.serializer(), item)
        val deserialized = json.decodeFromString(GraphQLRequestItem.serializer(), serialized)
        val payload = deserialized.graphql.body
        assertIs<GraphQLBodyPayload.Variants>(payload)
        assertEquals(2, payload.variants.size)
        assertEquals("List Users", payload.variants[0].title)
        assertTrue(payload.variants[0].selected == true)
        assertEquals("query { users { id } }", payload.variants[0].body.query)
        assertEquals("User Details", payload.variants[1].title)
        assertEquals("{\"id\":\"42\"}", payload.variants[1].body.variables)
    }

    @Test
    fun testPolymorphicDeserializationViaOpenCollectionItemSerializer() {
        val jsonString = """
            {
              "info": {
                "name": "Feed Query",
                "type": "graphql"
              },
              "graphql": {
                "method": "POST",
                "url": "https://api.example.com/graphql",
                "body": {
                  "query": "query { feed { items } }"
                }
              }
            }
        """.trimIndent()

        val item = json.decodeFromString(OpenCollectionItemSerializer, jsonString)
        assertIs<GraphQLRequestItem>(item)
        assertEquals("Feed Query", item.info.name)
        assertEquals("https://api.example.com/graphql", item.graphql.url)
        val payload = item.graphql.body
        assertIs<GraphQLBodyPayload.Single>(payload)
        assertEquals("query { feed { items } }", payload.body.query)
    }
}
