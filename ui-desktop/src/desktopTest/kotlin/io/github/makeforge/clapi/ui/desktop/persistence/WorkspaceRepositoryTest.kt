// Modified by MakeForge (2026)
package io.github.makeforge.clapi.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.CollectionNode
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.HistoryItem
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [WorkspaceRepository] persistence using the single global OpenCollection
 * document (`config.environments` and top-level folder items) and [PrivateStateRepository].
 */
class WorkspaceRepositoryTest {

    private val SETTINGS_ENV_KEY = "settings.selectedEnvName"

    @Before
    fun setUp() {
        PlatformStorage.remove(SETTINGS_ENV_KEY)
        WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository()
        WorkspaceRepository.privateStateRepository = PrivateStateRepository()
    }

    @After
    fun tearDown() {
        PlatformStorage.remove(SETTINGS_ENV_KEY)
        PlatformStorage.remove("clapi.workspace")
        WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository()
        WorkspaceRepository.privateStateRepository = PrivateStateRepository()
    }

    // ── Basic save / load ────────────────────────────────────────────────────

    @Test
    fun save_and_load_roundtrip_with_large_body_preserves_content_exactly() {
        val largeBody = "{\"data\":\"" + "X".repeat(200_000) + "\"}"

        val request = CollectionNode(
            id = "large-req-1",
            name = "Large Payload",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/large",
            bodyType = BodyType.JSON,
            bodyContent = largeBody,
            bodyContents = mapOf(BodyType.JSON.name to largeBody),
        )
        val collection = CollectionNode(
            id = "large-col-1",
            name = "Large Payload Collection",
            isFolder = true,
            children = mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val loadedRequest = restored.collections
            .firstOrNull { it.name == "Large Payload Collection" }
            ?.children
            ?.firstOrNull()

        assertNotNull(loadedRequest, "Request must be present after WorkspaceRepository load")
        assertEquals(
            largeBody.length,
            loadedRequest.bodyContent?.length,
            "Large body length must survive a WorkspaceRepository save/load cycle",
        )
        assertEquals(
            largeBody,
            loadedRequest.bodyContent,
            "Large body content must survive a WorkspaceRepository save/load cycle exactly",
        )
    }

    @Test
    fun save_and_load_with_multiple_large_per_type_bodies_preserves_all_types() {
        val jsonBody = "{\"j\":\"" + "j".repeat(100_000) + "\"}"
        val xmlBody  = "<root>" + "x".repeat(100_000) + "</root>"

        val request = CollectionNode(
            id = "multi-body-req-1",
            name = "Multi Body Request",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/multi",
            bodyType = BodyType.JSON,
            bodyContent = jsonBody,
            bodyContents = mapOf(
                BodyType.JSON.name to jsonBody,
                BodyType.XML.name  to xmlBody,
            ),
        )
        val collection = CollectionNode(
            id = "multi-body-col-1",
            name = "Multi Body Collection",
            isFolder = true,
            children = mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val loadedRequest = restored.collections
            .firstOrNull { it.name == "Multi Body Collection" }
            ?.children
            ?.firstOrNull()

        assertNotNull(loadedRequest)
        assertEquals(
            jsonBody,
            loadedRequest.bodyContents[BodyType.JSON.name],
            "Large JSON body must survive workspace save/load",
        )
        assertEquals(
            xmlBody,
            loadedRequest.bodyContents[BodyType.XML.name],
            "Large XML body must survive workspace save/load",
        )
    }

    @Test
    fun save_overwrites_previous_save_with_updated_content() {
        val original = "original body"
        val updated  = "updated body " + "u".repeat(10_000)

        fun buildState(body: String): AppState {
            val req = CollectionNode(
                id = "overwrite-req-1",
                name = "Overwrite Request",
                isFolder = false,
                method = HttpMethodType.POST,
                url = "https://api.example.com",
                bodyType = BodyType.JSON,
                bodyContent = body,
                bodyContents = mapOf(BodyType.JSON.name to body),
            )
            val col = CollectionNode(
                id = "overwrite-col-1",
                name = "Overwrite Collection",
                isFolder = true,
                children = mutableStateListOf(req),
            )
            return AppState(openDefaultTab = false).also { it.collections.add(col) }
        }

        WorkspaceRepository.save(buildState(original))
        WorkspaceRepository.save(buildState(updated))

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val body = restored.collections
            .firstOrNull { it.name == "Overwrite Collection" }
            ?.children?.firstOrNull()?.bodyContent

        assertEquals(updated, body, "Second save must overwrite the first; restored body must match updated value")
    }

    // ── Selected environment persistence ─────────────────────────────────────

    @Test
    fun selected_environment_is_restored_to_correct_index_after_save_and_load() {
        val source = AppState(openDefaultTab = false).also { state ->
            state.environments.add(EnvState("Development"))
            state.environments.add(EnvState("Staging"))
            state.environments.add(EnvState("Production"))
            state.selectedEnvIndex = 1
            state.settings.selectedEnvName = "Staging"
        }

        SettingsRepository.save(source.settings)
        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        SettingsRepository.load(restored.settings)
        WorkspaceRepository.load(restored)

        assertEquals(1, restored.selectedEnvIndex,
            "selectedEnvIndex must be 1 (Staging) after restore")
        assertEquals("Staging", restored.selectedEnvironment?.name,
            "selectedEnvironment must be Staging after restore")
    }

    @Test
    fun selected_environment_falls_back_to_first_when_saved_name_not_found() {
        val source = AppState(openDefaultTab = false).also { state ->
            state.environments.add(EnvState("Development"))
            state.environments.add(EnvState("Staging"))
            state.settings.selectedEnvName = "DeletedEnv"
        }

        SettingsRepository.save(source.settings)
        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        SettingsRepository.load(restored.settings)
        WorkspaceRepository.load(restored)

        assertEquals(0, restored.selectedEnvIndex,
            "Must fall back to index 0 when saved environment name is not found")
        assertEquals("Development", restored.selectedEnvironment?.name)
    }

    @Test
    fun selected_environment_falls_back_to_first_when_no_name_saved() {
        val source = AppState(openDefaultTab = false).also { state ->
            state.environments.add(EnvState("Development"))
            state.environments.add(EnvState("Staging"))
        }

        SettingsRepository.save(source.settings)
        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        SettingsRepository.load(restored.settings)
        WorkspaceRepository.load(restored)

        assertEquals(0, restored.selectedEnvIndex,
            "Must default to index 0 when no env name was saved")
    }

    @Test
    fun selected_environment_handles_empty_env_list_gracefully() {
        val source = AppState(openDefaultTab = false).also { state ->
            state.settings.selectedEnvName = "Ghost"
        }

        SettingsRepository.save(source.settings)
        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        SettingsRepository.load(restored.settings)
        WorkspaceRepository.load(restored)

        assertEquals(0, restored.selectedEnvIndex,
            "selectedEnvIndex must be 0 when environment list is empty")
    }

    @Test
    fun selected_last_environment_is_correctly_restored() {
        val source = AppState(openDefaultTab = false).also { state ->
            state.environments.add(EnvState("Dev"))
            state.environments.add(EnvState("Prod"))
            state.selectedEnvIndex = 1
            state.settings.selectedEnvName = "Prod"
        }

        SettingsRepository.save(source.settings)
        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        SettingsRepository.load(restored.settings)
        WorkspaceRepository.load(restored)

        assertEquals(1, restored.selectedEnvIndex)
        assertEquals("Prod", restored.selectedEnvironment?.name)
    }

    // ── Private store + global OpenCollection document (FR-014 to FR-019) ────

    @Test
    fun save_and_load_persists_global_variables_history_and_expanded_state() {
        val source = AppState(openDefaultTab = false)
        val collId = "coll-expand-1"
        val folderId = "folder-expand-1"
        source.collections.add(
            CollectionNode(
                id = collId,
                name = "My Collection",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = folderId, name = "Folder A", isFolder = true, children = mutableStateListOf()),
                    CollectionNode(id = "folder-b", name = "Folder B", isFolder = true, children = mutableStateListOf()),
                ),
            )
        )
        source.collectionExpandedState[collId] = false
        source.collectionExpandedState[folderId] = false
        source.globalVariables.add(MutableKeyValue("apiKey", "abc123", enabled = true, secret = false))
        source.historyItems.add(
            HistoryItem(
                requestId = "hist-1",
                method = HttpMethodType.GET,
                name = "Ping",
                url = "https://example.com/ping",
                timestamp = 1000L,
            )
        )

        assertTrue(WorkspaceRepository.save(source))

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val restoredColl = restored.collections.single { it.name == "My Collection" }
        val restoredFolderA = restoredColl.children.first { it.name == "Folder A" }
        assertEquals(collId, restoredColl.id)
        assertEquals(folderId, restoredFolderA.id)
        assertEquals(false, restored.collectionExpandedState[collId])
        assertEquals(false, restored.collectionExpandedState[folderId])
        assertNull(restored.collectionExpandedState["folder-b"])
        assertTrue(restored.globalVariables.any { it.key == "apiKey" && it.value == "abc123" })
        assertTrue(restored.historyItems.any { it.requestId == "hist-1" && it.name == "Ping" })
    }

