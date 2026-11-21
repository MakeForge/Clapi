package io.github.makeforge.clapi.core.network.keeper

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface KeeperClientManager {
    suspend fun getClient(configJson: String): KeeperClient
    suspend fun onEnvironmentSwitched(hasKeeperSecrets: Boolean)
    suspend fun shutdown()
    fun isConnected(): Boolean
}

class DefaultKeeperClientManager(
    private val clientFactory: (String) -> KeeperClient = { createKeeperClient(it) },
) : KeeperClientManager {

    private val mutex = Mutex()
    private var activeClient: KeeperClient? = null
    private var activeConfig: String? = null

    override suspend fun getClient(configJson: String): KeeperClient = mutex.withLock {
        val existing = activeClient
        if (existing != null && activeConfig == configJson) {
            return@withLock existing
        }
        if (existing != null) {
            try {
                existing.disconnect()
            } catch (_: Exception) {
            }
        }
        val client = clientFactory(configJson)
        activeClient = client
        activeConfig = configJson
        client
    }

    override suspend fun onEnvironmentSwitched(hasKeeperSecrets: Boolean) {
        if (!hasKeeperSecrets) {
            shutdown()
        }
    }

    override suspend fun shutdown(): Unit = mutex.withLock {
        val client = activeClient
        activeClient = null
        activeConfig = null
        if (client != null) {
            try {
                client.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    override fun isConnected(): Boolean = activeClient != null

    companion object {
        val Shared: KeeperClientManager by lazy { DefaultKeeperClientManager() }
    }
}
