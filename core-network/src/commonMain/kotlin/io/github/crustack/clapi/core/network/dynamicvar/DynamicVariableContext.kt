package io.github.crustack.clapi.core.network.dynamicvar

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenState

data class DynamicVariableContext(
    val effectiveAuthType: AuthType,
    val oauth2Config: OAuth2ResolvedConfig? = null,
    val oauth2TokenState: OAuth2TokenState = OAuth2TokenState(accessToken = ""),
    val variableLayers: List<Map<String, String>> = emptyList(),
    val oauth2TokenClient: OAuth2TokenClient? = null,
    val onOAuth2TokenUpdated: ((accessToken: String, refreshToken: String?, idToken: String?, expiresAtEpochMs: Long?) -> Unit)? = null,
    val log: ((message: String, isError: Boolean) -> Unit)? = null,
)
