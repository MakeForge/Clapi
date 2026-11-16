package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow

data class OAuth2ResolvedConfig(
    val flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val tokenUrl: String = "",
    val refreshTokenUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val credentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
    val username: String = "",
    val password: String = "",
    val scope: String? = null,
)