    @Test
    fun environments_persist_with_zero_collections_in_global_document_config() {
        // FR-015: Environments live in config.environments of the global document even when no collection exists.
        val source = AppState(openDefaultTab = false)
        source.collections.clear()
        source.environments.clear()
        source.environments.add(
            EnvState(
                name = "Staging",
                variables = listOf(
                    MutableKeyValue("baseUrl", "https://staging.example.com", enabled = true, secret = false),
                    MutableKeyValue("disabledVar", "off", enabled = false, secret = false),
                    MutableKeyValue("secretToken", "ignored", enabled = true, secret = true),
                ),
                color = "#FF0000",
            )
        )

        assertTrue(WorkspaceRepository.save(source))

        val globalDoc = runBlocking { WorkspaceRepository.canonicalRepository.loadGlobalCollection() }
        assertNotNull(globalDoc)
        assertTrue(globalDoc.items.isEmpty())
        assertEquals(1, globalDoc.config?.environments?.size)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        assertTrue(restored.collections.isEmpty())
        assertEquals(1, restored.environments.size)
        val env = restored.environments.single()
        assertEquals("Staging", env.name)
        assertEquals("#FF0000", env.color)
        assertEquals(3, env.variables.size)
        assertTrue(env.variables.any { it.key == "baseUrl" && it.value == "https://staging.example.com" && it.enabled && !it.secret })
        assertTrue(env.variables.any { it.key == "disabledVar" && it.value == "off" && !it.enabled && !it.secret })
        assertTrue(env.variables.any { it.key == "secretToken" && it.secret && it.enabled })
    }

