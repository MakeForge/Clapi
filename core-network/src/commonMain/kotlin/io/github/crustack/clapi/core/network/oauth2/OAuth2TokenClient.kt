package io.github.crustack.clapi.core.network.oauth2

import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OAuth2Exception(
    val error: String,
    val errorDescription: String? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(errorDescription ?: error, cause)

@Serializable
internal data class OAuth2ErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("message") val message: String? = null,
)

interface OAuth2TokenClient {
    suspend fun fetchClientCredentialsToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
        scope: String? = null,
    ): Result<OAuth2TokenResponse>

    suspend fun fetchPasswordToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
        username: String,
        password: String,
        scope: String? = null,
    ): Result<OAuth2TokenResponse>

    suspend fun refreshToken(
        tokenUrl: String,
        refreshToken: String,
        clientId: String? = null,
        clientSecret: String? = null,
        placement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
        scope: String? = null,
    ): Result<OAuth2TokenResponse>
}

class KtorOAuth2TokenClient(
    private val httpClient: HttpClient = createPlatformHttpClient(),
) : OAuth2TokenClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchClientCredentialsToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement,
        scope: String?,
    ): Result<OAuth2TokenResponse> = runCatching {
        val formParams = Parameters.build {
            append("grant_type", "client_credentials")
            if (placement == OAuth2CredentialsPlacement.BODY) {
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }
            if (!scope.isNullOrBlank()) {
                append("scope", scope.trim())
            }
        }

        executeTokenRequest(
            tokenUrl = tokenUrl,
            formParams = formParams,
            clientId = clientId,
            clientSecret = clientSecret,
            placement = placement,
        )
    }

    override suspend fun fetchPasswordToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement,
        username: String,
        password: String,
        scope: String?,
    ): Result<OAuth2TokenResponse> = runCatching {
        val formParams = Parameters.build {
            append("grant_type", "password")
            append("username", username)
            append("password", password)
            if (placement == OAuth2CredentialsPlacement.BODY) {
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }
            if (!scope.isNullOrBlank()) {
                append("scope", scope.trim())
            }
        }

        executeTokenRequest(
            tokenUrl = tokenUrl,
            formParams = formParams,
            clientId = clientId,
            clientSecret = clientSecret,
            placement = placement,
        )
    }

    override suspend fun refreshToken(
        tokenUrl: String,
        refreshToken: String,
        clientId: String?,
        clientSecret: String?,
        placement: OAuth2CredentialsPlacement,
        scope: String?,
    ): Result<OAuth2TokenResponse> = runCatching {
        val formParams = Parameters.build {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            if (placement == OAuth2CredentialsPlacement.BODY && !clientId.isNullOrBlank()) {
                append("client_id", clientId)
                if (!clientSecret.isNullOrBlank()) {
                    append("client_secret", clientSecret)
                }
            }
            if (!scope.isNullOrBlank()) {
                append("scope", scope.trim())
            }
        }

        executeTokenRequest(
            tokenUrl = tokenUrl,
            formParams = formParams,
            clientId = clientId.orEmpty(),
            clientSecret = clientSecret.orEmpty(),
            placement = if (clientId.isNullOrBlank()) OAuth2CredentialsPlacement.BODY else placement,
        )
    }

    private suspend fun executeTokenRequest(
        tokenUrl: String,
        formParams: Parameters,
        clientId: String,
        clientSecret: String,
        placement: OAuth2CredentialsPlacement,
    ): OAuth2TokenResponse {
        val response = httpClient.submitForm(
            url = tokenUrl,
            formParameters = formParams,
        ) {
            if (placement == OAuth2CredentialsPlacement.BASIC_AUTH_HEADER && clientId.isNotBlank()) {
                val basicAuth = "$clientId:$clientSecret".encodeToByteArray().encodeBase64()
                header(HttpHeaders.Authorization, "Basic $basicAuth")
            }
        }

        val responseText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            val errObj = runCatching { json.decodeFromString<OAuth2ErrorResponse>(responseText) }.getOrNull()
            val err = errObj?.error ?: "http_error_${response.status.value}"
            val desc = errObj?.errorDescription ?: errObj?.message ?: responseText.take(200)
            throw OAuth2Exception(
                error = err,
                errorDescription = desc,
                statusCode = response.status.value,
            )
        }

        return json.decodeFromString<OAuth2TokenResponse>(responseText)
    }
}
