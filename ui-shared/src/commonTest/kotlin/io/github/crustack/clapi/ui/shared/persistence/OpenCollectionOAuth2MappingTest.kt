package io.github.crustack.clapi.ui.shared.persistence

import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.opencollection.AuthOAuth2
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class OpenCollectionOAuth2MappingTest {

    @Test
    fun testCollectionDefaults_withClientCredentialsOAuth2_roundtrip() {
        val col = CollectionNode(
            id = "col-oauth2",
            name = "OAuth2 Collection",
            kind = RequestKind.COLLECTION,
            isFolder = true,
            authType = AuthType.OAUTH2,
            authOAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
            authOAuth2AccessTokenUrl = "https://auth.example.com/oauth/token",
            authOAuth2ClientId = "client-123",
            authOAuth2ClientSecret = "secret-456",
            authOAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
            authOAuth2Scope = "api.read api.write",
        )

        val doc = col.toOpenCollectionDocument()
        val authConfig = doc.request?.auth
        assertNotNull(authConfig)
        val authOAuth2 = assertIs<AuthOAuth2>(authConfig)
        assertEquals(OAuth2Flow.CLIENT_CREDENTIALS, authOAuth2.flow)
        assertEquals("https://auth.example.com/oauth/token", authOAuth2.accessTokenUrl)
        assertEquals("client-123", authOAuth2.credentials?.clientId)
        assertEquals("secret-456", authOAuth2.credentials?.clientSecret)
        assertEquals(OAuth2CredentialsPlacement.BASIC_AUTH_HEADER, authOAuth2.credentials?.placement)
        assertEquals("api.read api.write", authOAuth2.scope)

        val reloaded = doc.toCollectionNode()
        assertEquals(AuthType.OAUTH2, reloaded.authType)
        assertEquals(OAuth2Flow.CLIENT_CREDENTIALS, reloaded.authOAuth2Flow)
        assertEquals("https://auth.example.com/oauth/token", reloaded.authOAuth2AccessTokenUrl)
        assertEquals("client-123", reloaded.authOAuth2ClientId)
        assertEquals("secret-456", reloaded.authOAuth2ClientSecret)
        assertEquals(OAuth2CredentialsPlacement.BASIC_AUTH_HEADER, reloaded.authOAuth2CredentialsPlacement)
        assertEquals("api.read api.write", reloaded.authOAuth2Scope)
    }

    @Test
    fun testRequestItem_withPasswordOAuth2_roundtrip() {
        val req = CollectionNode(
            id = "req-oauth2",
            name = "Get User Profile",
            kind = RequestKind.HTTP,
            method = HttpMethodType.GET,
            url = "https://api.example.com/user",
            authType = AuthType.OAUTH2,
            authOAuth2Flow = OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS,
            authOAuth2AccessTokenUrl = "https://auth.example.com/oauth/token",
            authOAuth2ClientId = "mobile-app",
            authOAuth2ClientSecret = "app-secret",
            authOAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BODY,
            authOAuth2Username = "john_doe",
            authOAuth2Password = "supersecret",
            authOAuth2Scope = "openid profile",
        )

        val item = req.toOpenCollectionItem()
        val httpItem = assertIs<HttpRequestItem>(item)
        val authConfig = httpItem.http.auth
        assertNotNull(authConfig)
        val authOAuth2 = assertIs<AuthOAuth2>(authConfig)
        assertEquals(OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS, authOAuth2.flow)
        assertEquals("https://auth.example.com/oauth/token", authOAuth2.accessTokenUrl)
        assertEquals("mobile-app", authOAuth2.credentials?.clientId)
        assertEquals("app-secret", authOAuth2.credentials?.clientSecret)
        assertEquals(OAuth2CredentialsPlacement.BODY, authOAuth2.credentials?.placement)
        assertEquals("john_doe", authOAuth2.resourceOwner?.username)
        assertEquals("supersecret", authOAuth2.resourceOwner?.password)
        assertEquals("openid profile", authOAuth2.scope)

        val reloaded = httpItem.toCollectionNode()
        assertEquals(AuthType.OAUTH2, reloaded.authType)
        assertEquals(OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS, reloaded.authOAuth2Flow)
        assertEquals("https://auth.example.com/oauth/token", reloaded.authOAuth2AccessTokenUrl)
        assertEquals("mobile-app", reloaded.authOAuth2ClientId)
        assertEquals("app-secret", reloaded.authOAuth2ClientSecret)
        assertEquals(OAuth2CredentialsPlacement.BODY, reloaded.authOAuth2CredentialsPlacement)
        assertEquals("john_doe", reloaded.authOAuth2Username)
        assertEquals("supersecret", reloaded.authOAuth2Password)
        assertEquals("openid profile", reloaded.authOAuth2Scope)
    }
}
