package io.github.crustack.clapi.core.network.aws

import io.github.crustack.clapi.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface AwsSecretsManagerClient {
    /**
     * Retrieves secret data from AWS Secrets Manager using config object.
     */
    suspend fun fetchSecret(config: AwsSecretsManagerConfig): Map<String, String>

    /**
     * Retrieves secret data from AWS Secrets Manager using explicit parameters.
     */
    suspend fun fetchSecret(
        secretId: String,
        accessKey: String,
        secretKey: String,
        region: String = "us-east-1",
        sessionToken: String? = null,
        endpointUrl: String? = null,
        versionStage: String? = "AWSCURRENT",
    ): Map<String, String>
}

@Serializable
private data class GetSecretValueRequest(
    val SecretId: String,
    val VersionStage: String? = null,
)

@Serializable
private data class GetSecretValueResponse(
    val ARN: String? = null,
    val Name: String? = null,
    val SecretString: String? = null,
    val SecretBinary: String? = null,
)

private fun defaultAwsHttpClient(): HttpClient = createPlatformHttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

class KtorAwsSecretsManagerClient(
    private val httpClient: HttpClient = defaultAwsHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AwsSecretsManagerClient {

    override suspend fun fetchSecret(config: AwsSecretsManagerConfig): Map<String, String> {
        require(config.secretId.isNotBlank()) { "secretId must not be blank" }

        return fetchSecret(
            secretId = config.secretId,
            accessKey = config.accessKey,
            secretKey = config.secretKey,
            region = config.region,
            sessionToken = config.sessionToken,
            endpointUrl = config.endpointUrl,
            versionStage = config.versionStage,
        )
    }

    override suspend fun fetchSecret(
        secretId: String,
        accessKey: String,
        secretKey: String,
        region: String,
        sessionToken: String?,
        endpointUrl: String?,
        versionStage: String?,
    ): Map<String, String> {
        require(secretId.isNotBlank()) { "secretId must not be blank" }
        require(accessKey.isNotBlank()) { "accessKey must not be blank" }
        require(secretKey.isNotBlank()) { "secretKey must not be blank" }

        val host = endpointUrl?.removePrefix("http://")?.removePrefix("https://")?.trimEnd('/')
            ?: "secretsmanager.$region.amazonaws.com"
        val requestUrl = endpointUrl?.trimEnd('/') ?: "https://$host"

        val payload = json.encodeToString(
            GetSecretValueRequest.serializer(),
            GetSecretValueRequest(SecretId = secretId, VersionStage = versionStage),
        )

        val baseHeaders = mapOf(
            "host" to host,
            "x-amz-target" to "secretsmanager.GetSecretValue",
            "content-type" to "application/x-amz-json-1.1",
        )

        val signedHeaders = AwsSigV4Signer.sign(
            service = "secretsmanager",
            region = region,
            method = "POST",
            path = "/",
            headers = baseHeaders,
            payload = payload,
            accessKey = accessKey,
            secretKey = secretKey,
            sessionToken = sessionToken,
        )

        val response = httpClient.post(requestUrl) {
            contentType(ContentType.parse("application/x-amz-json-1.1"))
            signedHeaders.forEach { (k, v) ->
                header(k, v)
            }
            setBody(payload)
        }

        val rawBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
            if (rawBody.contains("ResourceNotFoundException")) {
                throw IllegalStateException("External secret not found in AWS Secrets Manager (Secret ID: '$secretId')")
            }
            if (rawBody.contains("UnrecognizedClientException") || rawBody.contains("SignatureDoesNotMatch") || response.status.value in listOf(401, 403)) {
                throw IllegalStateException("AWS authentication failed. Please check your AWS credentials.")
            }
            throw IllegalStateException("AWS Secrets Manager request failed [HTTP ${response.status.value}]: $rawBody")
        }

        val parsedResponse = json.decodeFromString(GetSecretValueResponse.serializer(), rawBody)
        val secretString = parsedResponse.SecretString

        return when {
            !secretString.isNullOrBlank() -> parseSecretString(secretString)
            parsedResponse.SecretBinary != null -> mapOf("secret" to parsedResponse.SecretBinary)
            else -> emptyMap()
        }
    }

    private fun parseSecretString(raw: String): Map<String, String> {
        return try {
            val element = json.parseToJsonElement(raw.trim())
            if (element is JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is JsonPrimitive) value.content else value.toString()
                }
            } else {
                mapOf("secret" to raw)
            }
        } catch (_: Exception) {
            mapOf("secret" to raw)
        }
    }
}
