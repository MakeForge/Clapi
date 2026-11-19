package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.AuthConfig as CoreAuthConfig
import io.github.makeforge.clapi.core.model.AuthType as CoreAuthType
import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthConfig
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.ClientCertificate
import io.github.makeforge.clapi.core.model.opencollection.PemCertificate
import io.github.makeforge.clapi.core.model.opencollection.Pkcs12Certificate
import io.github.makeforge.clapi.core.network.VariableResolver

/**
 * Resolves effective RabbitMQ AMQP 0-9-1 credentials from Clapi's AuthConfig hierarchy
 * (supporting folder/collection `Inherit`, `None`, `Basic`, `Bearer`, `OAuth2`, and TLS `EXTERNAL`).
 */
object RabbitMqAuthMapper {

    /**
     * Walks [requestAuth] followed by [ancestorAuthChain] (nearest parent folder -> collection root)
     * until a concrete non-inherit auth configuration is found.
     */
    fun resolveEffectiveOpenCollectionAuth(
        requestAuth: AuthConfig?,
        ancestorAuthChain: List<AuthConfig?> = emptyList(),
    ): AuthConfig? {
        if (requestAuth != null && requestAuth !is AuthInherit) {
            return requestAuth
        }
        if (requestAuth == null) {
            return null
        }
        for (ancestor in ancestorAuthChain) {
            if (ancestor != null && ancestor !is AuthInherit) {
                return ancestor
            }
        }
        return null
    }

    /**
     * Walks core UI [CoreAuthConfig] chain (nearest parent folder -> collection root).
     */
    fun resolveEffectiveCoreAuth(
        requestAuth: CoreAuthConfig,
        ancestorAuthChain: List<CoreAuthConfig> = emptyList(),
    ): CoreAuthConfig {
        if (requestAuth.type != CoreAuthType.INHERIT) return requestAuth
        for (ancestor in ancestorAuthChain) {
            if (ancestor.type != CoreAuthType.INHERIT) return ancestor
        }
        return CoreAuthConfig(type = CoreAuthType.NONE)
    }

    /**
     * Maps an OpenCollection [AuthConfig] (plus optional TLS client certificate setting) into
     * [RabbitMqAuthCredentials] with variable interpolation applied.
     */
    fun toAmqpCredentials(
        requestAuth: AuthConfig?,
        ancestorAuthChain: List<AuthConfig?> = emptyList(),
        variableLayers: List<Map<String, String>> = emptyList(),
        useTlsClientCert: Boolean = false,
        clientCertificates: List<ClientCertificate> = emptyList(),
        resolvedOAuth2Token: String? = null,
    ): RabbitMqAuthCredentials {
        if (useTlsClientCert) {
            return mapClientCertificate(clientCertificates.firstOrNull(), variableLayers)
        }

        val effective = resolveEffectiveOpenCollectionAuth(requestAuth, ancestorAuthChain)
            ?: return RabbitMqAuthCredentials.None

        return when (effective) {
            is AuthBasic -> RabbitMqAuthCredentials.Plain(
                username = VariableResolver.resolve(effective.username, variableLayers),
                password = VariableResolver.resolve(effective.password, variableLayers),
            )
            is AuthBearer -> RabbitMqAuthCredentials.OAuth2Bearer(
                token = VariableResolver.resolve(effective.token, variableLayers),
                username = "",
            )
            is AuthOAuth2 -> {
                val token = resolvedOAuth2Token
                    ?: effective.tokenConfig?.id?.let { VariableResolver.resolve(it, variableLayers) }
                    ?: ""
                val username = effective.resourceOwner?.username
                    ?.let { VariableResolver.resolve(it, variableLayers) }
                    .orEmpty()
                if (token.isNotBlank()) {
                    RabbitMqAuthCredentials.OAuth2Bearer(token = token, username = username)
                } else {
                    RabbitMqAuthCredentials.None
                }
            }
            else -> RabbitMqAuthCredentials.None
        }
    }

    /**
     * Maps core UI [CoreAuthConfig] (used by AppState/RequestTabState) into [RabbitMqAuthCredentials].
     */
    fun toAmqpCredentialsFromCore(
        requestAuth: CoreAuthConfig,
        ancestorAuthChain: List<CoreAuthConfig> = emptyList(),
        variableLayers: List<Map<String, String>> = emptyList(),
        useTlsClientCert: Boolean = false,
        clientCertificates: List<ClientCertificate> = emptyList(),
        resolvedOAuth2Token: String? = null,
    ): RabbitMqAuthCredentials {
        if (useTlsClientCert) {
            return mapClientCertificate(clientCertificates.firstOrNull(), variableLayers)
        }

        val effective = resolveEffectiveCoreAuth(requestAuth, ancestorAuthChain)
        return when (effective.type) {
            CoreAuthType.BASIC -> RabbitMqAuthCredentials.Plain(
                username = VariableResolver.resolve(effective.params["username"].orEmpty(), variableLayers),
                password = VariableResolver.resolve(effective.params["password"].orEmpty(), variableLayers),
            )
            CoreAuthType.BEARER, CoreAuthType.JWT -> RabbitMqAuthCredentials.OAuth2Bearer(
                token = VariableResolver.resolve(effective.params["token"].orEmpty(), variableLayers),
                username = VariableResolver.resolve(effective.params["username"].orEmpty(), variableLayers),
            )
            CoreAuthType.OAUTH2 -> {
                val token = resolvedOAuth2Token
                    ?: VariableResolver.resolve(effective.params["accessToken"].orEmpty(), variableLayers)
                val username = VariableResolver.resolve(effective.params["username"].orEmpty(), variableLayers)
                if (token.isNotBlank()) {
                    RabbitMqAuthCredentials.OAuth2Bearer(token = token, username = username)
                } else {
                    RabbitMqAuthCredentials.None
                }
            }
            else -> RabbitMqAuthCredentials.None
        }
    }

    private fun mapClientCertificate(
        cert: ClientCertificate?,
        variableLayers: List<Map<String, String>>,
    ): RabbitMqAuthCredentials.ExternalTls = when (cert) {
        is Pkcs12Certificate -> RabbitMqAuthCredentials.ExternalTls(
            certPath = VariableResolver.resolve(cert.pkcs12FilePath, variableLayers),
            passphrase = cert.passphrase?.let { VariableResolver.resolve(it, variableLayers) },
        )
        is PemCertificate -> RabbitMqAuthCredentials.ExternalTls(
            certPath = VariableResolver.resolve(cert.certificateFilePath, variableLayers),
            keyPath = VariableResolver.resolve(cert.privateKeyFilePath, variableLayers),
            passphrase = cert.passphrase?.let { VariableResolver.resolve(it, variableLayers) },
        )
        null -> RabbitMqAuthCredentials.ExternalTls()
    }
}
