package io.github.crustack.clapi.core.network.keeper

import com.keepersecurity.secretsManager.core.InMemoryStorage
import com.keepersecurity.secretsManager.core.KEY_APP_KEY
import com.keepersecurity.secretsManager.core.KEY_CLIENT_ID
import com.keepersecurity.secretsManager.core.KEY_CLIENT_KEY
import com.keepersecurity.secretsManager.core.KEY_HOSTNAME
import com.keepersecurity.secretsManager.core.KEY_OWNER_PUBLIC_KEY
import com.keepersecurity.secretsManager.core.KEY_PRIVATE_KEY
import com.keepersecurity.secretsManager.core.SecretsManagerOptions
import com.keepersecurity.secretsManager.core.getSecrets
import com.keepersecurity.secretsManager.core.initializeStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object KeeperStorageInitializer {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun initializeAndBind(oneTimeToken: String): String = withContext(Dispatchers.IO) {
        val storage = InMemoryStorage()
        initializeStorage(storage, oneTimeToken.trim())
        val options = SecretsManagerOptions(storage)
        // First getSecrets call redeems the one-time token and binds the storage with Keeper servers
        getSecrets(options)

        val config = KeeperClientConfig(
            hostname = storage.getString(KEY_HOSTNAME).orEmpty(),
            clientId = storage.getString(KEY_CLIENT_ID).orEmpty(),
            privateKey = storage.getString(KEY_PRIVATE_KEY).orEmpty(),
            appKey = storage.getString(KEY_APP_KEY),
            clientKey = storage.getString(KEY_CLIENT_KEY),
            appOwnerPublicKey = storage.getString(KEY_OWNER_PUBLIC_KEY),
            serverPublicKeyId = storage.getString("serverPublicKeyId"),
            serverPublicKey = storage.getString("serverPublicKey"),
        )

        json.encodeToString(KeeperClientConfig.serializer(), config)
    }
}

actual suspend fun initializeAndBindKeeperStorage(oneTimeToken: String): String {
    return KeeperStorageInitializer.initializeAndBind(oneTimeToken)
}
