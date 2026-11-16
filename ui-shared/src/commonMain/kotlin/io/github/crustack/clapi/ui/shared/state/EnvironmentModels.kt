package io.github.crustack.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.crustack.clapi.core.model.opencollection.ClientCertificate
import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.storage.git.ProtoPathResolver
import io.github.crustack.clapi.ui.shared.platform.generateUuid

// ── Environment model ───────────────────────────────────────────

class MutableExternalSecret(
    name: String = "",
    path: String = "",
    description: String = "",
    enabled: Boolean = true,
    url: String = "",
    targetField: String = "password",
    customFilters: Map<String, String> = emptyMap(),
    val uid: String = generateUuid(),
) {
    var name by mutableStateOf(name)
    var path by mutableStateOf(path)
    var description by mutableStateOf(description)
    var enabled by mutableStateOf(enabled)
    var url by mutableStateOf(url)
    var targetField by mutableStateOf(targetField)
    val customFilters = mutableStateMapOf<String, String>().also { it.putAll(customFilters) }
    val customFilterList = mutableStateListOf<MutableKeyValue>().also { list ->
        customFilters.forEach { (k, v) -> list.add(MutableKeyValue(key = k, value = v)) }
    }

    fun syncCustomFiltersFromList() {
        customFilters.clear()
        customFilterList.forEach { kv ->
            if (kv.key.isNotBlank()) {
                customFilters[kv.key] = kv.value
            }
        }
    }
}

class ProtoFileItemState(
    path: String,
    syncToGit: Boolean = ProtoPathResolver.isRelativeGitProtoPath(path),
) {
    var path by mutableStateOf(path)
    var syncToGit by mutableStateOf(syncToGit)
    val fileName: String get() = path.substringAfterLast('\\').substringAfterLast('/')
    val directory: String get() = path.substringBeforeLast('\\', "").substringBeforeLast('/', "")
}

class ProtoImportPathState(
    path: String,
    enabled: Boolean = true,
    syncToGit: Boolean = ProtoPathResolver.isRelativeGitProtoPath(path),
) {
    var path by mutableStateOf(path)
    var enabled by mutableStateOf(enabled)
    var syncToGit by mutableStateOf(syncToGit)
    val fileName: String get() = path.substringAfterLast('\\').substringAfterLast('/')
    val directory: String get() = path.substringBeforeLast('\\', "").substringBeforeLast('/', "")
}

class EnvState(
    name: String,
    variables: List<MutableKeyValue> = emptyList(),
    var color: String? = null,
    var description: Description? = null,
    var clientCertificates: List<ClientCertificate> = emptyList(),
    var extends: String? = null,
    var dotEnvFilePath: String? = null,
    externalSecretsProvider: String = "hashicorp-vault-server",
    externalSecrets: List<MutableExternalSecret> = emptyList(),
) {
    var name by mutableStateOf(name)
    val variables = mutableStateListOf<MutableKeyValue>().also { it.addAll(variables) }
    var externalSecretsProvider by mutableStateOf(externalSecretsProvider)
    val externalSecrets = mutableStateListOf<MutableExternalSecret>().also { it.addAll(externalSecrets) }

    /**
     * Produce a flat variable map for resolution (e.g. {{baseUrl}} → value).
     * Excludes `secret = true` variables whose in-memory value is blank so they do not shadow
     * lower-precedence variable layers (Folder, Collection, Global) with an empty string.
     */
    fun toVariableMap(): Map<String, String> =
        variables
            .filter { it.enabled && (!it.secret || it.value.isNotBlank()) }
            .associate { it.key to it.value }
}
