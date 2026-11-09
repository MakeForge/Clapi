package io.github.crustack.clapi.core.network.keepassxc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.purejava.KeepassProxyAccess
import java.util.prefs.Preferences

interface KeePassXcAssociationStorage {
    fun getAssociateId(): String?
    fun getIdKeyPairPublicKey(): String?
    fun saveAssociation(associateId: String, idKeyPairPublicKey: String)
}

class PreferencesKeePassXcAssociationStorage : KeePassXcAssociationStorage {
    private val prefs = Preferences.userRoot().node("io/github/crustack/clapi/keepassxc")
    override fun getAssociateId(): String? = prefs.get("associateId", null)
    override fun getIdKeyPairPublicKey(): String? = prefs.get("idKeyPairPublicKey", null)
    override fun saveAssociation(associateId: String, idKeyPairPublicKey: String) {
        prefs.put("associateId", associateId)
        prefs.put("idKeyPairPublicKey", idKeyPairPublicKey)
        prefs.flush()
    }
}

class PureJavaKeePassXcClient(
    private val associationStorage: KeePassXcAssociationStorage = PreferencesKeePassXcAssociationStorage(),
    private val proxyAccessProvider: () -> KeepassProxyAccess = { KeepassProxyAccess() },
) : KeePassXcClient {

    private var proxyAccess: KeepassProxyAccess? = null
    private var connected: Boolean = false

    @Synchronized
    private fun getOrConnectProxy(): KeepassProxyAccess {
        val current = proxyAccess
        if (current != null && connected) {
            return current
        }
        val kpa = proxyAccessProvider()
        val connectedOk = kpa.connect()
        if (!connectedOk) {
            throw IllegalStateException("Cannot connect to KeePassXC proxy. Please ensure KeePassXC is running and browser integration is enabled.")
        }
        proxyAccess = kpa
        connected = true
        return kpa
    }

    override suspend fun checkStatus(): KeePassXcStatus = withContext(Dispatchers.IO) {
        try {
            val kpa = getOrConnectProxy()
            if (kpa.isDatabaseLocked) {
                return@withContext KeePassXcStatus.DatabaseLocked
            }
            val id = associationStorage.getAssociateId()
            val key = associationStorage.getIdKeyPairPublicKey()
            if (id.isNullOrBlank() || key.isNullOrBlank()) {
                return@withContext KeePassXcStatus.NotAssociated(promptRequired = true)
            }
            if (!kpa.testAssociate(id, key)) {
                return@withContext KeePassXcStatus.NotAssociated(promptRequired = true)
            }
            KeePassXcStatus.Connected
        } catch (e: Exception) {
            KeePassXcStatus.Unavailable(e.message ?: "Unknown error connecting to KeePassXC")
        }
    }

    private fun ensureAssociated(kpa: KeepassProxyAccess): Pair<String, String> {
        val storedId = associationStorage.getAssociateId()
        val storedKey = associationStorage.getIdKeyPairPublicKey()

        if (!storedId.isNullOrBlank() && !storedKey.isNullOrBlank()) {
            if (kpa.testAssociate(storedId, storedKey)) {
                return storedId to storedKey
            }
        }

        // Trigger association
        val associated = kpa.associate()
        if (!associated) {
            throw IllegalStateException("KeePassXC association was denied or failed.")
        }
        val newId = kpa.associateId ?: throw IllegalStateException("Failed to obtain associate ID from KeePassXC.")
        val newKey = kpa.idKeyPairPublicKey ?: throw IllegalStateException("Failed to obtain ID key pair from KeePassXC.")

        associationStorage.saveAssociation(newId, newKey)
        return newId to newKey
    }

    override suspend fun queryLogins(
        url: String,
        customFilters: Map<String, String>,
    ): List<KeePassXcEntry> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext emptyList()
        val kpa = getOrConnectProxy()
        if (kpa.isDatabaseLocked) {
            throw IllegalStateException("KeePassXC database is locked. Please unlock your database.")
        }

        val (id, key) = ensureAssociated(kpa)
        val idKeyMap = listOf(mapOf("id" to id, "key" to key))

        val response = kpa.getLogins(url, "", true, idKeyMap)
            ?: return@withContext emptyList()

        KeePassXcEntryFilter.parseAndFilter(response, customFilters)
    }

    override suspend fun countMatchingLogins(
        url: String,
        customFilters: Map<String, String>,
    ): Int {
        return queryLogins(url, customFilters).size
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        synchronized(this@PureJavaKeePassXcClient) {
            try {
                proxyAccess = null
                connected = false
            } catch (_: Exception) {
            }
        }
    }
}

actual fun createKeePassXcClient(): KeePassXcClient = PureJavaKeePassXcClient()

