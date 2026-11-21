package io.github.crustack.clapi.core.network.keepassxc

data class KeePassXcEntry(
    val name: String,
    val login: String,
    val password: String?,
    val uuid: String,
    val group: String? = null,
    val stringFields: Map<String, String> = emptyMap(),
    val totp: String? = null,
)

sealed interface KeePassXcStatus {
    data object Connected : KeePassXcStatus
    data object DatabaseLocked : KeePassXcStatus
    data class NotAssociated(val promptRequired: Boolean) : KeePassXcStatus
    data class Unavailable(val reason: String) : KeePassXcStatus
}

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

    /**
     * Retrieves the current stored association identifier, if any.
     */
    suspend fun getAssociateId(): String? = null

    /**
     * Initiates manual association with KeePassXC.
     */
    suspend fun associate(): String = throw UnsupportedOperationException("Association not supported")

    /**
     * Clears existing association credentials and disconnects.
     */
    suspend fun unassociate() {}

    /**
     * Returns the detected KeePassXC proxy socket or named pipe path on the host system.
     */
    fun getProxyPath(): String = ""
}

expect fun createKeePassXcClient(): KeePassXcClient

