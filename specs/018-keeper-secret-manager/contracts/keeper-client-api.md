# Contract: Keeper Client API & Lifecycle Management

**Module**: `core-network` | **Target**: JVM Desktop

## 1. Client Interface

```kotlin
package io.github.crustack.clapi.core.network.keeper

import com.keepersecurity.secretsManager.core.KeeperSecrets

interface KeeperClient {
    /**
     * Retrieve secrets matching optional record UIDs from Keeper Security Cloud.
     */
    suspend fun getSecrets(recordsFilter: List<String> = emptyList()): KeeperSecrets

    /**
     * Quickly verifies if the client can reach Keeper and authenticate with the stored config.
     * Returns true if valid, false or throws on failure.
     */
    suspend fun testConnection(): Boolean

    /**
     * Disconnects and clears internal caches.
     */
    suspend fun disconnect()
}
```

## 2. Client Manager Interface

```kotlin
package io.github.crustack.clapi.core.network.keeper

interface KeeperClientManager {
    /**
     * Returns the active KeeperClient for the provided client configuration JSON string,
     * creating it lazily if not already present.
     */
    suspend fun getClient(configJson: String): KeeperClient

    /**
     * Lifecycle notification when active environment is switched.
     * If [hasKeeperSecrets] is false, any active KeeperClient is disconnected and destroyed.
     */
    suspend fun onEnvironmentSwitched(hasKeeperSecrets: Boolean)

    /**
     * Explicit shutdown of active client.
     */
    suspend fun shutdown()

    /**
     * Returns whether a KeeperClient is currently instantiated and connected.
     */
    fun isConnected(): Boolean
}
```

## 3. Storage Initializer

```kotlin
package io.github.crustack.clapi.core.network.keeper

object KeeperStorageInitializer {
    /**
     * Takes a one-time token (OTT), initializes an in-memory storage,
     * binds the storage by querying Keeper once to obtain the encrypted app key,
     * and returns the full serialized client configuration JSON.
     *
     * The one-time token is never stored.
     */
    suspend fun initializeAndBind(oneTimeToken: String): String
}
```
