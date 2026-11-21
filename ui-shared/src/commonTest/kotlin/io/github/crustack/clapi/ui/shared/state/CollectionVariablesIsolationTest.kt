package io.github.crustack.clapi.ui.shared.state

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.network.VariableResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionVariablesIsolationTest {

    @Test
    fun collection_variables_set_in_collection_A_do_not_bleed_into_collection_B() {
        val state = AppState(withDemoData = false)

        val reqA = CollectionNode(
            id = "req-a",
            name = "Request A",
            method = HttpMethodType.GET,
            url = "https://example.com/a",
        )
        val colA = CollectionNode(
            id = "col-a",
            name = "Collection A",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            variableEntries = listOf(KeyValueEntry("baseKey", "initial-a", enabled = true)),
        ).also { it.children.add(reqA) }

        val reqB = CollectionNode(
            id = "req-b",
            name = "Request B",
            method = HttpMethodType.GET,
            url = "https://example.com/b",
        )
        val colB = CollectionNode(
            id = "col-b",
            name = "Collection B",
            isFolder = true,
            kind = RequestKind.COLLECTION,
            variableEntries = listOf(KeyValueEntry("baseKey", "initial-b", enabled = true)),
        ).also { it.children.add(reqB) }

        state.collections.addAll(listOf(colA, colB))

        val tabA = RequestTabState(
            id = "req-a",
            name = "Request A",
            collectionId = "col-a",
            collectionName = "Collection A",
        )
        val tabB = RequestTabState(
            id = "req-b",
            name = "Request B",
            collectionId = "col-b",
            collectionName = "Collection B",
        )

        // Mutate collection variables in Collection A via script helper
        state.mergeCollectionScriptVariables(
            mapOf("sharedKey" to "from-collection-A", "baseKey" to "mutated-a"),
            tab = tabA,
        )

        val varsA = state.collectionVariablesFor(tabA)
        val varsB = state.collectionVariablesFor(tabB)

        assertEquals("from-collection-A", varsA["sharedKey"])
        assertEquals("mutated-a", varsA["baseKey"])

        assertNull(varsB["sharedKey"], "Collection B must not inherit runtime collection variable from Collection A")
        assertEquals("initial-b", varsB["baseKey"])

        val resolvedInA = VariableResolver.resolve("{{sharedKey}}/{{baseKey}}", state.activeVariableLayers(tabA))
        val resolvedInB = VariableResolver.resolve("{{sharedKey}}/{{baseKey}}", state.activeVariableLayers(tabB))

        assertEquals("from-collection-A/mutated-a", resolvedInA)
        assertEquals("{{sharedKey}}/initial-b", resolvedInB)
    }
}
