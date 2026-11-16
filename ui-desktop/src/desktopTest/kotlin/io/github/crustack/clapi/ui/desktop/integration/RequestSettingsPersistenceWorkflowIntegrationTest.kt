package io.github.crustack.clapi.ui.desktop.integration

import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.network.NetworkEvent
import io.github.crustack.clapi.core.network.NoOpNetworkLogger
import io.github.crustack.clapi.core.network.RetryPolicy
import io.github.crustack.server.module
import io.github.crustack.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository
import io.github.crustack.clapi.ui.shared.components.buildAuthConfig
import io.github.crustack.clapi.ui.shared.components.buildRequestBody
import io.github.crustack.clapi.ui.shared.network.NetworkClientFactory
import io.github.crustack.clapi.ui.shared.persistence.PrivateStateRepository
import io.github.crustack.clapi.ui.shared.persistence.SettingsRepository
import io.github.crustack.clapi.ui.shared.persistence.TabsRepository
import io.github.crustack.clapi.ui.shared.persistence.WorkspaceRepository
import io.github.crustack.clapi.ui.shared.platform.PlatformStorage
import io.github.crustack.clapi.ui.shared.state.AppState
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestSettingsPersistenceWorkflowIntegrationTest {

    companion object {
        private const val PORT = 18082
        private lateinit var server: io.ktor.server.engine.EmbeddedServer<*, *>

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = embeddedServer(Netty, port = PORT, module = io.ktor.server.application.Application::module)
            server.start(wait = false)
            Thread.sleep(300)
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop(500, 2000)
        }
    }

    @Before
    fun setUp() {
        clearStorage()
    }

    @After
    fun tearDown() {
        clearStorage()
    }

    @Test
    fun workflow_request_execution_works_after_settings_workspace_and_tabs_restore() = runTest {
        val source = AppState(openDefaultTab = false, withDemoData = false)
        source.addTabInSelectedCollection()

        val tab = source.activeTab!!
        tab.name = "Slow Request"
        tab.method = HttpMethodType.GET
        tab.url = "http://localhost:$PORT/api/slow?ms=200"
        tab.headers.clear()
        tab.headers.add(io.github.crustack.clapi.ui.shared.state.MutableKeyValue("Accept", "application/json"))
        tab.retryEnabled = true
        tab.retryCount = 2
        tab.retryDelayMs = 50L

        source.settings.requestTimeoutSec = 3
        source.settings.followRedirects = true
        source.settings.scriptPrefix = "api"

        SettingsRepository.save(source.settings)
        WorkspaceRepository.save(source)
        TabsRepository.save(source)

        assertNotNull(WorkspaceRepository.canonicalRepository.loadGlobalCollection())

        val restored = AppState(openDefaultTab = false, withDemoData = false)
        SettingsRepository.load(restored.settings)
        WorkspaceRepository.load(restored)
        TabsRepository.load(restored)
        restored.syncSidebarToActiveTab()

        assertEquals(3, restored.settings.requestTimeoutSec)
        assertEquals("api", restored.settings.scriptPrefix)

        val restoredTab = restored.activeTab!!
        assertEquals("Slow Request", restoredTab.name)
        assertEquals("http://localhost:$PORT/api/slow?ms=200", restoredTab.url)
        assertEquals(true, restoredTab.retryEnabled)
        assertEquals(2, restoredTab.retryCount)

        val request = RequestDefinition(
            id = restoredTab.id,
            name = restoredTab.name,
            method = restoredTab.method,
            url = restoredTab.url,
            queryParams = restoredTab.params.filter { it.enabled && it.key.isNotBlank() }
                .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) },
            headers = restoredTab.headers.filter { it.enabled && it.key.isNotBlank() }
                .map { KeyValueEntry(it.key, it.value, it.enabled, it.secret) },
            auth = buildAuthConfig(restoredTab),
            body = buildRequestBody(restoredTab.bodyType, restoredTab.bodyContent),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val client = NetworkClientFactory.build(
            settings = restored.settings,
            logger = NoOpNetworkLogger,
            retryPolicy = RetryPolicy(
                maxAttempts = restoredTab.retryCount,
                baseDelayMs = restoredTab.retryDelayMs,
                maxDelayMs = restoredTab.retryDelayMs * 10,
            ),
        )

        val events = client.execute(request, restored.activeVariableLayers()).toList()
        assertTrue(events.first() is NetworkEvent.Started)
        assertTrue(events.last() is NetworkEvent.Success)

        val response = (events.last() as NetworkEvent.Success).response
        assertEquals(200, response.statusCode)
    }

    private fun clearStorage() {
        WorkspaceRepository.canonicalRepository = DefaultCanonicalCollectionRepository()
        WorkspaceRepository.privateStateRepository = PrivateStateRepository()
        val keys = listOf(
            "clapi.tabs",
            "settings.autoSaveRequests",
            "settings.confirmBeforeDelete",
            "settings.defaultTimeoutSec",
            "settings.responseLayout",
            "settings.theme",
            "settings.language",
            "settings.requestTimeoutSec",
            "settings.followRedirects",
            "settings.collectionsExpanded",
            "settings.environmentsExpanded",
            "settings.proxyEnabled",
            "settings.httpProxy",
            "settings.httpsProxy",
            "settings.scriptPrefix",
        )
        keys.forEach { PlatformStorage.remove(it) }
    }
}
