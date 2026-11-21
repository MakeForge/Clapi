// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.components.sidebar

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class SidebarPerformanceTest {

    private fun largeCollection(size: Int = 150): MutableList<CollectionNode> {
        val requests = (1..size).map { index ->
            CollectionNode(
                id = "r$index",
                name = "Request $index",
                method = HttpMethodType.GET,
                url = "https://api.test/items/$index",
            )
        }
        val children = mutableStateListOf<CollectionNode>().also { it.addAll(requests) }

        return mutableStateListOf(
            CollectionNode(
                id = "c1",
                name = "Large Collection",
                isFolder = true,
                children = children,
            )
        )
    }

    @Test
    fun duplicate_delete_and_reorder_remain_fast_for_100_plus_requests() {
        val collections = largeCollection(150)

        val duplicateMs = measureTimeMillis {
            duplicateRequestInCollections(collections, "r70")
        }
        val deleteMs = measureTimeMillis {
            deleteRequestFromCollections(collections, "r20")
        }
        val reorderMs = measureTimeMillis {
            moveRequestBeforeRequest(collections, "r120", "r5")
        }

        assertTrue(duplicateMs < 200, "Duplicate took $duplicateMs ms")
        assertTrue(deleteMs < 200, "Delete took $deleteMs ms")
        assertTrue(reorderMs < 200, "Reorder took $reorderMs ms")
    }
}
