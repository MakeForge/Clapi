package io.github.crustack.clapi.core.network.keeper

import com.keepersecurity.secretsManager.core.Hosts
import com.keepersecurity.secretsManager.core.InMemoryStorage
import com.keepersecurity.secretsManager.core.KeeperRecord
import com.keepersecurity.secretsManager.core.KeeperRecordField
import com.keepersecurity.secretsManager.core.Login
import com.keepersecurity.secretsManager.core.Password
import com.keepersecurity.secretsManager.core.SecretsManagerOptions
import com.keepersecurity.secretsManager.core.Text
import com.keepersecurity.secretsManager.core.Url
import com.keepersecurity.secretsManager.core.getSecrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName

@OptIn(ExperimentalSerializationApi::class)
class KeeperSecretsManagerClient(
    private val configJson: String,
) : KeeperClient {

    private var cachedRecords: List<KeeperRecordSummary>? = null

    override suspend fun checkStatus(): KeeperConnectionStatusState = withContext(Dispatchers.IO) {
        try {
            val storage = InMemoryStorage(configJson)
            val options = SecretsManagerOptions(storage)
            getSecrets(options)
            KeeperConnectionStatusState.Connected
        } catch (e: Exception) {
            KeeperConnectionStatusState.Unavailable(e.message ?: "Failed to connect to Keeper")
        }
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val storage = InMemoryStorage(configJson)
            val options = SecretsManagerOptions(storage)
            getSecrets(options)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getRecords(recordsFilter: List<String>): List<KeeperRecordSummary> = withContext(Dispatchers.IO) {
        val existing = cachedRecords
        if (existing != null && recordsFilter.isEmpty()) {
            return@withContext existing
        }
        val storage = InMemoryStorage(configJson)
        val options = SecretsManagerOptions(storage)
        val secrets = getSecrets(options, recordsFilter)
        val summaries = secrets.records.map { it.toSummary() }
        if (recordsFilter.isEmpty()) {
            cachedRecords = summaries
        }
        summaries
    }

    override suspend fun countMatchingRecords(
        recordTitle: String,
        customFilters: Map<String, String>,
    ): Int = withContext(Dispatchers.IO) {
        val records = getRecords()
        records.count { record ->
            KeeperRecordFilter.matchesFilter(record, recordTitle, customFilters)
        }
    }

    override suspend fun disconnect() {
        cachedRecords = null
    }

    private fun KeeperRecord.toSummary(): KeeperRecordSummary {
        val fieldSummaries = data.fields.map { field ->
            val type = when (field) {
                is Login -> "login"
                is Password -> "password"
                is Url -> "url"
                is Text -> "text"
                is Hosts -> "host"
                else -> field.javaClass.simpleName.lowercase()
            }
            val strValues = extractFieldValues(field)
            KeeperRecordFieldSummary(
                type = type,
                label = field.label,
                values = strValues,
            )
        }
        val customSummaries = (data.custom ?: emptyList<KeeperRecordField>()).map { field ->
            val type = when (field) {
                is Login -> "login"
                is Password -> "password"
                is Url -> "url"
                is Text -> "text"
                is Hosts -> "host"
                else -> field.javaClass.simpleName.lowercase()
            }
            val strValues = extractFieldValues(field)
            KeeperRecordFieldSummary(
                type = type,
                label = field.label,
                values = strValues,
            )
        }
        return KeeperRecordSummary(
            recordUid = recordUid,
            title = data.title,
            type = data.type,
            password = getPassword(),
            fields = fieldSummaries,
            customFields = customSummaries,
        )
    }

    private fun extractFieldValues(field: KeeperRecordField): List<String> {
        return when (field) {
            is Login -> field.value
            is Password -> field.value
            is Url -> field.value
            is Text -> field.value
            is Hosts -> field.value.map { host ->
                val hostName = host.hostName ?: ""
                val port = host.port ?: ""
                """{"hostName":"$hostName","port":"$port"}"""
            }
            else -> emptyList()
        }
    }
}

actual fun createKeeperClient(configJson: String): KeeperClient {
    return KeeperSecretsManagerClient(configJson)
}
