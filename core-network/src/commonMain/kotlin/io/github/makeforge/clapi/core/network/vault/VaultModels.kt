package io.github.makeforge.clapi.core.network.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class VaultKvV2Response(
    val data: VaultKvV2Data,
)

@Serializable
data class VaultKvV2Data(
    val data: JsonObject,
    val metadata: JsonObject? = null,
)
