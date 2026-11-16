package io.github.makeforge.clapi.core.network.keeper

import kotlinx.serialization.Serializable

@Serializable
data class KeeperClientConfig(
    val hostname: String,
    val clientId: String,
    val privateKey: String,
    val appKey: String? = null,
    val clientKey: String? = null,
    val appOwnerPublicKey: String? = null,
    val serverPublicKeyId: String? = null,
    val serverPublicKey: String? = null,
) {
    val isBound: Boolean get() = !appKey.isNullOrBlank()
}
