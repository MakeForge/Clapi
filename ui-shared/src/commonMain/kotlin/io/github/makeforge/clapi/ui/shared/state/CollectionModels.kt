package io.github.makeforge.clapi.ui.shared.state

import io.github.makeforge.clapi.core.model.opencollection.GraphQLRequestSettings
import io.github.makeforge.clapi.core.model.opencollection.GraphQLBodyVariant
import androidx.compose.runtime.mutableStateListOf
import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.FormEntryType
import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.McpConnectionConfig
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.makeforge.clapi.core.model.opencollection.OAuth2Flow
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageType
import io.github.makeforge.clapi.core.model.opencollection.WebSocketMessageVariant
import io.github.makeforge.clapi.core.model.storage.StorageExtension
import io.github.makeforge.clapi.core.model.storage.StorageType
import io.github.makeforge.clapi.core.storage.git.ProtoPathResolver
import io.github.makeforge.clapi.ui.shared.platform.currentTimeMillis

// ── Data holders ────────────────────────────────────────────────

data class ConsoleEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = currentTimeMillis(),
)

data class TestResultEntry(val name: String, val passed: Boolean, val message: String = "")

data class ResolvedFolderAuth(
    val folderName: String,
    val authType: AuthType,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val apiKey: String? = null,
    val apiValue: String? = null,
    val apiPlacement: String? = null,
    val oauth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val oauth2AccessTokenUrl: String? = null,
    val oauth2RefreshTokenUrl: String? = null,
    val oauth2AuthorizationUrl: String? = null,
    val oauth2RedirectUri: String? = null,
    val oauth2Scope: String? = null,
    val oauth2ClientId: String? = null,
    val oauth2ClientSecret: String? = null,
    val oauth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
    val oauth2Username: String? = null,
    val oauth2Password: String? = null,
)

data class HistoryItem(
    val requestId: String,
    val method: HttpMethodType,
    val name: String,
    val url: String,
    val timestamp: Long,
    val collectionId: String? = null,
    val folderPath: List<String> = emptyList(),
)

data class CollectionNode(
    val id: String,
    val name: String,
    val isFolder: Boolean = false,
    val method: HttpMethodType? = null,
    val url: String? = null,
    val children: MutableList<CollectionNode> = mutableStateListOf(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    // Request configuration populated from collection import
    val userHeaders: List<Pair<String, String>> = emptyList(),
    /** Authoritative ordered rows. Null means this is a legacy collection. */
    val queryEntries: List<KeyValueEntry>? = null,
    /** Authoritative ordered rows. Null means [userHeaders] is the fallback. */
    val headerEntries: List<KeyValueEntry>? = null,
    val bodyType: BodyType? = null,
    val bodyContent: String? = null,
    /** Per-type raw body contents keyed by [BodyType.name]. */
    val bodyContents: Map<String, String> = emptyMap(),
    /** Structured form-data rows (FORM_DATA body type). */
    val formDataEntries: List<FormDataEntryState> = emptyList(),
    /** Structured urlencoded rows (X_WWW_FORM_URLENCODED body type). */
    val urlencodedEntries: List<FormDataEntryState> = emptyList(),
    val authType: AuthType? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
    val authApiPlacement: String? = null,
    val authOAuth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS,
    val authOAuth2AccessTokenUrl: String? = null,
    val authOAuth2RefreshTokenUrl: String? = null,
    val authOAuth2AuthorizationUrl: String? = null,
    val authOAuth2RedirectUri: String? = null,
    val authOAuth2Scope: String? = null,
    val authOAuth2ClientId: String? = null,
    val authOAuth2ClientSecret: String? = null,
    val authOAuth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER,
    val authOAuth2Username: String? = null,
    val authOAuth2Password: String? = null,
    val requestRef: String? = null,
    val kind: RequestKind = RequestKind.HTTP,
    val mcpConfig: McpConnectionConfig? = null,
    val variableEntries: List<KeyValueEntry>? = null,
    val protoFilePath: String? = null,
    val protoSyncToGit: Boolean = protoFilePath?.let { ProtoPathResolver.isRelativeGitProtoPath(it) } ?: false,
    val grpcMethod: String? = null,
    val grpcMethodType: String? = "unary",
    val summary: String? = null,
    val version: String? = null,
    val protoFiles: List<ProtoFileItemState> = emptyList(),
    val importPaths: List<ProtoImportPathState> = emptyList(),
    val storageExtension: StorageExtension? = null,
    val webSocketVariants: List<WebSocketMessageVariant> = emptyList(),
    val webSocketTimeout: String? = null,
    val webSocketKeepAliveInterval: String? = null,
    val webSocketDocs: String? = null,
    val webSocketMessageFormat: WebSocketMessageType? = null,
    val graphQlVariants: List<GraphQLBodyVariant> = emptyList(),
    val graphQlSettings: GraphQLRequestSettings? = null,
    val graphQlDocs: String? = null,
) {
    val isCollection: Boolean get() = kind == RequestKind.COLLECTION
    val storageType: StorageType get() = storageExtension?.type ?: StorageType.LOCAL
    val gitRemoteUrl: String get() = storageExtension?.git?.remoteUrl.orEmpty()
    val gitBranch: String get() = storageExtension?.git?.branch ?: "main"
}

/**
 * Immutable snapshot of a form-data/urlencoded row — used in [CollectionNode]
 * and import/export serialization.
 */
data class FormDataEntryState(
    val key: String = "",
    val type: FormEntryType = FormEntryType.TEXT,
    val value: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val bytesBase64: String = "",
)
