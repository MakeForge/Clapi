# Contract: KeePassXC Client & Lifecycle API

## Overview
This contract defines the programmatic abstraction between Clapi's secret resolution engine/UI and the local KeePassXC application instance via the native messaging proxy.

## 1. Client Interface Contract

```kotlin
interface KeePassXcClient {
    /**
     * Verifies connectivity with KeePassXC and database status.
     */
    suspend fun checkStatus(): KeePassXcStatus

    /**
     * Queries KeePassXC entries for the given URL.
     * Returns matching entries filtered in-memory by customFilters.
     */
    suspend fun queryLogins(
        url: String,
        customFilters: Map<String, String> = emptyMap(),
    ): List<KeePassXcEntry>

    /**
     * Counts matching entries for UI validation without exposing secret payloads.
     */
    suspend fun countMatchingLogins(
        url: String,
        customFilters: Map<String, String> = emptyMap(),
    ): Int

    /**
     * Cleanly closes the proxy connection and releases OS socket/named pipe handles.
     */
    suspend fun disconnect()
}
```

## 2. Status Contract

```kotlin
sealed interface KeePassXcStatus {
    data object Connected : KeePassXcStatus
    data object DatabaseLocked : KeePassXcStatus
    data class NotAssociated(val promptRequired: Boolean) : KeePassXcStatus
    data class Unavailable(val reason: String) : KeePassXcStatus
}
```

## 3. Client Lifecycle Manager Contract

```kotlin
interface KeePassXcClientManager {
    /**
     * Returns an active client instance. Connects lazily if not already connected.
     */
    suspend fun getClient(): KeePassXcClient

    /**
     * Called when active environment changes or KeePassXC is no longer in use.
     * Ensures all background sockets/proxies are disconnected.
     */
    suspend fun onEnvironmentSwitched(hasKeePassXcSecrets: Boolean)

    /**
     * Explicitly tears down any open connections.
     */
    suspend fun shutdown()
}
```

## 4. Error Handling Guarantees
- If KeePassXC is not running or proxy is disabled: throws or returns `Unavailable("Cannot connect to KeePassXC proxy...")`.
- If database is locked: returns `DatabaseLocked`.
- If user rejects association: returns `NotAssociated`.
- All methods must be non-blocking (suspendable / coroutine-friendly).
- Secret values must never be written to stdout, stderr, or unencrypted log files.
