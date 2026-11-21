package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.makeforge.clapi.core.model.opencollection.ClientCertificate
import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.storage.git.ProtoPathResolver
import io.github.makeforge.clapi.ui.shared.platform.generateUuid

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
    color: String? = null,
    var description: Description? = null,
    var clientCertificates: List<ClientCertificate> = emptyList(),
    extends: String? = null,
    var dotEnvFilePath: String? = null,
    externalSecretsProvider: String = "hashicorp-vault-server",
    externalSecrets: List<MutableExternalSecret> = emptyList(),
) {
    var name by mutableStateOf(name)
    var color by mutableStateOf(color)
    var extends by mutableStateOf(extends)
    val variables = mutableStateListOf<MutableKeyValue>().also { it.addAll(variables) }
    var externalSecretsProvider by mutableStateOf(externalSecretsProvider)
    val externalSecrets = mutableStateListOf<MutableExternalSecret>().also { it.addAll(externalSecrets) }

    /**
     * Produce a flat variable map for resolution (e.g. {{baseUrl}} → value).
     * Excludes `secret = true` variables whose in-memory value is blank so they do not shadow
     * lower-precedence variable layers (Folder, Collection, Global) with an empty string.
     * When [allEnvironments] is provided and this environment extends another,
     * variables are resolved hierarchically with child-overrides-parent precedence.
     */
    fun toVariableMap(allEnvironments: List<EnvState> = emptyList()): Map<String, String> {
        if (extends.isNullOrBlank() || allEnvironments.isEmpty()) {
            return variables
                .filter { it.enabled && !it.isDynamic && (!it.secret || it.value.isNotBlank()) }
                .associate { it.key to it.value }
        }
        return resolveEffectiveVariables(allEnvironments)
    }

    fun toVariableMap(): Map<String, String> = toVariableMap(emptyList())
}

data class InheritedVariableInfo(
    val key: String,
    val value: String,
    val sourceEnvName: String,
    val isInherited: Boolean,
    val isOverridden: Boolean = false,
    val overriddenValue: String? = null,
    val secret: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Resolves the effective variable map for this environment by traversing its inheritance chain.
 * Traverses from root parent to child so that child variables override parent variables.
 * Automatically halts on circular references.
 */
fun EnvState.resolveEffectiveVariables(allEnvironments: List<EnvState>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val visited = mutableSetOf<String>()

    fun walk(current: EnvState) {
        if (!visited.add(current.name)) return // cycle detected
        val parentName = current.extends?.trim()
        if (!parentName.isNullOrEmpty()) {
            val parent = allEnvironments.firstOrNull { it.name == parentName }
            if (parent != null) {
                walk(parent)
            }
        }
        current.variables.forEach { v ->
            val k = v.key.trim()
            if (k.isNotEmpty() && v.enabled) {
                if (v.isDynamic) {
                    result.remove(k)
                } else if (!v.secret || v.value.isNotBlank()) {
                    result[k] = v.value
                }
            }
        }
    }

    walk(this)
    return result
}

/**
 * Resolves the effective user-defined dynamic variables for this environment across its inheritance chain.
 */
fun EnvState.resolveEffectiveDynamicVariables(allEnvironments: List<EnvState> = emptyList()): List<DynamicVariable> {
    val result = LinkedHashMap<String, DynamicVariable>()
    val visited = mutableSetOf<String>()

    fun walk(current: EnvState) {
        if (!visited.add(current.name)) return
        val parentName = current.extends?.trim()
        if (!parentName.isNullOrEmpty() && allEnvironments.isNotEmpty()) {
            val parent = allEnvironments.firstOrNull { it.name == parentName }
            if (parent != null) {
                walk(parent)
            }
        }
        current.variables.forEach { v ->
            val k = v.key.trim()
            if (k.isNotEmpty() && v.enabled) {
                if (v.isDynamic) {
                    result[k] = v.toDynamicVariable()
                } else {
                    result.remove(k)
                }
            }
        }
    }

    walk(this)
    return result.values.toList()
}

/**
 * Checks whether [candidate] is a descendant of [targetName] in the inheritance tree.
 */
fun isDescendantOf(candidate: EnvState, targetName: String, allEnvironments: List<EnvState>): Boolean {
    val visited = mutableSetOf<String>()
    var curName: String? = candidate.extends?.trim()
    while (!curName.isNullOrEmpty()) {
        if (curName == targetName) return true
        if (!visited.add(curName)) break
        curName = allEnvironments.firstOrNull { it.name == curName }?.extends?.trim()
    }
    return false
}

/**
 * Computes all environments that [target] can safely extend without creating self-references
 * or circular inheritance chains.
 */
fun getEligibleParentEnvironments(target: EnvState, allEnvironments: List<EnvState>): List<EnvState> {
    return allEnvironments.filter { candidate ->
        candidate.name != target.name && !isDescendantOf(candidate, target.name, allEnvironments)
    }
}

/**
 * Resolves projection of both local and inherited variables for [target], indicating source environment
 * and whether a local variable overrides an inherited ancestor variable.
 */
fun resolveVariableProjections(
    target: EnvState,
    allEnvironments: List<EnvState>,
): List<InheritedVariableInfo> {
    val inheritedMap = mutableMapOf<String, Pair<String, String>>() // key -> (value, sourceEnvName)
    val visited = mutableSetOf<String>()
    visited.add(target.name)

    fun walkAncestors(envName: String?) {
        if (envName.isNullOrBlank() || !visited.add(envName)) return
        val env = allEnvironments.firstOrNull { it.name == envName } ?: return
        walkAncestors(env.extends?.trim())
        env.variables.forEach { v ->
            val k = v.key.trim()
            if (k.isNotEmpty() && v.enabled) {
                inheritedMap[k] = Pair(v.value, env.name)
            }
        }
    }
    walkAncestors(target.extends?.trim())

    val projections = mutableListOf<InheritedVariableInfo>()
    val localKeys = mutableSetOf<String>()

    target.variables.forEach { v ->
        val k = v.key.trim()
        localKeys.add(k)
        val ancestor = inheritedMap[k]
        projections.add(
            InheritedVariableInfo(
                key = v.key,
                value = v.value,
                sourceEnvName = target.name,
                isInherited = false,
                isOverridden = ancestor != null,
                overriddenValue = ancestor?.first,
                secret = v.secret,
                enabled = v.enabled,
            )
        )
    }

    inheritedMap.forEach { (k, pair) ->
        if (k !in localKeys) {
            projections.add(
                InheritedVariableInfo(
                    key = k,
                    value = pair.first,
                    sourceEnvName = pair.second,
                    isInherited = true,
                    isOverridden = false,
                    overriddenValue = null,
                )
            )
        }
    }

    return projections
}

