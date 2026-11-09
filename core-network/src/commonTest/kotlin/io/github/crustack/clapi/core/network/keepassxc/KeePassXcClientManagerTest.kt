package io.github.crustack.clapi.core.network.keepassxc

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeePassXcClientManagerTest {

    private class FakeClient : KeePassXcClient {
        var disconnected = false
        override suspend fun checkStatus(): KeePassXcStatus = KeePassXcStatus.Connected
        override suspend fun queryLogins(url: String, customFilters: Map<String, String>): List<KeePassXcEntry> = emptyList()
        override suspend fun countMatchingLogins(url: String, customFilters: Map<String, String>): Int = 0
        override suspend fun disconnect() {
            disconnected = true
        }
    }

    @Test
    fun getClient_lazily_instantiates_client() = runTest {
        var createCount = 0
        val manager = DefaultKeePassXcClientManager {
            createCount++
            FakeClient()
        }

        assertFalse(manager.isConnected())
        assertEquals(0, createCount)

        val client = manager.getClient()
        assertTrue(manager.isConnected())
        assertEquals(1, createCount)

        val sameClient = manager.getClient()
        assertEquals(1, createCount)
        assertTrue(client === sameClient)
    }

    @Test
    fun onEnvironmentSwitched_disconnects_when_no_keepassxc_secrets() = runTest {
        val fake = FakeClient()
        val manager = DefaultKeePassXcClientManager { fake }

        manager.getClient()
        assertTrue(manager.isConnected())
        assertFalse(fake.disconnected)

        // Switch to environment with keepassxc secrets: remains connected
        manager.onEnvironmentSwitched(hasKeePassXcSecrets = true)
        assertTrue(manager.isConnected())
        assertFalse(fake.disconnected)

        // Switch to environment without keepassxc secrets: disconnects
        manager.onEnvironmentSwitched(hasKeePassXcSecrets = false)
        assertFalse(manager.isConnected())
        assertTrue(fake.disconnected)
    }

    @Test
    fun shutdown_disconnects_active_client() = runTest {
        val fake = FakeClient()
        val manager = DefaultKeePassXcClientManager { fake }

        manager.getClient()
        assertTrue(manager.isConnected())

        manager.shutdown()
        assertFalse(manager.isConnected())
        assertTrue(fake.disconnected)
    }
}
