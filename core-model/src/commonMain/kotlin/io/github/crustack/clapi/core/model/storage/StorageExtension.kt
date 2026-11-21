package io.github.crustack.clapi.core.model.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

enum class StorageType {
    @SerialName("local")
    LOCAL,

    @SerialName("git")
    GIT;

    companion object {
        fun fromString(value: String?): StorageType = when (value?.lowercase()) {
            "git" -> GIT
            else -> LOCAL
        }
    }
}

@Serializable
data class StorageExtension(
    val type: StorageType = StorageType.LOCAL,
    val git: GitStorageConfig? = null,
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (type == StorageType.GIT) {
            if (git == null) {
                errors.add("Git storage configuration is required when storage type is 'git'")
            } else {
                if (git.remoteUrl.isBlank()) {
                    errors.add("Git remoteUrl cannot be blank")
                }
                if (git.branch.isBlank()) {
                    errors.add("Git branch cannot be blank")
                }
                if (git.credentials.type != "system") {
                    errors.add("Only 'system' credentials are supported for Git storage")
                }
            }
        }
        return errors
    }
}

@Serializable
data class GitStorageConfig(
    val remoteUrl: String,
    val branch: String = "main",
    val credentials: GitCredentialsConfig = GitCredentialsConfig(),
)

@Serializable
data class GitCredentialsConfig(
    val type: String = "system",
)

private val storageJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Map<String, JsonElement>.getStorageExtension(json: Json = storageJson): StorageExtension? {
    val element = this["storage"] ?: return null
    return try {
        json.decodeFromJsonElement<StorageExtension>(element)
    } catch (_: Exception) {
        null
    }
}

fun Map<String, JsonElement>.withStorageExtension(
    storage: StorageExtension,
    json: Json = storageJson,
): Map<String, JsonElement> {
    val element = json.encodeToJsonElement(storage)
    return this + ("storage" to element)
}
