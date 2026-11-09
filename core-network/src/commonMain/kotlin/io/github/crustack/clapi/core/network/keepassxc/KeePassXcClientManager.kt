package io.github.crustack.clapi.core.network.keepassxc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface KeePassXcClientManager {
    suspend fun getClient(): KeePassXcClient
    suspend fun onEnvironmentSwitched(hasKeePassXcSecrets: Boolean)
    suspend fun shutdown()
    fun isConnected(): Boolean
}

class DefaultKeePassXcClientManager(
    private val clientFactory: () -> KeePassXcClient = { createKeePassXcClient() },
) : KeePassXcClientManager {

    private val mutex = Mutex()
    private var activeClient: KeePassXcClient? = null

    override suspend fun getClient(): KeePassXcClient = mutex.withLock {
        val existing = activeClient
        if (existing != null) {
            return@withLock existing
        }
        val client = clientFactory()
        activeClient = client
        client
    }

    override suspend fun onEnvironmentSwitched(hasKeePassXcSecrets: Boolean) {
        if (!hasKeePassXcSecrets) {
            shutdown()
        }
    }

    override suspend fun shutdown(): Unit = mutex.withLock {
        val client = activeClient
        activeClient = null
        if (client != null) {
            try {
                client.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    override fun isConnected(): Boolean = activeClient != null

    companion object {
        val Shared: KeePassXcClientManager by lazy { DefaultKeePassXcClientManager() }
    }
}
