package io.github.makeforge.clapi.core.network.rabbitmq

import io.github.makeforge.clapi.core.model.opencollection.AuthBasic
import io.github.makeforge.clapi.core.model.opencollection.AuthBearer
import io.github.makeforge.clapi.core.model.opencollection.AuthInherit
import io.github.makeforge.clapi.core.model.opencollection.AuthOAuth2
import io.github.makeforge.clapi.core.model.opencollection.Pkcs12Certificate
import io.github.makeforge.clapi.core.model.opencollection.OAuth2ResourceOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RabbitMqAuthMapperTest {

    @Test
    fun inheritsBasicAuthFromCollectionAndResolvesSecrets() {
        val creds = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = AuthInherit,
            ancestorAuthChain = listOf(
                AuthInherit, // parent folder also inherits
                AuthBasic(username = "{{rmqUser}}", password = "{{rmqPass}}"), // collection root
            ),
            variableLayers = listOf(mapOf("rmqUser" to "clapi", "rmqPass" to "s3cr3t")),
        )

        val plain = assertIs<RabbitMqAuthCredentials.Plain>(creds)
        assertEquals("clapi", plain.username)
        assertEquals("s3cr3t", plain.password)
    }

    @Test
    fun mapsBearerAndOAuth2ToRabbitMqJwtMechanism() {
        val bearerCreds = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = AuthBearer(token = "{{jwt}}"),
            variableLayers = listOf(mapOf("jwt" to "eyJ.token.val")),
        )
        val bearer = assertIs<RabbitMqAuthCredentials.OAuth2Bearer>(bearerCreds)
        assertEquals("eyJ.token.val", bearer.token)
        assertEquals("", bearer.username)

        val oauthCreds = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = AuthOAuth2(resourceOwner = OAuth2ResourceOwner(username = "svc-user")),
            resolvedOAuth2Token = "oauth-access-token",
        )
        val oauth = assertIs<RabbitMqAuthCredentials.OAuth2Bearer>(oauthCreds)
        assertEquals("oauth-access-token", oauth.token)
        assertEquals("svc-user", oauth.username)
    }

    @Test
    fun mapsTlsClientCertificateToExternalSasl() {
        val creds = RabbitMqAuthMapper.toAmqpCredentials(
            requestAuth = AuthInherit,
            useTlsClientCert = true,
            clientCertificates = listOf(
                Pkcs12Certificate(domain = "rmq.local", pkcs12FilePath = "/certs/client.p12", passphrase = "{{certPass}}")
            ),
            variableLayers = listOf(mapOf("certPass" to "p12pass")),
        )
        val external = assertIs<RabbitMqAuthCredentials.ExternalTls>(creds)
        assertEquals("/certs/client.p12", external.certPath)
        assertEquals("p12pass", external.passphrase)
    }

    @Test
    fun mapsNullAuthToNone() {
        val creds = RabbitMqAuthMapper.toAmqpCredentials(requestAuth = null)
        assertIs<RabbitMqAuthCredentials.None>(creds)
    }
}
