package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.mcp.server.inspection.JsonPathInspector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonPathInspectorTest {

    private val sampleJson = """
        {
          "status": "ok",
          "data": {
            "users": [
              {"id": 101, "name": "Alice", "roles": ["admin", "dev"]},
              {"id": 102, "name": "Bob", "roles": ["viewer"]},
              {"id": 103, "name": "Charlie", "roles": ["dev"]}
            ],
            "meta": {
              "total": 3
            }
          }
        }
    """.trimIndent()

    @Test
    fun `extracts nested fields and array elements via JSONPath`() {
        val aliceName = JsonPathInspector.inspect("req-1", sampleJson, jsonPath = "$.data.users[0].name")
        assertEquals("Alice", aliceName.content)

        val lastUser = JsonPathInspector.inspect("req-1", sampleJson, jsonPath = "$.data.users[-1].id")
        assertEquals("103", lastUser.content)

        val allIds = JsonPathInspector.inspect("req-1", sampleJson, jsonPath = "$.data.users[*].id")
        assertTrue(allIds.content.contains("101"))
        assertTrue(allIds.content.contains("102"))
        assertTrue(allIds.content.contains("103"))

        val recursiveNames = JsonPathInspector.inspect("req-1", sampleJson, jsonPath = "$..name")
        assertTrue(recursiveNames.content.contains("Alice"))
        assertTrue(recursiveNames.content.contains("Charlie"))
    }

    @Test
    fun `supports array slicing and UTF-8 byteOffset pagination`() {
        val slice = JsonPathInspector.inspect("req-1", sampleJson, jsonPath = "$.data.users[0:2].name")
        assertTrue(slice.content.contains("Alice"))
        assertTrue(slice.content.contains("Bob"))
        assertFalse(slice.content.contains("Charlie"))

        val largeBody = "A".repeat(1000) + "B".repeat(1000)
        val page1 = JsonPathInspector.inspect("req-1", largeBody, byteOffset = 0, length = 500)
        assertEquals(500, page1.returnedBytes)
        assertEquals(2000, page1.totalBytes)
        assertTrue(page1.hasMore)
        assertEquals("A".repeat(500), page1.content)

        val page2 = JsonPathInspector.inspect("req-1", largeBody, byteOffset = 1000, length = 1000)
        assertEquals(1000, page2.returnedBytes)
        assertFalse(page2.hasMore)
        assertEquals("B".repeat(1000), page2.content)
    }
}
