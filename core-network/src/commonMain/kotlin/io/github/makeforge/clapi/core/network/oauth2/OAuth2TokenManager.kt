package io.github.makeforge.clapi.core.network.oauth2

import io.github.makeforge.clapi.core.model.AuthConfig
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.network.dynamicvar.OAuth2ResolvedConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OAuth2TokenRefreshResult(
    val accessToken: String,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val refreshed: Boolean,
    val updatedAuthConfig: AuthConfig? = null,
    val tokenState: OAuth2TokenState = OAuth2TokenState(
        accessToken = accessToken,
        refreshToken = refreshToken,
        idToken = idToken,
        expiresAtEpochMs = expiresAtEpochMillis,
    ),
)

/**
 * Single authoritative OAuth2 token lifecycle manager in `:core-network`.
 *
 * Coordinates token expiry checks, refresh-token grants, and fallback `client_credentials` /
 * `password` grant acquisitions behind an internal [Mutex] so concurrent or back-to-back
 * callers (e.g. `{{_auth.oauth2.accessToken}}` dynamic variable + `AuthType.OAUTH2` request auth)
 * never trigger duplicate token refresh HTTP requests.
 */
class OAuth2TokenManager(
    private val defaultTokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
    private val clock: () -> Long = ::currentEpochMillis,
) {
    private val mutex = Mutex()
    private val recentTokenCache = mutableMapOf<String, OAuth2TokenState>()

    private fun cacheKey(
        flow: OAuth2Flow,
        tokenUrl: String,
        clientId: String,
        username: String,
        scope: String?,
    ): String = "${flow.value}|$tokenUrl|$clientId|$username|${scope.orEmpty()}"

    suspend fun ensureValidToken(
        config: OAuth2ResolvedConfig,
        currentState: OAuth2TokenState,
        variableLayers: List<Map<String, String>> = emptyList(),
        tokenClient: OAuth2TokenClient = defaultTokenClient,
        expiryBufferMillis: Long = 30_000L,
        log: ((message: String, isError: Boolean) -> Unit)? = null,
        onTokenUpdated: ((OAuth2TokenState) -> Unit)? = null,
    ): Result<OAuth2TokenRefreshResult> = mutex.withLock {
        val now = clock()
        if (currentState.isValid(bufferMs = expiryBufferMillis, nowEpochMs = now)) {
            return@withLock Result.success(
                OAuth2TokenRefreshResult(
                    accessToken = currentState.accessToken,
                    refreshToken = currentState.refreshToken,
                    idToken = currentState.idToken,
                    expiresAtEpochMillis = currentState.expiresAtEpochMs,
                    refreshed = false,
                    tokenState = currentState,
                )
            )
        }

        val rawTokenUrl = config.tokenUrl
        val rawRefreshTokenUrl = config.refreshTokenUrl.ifBlank { rawTokenUrl }
        val tokenUrl = VariableResolver.resolve(rawTokenUrl, variableLayers)
        val refreshTokenUrl = VariableResolver.resolve(rawRefreshTokenUrl, variableLayers).ifBlank { tokenUrl }
        val clientId = VariableResolver.resolve(config.clientId, variableLayers)
        val clientSecret = VariableResolver.resolve(config.clientSecret, variableLayers)
        val username = VariableResolver.resolve(config.username, variableLayers)
        val password = VariableResolver.resolve(config.password, variableLayers)
        val scope = VariableResolver.resolve(config.scope.orEmpty(), variableLayers).ifBlank { null }
        val placement = config.credentialsPlacement
        val flow = config.flow

        if (tokenUrl.isBlank()) {
            val err = OAuth2Exception("missing_token_url", "OAuth2 Access Token URL is missing")
            log?.invoke(err.message ?: "OAuth2 Access Token URL is missing", true)
            return@withLock Result.failure(err)
        }

        val key = cacheKey(flow, tokenUrl, clientId, username, scope)
        val cached = recentTokenCache[key]
        if (cached != null &&
            cached.accessToken != currentState.accessToken &&
            cached.isValid(bufferMs = expiryBufferMillis, nowEpochMs = now)
        ) {
            onTokenUpdated?.invoke(cached)
            return@withLock Result.success(
                OAuth2TokenRefreshResult(
                    accessToken = cached.accessToken,
                    refreshToken = cached.refreshToken,
                    idToken = cached.idToken,
                    expiresAtEpochMillis = cached.expiresAtEpochMs,
                    refreshed = false,
                    tokenState = cached,
                )
            )
        }

        var acquiredResponse: OAuth2TokenResponse? = null
        val currentRefreshToken = currentState.refreshToken

        if (!currentRefreshToken.isNullOrBlank()) {
            val refreshResult = tokenClient.refreshToken(
                tokenUrl = refreshTokenUrl,
                refreshToken = currentRefreshToken,
                clientId = clientId,
                clientSecret = clientSecret,
                placement = placement,
                scope = scope,
            )
            refreshResult.fold(
                onSuccess = { resp ->
                    acquiredResponse = resp
                    log?.invoke("OAuth2 access token automatically refreshed", false)
                },
                onFailure = { err ->
                    log?.invoke(
                        "OAuth2 token refresh failed: ${err.message}. Falling back to primary credentials grant...",
                        false,
                    )
                },
            )
        }

        if (acquiredResponse == null) {
            val primaryResult = when (flow) {
                OAuth2Flow.CLIENT_CREDENTIALS -> {
                    tokenClient.fetchClientCredentialsToken(
                        tokenUrl = tokenUrl,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        placement = placement,
                        scope = scope,
                    )
                }
                OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS -> {
                    tokenClient.fetchPasswordToken(
                        tokenUrl = tokenUrl,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        placement = placement,
                        username = username,
                        password = password,
                        scope = scope,
                    )
                }
                else -> {
                    Result.failure(
                        OAuth2Exception(
                            "unsupported_auto_flow",
                            "Cannot automatically fetch token for flow ${flow.value}. Please authenticate manually.",
                        )
                    )
                }
            }

            primaryResult.fold(
                onSuccess = { resp ->
                    acquiredResponse = resp
                    log?.invoke("OAuth2 access token automatically acquired", false)
                },
                onFailure = { err ->
                    val msg = "OAuth2 token acquisition failed: ${err.message ?: "Unknown error"}"
                    log?.invoke(msg, true)
                    return@withLock Result.failure(OAuth2Exception("token_acquisition_failed", msg, cause = err))
                },
            )
        }

        val tokenResp = acquiredResponse!!
        val newNow = clock()
        val rawState = OAuth2TokenState.fromResponse(tokenResp, nowEpochMs = newNow)
        val mergedState = rawState.copy(
            refreshToken = tokenResp.refreshToken?.ifBlank { null } ?: currentState.refreshToken,
            idToken = tokenResp.idToken?.ifBlank { null } ?: currentState.idToken,
        )
        recentTokenCache[key] = mergedState
        onTokenUpdated?.invoke(mergedState)

        Result.success(
            OAuth2TokenRefreshResult(
                accessToken = mergedState.accessToken,
                refreshToken = mergedState.refreshToken,
                idToken = mergedState.idToken,
                expiresAtEpochMillis = mergedState.expiresAtEpochMs,
                refreshed = true,
                tokenState = mergedState,
            )
        )
    }

    suspend fun ensureValidToken(
        authConfig: AuthConfig,
        variableLayers: List<Map<String, String>> = emptyList(),
        tokenClient: OAuth2TokenClient = defaultTokenClient,
        expiryBufferMillis: Long = 30_000L,
        onTokenUpdated: ((AuthConfig) -> Unit)? = null,
    ): OAuth2TokenRefreshResult {
        val params = authConfig.params
        val flowStr = params["grantType"] ?: params["flow"] ?: OAuth2Flow.CLIENT_CREDENTIALS.value
        val flow = OAuth2Flow.entries.firstOrNull { it.value.equals(flowStr, ignoreCase = true) }
            ?: OAuth2Flow.CLIENT_CREDENTIALS
        val placementStr = params["credentialsPlacement"] ?: OAuth2CredentialsPlacement.BASIC_AUTH_HEADER.value
        val placement = OAuth2CredentialsPlacement.entries.firstOrNull {
            it.value.equals(placementStr, ignoreCase = true)
        } ?: OAuth2CredentialsPlacement.BASIC_AUTH_HEADER

        val config = OAuth2ResolvedConfig(
            flow = flow,
            tokenUrl = params["accessTokenUrl"] ?: params["tokenUrl"].orEmpty(),
            refreshTokenUrl = params["refreshTokenUrl"].orEmpty(),
            clientId = params["clientId"].orEmpty(),
            clientSecret = params["clientSecret"].orEmpty(),
            credentialsPlacement = placement,
            username = params["username"].orEmpty(),
            password = params["password"].orEmpty(),
            scope = params["scope"]?.ifBlank { null },
        )
        val currentState = OAuth2TokenState(
            accessToken = params["accessToken"].orEmpty(),
            refreshToken = params["refreshToken"]?.ifBlank { null },
            idToken = params["idToken"]?.ifBlank { null },
            expiresAtEpochMs = params["expiresAtEpochMs"]?.toLongOrNull(),
        )

        val result = ensureValidToken(
            config = config,
            currentState = currentState,
            variableLayers = variableLayers,
            tokenClient = tokenClient,
            expiryBufferMillis = expiryBufferMillis,
        ).getOrThrow()

        val updatedParams = params.toMutableMap().apply {
            put("accessToken", result.accessToken)
            result.refreshToken?.let { put("refreshToken", it) }
            result.idToken?.let { put("idToken", it) }
            result.expiresAtEpochMillis?.let { put("expiresAtEpochMs", it.toString()) }
        }
        val updatedAuth = authConfig.copy(params = updatedParams)
        if (result.refreshed) {
            onTokenUpdated?.invoke(updatedAuth)
        }
        return result.copy(updatedAuthConfig = updatedAuth)
    }

    companion object {
        val Shared = OAuth2TokenManager()
    }
}
