package io.github.makeforge.clapi.core.network.gcp

import io.github.makeforge.clapi.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

interface GcpTokenProvider {
    suspend fun getAccessToken(
        serviceAccountKey: String = "",
        authMethod: GcpAuthMethod = GcpAuthMethod.SERVICE_ACCOUNT_KEY,
        adcFilePath: String = "",
        forceRefresh: Boolean = false,
    ): String

    fun hasCredentials(
        serviceAccountKey: String = "",
        authMethod: GcpAuthMethod = GcpAuthMethod.SERVICE_ACCOUNT_KEY,
        adcFilePath: String = "",
    ): Boolean

    fun extractProjectId(serviceAccountKey: String): String?

    fun clearCache()
}

@Serializable
internal data class GcpTokenResponse(
    val access_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val error: String? = null,
    val error_description: String? = null,
)

private val sharedTokenHttpClient: HttpClient by lazy {
    createPlatformHttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}

class DefaultGcpTokenProvider(
    private val httpClient: HttpClient = sharedTokenHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : GcpTokenProvider {

    companion object {
        val Shared: DefaultGcpTokenProvider by lazy { DefaultGcpTokenProvider() }

        fun hasCredentials(
            serviceAccountKey: String = "",
            authMethod: GcpAuthMethod = GcpAuthMethod.SERVICE_ACCOUNT_KEY,
            adcFilePath: String = "",
        ): Boolean = Shared.hasCredentials(serviceAccountKey, authMethod, adcFilePath)

        fun extractProjectId(serviceAccountKey: String): String? =
            Shared.extractProjectId(serviceAccountKey)
    }

    private data class CachedToken(
        val accessToken: String,
        val validUntilMillis: Long,
    )

    private val mutex = Mutex()
    private val tokenCache = mutableMapOf<String, CachedToken>()

    override fun clearCache() {
        tokenCache.clear()
    }

    override fun hasCredentials(
        serviceAccountKey: String,
        authMethod: GcpAuthMethod,
        adcFilePath: String,
    ): Boolean {
        return when (authMethod) {
            GcpAuthMethod.SERVICE_ACCOUNT_KEY -> serviceAccountKey.isNotBlank()
            GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS -> resolveAdcFile(adcFilePath) != null
        }
    }

    override fun extractProjectId(serviceAccountKey: String): String? {
        if (serviceAccountKey.isBlank()) return null
        return try {
            val jsonText = resolveJsonContent(serviceAccountKey)
            val element = json.parseToJsonElement(jsonText)
            if (element is JsonObject) {
                element["project_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getAccessToken(
        serviceAccountKey: String,
        authMethod: GcpAuthMethod,
        adcFilePath: String,
        forceRefresh: Boolean,
    ): String {
        val cacheKey = buildCacheKey(serviceAccountKey, authMethod, adcFilePath)

        if (!forceRefresh) {
            mutex.withLock {
                val cached = tokenCache[cacheKey]
                if (cached != null && timeProvider() < cached.validUntilMillis) {
                    return cached.accessToken
                }
            }
        }

        return mutex.withLock {
            if (!forceRefresh) {
                val cached = tokenCache[cacheKey]
                if (cached != null && timeProvider() < cached.validUntilMillis) {
                    return cached.accessToken
                }
            }

            val tokenResult = fetchNewToken(serviceAccountKey, authMethod, adcFilePath)

            // Cache token for ~55 minutes (or 5 minutes before expiration)
            val expiresInSec = tokenResult.expiresIn.coerceAtLeast(300L)
            val bufferSec = 300L // 5 minutes before expiration auto-refresh
            val cacheDurationSec = (expiresInSec - bufferSec).coerceAtLeast(60L).coerceAtMost(55L * 60L)
            val validUntil = timeProvider() + (cacheDurationSec * 1000L)

            tokenCache[cacheKey] = CachedToken(
                accessToken = tokenResult.accessToken,
                validUntilMillis = validUntil,
            )

            tokenResult.accessToken
        }
    }

    private data class TokenFetchResult(
        val accessToken: String,
        val expiresIn: Long,
    )

    private suspend fun fetchNewToken(
        serviceAccountKey: String,
        authMethod: GcpAuthMethod,
        adcFilePath: String,
    ): TokenFetchResult {
        return when (authMethod) {
            GcpAuthMethod.SERVICE_ACCOUNT_KEY -> {
                require(serviceAccountKey.isNotBlank()) {
                    "GCP Service Account Key must not be blank"
                }
                val jsonContent = resolveJsonContent(serviceAccountKey)
                fetchTokenFromJson(jsonContent)
            }
            GcpAuthMethod.APPLICATION_DEFAULT_CREDENTIALS -> {
                val adcFile = resolveAdcFile(adcFilePath)
                    ?: throw IllegalStateException(
                        "Google Cloud Application Default Credentials not found. Run 'gcloud auth application-default login' or set GOOGLE_APPLICATION_CREDENTIALS."
                    )
                fetchTokenFromJson(adcFile.readText())
            }
        }
    }

    private suspend fun fetchTokenFromJson(jsonContent: String): TokenFetchResult {
        val root = try {
            json.parseToJsonElement(jsonContent) as? JsonObject
                ?: throw IllegalArgumentException("GCP credentials JSON must be a valid JSON object")
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse GCP credentials JSON: ${e.message}", e)
        }

        val type = root["type"]?.jsonPrimitive?.content.orEmpty()
        return when (type) {
            "service_account" -> fetchTokenFromServiceAccount(root)
            "authorized_user" -> fetchTokenFromAuthorizedUser(root)
            else -> {
                // If type is not specified or unknown, inspect fields to deduce type
                if (root.containsKey("private_key") && root.containsKey("client_email")) {
                    fetchTokenFromServiceAccount(root)
                } else if (root.containsKey("refresh_token") && root.containsKey("client_id")) {
                    fetchTokenFromAuthorizedUser(root)
                } else {
                    throw IllegalStateException("Unsupported GCP credential type '$type'. Supported types are 'service_account' and 'authorized_user'.")
                }
            }
        }
    }

    private suspend fun fetchTokenFromServiceAccount(root: JsonObject): TokenFetchResult {
        val clientEmail = root["client_email"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Service account JSON missing 'client_email'")
        val privateKeyPem = root["private_key"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Service account JSON missing 'private_key'")
        val tokenUri = root["token_uri"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "https://oauth2.googleapis.com/token"

        val nowEpochSec = timeProvider() / 1000L
        val signedJwt = buildAndSignJwt(
            clientEmail = clientEmail,
            privateKeyPem = privateKeyPem,
            tokenUri = tokenUri,
            nowEpochSec = nowEpochSec,
        )

        val response = httpClient.submitForm(
            url = tokenUri,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", signedJwt)
            },
        )

        val rawBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GCP OAuth token request failed [HTTP ${response.status.value}]: $rawBody")
        }

        val tokenResp = json.decodeFromString(GcpTokenResponse.serializer(), rawBody)
        val token = tokenResp.access_token
            ?: throw IllegalStateException("GCP OAuth token response did not contain access_token: $rawBody")
        val expiresIn = tokenResp.expires_in ?: 3600L

        return TokenFetchResult(token, expiresIn)
    }

    private suspend fun fetchTokenFromAuthorizedUser(root: JsonObject): TokenFetchResult {
        val clientId = root["client_id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Application default credentials missing 'client_id'")
        val clientSecret = root["client_secret"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Application default credentials missing 'client_secret'")
        val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Application default credentials missing 'refresh_token'")

        val tokenUri = "https://oauth2.googleapis.com/token"
        val response = httpClient.submitForm(
            url = tokenUri,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("refresh_token", refreshToken)
                append("grant_type", "refresh_token")
            },
        )

        val rawBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GCP ADC refresh token exchange failed [HTTP ${response.status.value}]: $rawBody")
        }

        val tokenResp = json.decodeFromString(GcpTokenResponse.serializer(), rawBody)
        val token = tokenResp.access_token
            ?: throw IllegalStateException("GCP OAuth token response did not contain access_token: $rawBody")
        val expiresIn = tokenResp.expires_in ?: 3600L

        return TokenFetchResult(token, expiresIn)
    }

    private fun buildAndSignJwt(
        clientEmail: String,
        privateKeyPem: String,
        tokenUri: String,
        nowEpochSec: Long,
    ): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val payload = """{"iss":"$clientEmail","scope":"https://www.googleapis.com/auth/cloud-platform","aud":"$tokenUri","exp":${nowEpochSec + 3600},"iat":$nowEpochSec}"""

        val headerEncoded = base64UrlEncode(header.encodeToByteArray())
        val payloadEncoded = base64UrlEncode(payload.encodeToByteArray())
        val signingInput = "$headerEncoded.$payloadEncoded"

        val privateKey = parsePkcs8PrivateKey(privateKeyPem)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.encodeToByteArray())
        val signatureBytes = signature.sign()
        val signatureEncoded = base64UrlEncode(signatureBytes)

        return "$signingInput.$signatureEncoded"
    }

    private fun parsePkcs8PrivateKey(pem: String): PrivateKey {
        val cleanPem = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()

        val keyBytes = Base64.getDecoder().decode(cleanPem)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun resolveJsonContent(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val file = File(expandPath(trimmed))
        if (file.exists() && file.isFile) {
            return file.readText()
        }
        return trimmed
    }

    private fun resolveAdcFile(customPath: String): File? {
        if (customPath.isNotBlank()) {
            val file = File(expandPath(customPath))
            if (file.exists() && file.isFile) return file
        }

        val envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        if (!envPath.isNullOrBlank()) {
            val file = File(expandPath(envPath))
            if (file.exists() && file.isFile) return file
        }

        val isWindows = System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true
        val defaultPath = if (isWindows) {
            val appData = System.getenv("APPDATA") ?: "${System.getProperty("user.home")}\\AppData\\Roaming"
            "$appData\\gcloud\\application_default_credentials.json"
        } else {
            "${System.getProperty("user.home")}/.config/gcloud/application_default_credentials.json"
        }

        val file = File(defaultPath)
        return if (file.exists() && file.isFile) file else null
    }

    private fun expandPath(path: String): String {
        val trimmed = path.trim()
        val userHome = System.getProperty("user.home") ?: ""
        return when {
            trimmed.startsWith("~" + File.separator) -> userHome + trimmed.removePrefix("~")
            trimmed == "~" -> userHome
            trimmed.startsWith("~/") -> "$userHome/${trimmed.removePrefix("~/")}"
            trimmed.startsWith("~\\") -> "$userHome\\${trimmed.removePrefix("~\\")}"
            else -> trimmed
        }
    }

    private fun buildCacheKey(
        serviceAccountKey: String,
        authMethod: GcpAuthMethod,
        adcFilePath: String,
    ): String {
        return "$authMethod:${serviceAccountKey.hashCode()}:${adcFilePath.trim()}"
    }
}
