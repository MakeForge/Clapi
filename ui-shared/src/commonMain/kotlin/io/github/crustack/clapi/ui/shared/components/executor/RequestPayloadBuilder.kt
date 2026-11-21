package io.github.crustack.clapi.ui.shared.components.executor

import io.github.crustack.clapi.core.model.AuthConfig
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.FormDataEntry
import io.github.crustack.clapi.core.model.GraphQlBody
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.RequestBody
import io.github.crustack.clapi.core.model.RequestDefinition
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.network.VariableResolver
import io.github.crustack.clapi.core.network.dynamicvar.OAuth2ResolvedConfig
import io.github.crustack.clapi.core.network.oauth2.KtorOAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2Exception
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenClient
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenManager
import io.github.crustack.clapi.core.network.oauth2.OAuth2TokenState
import io.github.crustack.clapi.ui.shared.components.splitRequestUrl
import io.github.crustack.clapi.ui.shared.platform.currentTimeMillis
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.HeaderKind
import io.github.crustack.clapi.ui.shared.state.LogLevel
import io.github.crustack.clapi.ui.shared.state.RequestTabState

internal const val BINARY_ATTACHMENT_PREFIX = "clapi-binary:"


/**
 * Canonical tab-to-request projection shared by Send and every Copy As format.
 * Script mutations are supplied as overrides by Send; Copy uses the tab defaults.
 */
data class PreparedTabRequest(
    val id: String,
    val name: String,
    val method: HttpMethodType,
    val url: String,
    val fragment: String,
    val queryParams: List<KeyValueEntry>,
    val headers: List<KeyValueEntry>,
    val auth: AuthConfig,
    val body: RequestBody,
) {
    fun toRequestDefinition(): RequestDefinition = RequestDefinition(
        id = id,
        name = name,
        method = method,
        url = url,
        queryParams = queryParams.filter { it.enabled && it.key.isNotBlank() },
        headers = headers.filter { it.enabled && it.key.isNotBlank() },
        auth = auth,
        body = body,
        createdAtEpochMillis = currentTimeMillis(),
        updatedAtEpochMillis = currentTimeMillis(),
    )
}

fun prepareTabRequest(
    tab: RequestTabState,
    method: HttpMethodType = tab.method,
    url: String = tab.url.trim(),
    queryParams: List<KeyValueEntry> = enabledQueryParamsForSend(tab),
    headers: List<KeyValueEntry> = enabledHeadersForSend(tab),
    bodyContent: String = tab.bodyContent,
    state: AppState? = null,
): PreparedTabRequest {
    val parts = splitRequestUrl(url)
    return PreparedTabRequest(
        id = tab.id,
        name = tab.name,
        method = method,
        url = parts.base,
        fragment = parts.fragment,
        queryParams = queryParams,
        headers = headers,
        auth = buildAuthConfig(tab, state),
        body = buildRequestBody(tab, bodyContent),
    )
}

