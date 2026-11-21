package io.github.crustack.clapi.ui.shared.persistence

import androidx.compose.runtime.toMutableStateList
import io.github.crustack.clapi.core.model.AuthType
import io.github.crustack.clapi.core.model.BodyType
import io.github.crustack.clapi.core.model.FormEntryType
import io.github.crustack.clapi.core.model.HttpMethodType
import io.github.crustack.clapi.core.model.KeyValueEntry
import io.github.crustack.clapi.core.model.McpConnectionConfig
import io.github.crustack.clapi.core.model.RequestKind
import io.github.crustack.clapi.core.model.storage.StorageExtension
import io.github.crustack.clapi.core.model.storage.StorageType
import io.github.crustack.clapi.core.model.storage.getStorageExtension
import io.github.crustack.clapi.core.model.storage.withStorageExtension
import io.github.crustack.clapi.core.storage.git.ProtoPathResolver
import io.github.crustack.clapi.core.model.opencollection.ApiKeyPlacement
import io.github.crustack.clapi.core.model.opencollection.AuthApiKey
import io.github.crustack.clapi.core.model.opencollection.AuthBasic
import io.github.crustack.clapi.core.model.opencollection.AuthBearer
import io.github.crustack.clapi.core.model.opencollection.AuthConfig
import io.github.crustack.clapi.core.model.opencollection.AuthInherit
import io.github.crustack.clapi.core.model.opencollection.AuthOAuth2
import io.github.crustack.clapi.core.model.opencollection.OAuth2ClientCredentials
import io.github.crustack.clapi.core.model.opencollection.OAuth2CredentialsPlacement
import io.github.crustack.clapi.core.model.opencollection.OAuth2Flow
import io.github.crustack.clapi.core.model.opencollection.OAuth2ResourceOwner
import io.github.crustack.clapi.core.model.opencollection.CollectionConfig
import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.CollectionProtobufConfig
import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.model.opencollection.ExternalSecrets
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretVariable
import io.github.crustack.clapi.core.model.opencollection.FolderInfo
import io.github.crustack.clapi.core.model.opencollection.FolderItem
import io.github.crustack.clapi.core.model.opencollection.FormParamEntry
import io.github.crustack.clapi.core.model.opencollection.FormUrlEncodedBody
import io.github.crustack.clapi.core.model.opencollection.GrpcMethodType
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.KeePassXcExternalSecret
import io.github.crustack.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.crustack.clapi.core.model.opencollection.HttpRequestBody
import io.github.crustack.clapi.core.model.opencollection.HttpRequestDetails
import io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader
import io.github.crustack.clapi.core.model.opencollection.HttpRequestInfo
import io.github.crustack.clapi.core.model.opencollection.HttpRequestItem
import io.github.crustack.clapi.core.model.opencollection.HttpRequestParam
import io.github.crustack.clapi.core.model.opencollection.HttpRequestParamType
import io.github.crustack.clapi.core.model.opencollection.HttpRequestRuntime
import io.github.crustack.clapi.core.model.opencollection.MultipartFormBody
import io.github.crustack.clapi.core.model.opencollection.MultipartPartEntry
import io.github.crustack.clapi.core.model.opencollection.MultipartPartType
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionItem
import io.github.crustack.clapi.core.model.opencollection.ProtoFile
import io.github.crustack.clapi.core.model.opencollection.ProtoFileImportPath
import io.github.crustack.clapi.core.model.opencollection.RawBody
import io.github.crustack.clapi.core.model.opencollection.RequestBodyType
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.RequestDefaults
import io.github.crustack.clapi.core.model.opencollection.Script
import io.github.crustack.clapi.core.model.opencollection.ScriptType
import io.github.crustack.clapi.core.model.opencollection.SecretVariable
import io.github.crustack.clapi.core.model.opencollection.VariableEntry
import io.github.crustack.clapi.core.model.opencollection.VariableValue
import io.github.crustack.clapi.ui.shared.platform.generateUuid
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.CollectionNode
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.FormDataEntryState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.ProtoFileItemState
import io.github.crustack.clapi.ui.shared.state.ProtoImportPathState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun collectClapiMetadata(
    node: CollectionNode,
    out: MutableMap<String, JsonObject>,
    path: String = "",
    collectionDir: String? = null,
) {
    if (node.isFolder) {
        val folderMeta = mutableMapOf<String, JsonElement>()
        if (node.authApiPlacement != null) {
            folderMeta["authApiPlacement"] = JsonPrimitive(node.authApiPlacement)
        }
        if (folderMeta.isNotEmpty()) {
            folderMeta["id"] = JsonPrimitive(node.id)
            folderMeta["name"] = JsonPrimitive(node.name)
            val key = if (path.isEmpty()) node.id else path
            out[key] = JsonObject(folderMeta)
        }
        node.children.forEachIndexed { index, child ->
            val childPath = if (path.isEmpty()) index.toString() else "$path/$index"
            collectClapiMetadata(child, out, childPath, collectionDir)
        }
    } else {
        val effectiveProtoFilePath = if (collectionDir != null && node.protoSyncToGit && !node.protoFilePath.isNullOrBlank()) {
            ProtoPathResolver.syncRequestProtoFile(node.protoFilePath, node.id, collectionDir)
        } else {
            node.protoFilePath
        }
        val meta = buildJsonObject {
            put("id", node.id)
            put("name", node.name)
            node.bodyType?.let { put("bodyType", it.name) }
            if (node.bodyContents.isNotEmpty()) {
                put("bodyContents", buildJsonObject {
                    node.bodyContents.forEach { (k, v) -> put(k, v) }
                })
            }
            if (node.queryEntries != null) {
                put("queryEntries", buildJsonArray {
                    node.queryEntries!!.forEach { entry ->
                        add(buildJsonObject {
                            put("key", entry.key)
                            put("value", entry.value)
                            put("enabled", entry.enabled)
                            put("secret", entry.secret)
                        })
                    }
                })
            }
            if (node.headerEntries != null) {
                put("headerEntries", buildJsonArray {
                    node.headerEntries!!.forEach { entry ->
                        add(buildJsonObject {
                            put("key", entry.key)
                            put("value", entry.value)
                            put("enabled", entry.enabled)
                            put("secret", entry.secret)
                        })
                    }
                })
            }
            node.authApiPlacement?.let { put("authApiPlacement", it) }
            put("kind", node.kind.name)
            node.requestRef?.let { put("requestRef", it) }
            effectiveProtoFilePath?.let { put("protoFilePath", it) }
            if (node.protoSyncToGit) {
                put("protoSyncToGit", true)
            }
            node.grpcMethod?.let { put("grpcMethod", it) }
            node.grpcMethodType?.let { put("grpcMethodType", it) }
            node.mcpConfig?.let { mcp ->
                put("mcpConfig", json.encodeToJsonElement(McpConnectionConfig.serializer(), mcp))
            }
        }
        out[node.name] = meta
        out[node.id] = meta
        if (path.isNotEmpty()) {
            out[path] = meta
        }
    }
}

/**
 * Produces a standalone OpenCollection document for single-collection export.
 * Per FR-017, single-collection exports omit `config` and environments.
 */
