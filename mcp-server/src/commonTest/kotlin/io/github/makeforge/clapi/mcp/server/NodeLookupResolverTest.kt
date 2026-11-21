package io.github.makeforge.clapi.mcp.server

import io.github.makeforge.clapi.mcp.server.backend.AmbiguousIdentifierException
import io.github.makeforge.clapi.mcp.server.backend.NodeLookupResolver
import io.github.makeforge.clapi.mcp.server.backend.NodeNotFoundException
import io.github.makeforge.clapi.mcp.server.backend.WorkspaceTreeNodeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeLookupResolverTest {

    private val sampleTree = listOf(
        WorkspaceTreeNodeDto(
            id = "col-111",
            name = "Payments API",
            kind = "collection",
            slashPath = "Payments API",
            children = listOf(
                WorkspaceTreeNodeDto(
                    id = "fld-222",
                    name = "Users",
                    kind = "folder",
                    slashPath = "Payments API/Users",
                    children = listOf(
                        WorkspaceTreeNodeDto(
                            id = "req-333",
                            name = "Get User",
                            kind = "http",
                            method = "GET",
                            url = "https://api.example.com/users/1",
                            slashPath = "Payments API/Users/Get User",
                        ),
                        WorkspaceTreeNodeDto(
                            id = "req-444",
                            name = "Duplicate Req",
                            kind = "http",
                            method = "GET",
                            slashPath = "Payments API/Users/Duplicate Req",
                        ),
                        WorkspaceTreeNodeDto(
                            id = "req-555",
                            name = "Duplicate Req",
                            kind = "http",
                            method = "POST",
                            slashPath = "Payments API/Users/Duplicate Req",
                        ),
                        WorkspaceTreeNodeDto(
                            id = "req-666",
                            name = "V1/Health",
                            kind = "http",
                            method = "GET",
                            slashPath = "Payments API/Users/V1\\/Health",
                        ),
                    ),
                ),
            ),
        )
    )

    @Test
    fun `resolves nodes by exact and case-insensitive UUID`() {
        val exact = NodeLookupResolver.resolve(sampleTree, "req-333")
        assertEquals("Get User", exact.name)

        val upper = NodeLookupResolver.resolve(sampleTree, "REQ-333")
        assertEquals("req-333", upper.id)
    }

    @Test
    fun `resolves nodes by slash path with leading or trailing slashes`() {
        val folder = NodeLookupResolver.resolve(sampleTree, "Payments API/Users")
        assertEquals("fld-222", folder.id)

        val req = NodeLookupResolver.resolve(sampleTree, "/Payments API/Users/Get User/")
        assertEquals("req-333", req.id)
    }

    @Test
    fun `resolves node names containing escaped literal slashes`() {
        val segments = NodeLookupResolver.parseSlashPath("Payments API/Users/V1\\/Health")
        assertEquals(listOf("Payments API", "Users", "V1/Health"), segments)

        val resolved = NodeLookupResolver.resolve(sampleTree, "Payments API/Users/V1\\/Health")
        assertEquals("req-666", resolved.id)
    }

    @Test
    fun `throws AmbiguousIdentifierException listing candidate UUIDs when slash path matches multiple items`() {
        val ex = assertFailsWith<AmbiguousIdentifierException> {
            NodeLookupResolver.resolve(sampleTree, "Payments API/Users/Duplicate Req")
        }
        assertEquals(2, ex.candidates.size)
        assertTrue(ex.message!!.contains("req-444"))
        assertTrue(ex.message!!.contains("req-555"))
    }

    @Test
    fun `throws NodeNotFoundException when identifier does not exist`() {
        assertFailsWith<NodeNotFoundException> {
            NodeLookupResolver.resolve(sampleTree, "Payments API/Users/NonExistent")
        }
    }
}
