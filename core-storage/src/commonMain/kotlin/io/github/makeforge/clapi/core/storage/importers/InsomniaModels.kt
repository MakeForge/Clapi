package io.github.makeforge.clapi.core.storage.importers

import kotlinx.serialization.json.JsonElement

data class InsomniaV5Document(
    val type: String,
    val schemaVersion: String = "5.1",
    val name: String? = null,
    val meta: InsomniaMeta? = null,
    val collection: List<InsomniaItem> = emptyList(),
    val environments: InsomniaEnvironments? = null,
    val spec: InsomniaSpec? = null,
    val scripts: InsomniaScripts? = null,
    val authentication: InsomniaAuth? = null,
    val headers: List<InsomniaHeader> = emptyList(),
)

data class InsomniaMeta(
    val id: String? = null,
    val description: String? = null,
    val created: Double? = null,
    val modified: Double? = null,
    val isPrivate: Boolean? = null,
    val sortKey: Double? = null,
)

data class InsomniaSpec(
    val contents: String? = null,
    val rawContentsElement: JsonElement? = null,
    val file: String? = null,
    val meta: InsomniaMeta? = null,
)

sealed interface InsomniaItem {
    val name: String
    val meta: InsomniaMeta?
}

data class InsomniaFolder(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val children: List<InsomniaItem> = emptyList(),
    val scripts: InsomniaScripts? = null,
    val authentication: InsomniaAuth? = null,
    val headers: List<InsomniaHeader> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val kvPairData: List<InsomniaKvPair> = emptyList(),
    val environmentType: String? = null,
) : InsomniaItem

data class InsomniaHttpRequest(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val url: String,
    val method: String,
    val headers: List<InsomniaHeader> = emptyList(),
    val parameters: List<InsomniaParameter> = emptyList(),
    val pathParameters: List<InsomniaParameter> = emptyList(),
    val body: InsomniaBody? = null,
    val authentication: InsomniaAuth? = null,
    val scripts: InsomniaScripts? = null,
    val settings: InsomniaRequestSettings? = null,
) : InsomniaItem

data class InsomniaGrpcRequest(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val url: String,
    val protoMethodName: String? = null,
    val protoFileId: String? = null,
    val reflectionApi: InsomniaReflectionApi? = null,
    val metadata: List<InsomniaHeader> = emptyList(),
    val body: InsomniaBody? = null,
) : InsomniaItem

data class InsomniaWebSocketRequest(
    override val name: String,
    override val meta: InsomniaMeta? = null,
    val url: String,
    val headers: List<InsomniaHeader> = emptyList(),
    val parameters: List<InsomniaParameter> = emptyList(),
    val pathParameters: List<InsomniaParameter> = emptyList(),
    val authentication: InsomniaAuth? = null,
    val body: InsomniaWebSocketBody? = null,
) : InsomniaItem

data class InsomniaReflectionApi(
    val enabled: Boolean = false,
    val url: String? = null,
    val apiKey: String? = null,
    val module: String? = null,
)

data class InsomniaRequestSettings(
    val renderRequestBody: Boolean? = null,
    val encodeUrl: Boolean? = null,
    val followRedirects: String? = null,
    val rebuildPath: Boolean? = null,
)

data class InsomniaHeader(
    val name: String,
    val value: String,
    val description: String? = null,
    val disabled: Boolean = false,
)

data class InsomniaParameter(
    val name: String,
    val value: String,
    val description: String? = null,
    val disabled: Boolean = false,
)

data class InsomniaBody(
    val mimeType: String? = null,
    val text: String? = null,
    val fileName: String? = null,
    val params: List<InsomniaBodyParam> = emptyList(),
)

data class InsomniaBodyParam(
    val name: String,
    val value: String = "",
    val description: String? = null,
    val disabled: Boolean = false,
    val type: String? = null,
    val fileName: String? = null,
)

data class InsomniaWebSocketBody(
    val name: String? = null,
    val value: String? = null,
    val mode: String? = null,
)

data class InsomniaScripts(
    val preRequest: String? = null,
    val afterResponse: String? = null,
)

data class InsomniaAuth(
    val type: String,
    val disabled: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val prefix: String? = null,
    val key: String? = null,
    val value: String? = null,
    val addTo: String? = null,
    // OAuth2
    val grantType: String? = null,
    val accessTokenUrl: String? = null,
    val authorizationUrl: String? = null,
    val redirectUrl: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val scope: String? = null,
    val credentialsInBody: Boolean? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    // AWS IAM
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
    val region: String? = null,
    val service: String? = null,
    // OAuth1
    val consumerKey: String? = null,
    val consumerSecret: String? = null,
    val tokenKey: String? = null,
    val tokenSecret: String? = null,
    val signatureMethod: String? = null,
    // NTLM
    val domain: String? = null,
)

data class InsomniaKvPair(
    val id: String? = null,
    val name: String,
    val value: String = "",
    val type: String = "str",
    val enabled: Boolean = true,
    val isConfidential: Boolean = false,
)

data class InsomniaEnvironments(
    val name: String = "Base Environment",
    val meta: InsomniaMeta? = null,
    val data: Map<String, String> = emptyMap(),
    val color: String? = null,
    val kvPairData: List<InsomniaKvPair> = emptyList(),
    val environmentType: String? = null,
    val confidentialKeys: List<String> = emptyList(),
    val subEnvironments: List<InsomniaSubEnvironment> = emptyList(),
)

data class InsomniaSubEnvironment(
    val name: String,
    val meta: InsomniaMeta? = null,
    val data: Map<String, String> = emptyMap(),
    val color: String? = null,
    val kvPairData: List<InsomniaKvPair> = emptyList(),
    val environmentType: String? = null,
    val confidentialKeys: List<String> = emptyList(),
)