fun CollectionNode.toOpenCollectionDocument(collectionDir: String? = null): OpenCollectionDocument {
    val ext = mutableMapOf<String, JsonElement>()
    ext["id"] = JsonPrimitive(id)
    val clapiMetaMap = mutableMapOf<String, JsonObject>()
    collectClapiMetadata(this, clapiMetaMap, collectionDir = collectionDir)
    if (clapiMetaMap.isNotEmpty()) {
        ext["clapi"] = buildJsonObject {
            put("requests", buildJsonObject {
                clapiMetaMap.forEach { (k, v) -> put(k, v) }
            })
        }
    }
    val protobufConfig = if (protoFiles.isNotEmpty() || importPaths.isNotEmpty()) {
        CollectionProtobufConfig(
            protoFiles = protoFiles
                .filter { it.path.isNotBlank() }
                .map { pf ->
                    val effectivePath = if (collectionDir != null && pf.syncToGit) {
                        ProtoPathResolver.syncCollectionProtoFile(pf.path, collectionDir)
                    } else {
                        pf.path
                    }
                    ProtoFile(type = "file", path = effectivePath)
                },
            importPaths = importPaths
                .filter { it.path.isNotBlank() }
                .map { ip ->
                    val effectivePath = if (collectionDir != null && ip.syncToGit) {
                        ProtoPathResolver.syncCollectionImportPath(ip.path, collectionDir)
                    } else {
                        ip.path
                    }
                    ProtoFileImportPath(path = effectivePath, disabled = if (!ip.enabled) true else null)
                },
        )
    } else null
    val storage = storageExtension ?: StorageExtension(type = StorageType.LOCAL)
    val extWithStorage = ext.withStorageExtension(storage)
    return OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(
            name = name,
            summary = summary?.takeIf { it.isNotBlank() },
            version = version?.takeIf { it.isNotBlank() },
        ),
        config = if (protobufConfig != null) CollectionConfig(protobuf = protobufConfig) else null,
        request = toRequestDefaults(),
        bundled = true,
        items = children.map { it.toOpenCollectionItem(collectionDir = collectionDir) },
        extensions = extWithStorage,
    )
}

fun CollectionNode.toOpenCollectionDocumentForGitSync(collectionDir: String): OpenCollectionDocument =
    toOpenCollectionDocument(collectionDir = collectionDir)

/**
 * Maps a Clapi Collection [CollectionNode] to a top-level [FolderItem] of the global OpenCollection document.
 */
fun CollectionNode.toCollectionFolder(asCollection: Boolean = isCollection): FolderItem {
    val ext = mutableMapOf<String, JsonElement>("id" to JsonPrimitive(id))
    val clapiMap = mutableMapOf<String, JsonElement>()
    if (authType == AuthType.JWT) {
        clapiMap["authType"] = JsonPrimitive("jwt")
    }
    if (asCollection || isCollection) {
        clapiMap["isCollection"] = JsonPrimitive(true)
        val storage = storageExtension ?: StorageExtension(type = StorageType.LOCAL)
        ext.putAll(emptyMap<String, JsonElement>().withStorageExtension(storage))
    }
    summary?.takeIf { it.isNotBlank() }?.let { clapiMap["summary"] = JsonPrimitive(it) }
    version?.takeIf { it.isNotBlank() }?.let { clapiMap["version"] = JsonPrimitive(it) }
    if (protoFiles.isNotEmpty()) {
        clapiMap["protoFiles"] = buildJsonArray {
            protoFiles.forEach { add(buildJsonObject { put("path", it.path); put("syncToGit", it.syncToGit) }) }
        }
    }
    if (importPaths.isNotEmpty()) {
        clapiMap["importPaths"] = buildJsonArray {
            importPaths.forEach { add(buildJsonObject { put("path", it.path); put("enabled", it.enabled); put("syncToGit", it.syncToGit) }) }
        }
    }
    if (clapiMap.isNotEmpty()) {
        ext["clapi"] = JsonObject(clapiMap)
    }
    return FolderItem(
        info = FolderInfo(name = name, type = "folder"),
        items = children.map { it.toOpenCollectionItem() },
        request = toRequestDefaults(),
        extensions = ext,
    )
}

/**
 * Maps a standalone [OpenCollectionDocument] into a top-level [FolderItem] representing a Clapi Collection.
 * Preserves the document's `extensions["id"]` (FR-019) or assigns a new UUID when absent.
 */
fun OpenCollectionDocument.toCollectionFolder(nameOverride: String? = null): FolderItem {
    val folderId = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: generateUuid()
    return FolderItem(
        info = FolderInfo(name = nameOverride ?: info.name, type = "folder"),
        items = items,
        request = request,
        extensions = extensions + ("id" to JsonPrimitive(folderId)),
    )
}

/**
 * Builds the single global [OpenCollectionDocument] from [AppState] (FR-014, FR-015).
 * Every Clapi Collection becomes a top-level [FolderItem], and environments are stored in `config.environments`.
 */
fun AppState.toGlobalOpenCollectionDocument(): OpenCollectionDocument {
    val ext = mutableMapOf<String, JsonElement>()
    val clapiMetaMap = mutableMapOf<String, JsonObject>()
    collections.forEachIndexed { index, collection ->
        collectClapiMetadata(collection, clapiMetaMap, index.toString())
    }
    if (clapiMetaMap.isNotEmpty()) {
        ext["clapi"] = buildJsonObject {
            put("requests", buildJsonObject {
                clapiMetaMap.forEach { (k, v) -> put(k, v) }
            })
        }
    }
    val openCollectionEnvs = environments.map { it.toOpenCollectionEnvironment() }
    val protobufConfig = if (collectionProtoFiles.isNotEmpty() || collectionImportPaths.isNotEmpty()) {
        CollectionProtobufConfig(
            protoFiles = collectionProtoFiles
                .filter { it.path.isNotBlank() }
                .map { ProtoFile(type = "file", path = it.path) },
            importPaths = collectionImportPaths
                .filter { it.path.isNotBlank() }
                .map { ProtoFileImportPath(path = it.path, disabled = if (!it.enabled) true else null) },
        )
    } else null
    return OpenCollectionDocument(
        opencollection = "1.0.0",
        info = CollectionInfo(name = "Workspace"),
        config = CollectionConfig(
            environments = openCollectionEnvs,
            protobuf = protobufConfig,
        ),
        bundled = true,
        items = collections.map { it.toCollectionFolder(asCollection = true) },
        extensions = ext,
    )
}

fun Environment.toEnvState(): EnvState {
    val extSecrets = externalSecrets?.variables?.mapNotNull { v ->
        when (v) {
            is HashicorpVaultExternalSecret -> MutableExternalSecret(
                name = v.name,
                path = v.path,
                description = v.description?.content ?: "",
                enabled = v.disabled != true,
            )
            is KeePassXcExternalSecret -> MutableExternalSecret(
                name = v.name,
                description = v.description?.content ?: "",
                enabled = v.disabled != true,
                url = v.url,
                targetField = v.targetField,
                customFilters = v.customFilters,
            )
            is AwsSecretsManagerExternalSecret -> MutableExternalSecret(
                name = v.name,
                path = v.secretName,
                description = v.description?.content ?: "",
                enabled = v.disabled != true,
                targetField = v.targetField,
                customFilters = v.customFilters,
            )
        }
    } ?: emptyList()

    return EnvState(
        name = name,
        variables = variables.map { it.toMutableKeyValue() },
        color = color,
        description = description,
        clientCertificates = clientCertificates,
        extends = extends,
        dotEnvFilePath = dotEnvFilePath,
        externalSecretsProvider = externalSecrets?.type?.value ?: "hashicorp-vault-server",
        externalSecrets = extSecrets,
    )
}

