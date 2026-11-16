package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.ui.shared.components.executor.*

import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.opencollection.GraphQLBody
import io.github.crustack.clapi.core.model.opencollection.GraphQLBodyVariant
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLRequestExecutionTest {

    @Test
    fun graphql_request_defaults_and_variant_effective_getters() {
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://api.example.com/graphql",
            kind = RequestKind.GRAPHQL,
        )

        assertEquals("POST", tab.graphQlMethod)
        assertTrue(tab.effectiveGraphQlQuery.contains("query"))
        assertTrue(tab.effectiveGraphQlVariables.contains("{"))

        // Update single-body query & variables
        tab.effectiveGraphQlQuery = "query GetAllUsers { users { id } }"
        tab.effectiveGraphQlVariables = "{\"limit\": 10}"

        assertEquals("query GetAllUsers { users { id } }", tab.effectiveGraphQlQuery)
        assertEquals("{\"limit\": 10}", tab.effectiveGraphQlVariables)

        // Add variants
        tab.graphQlVariants.add(
            GraphQLBodyVariant(
                title = "Variant 1",
                selected = true,
                body = GraphQLBody(query = "query V1 { v1 }", variables = "{\"v\": 1}"),
            )
        )
        tab.graphQlVariants.add(
            GraphQLBodyVariant(
                title = "Variant 2",
                selected = false,
                body = GraphQLBody(query = "query V2 { v2 }", variables = "{\"v\": 2}"),
            )
        )
        tab.activeGraphQLVariantIndex = 0

        assertEquals("query V1 { v1 }", tab.effectiveGraphQlQuery)
        assertEquals("{\"v\": 1}", tab.effectiveGraphQlVariables)

        // Switch to Variant 2
        tab.activeGraphQLVariantIndex = 1
        assertEquals("query V2 { v2 }", tab.effectiveGraphQlQuery)
        assertEquals("{\"v\": 2}", tab.effectiveGraphQlVariables)

        // Mutate Variant 2 through effective setter
        tab.effectiveGraphQlQuery = "query V2Updated { v2 }"
        assertEquals("query V2Updated { v2 }", tab.graphQlVariants[1].body.query)
    }

    @Test
    fun graphql_request_execution_skips_when_url_is_blank() = runTest {
        val state = AppState()
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "",
            kind = RequestKind.GRAPHQL,
        )

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertEquals(null, tab.response)
    }

    @Test
    fun graphql_request_pre_script_executes_and_mutates_state() = runTest {
        val state = AppState()
        val tab = RequestTabState(
            name = "Test GraphQL",
            url = "https://invalid.clapi.test/graphql",
            kind = RequestKind.GRAPHQL,
        )
        tab.preRequestScript = """
            clapi.globals.set("test_var", "hello_graphql");
        """.trimIndent()

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertEquals("hello_graphql", state.globalVariables.find { it.key == "test_var" }?.value)
    }
}