fun buildAuthConfig(tab: RequestTabState, state: AppState? = null): AuthConfig {
    val effectiveAuthType: AuthType
    val effectiveParams: Map<String, String>
    val effectivePlacement: String?
    if (tab.authType == AuthType.INHERIT && state != null) {
        val resolved = state.resolveInheritedAuth(tab)
        if (resolved != null && resolved.authType != AuthType.NONE && resolved.authType != AuthType.INHERIT) {
            effectiveAuthType = resolved.authType
            effectiveParams = when (resolved.authType) {
                AuthType.BASIC -> mapOf("username" to (resolved.username ?: ""), "password" to (resolved.password ?: ""))
                AuthType.BEARER, AuthType.JWT -> mapOf("token" to (resolved.token ?: ""))
                AuthType.API_KEY -> mapOf("key" to (resolved.apiKey ?: ""), "value" to (resolved.apiValue ?: ""))
                AuthType.OAUTH2 -> mapOf(
                    "accessToken" to tab.authOAuth2AccessToken,
                    "flow" to resolved.oauth2Flow.value,
                    "accessTokenUrl" to (resolved.oauth2AccessTokenUrl ?: ""),
                    "refreshTokenUrl" to (resolved.oauth2RefreshTokenUrl ?: ""),
                    "clientId" to (resolved.oauth2ClientId ?: ""),
                    "clientSecret" to (resolved.oauth2ClientSecret ?: ""),
                    "scope" to (resolved.oauth2Scope ?: ""),
                    "username" to (resolved.oauth2Username ?: ""),
                    "password" to (resolved.oauth2Password ?: ""),
                    "credentialsPlacement" to resolved.oauth2CredentialsPlacement.value,
                )
                else -> emptyMap()
            }
            effectivePlacement = if (resolved.authType == AuthType.API_KEY) {
                io.github.crustack.clapi.ui.shared.state.normalizeApiKeyPlacement(resolved.apiPlacement)
            } else null
        } else {
            effectiveAuthType = AuthType.NONE
            effectiveParams = emptyMap()
            effectivePlacement = null
        }
    } else {
        effectiveAuthType = tab.authType
        effectiveParams = when (tab.authType) {
            AuthType.BASIC   -> mapOf("username" to tab.authUsername, "password" to tab.authPassword)
            AuthType.BEARER  -> mapOf("token" to tab.authToken)
            AuthType.JWT     -> mapOf("token" to tab.authToken)
            AuthType.API_KEY -> mapOf("key" to tab.authApiKey, "value" to tab.authApiValue)
            AuthType.OAUTH2  -> mapOf(
                "accessToken" to tab.authOAuth2AccessToken,
                "flow" to tab.authOAuth2Flow.value,
                "accessTokenUrl" to tab.authOAuth2AccessTokenUrl,
                "refreshTokenUrl" to tab.authOAuth2RefreshTokenUrl,
                "clientId" to tab.authOAuth2ClientId,
                "clientSecret" to tab.authOAuth2ClientSecret,
                "scope" to tab.authOAuth2Scope,
                "username" to tab.authOAuth2Username,
                "password" to tab.authOAuth2Password,
                "credentialsPlacement" to tab.authOAuth2CredentialsPlacement.value,
            )
            else             -> emptyMap()
        }
        effectivePlacement = if (tab.authType == AuthType.API_KEY) {
            io.github.crustack.clapi.ui.shared.state.normalizeApiKeyPlacement(tab.authApiPlacement)
        } else null
    }
    return AuthConfig(
        type = effectiveAuthType,
        params = effectiveParams,
        placement = effectivePlacement,
    )
}

/**
 * Validates the OAuth2 access token prior to request execution.
 * If expired or missing, attempts to refresh using the refresh token, or
 * falls back to executing the primary credentials grant.
 *
 * @return true if the token is valid or successfully acquired, false if acquisition failed.
 */
