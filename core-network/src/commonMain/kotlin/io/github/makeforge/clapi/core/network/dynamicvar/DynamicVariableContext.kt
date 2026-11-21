package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable as UserDynamicVariable
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenManager
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenState
import io.github.makeforge.clapi.core.scripting.DynamicVariableEngine
import io.github.makeforge.clapi.core.scripting.RequestContext

data class DynamicVariableContext(
    val effectiveAuthType: AuthType = AuthType.NONE,
    val oauth2Config: OAuth2ResolvedConfig? = null,
    val oauth2TokenState: OAuth2TokenState = OAuth2TokenState(accessToken = ""),
    val variableLayers: List<Map<String, String>> = emptyList(),
    val oauth2TokenClient: OAuth2TokenClient? = null,
    val oauth2TokenManager: OAuth2TokenManager = OAuth2TokenManager.Shared,
    val onOAuth2TokenUpdated: ((accessToken: String, refreshToken: String?, idToken: String?, expiresAtEpochMs: Long?) -> Unit)? = null,
    val log: ((message: String, isError: Boolean) -> Unit)? = null,
    val userDynamicVariables: List<UserDynamicVariable> = emptyList(),
    val requestContext: RequestContext? = null,
    val dynamicEngine: DynamicVariableEngine? = null,
)
