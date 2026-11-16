package io.github.crustack.clapi.core.network.dynamicvar

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.oauth2.OAuth2Exception
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenResponse
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenState

class OAuth2DynamicVariableProvider : DynamicVariableProvider {

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

        val variableLayers = context.variableLayers
        val rawTokenUrl = config.tokenUrl
        val rawRefreshTokenUrl = config.refreshTokenUrl.ifBlank { rawTokenUrl }
        val rawClientId = config.clientId
        val rawClientSecret = config.clientSecret
        val rawUsername = config.username
        val rawPassword = config.password
        val rawScope = config.scope.orEmpty()
        val credentialsPlacement = config.credentialsPlacement
        val flow = config.flow

        val tokenUrl = VariableResolver.resolve(rawTokenUrl, variableLayers)
        val refreshTokenUrl = VariableResolver.resolve(rawRefreshTokenUrl, variableLayers).ifBlank { tokenUrl }
        val clientId = VariableResolver.resolve(rawClientId, variableLayers)
        val clientSecret = VariableResolver.resolve(rawClientSecret, variableLayers)
        val username = VariableResolver.resolve(rawUsername, variableLayers)
        val password = VariableResolver.resolve(rawPassword, variableLayers)
        val scope = VariableResolver.resolve(rawScope, variableLayers).ifBlank { null }

        if (tokenUrl.isBlank()) {
            throw DynamicVariableResolutionException("OAuth2 Access Token URL is missing.")
        }

        var acquiredToken: OAuth2TokenResponse? = null
        val currentRefreshToken = context.oauth2TokenState.refreshToken

        if (!currentRefreshToken.isNullOrBlank()) {
            val refreshResult = tokenClient.refreshToken(
                tokenUrl = refreshTokenUrl,
                refreshToken = currentRefreshToken,
                clientId = clientId,
                clientSecret = clientSecret,
                placement = credentialsPlacement,
                scope = scope,
            )
            refreshResult.fold(
                onSuccess = { resp ->
                    acquiredToken = resp
                    context.log?.invoke("OAuth2 access token automatically refreshed for dynamic variable", false)
                },
                onFailure = { err ->
                    context.log?.invoke(
                        "OAuth2 token refresh failed: ${err.message}. Falling back to primary credentials grant...",
                        false,
                    )
                }
            )
        }

        if (acquiredToken == null) {
            val primaryResult = when (flow) {
                OAuth2Flow.CLIENT_CREDENTIALS -> {
                    tokenClient.fetchClientCredentialsToken(
                        tokenUrl = tokenUrl,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        placement = credentialsPlacement,
                        scope = scope,
                    )
                }
                OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS -> {
                    tokenClient.fetchPasswordToken(
                        tokenUrl = tokenUrl,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        placement = credentialsPlacement,
                        username = username,
                        password = password,
                        scope = scope,
                    )
                }
                else -> {
                    Result.failure(OAuth2Exception("Cannot automatically fetch token for flow ${flow.value}. Please authenticate manually."))
                }
            }

            primaryResult.fold(
                onSuccess = { resp ->
                    acquiredToken = resp
                    context.log?.invoke("OAuth2 access token automatically acquired for dynamic variable", false)
                },
                onFailure = { err ->
                    val msg = "OAuth2 token acquisition failed: ${err.message ?: "Unknown error"}"
                    context.log?.invoke(msg, true)
                    throw DynamicVariableResolutionException(msg, err)
                }
            )
        }

        val tokenResp = acquiredToken ?: throw DynamicVariableResolutionException("Failed to acquire OAuth2 access token.")
        val newState = OAuth2TokenState.fromResponse(tokenResp)

        context.onOAuth2TokenUpdated?.invoke(
            tokenResp.accessToken,
            tokenResp.refreshToken,
            tokenResp.idToken,
            newState.expiresAtEpochMs,
        )

        return mapOf(ACCESS_TOKEN_VARIABLE to tokenResp.accessToken)
    }

    companion object {
        const val ACCESS_TOKEN_VARIABLE = "_auth.oauth2.accessToken"
    }
}
