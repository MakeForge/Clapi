package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Author(
    val name: String? = null,
    val email: String? = null,
    val url: String? = null,
)

@Serializable
data class CollectionInfo(
    val name: String,
    val summary: String? = null,
    val version: String? = null,
    val authors: List<Author>? = null,
)

@Serializable
data class ProxyAuthConfig(
    val disabled: Boolean? = null,
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class ProxyConnectionConfig(
    val protocol: String? = null,
    val hostname: String? = null,
    val port: Int? = null,
    val auth: ProxyAuthConfig? = null,
    val bypassProxy: String? = null,
)

@Serializable
data class ProxyConfig(
    val disabled: Boolean? = null,
    val inherit: Boolean? = null,
    val config: ProxyConnectionConfig? = null,
)

@Serializable
data class ProtoFile(
    val type: String = "file",
    val path: String,
)

@Serializable
data class ProtoFileImportPath(
    val path: String,
    val disabled: Boolean? = null,
)

@Serializable
data class CollectionProtobufConfig(
    val protoFiles: List<ProtoFile> = emptyList(),
    val importPaths: List<ProtoFileImportPath> = emptyList(),
)

@Serializable
data class CollectionConfig(
    val environments: List<Environment> = emptyList(),
    val protobuf: CollectionProtobufConfig? = null,
    val proxy: ProxyConfig? = null,
    val clientCertificates: List<ClientCertificate> = emptyList(),
)

@Serializable
data class RequestDefaults(
    val headers: List<HttpRequestHeader> = emptyList(),
    val auth: AuthConfig? = null,
    val variables: List<VariableEntry> = emptyList(),
    val scripts: List<Script> = emptyList(),
    val settings: JsonElement? = null,
)

@Serializable
data class OpenCollectionDocument(
    val opencollection: String = "1.0.0",
    val info: CollectionInfo,
    val config: CollectionConfig? = null,
    val items: List<OpenCollectionItem> = emptyList(),
    val request: RequestDefaults? = null,
    val docs: Documentation? = null,
    val bundled: Boolean? = true,
    val extensions: Map<String, JsonElement> = emptyMap(),
)