fun EnvState.toOpenCollectionEnvironment(): Environment {
    val isKeePassXc = externalSecretsProvider.equals("keepassxc", ignoreCase = true)
    val isAws = externalSecretsProvider.equals("aws-secrets-manager", ignoreCase = true)
    val extSecretList: List<ExternalSecretVariable> = when {
        isKeePassXc -> {
            externalSecrets
                .filter { it.name.trim().isNotEmpty() && it.url.trim().isNotEmpty() }
                .map {
                    KeePassXcExternalSecret(
                        name = it.name.trim(),
                        url = it.url.trim(),
                        targetField = it.targetField.ifBlank { "password" }.trim(),
                        customFilters = it.customFilters.toMap(),
                        description = if (it.description.isNotBlank()) Description(content = it.description.trim()) else null,
                        disabled = if (!it.enabled) true else null,
                    )
                }
        }
        isAws -> {
            externalSecrets
                .filter { it.name.trim().isNotEmpty() && (it.path.trim().isNotEmpty() || it.url.trim().isNotEmpty()) }
                .map {
                    AwsSecretsManagerExternalSecret(
                        name = it.name.trim(),
                        secretName = it.path.trim().ifBlank { it.url.trim() },
                        targetField = it.targetField.trim(),
                        customFilters = it.customFilters.toMap(),
                        description = if (it.description.isNotBlank()) Description(content = it.description.trim()) else null,
                        disabled = if (!it.enabled) true else null,
                    )
                }
        }
        else -> {
            externalSecrets
                .filter { it.name.trim().isNotEmpty() && it.path.trim().isNotEmpty() }
                .map {
                    HashicorpVaultExternalSecret(
                        name = it.name.trim(),
                        path = it.path.trim(),
                        description = if (it.description.isNotBlank()) Description(content = it.description.trim()) else null,
                        disabled = if (!it.enabled) true else null,
                    )
                }
        }
    }

    val extSecrets = if (extSecretList.isNotEmpty()) {
        ExternalSecrets(
            type = externalSecretsProvider.ifBlank { "hashicorp-vault-server" },
            variables = extSecretList,
        )
    } else null

    return Environment(
        name = name,
        color = color,
        description = description,
        variables = variables
            .filter { it.key.trim().isNotEmpty() }
            .map { it.toVariableEntry() },
        clientCertificates = clientCertificates,
        extends = extends,
        dotEnvFilePath = dotEnvFilePath,
        externalSecrets = extSecrets,
    )
}

fun VariableEntry.toMutableKeyValue(): MutableKeyValue =
    when (this) {
        is RegularVariable -> MutableKeyValue(
            key = name,
            value = value?.asString ?: "",
            enabled = disabled != true,
            secret = false,
        )
        is SecretVariable -> MutableKeyValue(
            key = name,
            value = "",
            enabled = disabled != true,
            secret = true,
        )
    }

fun MutableKeyValue.toVariableEntry(): VariableEntry {
    val trimmedKey = key.trim()
    val disabledFlag = if (enabled) null else true
    return if (secret) {
        SecretVariable(
            name = trimmedKey,
            disabled = disabledFlag,
            secret = true,
        )
    } else {
        RegularVariable(
            name = trimmedKey,
            value = VariableValue.of(value),
            disabled = disabledFlag,
        )
    }
}

/**
 * Merges an imported OpenCollection [Environment] into this [EnvState] (FR-016).
 * Missing variables and fields are added; existing entries are never removed or overwritten.
 */
fun EnvState.mergeFrom(incoming: Environment) {
    if (color == null && incoming.color != null) {
        color = incoming.color
    }
    if (description == null && incoming.description != null) {
        description = incoming.description
    }
    if (extends == null && incoming.extends != null) {
        extends = incoming.extends
    }
    if (dotEnvFilePath == null && incoming.dotEnvFilePath != null) {
        dotEnvFilePath = incoming.dotEnvFilePath
    }
    if (incoming.clientCertificates.isNotEmpty()) {
        val existingDomains = clientCertificates.map { it.domain }.toSet()
        val missingCerts = incoming.clientCertificates.filter { it.domain !in existingDomains }
        if (missingCerts.isNotEmpty()) {
            clientCertificates = clientCertificates + missingCerts
        }
    }
    val existingKeys = variables.map { it.key }.toMutableSet()
    for (variable in incoming.variables) {
        if (variable.name.isNotBlank() && variable.name !in existingKeys) {
            variables.add(variable.toMutableKeyValue())
            existingKeys.add(variable.name)
        }
    }
    incoming.externalSecrets?.let { ext ->
        if (externalSecretsProvider.isBlank()) {
            externalSecretsProvider = ext.type.value
        }
        val existingSecretNames = externalSecrets.map { it.name }.toMutableSet()
        for (sec in ext.variables) {
            when (sec) {
                is HashicorpVaultExternalSecret -> {
                    if (sec.name.isNotBlank() && sec.name !in existingSecretNames) {
                        externalSecrets.add(
                            MutableExternalSecret(
                                name = sec.name,
                                path = sec.path,
                                description = sec.description?.content.orEmpty(),
                                enabled = sec.disabled != true,
                            )
                        )
                        existingSecretNames.add(sec.name)
                    }
                }
                is KeePassXcExternalSecret -> {
                    if (sec.name.isNotBlank() && sec.name !in existingSecretNames) {
                        externalSecrets.add(
                            MutableExternalSecret(
                                name = sec.name,
                                description = sec.description?.content.orEmpty(),
                                enabled = sec.disabled != true,
                                url = sec.url,
                                targetField = sec.targetField,
                                customFilters = sec.customFilters,
                            )
                        )
                        existingSecretNames.add(sec.name)
                    }
                }
                is AwsSecretsManagerExternalSecret -> {
                    if (sec.name.isNotBlank() && sec.name !in existingSecretNames) {
                        externalSecrets.add(
                            MutableExternalSecret(
                                name = sec.name,
                                path = sec.secretName,
                                description = sec.description?.content.orEmpty(),
                                enabled = sec.disabled != true,
                                targetField = sec.targetField,
                                customFilters = sec.customFilters,
                            )
                        )
                        existingSecretNames.add(sec.name)
                    }
                }
            }
        }
    }
}

internal fun CollectionNode.buildOAuth2AuthConfig(): AuthOAuth2 =
    AuthOAuth2(
        flow = authOAuth2Flow,
        accessTokenUrl = authOAuth2AccessTokenUrl?.ifBlank { null },
        refreshTokenUrl = authOAuth2RefreshTokenUrl?.ifBlank { null },
        authorizationUrl = authOAuth2AuthorizationUrl?.ifBlank { null },
        redirectUri = authOAuth2RedirectUri?.ifBlank { null },
        credentials = OAuth2ClientCredentials(
            clientId = authOAuth2ClientId?.ifBlank { null },
            clientSecret = authOAuth2ClientSecret?.ifBlank { null },
            placement = authOAuth2CredentialsPlacement,
        ),
        resourceOwner = if (authOAuth2Flow == OAuth2Flow.RESOURCE_OWNER_PASSWORD_CREDENTIALS) {
            OAuth2ResourceOwner(
                username = authOAuth2Username?.ifBlank { null },
                password = authOAuth2Password?.ifBlank { null },
            )
        } else null,
        scope = authOAuth2Scope?.ifBlank { null },
    )

