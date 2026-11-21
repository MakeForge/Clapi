package io.github.crustack.clapi.core.network.keeper

data class KeeperRecordFieldSummary(
    val type: String,
    val label: String? = null,
    val values: List<String> = emptyList(),
    val rawJson: String? = null,
)

data class KeeperRecordSummary(
    val recordUid: String,
    val title: String,
    val type: String,
    val password: String? = null,
    val fields: List<KeeperRecordFieldSummary> = emptyList(),
    val customFields: List<KeeperRecordFieldSummary> = emptyList(),
)

sealed interface KeeperConnectionStatusState {
    data object Connected : KeeperConnectionStatusState
    data class Unavailable(val reason: String) : KeeperConnectionStatusState
}

interface KeeperClient {
    suspend fun checkStatus(): KeeperConnectionStatusState
    suspend fun testConnection(): Boolean
    suspend fun getRecords(recordsFilter: List<String> = emptyList()): List<KeeperRecordSummary>
    suspend fun countMatchingRecords(
        recordTitle: String,
        customFilters: Map<String, String> = emptyMap(),
    ): Int
    suspend fun disconnect()
}

expect fun createKeeperClient(configJson: String): KeeperClient

expect suspend fun initializeAndBindKeeperStorage(oneTimeToken: String): String
