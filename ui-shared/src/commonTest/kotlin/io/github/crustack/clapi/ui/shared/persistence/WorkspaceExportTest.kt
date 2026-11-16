package io.github.crustack.clapi.ui.shared.persistence

import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.storage.opencollection.OpenCollectionSerializer
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceExportTest {

    @Test
    fun exportWorkspaceToOpenCollectionYaml_includesEnvironmentsAndCollections() {
        val state = AppState(openDefaultTab = false)
        val devEnv = EnvState(
            name = "Dev Environment",
            variables = listOf(
                MutableKeyValue(key = "baseUrl", value = "http://dev.localhost", enabled = true),
                MutableKeyValue(key = "token", value = "dev_secret", enabled = true, secret = true),
            )
        )
        val prodEnv = EnvState(
            name = "Prod Environment",
            variables = listOf(
                MutableKeyValue(key = "baseUrl", value = "https://api.prod.com", enabled = true),
            )
        )
        state.environments.addAll(listOf(devEnv, prodEnv))

        val request = CollectionNode(
            id = "req-1",
            name = "Get Status",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "{{baseUrl}}/status",
        )
        val folder = CollectionNode(
            id = "col-1",
            name = "My API",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(request),
        )
        state.collections.add(folder)

        val yaml = ImportExportRepository.exportWorkspaceToOpenCollectionYaml(state)

        assertTrue(yaml.contains("opencollection: 1.0.0"))
        assertTrue(yaml.contains("environments:"))
        assertTrue(yaml.contains("name: Dev Environment"))
        assertTrue(yaml.contains("name: Prod Environment"))
        assertTrue(yaml.contains("baseUrl"))
        assertTrue(yaml.contains("http://dev.localhost"))
        assertTrue(yaml.contains("My API"))
        assertTrue(yaml.contains("Get Status"))

        // Roundtrip test: deserialize and check
        val doc = OpenCollectionSerializer.deserializeYaml(yaml)
        assertEquals(2, doc.config?.environments?.size)
        assertEquals("Dev Environment", doc.config?.environments?.get(0)?.name)
        assertEquals("Prod Environment", doc.config?.environments?.get(1)?.name)
    }

    @Test
    fun exportWorkspaceToOpenApiYaml_includesEnvironmentsAndPaths() {
        val state = AppState(openDefaultTab = false)
        val env = EnvState(
            name = "Production",
            variables = listOf(
                MutableKeyValue(key = "baseUrl", value = "https://api.clapi.com", enabled = true),
                MutableKeyValue(key = "apiKey", value = "abc123xyz", enabled = true),
            )
        )
        state.environments.add(env)

        val request = CollectionNode(
            id = "req-2",
            name = "Create Item",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "{{baseUrl}}/items",
            bodyType = BodyType.JSON,
            bodyContent = """{"title":"Hello"}""",
            queryEntries = listOf(KeyValueEntry(key = "sync", value = "true", enabled = true)),
        )
        val folder = CollectionNode(
            id = "col-2",
            name = "Items Service",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(request),
        )
        state.collections.add(folder)

        val yaml = ImportExportRepository.exportWorkspaceToOpenApiYaml(state)

        assertTrue(yaml.contains("openapi: 3.0.3"))
        assertTrue(yaml.contains("servers:"))
        assertTrue(yaml.contains("https://api.clapi.com"))
        assertTrue(yaml.contains("x-environments:"))
        assertTrue(yaml.contains("name: Production"))
        assertTrue(yaml.contains("apiKey: abc123xyz"))
        assertTrue(yaml.contains("/items:"))
        assertTrue(yaml.contains("post:"))
        assertTrue(yaml.contains("summary: Create Item"))
        assertTrue(yaml.contains("sync"))
        assertTrue(yaml.contains("application/json"))
    }
}
