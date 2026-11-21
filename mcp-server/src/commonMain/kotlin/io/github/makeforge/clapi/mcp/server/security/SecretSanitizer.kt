package io.github.makeforge.clapi.mcp.server.security

import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.opencollection.ApiKeyPlacement
import io.github.makeforge.clapi.core.model.opencollection.AuthApiKey
import io.github.makeforge.clapi.core.model.opencollection.AuthAwsV4
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthConfig
import io.github.makeforge.clapi.core.model.opencollection.AuthDigest
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.AuthNTLM
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth1
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.AuthWsse
import io.github.makeforge.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.AzureKeyVaultExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretVariable
import io.github.makeforge.clapi.core.model.opencollection.FolderItem
import io.github.makeforge.clapi.core.model.opencollection.GcpSecretsManagerExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestItem
import io.github.makeforge.clapi.core.model.opencollection.GrpcRequestItem
import io.github.makeforge.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestHeader
import io.github.makeforge.clapi.core.model.opencollection.HttpRequestItem
import io.github.makeforge.clapi.core.model.opencollection.KeePassXcExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.KeeperSecurityExternalSecret
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ClientCredentials
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ResourceOwner
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionItem
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.RequestDefaults
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableEntry
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.model.opencollection.WebSocketRequestItem
import io.github.makeforge.clapi.core.scripting.AssertionResult
import io.github.makeforge.clapi.mcp.server.backend.ExecutionSnapshot
import io.github.makeforge.clapi.mcp.server.backend.MASKED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.REDACTED_SECRET_PLACEHOLDER
import io.github.makeforge.clapi.mcp.server.backend.SanitizedAuthDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedExternalSecretDeclarationDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedKeyValueDto
import io.github.makeforge.clapi.mcp.server.backend.SanitizedVariableDto
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Immutable Zero-Exfiltration Sanitization Pipeline for Clapi MCP Server.
 *
 * Guarantees that plaintext secret variables, auth credentials, sensitive headers,
 * and external secret values never leak into MCP resources, tool outputs, snapshots, or errors.
 */
object SecretSanitizer {

    const val MIN_SCRUB_SECRET_LENGTH = 4

    val SENSITIVE_HEADER_NAMES: Set<String> = setOf(
        "authorization",
        "proxy-authorization",
        "x-api-key",
        "api-key",
        "token",
        "cookie",
        "set-cookie",
        "x-auth-token",
    )

    private val AUTH_SCHEME_PREFIXES = listOf("Bearer ", "Basic ", "Digest ", "Token ")

    fun isSensitiveHeader(headerName: String): Boolean =
        headerName.trim().lowercase() in SENSITIVE_HEADER_NAMES