internal fun CollectionNode.toRequestDefaults(): RequestDefaults? {
    val effectiveHeaders = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
        HttpRequestHeader(
            name = it.key,
            value = it.value,
            disabled = if (it.enabled) null else true
        )
    }
    val authConfig: AuthConfig? = when (authType) {
        AuthType.BASIC -> AuthBasic(username = authUsername ?: "", password = authPassword ?: "")
        AuthType.BEARER, AuthType.JWT -> AuthBearer(token = authToken ?: "")
        AuthType.API_KEY -> AuthApiKey(
            key = authApiKey ?: "",
            value = authApiValue ?: "",
            placement = authApiPlacement ?: "header"
        )
        AuthType.OAUTH2 -> buildOAuth2AuthConfig()
        AuthType.INHERIT -> AuthInherit
        else -> null
    }
    val scripts = mutableListOf<Script>()
    if (!preRequestScript.isNullOrBlank()) {
        scripts.add(Script(type = "before-request", code = preRequestScript))
    }
    if (!testScript.isNullOrBlank()) {
        scripts.add(Script(type = "after-response", code = testScript))
    }
    val vars = (variableEntries ?: emptyList()).filter { it.key.isNotBlank() }.map {
        RegularVariable(
            name = it.key,
            value = VariableValue.of(it.value),
            disabled = if (it.enabled) null else true,
        )
    }
    return if (effectiveHeaders.isNotEmpty() || authConfig != null || scripts.isNotEmpty() || vars.isNotEmpty()) {
        RequestDefaults(
            headers = effectiveHeaders,
            auth = authConfig,
            variables = vars,
            scripts = scripts,
        )
    } else {
        null
    }
}

fun CollectionNode.toOpenCollectionItem(collectionDir: String? = null): OpenCollectionItem {
    return if (isFolder) {
        val ext = mutableMapOf<String, JsonElement>()
        ext["id"] = JsonPrimitive(id)
        val clapiFields = mutableMapOf<String, JsonElement>()
        if (authType == AuthType.JWT) {
            clapiFields["authType"] = JsonPrimitive("jwt")
        }
        if (clapiFields.isNotEmpty()) {
            ext["clapi"] = JsonObject(clapiFields)
        }
        FolderItem(
            info = FolderInfo(name = name, type = "folder"),
            items = children.map { it.toOpenCollectionItem(collectionDir = collectionDir) },
            request = toRequestDefaults(),
            extensions = ext
        )
    } else {
        val ext = mutableMapOf<String, JsonElement>()
        if (requestRef != null) {
            ext["requestRef"] = JsonPrimitive(requestRef)
        }
        if (authType == AuthType.JWT) {
            ext["clapi"] = buildJsonObject { put("authType", "jwt") }
        }
        if (kind != RequestKind.HTTP) {
            ext["kind"] = JsonPrimitive(kind.name)
        }

        val effectiveHeaders = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
            HttpRequestHeader(
                name = it.key,
                value = it.value,
                disabled = if (it.enabled) null else true
            )
        }

        val effectiveParams = (queryEntries ?: emptyList()).map {
            HttpRequestParam(
                name = it.key,
                value = it.value,
                type = "query",
                disabled = if (it.enabled) null else true
            )
        }

        val body: HttpRequestBody? = when (bodyType) {
            BodyType.FORM_DATA -> MultipartFormBody(
                data = formDataEntries.map {
                    MultipartPartEntry(
                        name = it.key,
                        type = if (it.type == FormEntryType.FILE) MultipartPartType.FILE else MultipartPartType.TEXT,
                        value = JsonPrimitive(it.value),
                        description = if (it.description.isNotBlank()) Description(it.description) else null,
                        disabled = if (it.enabled) null else true
                    )
                }
            )
            BodyType.X_WWW_FORM_URLENCODED -> FormUrlEncodedBody(
                data = urlencodedEntries.map {
                    FormParamEntry(
                        name = it.key,
                        value = it.value,
                        description = if (it.description.isNotBlank()) Description(it.description) else null,
                        disabled = if (it.enabled) null else true
                    )
                }
            )
            BodyType.JSON, BodyType.XML, BodyType.HTML, BodyType.JAVASCRIPT, BodyType.RAW_TEXT, BodyType.GRAPHQL -> {
                val rawType = when (bodyType) {
                    BodyType.JSON, BodyType.GRAPHQL -> "json"
                    BodyType.XML -> "xml"
                    else -> "text"
                }
                RawBody(type = rawType, data = bodyContent ?: "")
            }
            else -> if (!bodyContent.isNullOrBlank()) RawBody(type = "text", data = bodyContent) else null
        }

        val auth = when (authType) {
            AuthType.BASIC -> AuthBasic(username = authUsername ?: "", password = authPassword ?: "")
            AuthType.BEARER -> AuthBearer(token = authToken ?: "")
            AuthType.JWT -> AuthBearer(token = authToken ?: "")
            AuthType.API_KEY -> AuthApiKey(
                key = authApiKey ?: "",
                value = authApiValue ?: "",
                placement = authApiPlacement ?: "header"
            )
            AuthType.OAUTH2 -> buildOAuth2AuthConfig()
            AuthType.INHERIT -> AuthInherit
            else -> null
        }

        val scripts = mutableListOf<Script>()
        if (!preRequestScript.isNullOrBlank()) {
            scripts.add(Script(type = "before-request", code = preRequestScript))
        }
        if (!testScript.isNullOrBlank()) {
            scripts.add(Script(type = "after-response", code = testScript))
        }

        if (kind == RequestKind.GRPC) {
            val effectiveMetadata = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
                io.github.crustack.clapi.core.model.opencollection.GrpcMetadata(
                    name = it.key,
                    value = it.value,
                    disabled = if (it.enabled) null else true,
                )
            }
            val grpcRuntime = if (scripts.isNotEmpty()) {
                io.github.crustack.clapi.core.model.opencollection.GrpcRequestRuntime(scripts = scripts)
            } else {
                null
            }
            val effectiveProtoFilePath = if (collectionDir != null && protoSyncToGit && !protoFilePath.isNullOrBlank()) {
                ProtoPathResolver.syncRequestProtoFile(protoFilePath, id, collectionDir)
            } else {
                protoFilePath
            }
            return io.github.crustack.clapi.core.model.opencollection.GrpcRequestItem(
                info = io.github.crustack.clapi.core.model.opencollection.GrpcRequestInfo(name = name),
                grpc = io.github.crustack.clapi.core.model.opencollection.GrpcRequestDetails(
                    url = url ?: "",
                    method = grpcMethod,
                    methodType = grpcMethodType?.let { GrpcMethodType.fromValue(it) } ?: GrpcMethodType.UNARY,
                    protoFilePath = effectiveProtoFilePath,
                    metadata = effectiveMetadata,
                    message = bodyContent ?: bodyContents[BodyType.JSON.name] ?: "",
                    auth = auth,
                ),
                runtime = grpcRuntime,
                extensions = ext,
            )
        }

        if (kind == RequestKind.WEBSOCKET) {
            val effectiveHeaders = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
                io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader(
                    name = it.key,
                    value = it.value,
                    disabled = if (it.enabled) null else true,
                )
            }
            val wsRuntime = if (scripts.isNotEmpty()) {
                io.github.crustack.clapi.core.model.opencollection.WebSocketRequestRuntime(scripts = scripts)
            } else {
                null
            }
            val messagePayload = if (webSocketVariants.isNotEmpty()) {
                io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Variants(webSocketVariants)
            } else if (!bodyContent.isNullOrBlank()) {
                io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Single(
                    io.github.crustack.clapi.core.model.opencollection.WebSocketMessage(
                        type = webSocketMessageFormat ?: io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType.TEXT,
                        data = bodyContent!!
                    )
                )
            } else null

            val wsSettings = if (webSocketTimeout != null || webSocketKeepAliveInterval != null) {
                io.github.crustack.clapi.core.model.opencollection.WebSocketRequestSettings(
                    timeout = webSocketTimeout?.let { str ->
                        str.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(str)
                    },
                    keepAliveInterval = webSocketKeepAliveInterval?.let { str ->
                        str.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(str)
                    },
                )
            } else null

            return io.github.crustack.clapi.core.model.opencollection.WebSocketRequestItem(
                info = io.github.crustack.clapi.core.model.opencollection.WebSocketRequestInfo(name = name),
                websocket = io.github.crustack.clapi.core.model.opencollection.WebSocketRequestDetails(
                    url = url ?: "ws://",
                    headers = effectiveHeaders,
                    message = messagePayload,
                    auth = auth,
                ),
                runtime = wsRuntime,
                settings = wsSettings,
                docs = webSocketDocs,
                extensions = ext,
            )
        }

        if (kind == RequestKind.GRAPHQL) {
            val effectiveHeaders = (headerEntries ?: userHeaders.map { KeyValueEntry(it.first, it.second) }).map {
                io.github.crustack.clapi.core.model.opencollection.HttpRequestHeader(
                    name = it.key,
                    value = it.value,
                    disabled = if (it.enabled) null else true,
                )
            }
            val effectiveParams = queryEntries?.map {
                io.github.crustack.clapi.core.model.opencollection.HttpRequestParam(
                    name = it.key,
                    value = it.value,
                    disabled = if (it.enabled) null else true,
                )
            } ?: emptyList()
            val gqlRuntime = if (scripts.isNotEmpty()) {
                io.github.crustack.clapi.core.model.opencollection.GraphQLRequestRuntime(scripts = scripts)
            } else {
                null
            }
            val bodyPayload = if (graphQlVariants.isNotEmpty()) {
                io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload.Variants(graphQlVariants)
            } else if (!bodyContent.isNullOrBlank()) {
                io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload.Single(
                    io.github.crustack.clapi.core.model.opencollection.GraphQLBody(
                        query = bodyContent!!,
                    )
                )
            } else null

            return io.github.crustack.clapi.core.model.opencollection.GraphQLRequestItem(
                info = io.github.crustack.clapi.core.model.opencollection.GraphQLRequestInfo(name = name),
                graphql = io.github.crustack.clapi.core.model.opencollection.GraphQLRequestDetails(
                    method = method?.name ?: "POST",
                    url = url ?: "https://",
                    headers = effectiveHeaders,
                    params = effectiveParams,
                    body = bodyPayload,
                    auth = auth,
                ),
                runtime = gqlRuntime,
                settings = graphQlSettings,
                docs = graphQlDocs,
                extensions = ext,
            )
        }

        val runtime = if (scripts.isNotEmpty()) HttpRequestRuntime(scripts = scripts) else null

        HttpRequestItem(
            info = HttpRequestInfo(name = name, type = "http"),
            http = HttpRequestDetails(
                method = method?.name ?: "GET",
                url = url ?: "",
                headers = effectiveHeaders,
                params = effectiveParams,
                body = body,
                auth = auth
            ),
            runtime = runtime,
            extensions = ext
        )
    }
}

