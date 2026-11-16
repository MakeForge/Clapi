package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.RequestKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectionInheritanceTest {

    @Test
    fun getAncestorFolders_resolves_collection_and_folder_chain() {
        val state = AppState(openDefaultTab = false)
        val col = state.createNewCollection("Commerce API")

        val req1 = CollectionNode(
            id = "req-1",
            name = "List Products",
            method = HttpMethodType.GET,
            url = "https://api.example.com/products",
        )
        val folder1 = CollectionNode(
            id = "f-orders",
            name = "Orders",
            isFolder = true,
            children = mutableStateListOf(
                CollectionNode(
                    id = "req-2",
                    name = "Create Order",
                    method = HttpMethodType.POST,
                    url = "https://api.example.com/orders",
                )
            ),
        )
        col.children.add(req1)
        col.children.add(folder1)
        state.notifyCollectionsChanged()

        // 1. Direct child request of collection
        val tabReq1 = RequestTabState(id = "req-1", name = "List Products")
        val ancestorsReq1 = state.getAncestorFolders(tabReq1)
        assertEquals(1, ancestorsReq1.size)
        assertEquals(col.id, ancestorsReq1[0].id)
        assertTrue(ancestorsReq1[0].isCollection)

        // 2. Request inside folder inside collection
        val tabReq2 = RequestTabState(id = "req-2", name = "Create Order")
        val ancestorsReq2 = state.getAncestorFolders(tabReq2)
        assertEquals(2, ancestorsReq2.size)
        assertEquals(col.id, ancestorsReq2[0].id)
        assertEquals(folder1.id, ancestorsReq2[1].id)

        // 3. Folder tab inside collection
        val tabFolder = RequestTabState(id = "f-orders", name = "Orders", kind = RequestKind.FOLDER)
        val ancestorsFolder = state.getAncestorFolders(tabFolder)
        assertEquals(1, ancestorsFolder.size)
        assertEquals(col.id, ancestorsFolder[0].id)

        // 4. Collection tab itself has no ancestors
        val tabCol = RequestTabState(id = col.id, name = col.name, kind = RequestKind.COLLECTION)
        val ancestorsCol = state.getAncestorFolders(tabCol)
        assertTrue(ancestorsCol.isEmpty())
    }

    @Test
    fun collection_headers_and_variables_cascade_to_requests_and_folders() {
        val state = AppState(openDefaultTab = false)
        val col = state.createNewCollection("API Root").copy(
            headerEntries = listOf(
                KeyValueEntry(key = "X-Api-Key", value = "col-secret-key", enabled = true),
                KeyValueEntry(key = "X-Custom-Header", value = "custom-val", enabled = true),
            ),
            variableEntries = listOf(
                KeyValueEntry(key = "baseUrl", value = "https://api.example.com", enabled = true),
            ),
        )
        val folder = CollectionNode(
            id = "f1",
            name = "Users",
            isFolder = true,
            headerEntries = listOf(
                KeyValueEntry(key = "X-Folder-Meta", value = "meta-value", enabled = true),
            ),
            children = mutableStateListOf(
                CollectionNode(
                    id = "r1",
                    name = "Get User",
                    method = HttpMethodType.GET,
                    url = "{{baseUrl}}/users/1",
                )
            ),
        )
        col.children.add(folder)
        state.collections[0] = col

        // Test request receives collection & folder defaults
        val reqTab = RequestTabState(id = "r1", name = "Get User")
        state.applyInheritedDefaults(reqTab)

        val inheritedHeaders = reqTab.headers.filter { it.kind == HeaderKind.INHERITED }
        assertEquals(3, inheritedHeaders.size)
        assertTrue(inheritedHeaders.any { it.key == "X-Api-Key" && it.value == "col-secret-key" })
        assertTrue(inheritedHeaders.any { it.key == "X-Custom-Header" && it.value == "custom-val" })
        assertTrue(inheritedHeaders.any { it.key == "X-Folder-Meta" && it.value == "meta-value" })

        val inheritedVars = reqTab.variables.filter { it.kind == HeaderKind.INHERITED }
        assertEquals(1, inheritedVars.size)
        assertEquals("baseUrl", inheritedVars[0].key)
        assertEquals("https://api.example.com", inheritedVars[0].value)

        // Test folder receives collection defaults
        val folderTab = RequestTabState(id = "f1", name = "Users", kind = RequestKind.FOLDER)
        state.applyInheritedDefaults(folderTab)

        val folderInheritedHeaders = folderTab.headers.filter { it.kind == HeaderKind.INHERITED }
        assertEquals(2, folderInheritedHeaders.size)
        assertTrue(folderInheritedHeaders.any { it.key == "X-Api-Key" && it.value == "col-secret-key" })
        assertTrue(folderInheritedHeaders.any { it.key == "X-Custom-Header" && it.value == "custom-val" })

        val folderInheritedVars = folderTab.variables.filter { it.kind == HeaderKind.INHERITED }
        assertEquals(1, folderInheritedVars.size)
        assertEquals("baseUrl", folderInheritedVars[0].key)
    }

    @Test
    fun local_headers_and_variables_override_inherited_ancestors() {
        val state = AppState(openDefaultTab = false)
        val col = state.createNewCollection("API Root").copy(
            headerEntries = listOf(
                KeyValueEntry(key = "X-Environment", value = "global-prod", enabled = true),
            ),
            variableEntries = listOf(
                KeyValueEntry(key = "timeout", value = "5000", enabled = true),
            ),
        )
        val req = CollectionNode(
            id = "r1",
            name = "Fast Ping",
            method = HttpMethodType.GET,
            url = "https://example.com/ping",
        )
        col.children.add(req)
        state.collections[0] = col

        val reqTab = RequestTabState(id = "r1", name = "Fast Ping")
        // User explicitly defines timeout and X-Environment on the request
        reqTab.headers.add(MutableKeyValue(key = "X-Environment", value = "local-override", kind = HeaderKind.USER))
        reqTab.variables.add(MutableKeyValue(key = "timeout", value = "1000", kind = HeaderKind.USER))

        state.applyInheritedDefaults(reqTab)

        // Local entries take precedence; no duplicate inherited entry is added
        val header = reqTab.headers.first { it.key.equals("X-Environment", ignoreCase = true) }
        assertEquals("local-override", header.value)
        assertEquals(HeaderKind.USER, header.kind)
        assertEquals(1, reqTab.headers.count { it.key.equals("X-Environment", ignoreCase = true) })

        val variable = reqTab.variables.first { it.key.equals("timeout", ignoreCase = true) }
        assertEquals("1000", variable.value)
        assertEquals(HeaderKind.USER, variable.kind)
        assertEquals(1, reqTab.variables.count { it.key.equals("timeout", ignoreCase = true) })
    }

    @Test
    fun auth_inheritance_resolves_from_folder_to_collection() {
        val state = AppState(openDefaultTab = false)
        val col = state.createNewCollection("Secured API").copy(
            authType = AuthType.BEARER,
            authToken = "super-secret-col-token",
        )
        val folder = CollectionNode(
            id = "f-auth",
            name = "Auth Folder",
            isFolder = true,
            authType = AuthType.INHERIT,
            children = mutableStateListOf(
                CollectionNode(
                    id = "r-sec",
                    name = "Protected Resource",
                    method = HttpMethodType.GET,
                    url = "https://api.example.com/protected",
                )
            ),
        )
        col.children.add(folder)
        state.collections[0] = col

        // 1. Folder with INHERIT resolves to collection's Bearer token
        val folderTab = RequestTabState(id = "f-auth", name = "Auth Folder", kind = RequestKind.FOLDER).apply {
            authType = AuthType.INHERIT
        }
        val resolvedFolderAuth = state.resolveInheritedAuth(folderTab)
        assertNotNull(resolvedFolderAuth)
        assertEquals(AuthType.BEARER, resolvedFolderAuth.authType)
        assertEquals("super-secret-col-token", resolvedFolderAuth.token)
        assertEquals("Secured API", resolvedFolderAuth.folderName)

        // 2. Request with INHERIT inside folder also cascades to collection's Bearer token
        val reqTab = RequestTabState(id = "r-sec", name = "Protected Resource").apply {
            authType = AuthType.INHERIT
        }
        val resolvedReqAuth = state.resolveInheritedAuth(reqTab)
        assertNotNull(resolvedReqAuth)
        assertEquals(AuthType.BEARER, resolvedReqAuth.authType)
        assertEquals("super-secret-col-token", resolvedReqAuth.token)
        assertEquals("Secured API", resolvedReqAuth.folderName)
    }
}
