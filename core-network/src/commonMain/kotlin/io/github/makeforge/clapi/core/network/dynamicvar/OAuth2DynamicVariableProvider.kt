package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.network.oauth2.OAuth2TokenManager

class OAuth2DynamicVariableProvider(
    private val defaultTokenManager: OAuth2TokenManager = OAuth2TokenManager.Shared,
) : DynamicVariableProvider {

    override val name: String = "OAuth2TokenProvider"

    override fun supports(token: String): Boolean =
        token == ACCESS_TOKEN_VARIABLE

    override suspend fun resolve(
        tokens: Set<String>,
        context: DynamicVariableContext,
    ): Map<String, String> {
        if (!tokens.contains(ACCESS_TOKEN_VARIABLE)) {
            return emptyMap()
        }

        if (context.effectiveAuthType != AuthType.OAUTH2) {
            throw DynamicVariableResolutionException(
                "Variable '{{$ACCESS_TOKEN_VARIABLE}}' requires OAuth2 authentication to be enabled on the request or parent collection."
            )
        }

        if (context.oauth2TokenState.isValid()) {
            return mapOf(ACCESS_TOKEN_VARIABLE to context.oauth2TokenState.accessToken)
        }

        val config = context.oauth2Config ?: throw DynamicVariableResolutionException(
            "OAuth2 configuration is missing for variable '{{$ACCESS_TOKEN_VARIABLE}}'."
        )

        val tokenClient = context.oauth2TokenClient ?: throw DynamicVariableResolutionException(
            "OAuth2TokenClient is not configured in execution context."
        )

        val manager = context.oauth2TokenManager
        val refreshResult = manager.ensureValidToken(
            config = config,
            currentState = context.oauth2TokenState,
            variableLayers = context.variableLayers,
            tokenClient = tokenClient,
            log = context.log,
            onTokenUpdated = { newState ->
                context.onOAuth2TokenUpdated?.invoke(
                    newState.accessToken,
                    newState.refreshToken,
                    newState.idToken,
                    newState.expiresAtEpochMs,
                )
            },
        ).getOrElse { err ->
            throw DynamicVariableResolutionException(err.message ?: "Failed to acquire OAuth2 access token.", err)
        }

        return mapOf(ACCESS_TOKEN_VARIABLE to refreshResult.accessToken)
    }

    companion object {
        const val ACCESS_TOKEN_VARIABLE = "_auth.oauth2.accessToken"
    }
}