    fun maskHeaderValue(headerName: String, rawValue: String, isSecretFlagged: Boolean = false): String {
        if (!isSecretFlagged && !isSensitiveHeader(headerName)) {
            return rawValue
        }
        val trimmed = rawValue.trim()
        for (prefix in AUTH_SCHEME_PREFIXES) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val canonicalPrefix = trimmed.substring(0, prefix.length)
                return "$canonicalPrefix$MASKED_SECRET_PLACEHOLDER"
            }
        }
        return MASKED_SECRET_PLACEHOLDER
    }

    fun sanitizeHeader(
        key: String,
        value: String,
        enabled: Boolean = true,
        secret: Boolean = false,
        description: String = "",
        knownSecrets: Set<String> = emptySet(),
    ): SanitizedKeyValueDto {
        val isSensitive = secret || isSensitiveHeader(key)
        val maskedValue = if (isSensitive) {
            maskHeaderValue(key, value, isSecretFlagged = true)
        } else {
            scrubText(value, knownSecrets)
        }
        return SanitizedKeyValueDto(
            key = key,
            value = maskedValue,
            enabled = enabled,
            secret = isSensitive,
            description = description,
        )
    }

    fun sanitizeHeaders(
        headers: List<KeyValueEntry>,
        knownSecrets: Set<String> = emptySet(),
    ): List<SanitizedKeyValueDto> =
        headers.map { entry ->
            sanitizeHeader(
                key = entry.key,
                value = entry.value,
                enabled = entry.enabled,
                secret = entry.secret,
                description = entry.description,
                knownSecrets = knownSecrets,
            )
        }

    fun sanitizeHttpHeaders(
        headers: List<HttpRequestHeader>,
        knownSecrets: Set<String> = emptySet(),
    ): List<SanitizedKeyValueDto> =
        headers.map { entry ->
            sanitizeHeader(
                key = entry.name,
                value = entry.value,
                enabled = entry.disabled != true,
                secret = isSensitiveHeader(entry.name),
                description = entry.description?.content.orEmpty(),
                knownSecrets = knownSecrets,
            )
        }

    fun sanitizeVariable(entry: VariableEntry, secretValueLookup: ((String) -> String?)? = null): SanitizedVariableDto =
        when (entry) {
            is RegularVariable -> {
                val strVal = entry.value?.asString.orEmpty()
                SanitizedVariableDto(
                    key = entry.name,
                    value = strVal,
                    kind = "regular",
                    variableType = entry.type.value,
                    secret = false,
                    isSet = strVal.isNotBlank(),
                    enabled = entry.disabled != true,
                )
            }
            is SecretVariable -> {
                val strVal = secretValueLookup?.invoke(entry.name).orEmpty()
                SanitizedVariableDto(
                    key = entry.name,
                    value = MASKED_SECRET_PLACEHOLDER,
                    kind = "secret",
                    variableType = entry.type.value,
                    secret = true,
                    isSet = strVal.isNotBlank(),
                    enabled = entry.disabled != true,
                )
            }
            is DynamicVariable -> {
                SanitizedVariableDto(
                    key = entry.name,
                    value = "",
                    kind = "dynamic",
                    variableType = entry.type.value,
                    secret = false,
                    isSet = entry.script.isNotBlank(),
                    enabled = entry.disabled != true,
                    dynamicScript = entry.script,
                    dynamicParameters = entry.parameters,
                )
            }
        }

    fun sanitizeAuth(auth: AuthConfig?, jwtOverride: Boolean = false): SanitizedAuthDto {
        if (auth == null) {
            return SanitizedAuthDto(type = "NONE", isConfigured = false)
        }
        return when (auth) {
            is AuthInherit -> SanitizedAuthDto(type = "INHERIT", isConfigured = true)
            is AuthBasic -> SanitizedAuthDto(
                type = "BASIC",
                username = auth.username,
                password = if (auth.password.isNotBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = auth.username.isNotBlank() || auth.password.isNotBlank(),
            )
            is AuthBearer -> SanitizedAuthDto(
                type = if (jwtOverride) "JWT" else "BEARER",
                token = if (auth.token.isNotBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = auth.token.isNotBlank(),
            )
            is AuthApiKey -> SanitizedAuthDto(
                type = "API_KEY",
                apiKey = auth.key,
                apiValue = if (auth.value.isNotBlank()) MASKED_SECRET_PLACEHOLDER else null,
                apiPlacement = auth.placement.value,
                isConfigured = auth.key.isNotBlank() && auth.value.isNotBlank(),
            )
            is AuthOAuth2 -> {
                val clientSecret = auth.credentials?.clientSecret
                val ownerPass = auth.resourceOwner?.password
                SanitizedAuthDto(
                    type = "OAUTH2",
                    oauth2Flow = auth.flow.value,
                    oauth2AccessTokenUrl = auth.accessTokenUrl,
                    oauth2RefreshTokenUrl = auth.refreshTokenUrl,
                    oauth2AuthorizationUrl = auth.authorizationUrl,
                    oauth2RedirectUri = auth.redirectUri,
                    oauth2ClientId = auth.credentials?.clientId,
                    oauth2ClientSecret = if (!clientSecret.isNullOrBlank()) MASKED_SECRET_PLACEHOLDER else null,
                    oauth2CredentialsPlacement = auth.credentials?.placement?.value,
                    oauth2Username = auth.resourceOwner?.username,
                    oauth2Password = if (!ownerPass.isNullOrBlank()) MASKED_SECRET_PLACEHOLDER else null,
                    oauth2Scope = auth.scope,
                    isConfigured = !auth.credentials?.clientId.isNullOrBlank() || !clientSecret.isNullOrBlank(),
                )
            }
            is AuthDigest -> SanitizedAuthDto(
                type = "BASIC",
                username = auth.username,
                password = if (auth.password.isNotBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = auth.password.isNotBlank(),
            )
            is AuthNTLM -> SanitizedAuthDto(
                type = "BASIC",
                username = auth.username,
                password = if (auth.password.isNotBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = auth.password.isNotBlank(),
            )
            is AuthWsse -> SanitizedAuthDto(
                type = "BASIC",
                username = auth.username,
                password = if (auth.password.isNotBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = auth.password.isNotBlank(),
            )
            is AuthAwsV4 -> SanitizedAuthDto(
                type = "API_KEY",
                apiKey = auth.accessKeyId,
                apiValue = if (!auth.secretAccessKey.isNullOrBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = !auth.secretAccessKey.isNullOrBlank(),
            )
            is AuthOAuth1 -> SanitizedAuthDto(
                type = "OAUTH2",
                oauth2ClientId = auth.consumerKey,
                oauth2ClientSecret = if (!auth.consumerSecret.isNullOrBlank()) MASKED_SECRET_PLACEHOLDER else null,
                isConfigured = !auth.consumerSecret.isNullOrBlank(),
            )
        }
    }

    /**
     * Merges a [SanitizedAuthDto] update from an MCP tool with an existing [AuthConfig],
     * preserving existing secret credentials whenever the incoming value is `"********"` or null.
     */
    fun mergeAuthUpdate(incoming: SanitizedAuthDto?, existing: AuthConfig?): AuthConfig? {
        if (incoming == null) return existing
        return when (incoming.type.trim().uppercase()) {
            "NONE", "" -> null
            "INHERIT" -> AuthInherit
            "BASIC" -> {
                val prev = existing as? AuthBasic
                val username = incoming.username ?: prev?.username.orEmpty()
                val password = preserveSecretIfMasked(incoming.password, prev?.password.orEmpty())
                AuthBasic(username = username, password = password)
            }
            "BEARER", "JWT" -> {
                val prev = existing as? AuthBearer
                val token = preserveSecretIfMasked(incoming.token, prev?.token.orEmpty())
                AuthBearer(token = token)
            }
            "API_KEY", "APIKEY" -> {
                val prev = existing as? AuthApiKey
                val key = incoming.apiKey ?: prev?.key.orEmpty()
                val value = preserveSecretIfMasked(incoming.apiValue, prev?.value.orEmpty())
                val placement = ApiKeyPlacement.fromValue(incoming.apiPlacement)
                    ?: prev?.placement
                    ?: ApiKeyPlacement.HEADER
                AuthApiKey(key = key, value = value, placement = placement)
            }
            "OAUTH2" -> {
                val prev = existing as? AuthOAuth2
                val clientId = incoming.oauth2ClientId ?: prev?.credentials?.clientId
                val clientSecret = preserveSecretIfMasked(
                    incoming.oauth2ClientSecret,
                    prev?.credentials?.clientSecret.orEmpty()
                ).takeIf { it.isNotEmpty() }
                val ownerUser = incoming.oauth2Username ?: prev?.resourceOwner?.username
                val ownerPass = preserveSecretIfMasked(
                    incoming.oauth2Password,
                    prev?.resourceOwner?.password.orEmpty()
                ).takeIf { it.isNotEmpty() }
                AuthOAuth2(
                    flow = incoming.oauth2Flow ?: prev?.flow?.value ?: "client_credentials",
                    accessTokenUrl = incoming.oauth2AccessTokenUrl ?: prev?.accessTokenUrl,
                    refreshTokenUrl = incoming.oauth2RefreshTokenUrl ?: prev?.refreshTokenUrl,
                    authorizationUrl = incoming.oauth2AuthorizationUrl ?: prev?.authorizationUrl,
                    redirectUri = incoming.oauth2RedirectUri ?: prev?.redirectUri,
                    credentials = OAuth2ClientCredentials(
                        clientId = clientId,
                        clientSecret = clientSecret,
                        placement = incoming.oauth2CredentialsPlacement
                            ?: prev?.credentials?.placement?.value
                            ?: "basic_auth_header"
                    ),
                    resourceOwner = if (ownerUser != null || ownerPass != null) {
                        OAuth2ResourceOwner(username = ownerUser, password = ownerPass)
                    } else prev?.resourceOwner,
                    scope = incoming.oauth2Scope ?: prev?.scope,
                    tokenConfig = prev?.tokenConfig,
                    settings = prev?.settings,
                )
            }
            else -> existing
        }
    }

    fun preserveSecretIfMasked(incoming: String?, existing: String): String =
        if (incoming == null || incoming == MASKED_SECRET_PLACEHOLDER) existing else incoming

    fun sanitizeExternalSecretDeclaration(variable: ExternalSecretVariable): SanitizedExternalSecretDeclarationDto =
        when (variable) {
            is HashicorpVaultExternalSecret -> SanitizedExternalSecretDeclarationDto(
                name = variable.name,
                providerType = "hashicorp-vault-server",
                path = variable.path,
                enabled = variable.disabled != true,
            )
            is AwsSecretsManagerExternalSecret -> SanitizedExternalSecretDeclarationDto(
                name = variable.name,
                providerType = "aws-secrets-manager",
                path = variable.secretName,
                targetField = variable.targetField,
                customFilters = variable.customFilters,
                enabled = variable.disabled != true,
            )
            is AzureKeyVaultExternalSecret -> SanitizedExternalSecretDeclarationDto(
                name = variable.name,
                providerType = "azure-key-vault",
                path = variable.secretName,
                url = variable.vaultName,
                targetField = variable.targetField,
                customFilters = variable.customFilters,
                enabled = variable.disabled != true,
            )
            is GcpSecretsManagerExternalSecret -> SanitizedExternalSecretDeclarationDto(
                name = variable.name,
                providerType = "gcp-secrets-manager",
                path = variable.secretId,
                url = variable.projectId,
                targetField = variable.targetField,
                customFilters = variable.customFilters,
                enabled = variable.disabled != true,
            )
            is KeeperSecurityExternalSecret -> SanitizedExternalSecretDeclarationDto(
                name = variable.name,
                providerType = "keeper-security-cloud",
                path = variable.recordTitle,
                targetField = variable.targetField,
                customFilters = variable.customFilters,
                enabled = variable.disabled != true,
            )
            is KeePassXcExternalSecret -> SanitizedExternalSecretDeclarationDto(
                name = variable.name,
                providerType = "keepassxc",
                path = variable.name,
                url = variable.url,
                targetField = variable.targetField,
                customFilters = variable.customFilters,
                enabled = variable.disabled != true,
            )
        }

    /**
     * Extracts all secret values (including URL-encoded and Basic Auth Base64-encoded variants)
     * that meet [MIN_SCRUB_SECRET_LENGTH] (≥ 4 chars), sorted by length descending.
     */
    fun expandScrubCandidates(
        rawSecrets: Iterable<String>,
        basicAuthPairs: Iterable<Pair<String, String>> = emptyList(),
    ): List<String> {
        val candidates = mutableSetOf<String>()
        for (secret in rawSecrets) {
            val trimmed = secret.trim()
            if (trimmed.length >= MIN_SCRUB_SECRET_LENGTH && trimmed != MASKED_SECRET_PLACEHOLDER) {
                candidates.add(trimmed)
                runCatching {
                    val urlEncoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
                    if (urlEncoded.length >= MIN_SCRUB_SECRET_LENGTH) {
                        candidates.add(urlEncoded)
                    }
                }
            }
        }
        for ((user, pass) in basicAuthPairs) {
            if (pass.trim().length >= MIN_SCRUB_SECRET_LENGTH && pass.trim() != MASKED_SECRET_PLACEHOLDER) {
                val encoded = Base64.getEncoder()
                    .encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
                if (encoded.length >= MIN_SCRUB_SECRET_LENGTH) {
                    candidates.add(encoded)
                }
            }
        }
        return candidates.sortedByDescending { it.length }
    }

    /**
     * Replaces every occurrence of any known secret (length ≥ 4) in [text] with `"[REDACTED_SECRET]"`.
     */
    fun scrubText(text: String, knownSecrets: Iterable<String>): String {
        if (text.isEmpty()) return text
        val candidates = expandScrubCandidates(knownSecrets)
        if (candidates.isEmpty()) return text
        var result = text
        for (secret in candidates) {
            if (result.contains(secret)) {
                result = result.replace(secret, REDACTED_SECRET_PLACEHOLDER)
            }
        }
        return result
    }

    fun sanitizeExecutionSnapshot(
        snapshot: ExecutionSnapshot,
        knownSecrets: Iterable<String>,
        basicAuthPairs: Iterable<Pair<String, String>> = emptyList(),
    ): ExecutionSnapshot {
        val expanded = expandScrubCandidates(knownSecrets, basicAuthPairs).toSet()
        return snapshot.copy(
            sanitizedRequestHeaders = snapshot.sanitizedRequestHeaders.map { h ->
                sanitizeHeader(h.key, h.value, h.enabled, h.secret, h.description, expanded)
            },
            sanitizedResponseHeaders = snapshot.sanitizedResponseHeaders.map { h ->
                sanitizeHeader(h.key, h.value, h.enabled, h.secret, h.description, expanded)
            },
            sanitizedBodyText = scrubText(snapshot.sanitizedBodyText, expanded),
            assertions = snapshot.assertions.map { a ->
                AssertionResult(
                    name = scrubText(a.name, expanded),
                    passed = a.passed,
                    message = a.message?.let { scrubText(it, expanded) },
                )
            },
            scriptLogs = snapshot.scriptLogs.map { scrubText(it, expanded) },
            scriptError = snapshot.scriptError?.let { scrubText(it, expanded) },
        )
    }

    /**
     * Sanitizes an [OpenCollectionDocument] before export so that secret variable values
     * and auth credentials are stripped or masked.
     */
    fun sanitizeDocumentForExport(doc: OpenCollectionDocument): OpenCollectionDocument {
        val cleanConfig = doc.config?.copy(
            environments = doc.config?.environments.orEmpty().map { env ->
                env.copy(
                    variables = env.variables.map(::sanitizeVariableEntryForExport)
                )
            }
        )
        return doc.copy(
            config = cleanConfig,
            request = doc.request?.let(::sanitizeRequestDefaultsForExport),
            items = doc.items.map(::sanitizeItemForExport),
        )
    }

    private fun sanitizeVariableEntryForExport(v: VariableEntry): VariableEntry =
        when (v) {
            is SecretVariable -> v
            else -> v
        }

    private fun sanitizeAuthForExport(auth: AuthConfig?): AuthConfig? =
        when (auth) {
            null -> null
            is AuthBasic -> auth.copy(password = "")
            is AuthBearer -> auth.copy(token = "")
            is AuthApiKey -> auth.copy(value = "")
            is AuthOAuth2 -> auth.copy(
                credentials = auth.credentials?.copy(clientSecret = null),
                resourceOwner = auth.resourceOwner?.copy(password = null),
            )
            is AuthDigest -> auth.copy(password = "")
            is AuthNTLM -> auth.copy(password = "")
            is AuthWsse -> auth.copy(password = "")
            is AuthAwsV4 -> auth.copy(secretAccessKey = null, sessionToken = null)
            is AuthOAuth1 -> auth.copy(consumerSecret = null, accessTokenSecret = null)
            is AuthInherit -> auth
        }

    private fun sanitizeRequestDefaultsForExport(defaults: RequestDefaults): RequestDefaults =
        defaults.copy(
            headers = defaults.headers.map { h ->
                if (isSensitiveHeader(h.name)) h.copy(value = "") else h
            },
            auth = sanitizeAuthForExport(defaults.auth),
            variables = defaults.variables.map(::sanitizeVariableEntryForExport),
        )

    private fun sanitizeItemForExport(item: OpenCollectionItem): OpenCollectionItem =
        when (item) {
            is FolderItem -> item.copy(
                request = item.request?.let(::sanitizeRequestDefaultsForExport),
                items = item.items.map(::sanitizeItemForExport),
            )
            is HttpRequestItem -> item.copy(
                http = item.http.copy(
                    headers = item.http.headers.map { h ->
                        if (isSensitiveHeader(h.name)) h.copy(value = "") else h
                    },
                    auth = sanitizeAuthForExport(item.http.auth),
                ),
                runtime = item.runtime?.copy(
                    variables = item.runtime?.variables.orEmpty().map(::sanitizeVariableEntryForExport)
                ),
            )
            is GrpcRequestItem -> item.copy(
                grpc = item.grpc.copy(
                    metadata = item.grpc.metadata.map { m ->
                        if (isSensitiveHeader(m.name)) m.copy(value = "") else m
                    },
                    auth = sanitizeAuthForExport(item.grpc.auth),
                ),
                runtime = item.runtime?.copy(
                    variables = item.runtime?.variables.orEmpty().map(::sanitizeVariableEntryForExport)
                ),
            )
            is GraphQLRequestItem -> item.copy(
                graphql = item.graphql.copy(
                    headers = item.graphql.headers.map { h ->
                        if (isSensitiveHeader(h.name)) h.copy(value = "") else h
                    },
                    auth = sanitizeAuthForExport(item.graphql.auth),
                ),
                runtime = item.runtime?.copy(
                    variables = item.runtime?.variables.orEmpty().map(::sanitizeVariableEntryForExport)
                ),
            )
            is WebSocketRequestItem -> item.copy(
                websocket = item.websocket.copy(
                    headers = item.websocket.headers.map { h ->
                        if (isSensitiveHeader(h.name)) h.copy(value = "") else h
                    },
                    auth = sanitizeAuthForExport(item.websocket.auth),
                ),
                runtime = item.runtime?.copy(
                    variables = item.runtime?.variables.orEmpty().map(::sanitizeVariableEntryForExport)
                ),
            )
        }
}