suspend fun ensureValidOAuth2Token(
    state: AppState,
    tab: RequestTabState,
    variableLayers: List<Map<String, String>>,
    tokenClient: OAuth2TokenClient = KtorOAuth2TokenClient(),
    tokenManager: OAuth2TokenManager = OAuth2TokenManager.Shared,
): Boolean {
    val effectiveAuthType = if (tab.authType == AuthType.INHERIT) {
        state.resolveInheritedAuth(tab)?.authType ?: AuthType.NONE
    } else {
        tab.authType
    }

    if (effectiveAuthType != AuthType.OAUTH2) {
        return true
    }

    val inherited = if (tab.authType == AuthType.INHERIT) state.resolveInheritedAuth(tab) else null

    val flow = inherited?.oauth2Flow ?: tab.authOAuth2Flow
    val rawTokenUrl = inherited?.oauth2AccessTokenUrl ?: tab.authOAuth2AccessTokenUrl
    val rawRefreshTokenUrl = (inherited?.oauth2RefreshTokenUrl ?: tab.authOAuth2RefreshTokenUrl).ifBlank { rawTokenUrl }
    val rawClientId = inherited?.oauth2ClientId ?: tab.authOAuth2ClientId
    val rawClientSecret = inherited?.oauth2ClientSecret ?: tab.authOAuth2ClientSecret
    val credentialsPlacement = inherited?.oauth2CredentialsPlacement ?: tab.authOAuth2CredentialsPlacement
    val rawUsername = inherited?.oauth2Username ?: tab.authOAuth2Username
    val rawPassword = inherited?.oauth2Password ?: tab.authOAuth2Password
    val rawScope = inherited?.oauth2Scope ?: tab.authOAuth2Scope

    val currentTokenState = OAuth2TokenState(
        accessToken = tab.authOAuth2AccessToken,
        refreshToken = tab.authOAuth2RefreshToken.ifBlank { null },
        idToken = tab.authOAuth2IdToken.ifBlank { null },
        expiresAtEpochMs = tab.authOAuth2ExpiresAtEpochMs,
    )

    if (currentTokenState.isValid()) {
        return true
    }

    val config = OAuth2ResolvedConfig(
        flow = flow,
        tokenUrl = rawTokenUrl,
        refreshTokenUrl = rawRefreshTokenUrl,
        clientId = rawClientId,
        clientSecret = rawClientSecret,
        credentialsPlacement = credentialsPlacement,
        username = rawUsername,
        password = rawPassword,
        scope = rawScope.ifBlank { null },
    )

    state.log("OAuth2 access token is missing or expired for '${tab.name}'. Fetching new token...")

    val result = tokenManager.ensureValidToken(
        config = config,
        currentState = currentTokenState,
        variableLayers = variableLayers,
        tokenClient = tokenClient,
        log = { msg, isError ->
            state.log(msg, if (isError) LogLevel.ERROR else LogLevel.INFO)
        },
        onTokenUpdated = { newState ->
            tab.authOAuth2AccessToken = newState.accessToken
            if (!newState.refreshToken.isNullOrBlank()) {
                tab.authOAuth2RefreshToken = newState.refreshToken.orEmpty()
            }
            if (!newState.idToken.isNullOrBlank()) {
                tab.authOAuth2IdToken = newState.idToken.orEmpty()
            }
            if (newState.expiresAtEpochMs != null) {
                tab.authOAuth2ExpiresAtEpochMs = newState.expiresAtEpochMs
            }
            tab.authOAuth2FetchError = null
        },
    )

    return result.fold(
        onSuccess = { true },
        onFailure = { err ->
            val msg = err.message ?: "OAuth2 token acquisition failed"
            tab.authOAuth2FetchError = msg
            tab.lastError = msg
            false
        },
    )
}

/**
 * Builds a [RequestBody] from [tab] state, using structured form rows when available.
 * Falls back to parsing [effectiveBodyContent] for older/simpler body types.
 */
fun buildRequestBody(tab: RequestTabState, effectiveBodyContent: String): RequestBody {
    return when (tab.bodyType) {
        BodyType.FORM_DATA -> {
            val rows = tab.formRows.filter { it.enabled }
            val formDataEntries = rows.map {
                FormDataEntry(it.key, it.type, it.value, it.description, it.enabled, it.bytesBase64)
            }
            val formEntries = rows.map { KeyValueEntry(it.key, it.value) }
            RequestBody(
                type = BodyType.FORM_DATA,
                formEntries = formEntries,
                formDataEntries = formDataEntries,
            )
        }
        BodyType.X_WWW_FORM_URLENCODED -> {
            val rows = tab.urlencodedRows.filter { it.enabled }
            val formEntries = if (rows.isNotEmpty()) {
                rows.map { KeyValueEntry(it.key, it.value) }
            } else {
                // Backward compat: parse from raw bodyContent
                effectiveBodyContent.split("&", "\n").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) KeyValueEntry(part.substring(0, idx).trim(), part.substring(idx + 1).trim())
                    else null
                }
            }
            RequestBody(type = BodyType.X_WWW_FORM_URLENCODED, formEntries = formEntries)
        }
        BodyType.GRAPHQL -> RequestBody(type = BodyType.GRAPHQL, graphQl = GraphQlBody(query = effectiveBodyContent))
        else -> buildRequestBody(tab.bodyType, effectiveBodyContent)
    }
}

