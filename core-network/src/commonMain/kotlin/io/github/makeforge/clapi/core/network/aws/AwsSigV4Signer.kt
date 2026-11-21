package io.github.makeforge.clapi.core.network.aws

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AwsSigV4Signer {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun sign(
        service: String,
        region: String,
        method: String,
        path: String,
        headers: Map<String, String>,
        payload: String,
        accessKey: String,
        secretKey: String,
        sessionToken: String? = null,
        now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
    ): Map<String, String> {
        val amzDate = now.format(DATE_FORMAT)
        val dateStamp = now.format(DATE_ONLY_FORMAT)

        val mutHeaders = headers.toMutableMap()
        mutHeaders["x-amz-date"] = amzDate
        if (!sessionToken.isNullOrBlank()) {
            mutHeaders["x-amz-security-token"] = sessionToken
        }

        val canonicalHeaders = mutHeaders.entries
            .sortedBy { it.key.lowercase() }
            .joinToString("") { "${it.key.lowercase()}:${it.value.trim()}\n" }

        val signedHeaders = mutHeaders.keys
            .map { it.lowercase() }
            .sorted()
            .joinToString(";")

        val payloadHash = sha256Hex(payload)
        val canonicalRequest = "$method\n$path\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authHeader = "AWS4-HMAC-SHA256 " +
                "Credential=$accessKey/$credentialScope, " +
                "SignedHeaders=$signedHeaders, " +
                "Signature=$signature"

        mutHeaders["Authorization"] = authHeader
        return mutHeaders
    }

    private fun sha256Hex(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    private fun getSignatureKey(key: String, dateStamp: String, region: String, service: String): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }
}
