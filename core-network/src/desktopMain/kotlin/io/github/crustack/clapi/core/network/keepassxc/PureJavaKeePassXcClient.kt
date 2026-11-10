package io.github.crustack.clapi.core.network.keepassxc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.purejava.KeepassProxyAccess
import java.io.File
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.milliseconds

interface KeePassXcAssociationStorage {
    fun getAssociateId(): String?
    fun getIdKeyPairPublicKey(): String?
    fun saveAssociation(associateId: String, idKeyPairPublicKey: String)
    fun clearAssociation()
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
    override fun clearAssociation() {
        prefs.remove("associateId")
        prefs.remove("idKeyPairPublicKey")
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

    private suspend fun ensureAssociated(kpa: KeepassProxyAccess): Pair<String, String> =withContext(Dispatchers.IO) {
        val storedId = associationStorage.getAssociateId()
        val storedKey = associationStorage.getIdKeyPairPublicKey()

        if (!storedId.isNullOrBlank() && !storedKey.isNullOrBlank()) {
            if (kpa.testAssociate(storedId, storedKey)) {
                return@withContext storedId to storedKey
            }
        }

        // Trigger association
        kpa.associate()
        val associated = retryUntilTrue() {
            kpa.getDatabasehash().isPresent && kpa.testAssociate(kpa.associateId, kpa.idKeyPairPublicKey)
        }
        if (!associated) {
            throw IllegalStateException("KeePassXC association was denied or failed.")
        }
        val newId = kpa.associateId ?: throw IllegalStateException("Failed to obtain associate ID from KeePassXC.")
        val newKey = kpa.idKeyPairPublicKey ?: throw IllegalStateException("Failed to obtain ID key pair from KeePassXC.")

        associationStorage.saveAssociation(newId, newKey)
        return@withContext newId to newKey
    }

    suspend fun retryUntilTrue(
        maxAttempts: Int = 15,
        delayMillis: Long = 2_000L,
        block: suspend () -> Boolean
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (block()) return true
            if (attempt < maxAttempts - 1) {
                delay(delayMillis.milliseconds)
            }
        }
        return false
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
                proxyAccess?.closeConnection()
            } catch (_: Exception) {
            }
            try {
                proxyAccess?.shutdown()
            } catch (_: Exception) {
            }
            proxyAccess = null
            connected = false
        }
    }

    override suspend fun getAssociateId(): String? = withContext(Dispatchers.IO) {
        associationStorage.getAssociateId()
    }

    override suspend fun associate(): String = withContext(Dispatchers.IO) {
        val kpa = getOrConnectProxy()
        if (kpa.isDatabaseLocked) {
            throw IllegalStateException("KeePassXC database is locked. Please unlock your database first.")
        }
        kpa.associate()
        val associated = retryUntilTrue {
            kpa.getDatabasehash().isPresent && kpa.testAssociate(kpa.associateId, kpa.idKeyPairPublicKey)
        }
        if (!associated) {
            throw IllegalStateException("KeePassXC association was denied or timed out.")
        }
        val newId = kpa.associateId ?: throw IllegalStateException("Failed to obtain associate ID from KeePassXC.")
        val newKey = kpa.idKeyPairPublicKey ?: throw IllegalStateException("Failed to obtain ID key pair from KeePassXC.")

        associationStorage.saveAssociation(newId, newKey)
        newId
    }

    override suspend fun unassociate(): Unit = withContext(Dispatchers.IO) {
        associationStorage.clearAssociation()
        try {
            val userHome = System.getProperty("user.home") ?: ""
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            val datPath = when {
                osName.contains("win") -> (System.getenv("AppData") ?: "") + "/keepass-proxy-access/keepass-proxy-access.dat"
                osName.contains("mac") -> "$userHome/Library/Application Support/keepass-proxy-access/keepass-proxy-access.dat"
                else -> "$userHome/.config/keepass-proxy-access/keepass-proxy-access.dat"
            }
            val datFile = File(datPath)
            if (datFile.exists()) {
                datFile.delete()
            }
        } catch (_: Exception) {
        }
        disconnect()
    }

    override fun getProxyPath(): String {
        return resolveKeePassXcProxyPath()
    }
}

fun resolveKeePassXcProxyPath(): String {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val proxyName = "org.keepassxc.KeePassXC.BrowserServer"
    val flatpakPath = "/app/org.keepassxc.KeePassXC"
    return when {
        osName.contains("win") -> {
            val username = System.getenv("USERNAME") ?: ""
            "\\\\.\\pipe\\$proxyName" + if (username.isNotBlank()) "_$username" else ""
        }
        osName.contains("mac") -> {
            val tmpDir = System.getenv("TMPDIR") ?: "/tmp"
            "${tmpDir.trimEnd('/')}/$proxyName"
        }
        osName.contains("linux") -> {
            val type = try {
                org.keepassxc.KindOfKeePassXC.determineType().orElse(null)
            } catch (_: Throwable) {
                null
            }
            val socketDir = when (type) {
                org.keepassxc.KeePassXCType.Flatpak -> {
                    (System.getenv("XDG_RUNTIME_DIR") ?: "") + flatpakPath
                }
                org.keepassxc.KeePassXCType.Snap -> {
                    System.getProperty("user.home") + "/snap/keepassxc/common"
                }
                else -> {
                    val xdg = System.getenv("XDG_RUNTIME_DIR")
                    if (xdg == null) {
                        System.getenv("TMPDIR") ?: "/tmp"
                    } else {
                        val flatpakDir = File(xdg + flatpakPath)
                        if (flatpakDir.exists()) {
                            xdg + flatpakPath
                        } else {
                            xdg
                        }
                    }
                }
            }
            "${socketDir.trimEnd('/')}/$proxyName"
        }
        else -> "-"
    }
}

actual fun createKeePassXcClient(): KeePassXcClient = PureJavaKeePassXcClient()