/**
 * Builds a [RequestBody] for the given body type and raw content string.
 * For FORM_DATA and X_WWW_FORM_URLENCODED, parses [content] as key=value pairs
 * separated by & or newlines and populates [RequestBody.formEntries].
 */
fun buildRequestBody(bodyType: BodyType, content: String): RequestBody {
    val rawContent = content.ifBlank { null }
    val binaryAttachment = parseBinaryAttachment(content)
    val formEntries = when (bodyType) {
        BodyType.FORM_DATA, BodyType.X_WWW_FORM_URLENCODED -> {
            content.split("&", "\n").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) KeyValueEntry(
                    key = part.substring(0, idx).trim(),
                    value = part.substring(idx + 1).trim(),
                ) else null
            }
        }
        else -> emptyList()
    }
    return RequestBody(
        type = bodyType,
        content = if (bodyType == BodyType.GRAPHQL) null else rawContent,
        formEntries = formEntries,
        graphQl = if (bodyType == BodyType.GRAPHQL) GraphQlBody(query = content) else null,
        binaryName = binaryAttachment?.first,
        binaryBytesBase64 = binaryAttachment?.second,
    )
}

private fun parseBinaryAttachment(content: String): Pair<String, String>? {
    if (!content.startsWith(BINARY_ATTACHMENT_PREFIX)) return null
    val separator = content.indexOf('\n')
    if (separator <= BINARY_ATTACHMENT_PREFIX.length || separator >= content.length - 1) return null
    val fileName = content.substring(BINARY_ATTACHMENT_PREFIX.length, separator).trim()
    val base64 = content.substring(separator + 1).trim()
    if (fileName.isBlank() || base64.isBlank()) return null
    return fileName to base64
}

/** Builds a cURL command string for the given tab, resolving {{vars}} from variable layers. */

fun enabledHeadersForSend(tab: RequestTabState): List<KeyValueEntry> {
    val enabled = tab.headers.filter { it.enabled && it.key.isNotBlank() }
    val userHeaders = enabled.filter { it.kind != HeaderKind.INHERITED }
    val inheritedHeaders = enabled.filter { it.kind == HeaderKind.INHERITED }
    val userKeys = userHeaders.map { it.key.lowercase() }.toSet()
    val activeInherited = inheritedHeaders.filter { it.key.lowercase() !in userKeys }
    return (userHeaders + activeInherited).map { KeyValueEntry(it.key, it.value, secret = it.secret) }
}

fun enabledQueryParamsForSend(tab: RequestTabState): List<KeyValueEntry> {
    val enabled = tab.params.filter { it.enabled && it.key.isNotBlank() }
    val userParams = enabled.filter { it.kind != HeaderKind.INHERITED }
    val inheritedParams = enabled.filter { it.kind == HeaderKind.INHERITED }
    val userKeys = userParams.map { it.key.lowercase() }.toSet()
    val activeInherited = inheritedParams.filter { it.key.lowercase() !in userKeys }
    return (userParams + activeInherited).map { KeyValueEntry(it.key, it.value, secret = it.secret) }
}

internal fun resolveUrlForLog(
    url: String,
    variableLayers: List<Map<String, String>>,
    requestScopedVars: Map<String, String> = emptyMap(),
): String {
    val layers = if (requestScopedVars.isNotEmpty()) {
        listOf(requestScopedVars) + variableLayers
    } else {
        variableLayers
    }
    return VariableResolver.resolve(url, layers, removeUnresolved = false)
}

/** cURL command with raw (unresolved) {{variables}} preserved – useful for sharing templates. */
