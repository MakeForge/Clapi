package io.github.crustack.clapi.core.network.keeper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeeperClientManagerTest {

    private class FakeClient : KeeperClient {
        var disconnected = false
        override suspend fun checkStatus(): KeeperConnectionStatusState = KeeperConnectionStatusState.Connected
        override suspend fun testConnection(): Boolean = true
        override suspend fun getRecords(recordsFilter: List<String>): List<KeeperRecordSummary> = emptyList()
        override suspend fun countMatchingRecords(recordTitle: String, customFilters: Map<String, String>): Int = 0
        override suspend fun disconnect() {
            disconnected = true
        }
    }

    @Test
    fun getClient_lazily_instantiates_client() = runTest {
        var createCount = 0
        val manager = DefaultKeeperClientManager {
            createCount++
            FakeClient()
        }

        assertFalse(manager.isConnected())
        assertEquals(0, createCount)

        val client = manager.getClient("config-1")
        assertTrue(manager.isConnected())
        assertEquals(1, createCount)

        val sameClient = manager.getClient("config-1")
        assertEquals(1, createCount)
        assertTrue(client === sameClient)
    }

    @Test
    fun onEnvironmentSwitched_disconnects_when_no_keeper_secrets() = runTest {
        val fake = FakeClient()
        val manager = DefaultKeeperClientManager { fake }

        manager.getClient("config-1")
        assertTrue(manager.isConnected())
        assertFalse(fake.disconnected)

        // Switch to environment with keeper secrets: remains connected
        manager.onEnvironmentSwitched(hasKeeperSecrets = true)
        assertTrue(manager.isConnected())
        assertFalse(fake.disconnected)

        // Switch to environment without keeper secrets: disconnects
        manager.onEnvironmentSwitched(hasKeeperSecrets = false)
        assertFalse(manager.isConnected())
        assertTrue(fake.disconnected)
    }

    @Test
    fun shutdown_disconnects_active_client() = runTest {
        val fake = FakeClient()
        val manager = DefaultKeeperClientManager { fake }

        manager.getClient("config-1")
        assertTrue(manager.isConnected())

        manager.shutdown()
        assertFalse(manager.isConnected())
        assertTrue(fake.disconnected)
    }
}