fun OpenCollectionDocument.toCollectionNode(
    nameOverride: String? = null,
    preserveIds: Boolean = true,
    existingIds: Set<String> = emptySet(),
): CollectionNode {
    val clapiRequests = (extensions["clapi"] as? JsonObject)?.get("requests")?.jsonObject
    val folder = toCollectionFolder(nameOverride = nameOverride)
    val baseNode = folder.toCollectionNode(
        preserveIds = preserveIds,
        clapiRequests = clapiRequests,
        path = "",
        existingIds = existingIds,
    )
    val protoFilesState = config?.protobuf?.protoFiles.orEmpty()
        .map { ProtoFileItemState(path = it.path) }
    val importPathsState = config?.protobuf?.importPaths.orEmpty()
        .map { ProtoImportPathState(path = it.path, enabled = it.disabled != true) }
    val storageExt = extensions.getStorageExtension() ?: StorageExtension(type = StorageType.LOCAL)

    return baseNode.copy(
        kind = RequestKind.COLLECTION,
        summary = info.summary,
        version = info.version,
        protoFiles = protoFilesState,
        importPaths = importPathsState,
        storageExtension = storageExt,
    )
}

fun OpenCollectionItem.toCollectionNode(
    preserveIds: Boolean = true,
    clapiRequests: JsonObject? = null,
    path: String = "",
    existingIds: Set<String> = emptySet(),
): CollectionNode {
    return when (this) {
        is FolderItem -> {
            val candidateId = (extensions["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            val folderId = if (preserveIds && candidateId != null && candidateId !in existingIds) {
                candidateId
            } else {
                generateUuid()
            }
            val updatedExistingIds = existingIds + folderId

            val req = request
            val headers = req?.headers?.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = req?.headers?.map { it.name to it.value } ?: emptyList()

            var authType: AuthType? = null
            var authUsername: String? = null
            var authPassword: String? = null
            var authToken: String? = null
            var authApiKey: String? = null
            var authApiValue: String? = null
            var authApiPlacement: String? = null
            var authOAuth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
            var authOAuth2AccessTokenUrl: String? = null
            var authOAuth2RefreshTokenUrl: String? = null
            var authOAuth2AuthorizationUrl: String? = null
            var authOAuth2RedirectUri: String? = null
            var authOAuth2Scope: String? = null
            var authOAuth2ClientId: String? = null
            var authOAuth2ClientSecret: String? = null
            var authOAuth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
            var authOAuth2Username: String? = null
            var authOAuth2Password: String? = null

            val clapiExt = extensions["clapi"] as? JsonObject
            val isJwt = (clapiExt?.get("authType") as? JsonPrimitive)?.content == "jwt"

            when (val a = req?.auth) {
                is AuthBasic -> {
                    authType = AuthType.BASIC
                    authUsername = a.username
                    authPassword = a.password
                }
                is AuthBearer -> {
                    authType = if (isJwt) AuthType.JWT else AuthType.BEARER
                    authToken = a.token
                }
                is AuthApiKey -> {
                    authType = AuthType.API_KEY
                    authApiKey = a.key
                    authApiValue = a.value
                    authApiPlacement = a.placement.value
                }
                is AuthOAuth2 -> {
                    authType = AuthType.OAUTH2
                    authOAuth2Flow = a.flow
                    authOAuth2AccessTokenUrl = a.accessTokenUrl
                    authOAuth2RefreshTokenUrl = a.refreshTokenUrl
                    authOAuth2AuthorizationUrl = a.authorizationUrl
                    authOAuth2RedirectUri = a.redirectUri
                    authOAuth2Scope = a.scope
                    authOAuth2ClientId = a.credentials?.clientId
                    authOAuth2ClientSecret = a.credentials?.clientSecret
                    authOAuth2CredentialsPlacement = a.credentials?.placement ?: OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
                    authOAuth2Username = a.resourceOwner?.username
                    authOAuth2Password = a.resourceOwner?.password
                }
                is AuthInherit -> {
                    authType = AuthType.INHERIT
                }
                else -> {
                    authType = null
                }
            }

            val preScript = req?.scripts?.firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
            val postScript = req?.scripts?.firstOrNull { it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.TESTS }?.code

            val meta = clapiExt
                ?: (if (path.isNotEmpty()) clapiRequests?.get(path) as? JsonObject else null)
                ?: clapiRequests?.get(info.name) as? JsonObject
                ?: clapiRequests?.get(folderId) as? JsonObject

            if (meta != null) {
                meta["authApiPlacement"]?.jsonPrimitive?.contentOrNull?.let {
                    authApiPlacement = it
                }
            }

            val isCol = (meta?.get("isCollection") as? JsonPrimitive)?.booleanOrNull == true
            val colSummary = (meta?.get("summary") as? JsonPrimitive)?.contentOrNull
            val colVersion = (meta?.get("version") as? JsonPrimitive)?.contentOrNull
            val colProtoFiles = (meta?.get("protoFiles") as? JsonArray)?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val p = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val syncToGit = obj["syncToGit"]?.jsonPrimitive?.booleanOrNull ?: ProtoPathResolver.isRelativeGitProtoPath(p)
                ProtoFileItemState(p, syncToGit)
            }.orEmpty()
            val colImportPaths = (meta?.get("importPaths") as? JsonArray)?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val p = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                val syncToGit = obj["syncToGit"]?.jsonPrimitive?.booleanOrNull ?: ProtoPathResolver.isRelativeGitProtoPath(p)
                ProtoImportPathState(p, enabled, syncToGit)
            }.orEmpty()
            val colStorageExt = if (isCol) {
                extensions.getStorageExtension()
                    ?: (meta?.get("storage")?.let {
                        (emptyMap<String, JsonElement>() + ("storage" to it)).getStorageExtension()
                    }) ?: StorageExtension(type = StorageType.LOCAL)
            } else null

            val folderVariables = req?.variables?.map { v ->
                when (v) {
                    is RegularVariable -> KeyValueEntry(
                        key = v.name,
                        value = v.value?.asString ?: "",
                        enabled = v.disabled != true,
                        secret = false,
                    )
                    is SecretVariable -> KeyValueEntry(
                        key = v.name,
                        value = "",
                        enabled = v.disabled != true,
                        secret = true,
                    )
                }
            }

            CollectionNode(
                id = folderId,
                name = info.name,
                isFolder = true,
                kind = if (isCol) RequestKind.COLLECTION else RequestKind.FOLDER,
                summary = colSummary,
                version = colVersion,
                protoFiles = colProtoFiles,
                importPaths = colImportPaths,
                storageExtension = colStorageExt,
                headerEntries = headers,
                userHeaders = userHeaders,
                variableEntries = folderVariables,
                queryEntries = null,
                authType = authType,
                authUsername = authUsername,
                authPassword = authPassword,
                authToken = authToken,
                authApiKey = authApiKey,
                authApiValue = authApiValue,
                authApiPlacement = authApiPlacement,
                authOAuth2Flow = authOAuth2Flow,
                authOAuth2AccessTokenUrl = authOAuth2AccessTokenUrl,
                authOAuth2RefreshTokenUrl = authOAuth2RefreshTokenUrl,
                authOAuth2AuthorizationUrl = authOAuth2AuthorizationUrl,
                authOAuth2RedirectUri = authOAuth2RedirectUri,
                authOAuth2Scope = authOAuth2Scope,
                authOAuth2ClientId = authOAuth2ClientId,
                authOAuth2ClientSecret = authOAuth2ClientSecret,
                authOAuth2CredentialsPlacement = authOAuth2CredentialsPlacement,
                authOAuth2Username = authOAuth2Username,
                authOAuth2Password = authOAuth2Password,
                preRequestScript = preScript,
                testScript = postScript,
                children = items.mapIndexed { index, child ->
                    val childPath = if (path.isEmpty()) index.toString() else "$path/$index"
                    child.toCollectionNode(
                        preserveIds = preserveIds,
                        clapiRequests = clapiRequests,
                        path = childPath,
                        existingIds = updatedExistingIds,
                    )
                }.toMutableStateList()
            )
        }
        is HttpRequestItem -> {
            val reqId = generateUuid()
            var reqRef = (extensions["requestRef"] as? JsonPrimitive)?.content

            val headers = http.headers.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = http.headers.map { it.name to it.value }
            var queryParams: List<KeyValueEntry>? = http.params.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            var headerEntries: List<KeyValueEntry>? = headers

            var bodyType: BodyType = BodyType.NONE
            var bodyContent: String? = null
            var bodyContents: Map<String, String> = emptyMap()
            var formData = emptyList<FormDataEntryState>()
            var urlencoded = emptyList<FormDataEntryState>()

            when (val b = http.body) {
                is MultipartFormBody -> {
                    bodyType = BodyType.FORM_DATA
                    formData = b.data.map {
                        val strVal = (it.value as? JsonPrimitive)?.content ?: it.value.toString()
                        FormDataEntryState(
                            key = it.name,
                            type = if (it.type == MultipartPartType.FILE) FormEntryType.FILE else FormEntryType.TEXT,
                            value = strVal,
                            description = it.description?.content ?: "",
                            enabled = it.disabled != true
                        )
                    }
                }
                is FormUrlEncodedBody -> {
                    bodyType = BodyType.X_WWW_FORM_URLENCODED
                    urlencoded = b.data.map {
                        FormDataEntryState(
                            key = it.name,
                            value = it.value,
                            description = it.description?.content ?: "",
                            enabled = it.disabled != true
                        )
                    }
                }
                is RawBody -> {
                    bodyType = when (b.type) {
                        RequestBodyType.JSON -> BodyType.JSON
                        RequestBodyType.XML -> BodyType.XML
                        else -> BodyType.RAW_TEXT
                    }
                    bodyContent = b.data
                }
                else -> {
                    bodyType = BodyType.NONE
                }
            }

            var authType: AuthType = AuthType.NONE
            var authUsername: String? = null
            var authPassword: String? = null
            var authToken: String? = null
            var authApiKey: String? = null
            var authApiValue: String? = null
            var authApiPlacement: String? = null
            var authOAuth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
            var authOAuth2AccessTokenUrl: String? = null
            var authOAuth2RefreshTokenUrl: String? = null
            var authOAuth2AuthorizationUrl: String? = null
            var authOAuth2RedirectUri: String? = null
            var authOAuth2Scope: String? = null
            var authOAuth2ClientId: String? = null
            var authOAuth2ClientSecret: String? = null
            var authOAuth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
            var authOAuth2Username: String? = null
            var authOAuth2Password: String? = null
            var requestKind: RequestKind = if (http.url.startsWith("ws://", ignoreCase = true) || http.url.startsWith("wss://", ignoreCase = true)) {
                RequestKind.WEBSOCKET
            } else {
                RequestKind.HTTP
            }

            val clapiExt = extensions["clapi"] as? JsonObject
            val isJwt = (clapiExt?.get("authType") as? JsonPrimitive)?.content == "jwt"

            when (val a = http.auth) {
                is AuthBasic -> {
                    authType = AuthType.BASIC
                    authUsername = a.username
                    authPassword = a.password
                }
                is AuthBearer -> {
                    authType = if (isJwt) AuthType.JWT else AuthType.BEARER
                    authToken = a.token
                }
                is AuthApiKey -> {
                    authType = AuthType.API_KEY
                    authApiKey = a.key
                    authApiValue = a.value
                    authApiPlacement = a.placement.value
                }
                is AuthOAuth2 -> {
                    authType = AuthType.OAUTH2
                    authOAuth2Flow = a.flow
                    authOAuth2AccessTokenUrl = a.accessTokenUrl
                    authOAuth2RefreshTokenUrl = a.refreshTokenUrl
                    authOAuth2AuthorizationUrl = a.authorizationUrl
                    authOAuth2RedirectUri = a.redirectUri
                    authOAuth2Scope = a.scope
                    authOAuth2ClientId = a.credentials?.clientId
                    authOAuth2ClientSecret = a.credentials?.clientSecret
                    authOAuth2CredentialsPlacement = a.credentials?.placement ?: OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
                    authOAuth2Username = a.resourceOwner?.username
                    authOAuth2Password = a.resourceOwner?.password
                }
                is AuthInherit -> {
                    authType = AuthType.INHERIT
                }
                else -> {
                    authType = AuthType.NONE
                }
            }

            val meta = (if (path.isNotEmpty()) clapiRequests?.get(path) as? JsonObject else null)
                ?: clapiRequests?.get(info.name) as? JsonObject
                ?: clapiRequests?.get(reqId) as? JsonObject
            if (meta != null) {
                meta["bodyType"]?.jsonPrimitive?.contentOrNull?.let { typeName ->
                    runCatching { BodyType.valueOf(typeName) }.getOrNull()?.let { bodyType = it }
                }
                meta["bodyContents"]?.jsonObject?.let { obj ->
                    bodyContents = obj.mapValues { it.value.jsonPrimitive.content }
                }
                meta["queryEntries"]?.jsonArray?.let { arr ->
                    queryParams = arr.mapNotNull { it as? JsonObject }.map { entryObj ->
                        KeyValueEntry(
                            key = entryObj["key"]?.jsonPrimitive?.content ?: "",
                            value = entryObj["value"]?.jsonPrimitive?.content ?: "",
                            enabled = entryObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            secret = entryObj["secret"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    }
                }
                meta["headerEntries"]?.jsonArray?.let { arr ->
                    headerEntries = arr.mapNotNull { it as? JsonObject }.map { entryObj ->
                        KeyValueEntry(
                            key = entryObj["key"]?.jsonPrimitive?.content ?: "",
                            value = entryObj["value"]?.jsonPrimitive?.content ?: "",
                            enabled = entryObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            secret = entryObj["secret"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    }
                }
                meta["authApiPlacement"]?.jsonPrimitive?.contentOrNull?.let {
                    authApiPlacement = it
                }
                meta["kind"]?.jsonPrimitive?.contentOrNull?.let { kindName ->
                    runCatching { RequestKind.valueOf(kindName) }.getOrNull()?.let { requestKind = it }
                }
                meta["requestRef"]?.jsonPrimitive?.contentOrNull?.let {
                    reqRef = it
                }
            }

            val preScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
            val postScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.TESTS }?.code

            val httpMethod = HttpMethodType.entries.firstOrNull {
                it.name.equals(http.method, ignoreCase = true)
            } ?: HttpMethodType.GET

            val mcpConfig = meta?.get("mcpConfig")?.let {
                runCatching {
                    json.decodeFromJsonElement(McpConnectionConfig.serializer(), it)
                }.getOrNull()
            }

            val effectiveKind = if (bodyType == BodyType.GRAPHQL) RequestKind.GRAPHQL else requestKind

            CollectionNode(
                id = reqId,
                name = info.name,
                isFolder = false,
                method = httpMethod,
                url = http.url,
                headerEntries = headerEntries,
                userHeaders = userHeaders,
                queryEntries = queryParams,
                bodyType = bodyType,
                bodyContent = bodyContent,
                bodyContents = bodyContents,
                formDataEntries = formData,
                urlencodedEntries = urlencoded,
                authType = authType,
                authUsername = authUsername,
                authPassword = authPassword,
                authToken = authToken,
                authApiKey = authApiKey,
                authApiValue = authApiValue,
                authApiPlacement = authApiPlacement,
                authOAuth2Flow = authOAuth2Flow,
                authOAuth2AccessTokenUrl = authOAuth2AccessTokenUrl,
                authOAuth2RefreshTokenUrl = authOAuth2RefreshTokenUrl,
                authOAuth2AuthorizationUrl = authOAuth2AuthorizationUrl,
                authOAuth2RedirectUri = authOAuth2RedirectUri,
                authOAuth2Scope = authOAuth2Scope,
                authOAuth2ClientId = authOAuth2ClientId,
                authOAuth2ClientSecret = authOAuth2ClientSecret,
                authOAuth2CredentialsPlacement = authOAuth2CredentialsPlacement,
                authOAuth2Username = authOAuth2Username,
                authOAuth2Password = authOAuth2Password,
                preRequestScript = preScript,
                testScript = postScript,
                requestRef = reqRef ?: generateUuid(),
                kind = effectiveKind,
                mcpConfig = mcpConfig,
                graphQlDocs = if (effectiveKind == RequestKind.GRAPHQL) docs else null,
            )
        }
        is io.github.crustack.clapi.core.model.opencollection.GrpcRequestItem -> {
            val reqId = generateUuid()
            var reqRef = (extensions["requestRef"] as? JsonPrimitive)?.content
            var headerEntries: List<KeyValueEntry>? = grpc.metadata.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = grpc.metadata.map { it.name to it.value }
            var bodyContent: String? = grpc.message
            var bodyContents: Map<String, String> = if (!grpc.message.isNullOrBlank()) {
                mapOf(BodyType.JSON.name to grpc.message!!)
            } else {
                emptyMap()
            }

            var authType: AuthType = AuthType.NONE
            var authUsername: String? = null
            var authPassword: String? = null
            var authToken: String? = null
            var authApiKey: String? = null
            var authApiValue: String? = null
            var authApiPlacement: String? = null
            var authOAuth2Flow: OAuth2Flow = OAuth2Flow.CLIENT_CREDENTIALS
            var authOAuth2AccessTokenUrl: String? = null
            var authOAuth2RefreshTokenUrl: String? = null
            var authOAuth2AuthorizationUrl: String? = null
            var authOAuth2RedirectUri: String? = null
            var authOAuth2Scope: String? = null
            var authOAuth2ClientId: String? = null
            var authOAuth2ClientSecret: String? = null
            var authOAuth2CredentialsPlacement: OAuth2CredentialsPlacement = OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
            var authOAuth2Username: String? = null
            var authOAuth2Password: String? = null

            val clapiExt = extensions["clapi"] as? JsonObject
            val isJwt = (clapiExt?.get("authType") as? JsonPrimitive)?.content == "jwt"

            when (val a = grpc.auth) {
                is AuthBasic -> {
                    authType = AuthType.BASIC
                    authUsername = a.username
                    authPassword = a.password
                }
                is AuthBearer -> {
                    authType = if (isJwt) AuthType.JWT else AuthType.BEARER
                    authToken = a.token
                }
                is AuthApiKey -> {
                    authType = AuthType.API_KEY
                    authApiKey = a.key
                    authApiValue = a.value
                    authApiPlacement = a.placement.value
                }
                is AuthOAuth2 -> {
                    authType = AuthType.OAUTH2
                    authOAuth2Flow = a.flow
                    authOAuth2AccessTokenUrl = a.accessTokenUrl
                    authOAuth2RefreshTokenUrl = a.refreshTokenUrl
                    authOAuth2AuthorizationUrl = a.authorizationUrl
                    authOAuth2RedirectUri = a.redirectUri
                    authOAuth2Scope = a.scope
                    authOAuth2ClientId = a.credentials?.clientId
                    authOAuth2ClientSecret = a.credentials?.clientSecret
                    authOAuth2CredentialsPlacement = a.credentials?.placement ?: OAuth2CredentialsPlacement.BASIC_AUTH_HEADER
                    authOAuth2Username = a.resourceOwner?.username
                    authOAuth2Password = a.resourceOwner?.password
                }
                is AuthInherit -> {
                    authType = AuthType.INHERIT
                }
                else -> {
                    authType = AuthType.NONE
                }
            }

            var resolvedProtoFilePath: String? = grpc.protoFilePath
            var resolvedGrpcMethod: String? = grpc.method
            var resolvedGrpcMethodType: String? = grpc.methodType?.value ?: "unary"

            val meta = (if (path.isNotEmpty()) clapiRequests?.get(path) as? JsonObject else null)
                ?: clapiRequests?.get(info.name) as? JsonObject
                ?: clapiRequests?.get(reqId) as? JsonObject
            if (meta != null) {
                meta["bodyContents"]?.jsonObject?.let { obj ->
                    bodyContents = obj.mapValues { it.value.jsonPrimitive.content }
                }
                meta["headerEntries"]?.jsonArray?.let { arr ->
                    headerEntries = arr.mapNotNull { it as? JsonObject }.map { entryObj ->
                        KeyValueEntry(
                            key = entryObj["key"]?.jsonPrimitive?.content ?: "",
                            value = entryObj["value"]?.jsonPrimitive?.content ?: "",
                            enabled = entryObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            secret = entryObj["secret"]?.jsonPrimitive?.booleanOrNull ?: false,
                        )
                    }
                }
                meta["authApiPlacement"]?.jsonPrimitive?.contentOrNull?.let {
                    authApiPlacement = it
                }
                meta["requestRef"]?.jsonPrimitive?.contentOrNull?.let {
                    reqRef = it
                }
                if (resolvedProtoFilePath == null) {
                    resolvedProtoFilePath = meta["protoFilePath"]?.jsonPrimitive?.contentOrNull
                }
                if (resolvedGrpcMethod == null) {
                    resolvedGrpcMethod = meta["grpcMethod"]?.jsonPrimitive?.contentOrNull
                }
                meta["grpcMethodType"]?.jsonPrimitive?.contentOrNull?.let {
                    resolvedGrpcMethodType = it
                }
            }

            val resolvedProtoSyncToGit = meta?.get("protoSyncToGit")?.jsonPrimitive?.booleanOrNull
                ?: (resolvedProtoFilePath?.let { ProtoPathResolver.isRelativeGitProtoPath(it) } ?: false)

            val preScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
            val postScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.TESTS }?.code

            CollectionNode(
                id = reqId,
                name = info.name,
                isFolder = false,
                method = HttpMethodType.POST,
                url = grpc.url,
                headerEntries = headerEntries,
                userHeaders = userHeaders,
                bodyType = BodyType.JSON,
                bodyContent = bodyContent,
                bodyContents = bodyContents,
                authType = authType,
                authUsername = authUsername,
                authPassword = authPassword,
                authToken = authToken,
                authApiKey = authApiKey,
                authApiValue = authApiValue,
                authApiPlacement = authApiPlacement,
                authOAuth2Flow = authOAuth2Flow,
                authOAuth2AccessTokenUrl = authOAuth2AccessTokenUrl,
                authOAuth2RefreshTokenUrl = authOAuth2RefreshTokenUrl,
                authOAuth2AuthorizationUrl = authOAuth2AuthorizationUrl,
                authOAuth2RedirectUri = authOAuth2RedirectUri,
                authOAuth2Scope = authOAuth2Scope,
                authOAuth2ClientId = authOAuth2ClientId,
                authOAuth2ClientSecret = authOAuth2ClientSecret,
                authOAuth2CredentialsPlacement = authOAuth2CredentialsPlacement,
                authOAuth2Username = authOAuth2Username,
                authOAuth2Password = authOAuth2Password,
                preRequestScript = preScript,
                testScript = postScript,
                requestRef = reqRef ?: generateUuid(),
                kind = RequestKind.GRPC,
                protoFilePath = resolvedProtoFilePath,
                protoSyncToGit = resolvedProtoSyncToGit,
                grpcMethod = resolvedGrpcMethod,
                grpcMethodType = resolvedGrpcMethodType,
            )
        }
        is io.github.crustack.clapi.core.model.opencollection.WebSocketRequestItem -> {
            val reqId = generateUuid()
            val reqRef = (extensions["requestRef"] as? JsonPrimitive)?.content
            val headerEntries: List<KeyValueEntry> = websocket.headers.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = websocket.headers.map { it.name to it.value }
            val variants = when (val msg = websocket.message) {
                is io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Variants -> msg.variants
                is io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Single -> listOf(
                    io.github.crustack.clapi.core.model.opencollection.WebSocketMessageVariant(
                        title = "Default",
                        selected = true,
                        message = msg.message
                    )
                )
                null -> emptyList()
            }
            val firstMsg = when (val msg = websocket.message) {
                is io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Single -> msg.message.data
                is io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Variants -> msg.variants.firstOrNull { it.selected == true }?.message?.data
                    ?: msg.variants.firstOrNull()?.message?.data
                null -> null
            }
            val msgFormat = when (val msg = websocket.message) {
                is io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Single -> msg.message.type
                is io.github.crustack.clapi.core.model.opencollection.WebSocketMessagePayload.Variants -> msg.variants.firstOrNull { it.selected == true }?.message?.type
                    ?: msg.variants.firstOrNull()?.message?.type
                null -> io.github.crustack.clapi.core.model.opencollection.WebSocketMessageType.TEXT
            }
            val preScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
            val postScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.TESTS }?.code

            CollectionNode(
                id = reqId,
                name = info.name,
                isFolder = false,
                url = websocket.url,
                method = HttpMethodType.GET,
                headerEntries = headerEntries,
                userHeaders = userHeaders,
                bodyContent = firstMsg,
                preRequestScript = preScript,
                testScript = postScript,
                requestRef = reqRef ?: generateUuid(),
                kind = RequestKind.WEBSOCKET,
                webSocketVariants = variants,
                webSocketTimeout = (settings?.timeout as? JsonPrimitive)?.content,
                webSocketKeepAliveInterval = (settings?.keepAliveInterval as? JsonPrimitive)?.content,
                webSocketDocs = docs,
                webSocketMessageFormat = msgFormat,
            )
        }
        is io.github.crustack.clapi.core.model.opencollection.GraphQLRequestItem -> {
            val reqId = generateUuid()
            val reqRef = (extensions["requestRef"] as? JsonPrimitive)?.content
            val headerEntries: List<KeyValueEntry> = graphql.headers.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val userHeaders = graphql.headers.map { it.name to it.value }
            val queryEntries: List<KeyValueEntry> = graphql.params.map {
                KeyValueEntry(key = it.name, value = it.value, enabled = it.disabled != true)
            }
            val variants = when (val b = graphql.body) {
                is io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload.Variants -> b.variants
                is io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload.Single -> listOf(
                    io.github.crustack.clapi.core.model.opencollection.GraphQLBodyVariant(
                        title = "Default",
                        selected = true,
                        body = b.body
                    )
                )
                null -> emptyList()
            }
            val firstQuery = when (val b = graphql.body) {
                is io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload.Single -> b.body.query
                is io.github.crustack.clapi.core.model.opencollection.GraphQLBodyPayload.Variants -> b.variants.firstOrNull { it.selected == true }?.body?.query
                    ?: b.variants.firstOrNull()?.body?.query
                null -> null
            }
            val preScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.BEFORE_REQUEST }?.code
            val postScript = runtime?.scripts?.firstOrNull { it.type == ScriptType.AFTER_RESPONSE || it.type == ScriptType.TESTS }?.code
            val resolvedMethod = runCatching { HttpMethodType.valueOf(graphql.method?.uppercase() ?: "POST") }.getOrDefault(HttpMethodType.POST)

            CollectionNode(
                id = reqId,
                name = info.name,
                isFolder = false,
                url = graphql.url,
                method = resolvedMethod,
                headerEntries = headerEntries,
                userHeaders = userHeaders,
                queryEntries = queryEntries,
                bodyContent = firstQuery,
                preRequestScript = preScript,
                testScript = postScript,
                requestRef = reqRef ?: generateUuid(),
                kind = RequestKind.GRAPHQL,
                graphQlVariants = variants,
                graphQlSettings = settings,
                graphQlDocs = docs,
            )
        }
    }
}