    @Test
    fun importing_collection_merges_environments_by_name_without_overwriting_existing_entries() {
        // FR-016: Merge by name keeps existing entries and adds missing variables/fields.
        val state = AppState(openDefaultTab = false)
        state.environments.add(
            EnvState(
                name = "Dev",
                variables = listOf(MutableKeyValue("baseUrl", "http://existing", enabled = true)),
                color = "#112233",
            )
        )

        val importedJson = """
            {
              "opencollection": "1.0.0",
              "info": { "name": "Imported API" },
              "config": {
                "environments": [
                  {
                    "name": "Dev",
                    "color": "#999999",
                    "extends": "Base",
                    "variables": [
                      { "name": "baseUrl", "value": "http://overwritten" },
                      { "name": "newKey", "value": "addedValue" }
                    ]
                  },
                  {
                    "name": "Prod",
                    "variables": [
                      { "name": "baseUrl", "value": "https://prod.example.com" }
                    ]
                  }
                ]
              },
              "items": []
            }
        """.trimIndent()

        ImportExportRepository.importCollectionFromString(state, importedJson)

        assertEquals(2, state.environments.size)
        val dev = state.environments.first { it.name == "Dev" }
        assertEquals("#112233", dev.color, "Existing color must not be overwritten")
        assertEquals("Base", dev.extends, "Missing field 'extends' must be added")
        assertEquals("http://existing", dev.variables.first { it.key == "baseUrl" }.value, "Existing variable must not be overwritten")
        assertEquals("addedValue", dev.variables.first { it.key == "newKey" }.value, "Missing variable must be added")

        val prod = state.environments.first { it.name == "Prod" }
        assertEquals("https://prod.example.com", prod.variables.single().value)
    }

    @Test
    fun single_collection_export_omits_config_and_environments() {
        // FR-017: Single-collection export produces a standalone document with no config and no environments.
        val state = AppState(openDefaultTab = false)
        state.environments.add(
            EnvState("Dev", variables = listOf(MutableKeyValue("k", "v")))
        )
        val collection = CollectionNode(
            id = "col-1",
            name = "Exported Collection",
            isFolder = true,
            children = mutableStateListOf(
                CollectionNode(id = "req-1", name = "Ping", isFolder = false, method = HttpMethodType.GET, url = "https://example.com")
            ),
        )
        state.collections.add(collection)

        val exportedJson = ImportExportRepository.exportCollectionToString(collection)
        val root = Json.parseToJsonElement(exportedJson).jsonObject
        assertFalse(root.containsKey("config"), "Single-collection export must not contain 'config'")
        assertFalse(exportedJson.contains("\"environments\""), "Single-collection export must not contain environments")
    }

    @Test
    fun stored_legacy_clapi_workspace_key_is_ignored_on_load() {
        // FR-018: A stored clapi.workspace key in PlatformStorage is ignored and never read.
        PlatformStorage.putString(
            "clapi.workspace",
            """
            {
              "type": "clapiWorkspace",
              "version": "1.0",
              "collections": [
                { "name": "Ghost Legacy Collection", "folders": [], "requests": [] }
              ],
              "environments": [
                { "name": "Ghost Legacy Env", "variables": { "a": "b" } }
              ]
            }
            """.trimIndent(),
        )

        val state = AppState(openDefaultTab = false)
        WorkspaceRepository.load(state)

        assertTrue(state.collections.isEmpty(), "Legacy clapi.workspace collections must be ignored")
        assertTrue(state.environments.isEmpty(), "Legacy clapi.workspace environments must be ignored")
    }
}
