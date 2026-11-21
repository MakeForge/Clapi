package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.core.model.HttpMethodType
import io.github.makeforge.clapi.core.storage.opencollection.fileExists
import io.github.makeforge.clapi.core.storage.opencollection.readTextFile
import io.github.makeforge.clapi.core.storage.opencollection.writeTextFile
import io.github.makeforge.clapi.ui.shared.state.HistoryItem
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Private store for global variables, request history, and expanded tree state.
 *
 * Not a user-facing format and not a `clapi*` type. When [baseDirectory] is set, the file is
 * [PRIVATE_STATE_FILENAME] in that directory — the same directory convention as
 * [io.github.makeforge.clapi.core.storage.opencollection.DefaultCanonicalCollectionRepository.baseDirectory].
 * When no directory is set, the snapshot stays in memory.
 */
class PrivateStateRepository(
    private val baseDirectory: String? = null,
    private val readText: (String) -> String? = { path ->
        if (fileExists(path)) readTextFile(path) else null
    },
    private val writeText: (String, String) -> Unit = { path, content ->
        writeTextFile(path, content)
    },
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private var memory: String? = null

    fun save(state: PrivateWorkspaceState) {
        val path = storagePath()
        if (path == null) {
            memory = json.encodeToString(state)
        } else {
            // Preserve sibling keys written by HeadlessWorkspaceBackend (e.g. activeEnvironment, scopedSecrets)
            val existingObj = runCatching {
                readText(path)?.let { json.parseToJsonElement(it).jsonObject }
            }.getOrNull()
            val payload = if (existingObj != null) {
                val encoded = json.encodeToJsonElement(PrivateWorkspaceState.serializer(), state).jsonObject
                val merged = buildJsonObject {
                    existingObj.forEach { (k, v) -> put(k, v) }
                    encoded.forEach { (k, v) -> put(k, v) }
                }
                json.encodeToString(JsonObject.serializer(), merged)
            } else {
                json.encodeToString(state)
            }
            writeText(path, payload)
        }
    }

    fun load(): PrivateWorkspaceState? {
        val raw = storagePath()?.let(readText) ?: memory ?: return null
        return json.decodeFromString(PrivateWorkspaceState.serializer(), raw)
    }

    private fun storagePath(): String? = baseDirectory?.let { "$it/$PRIVATE_STATE_FILENAME" }
}

/** Sibling of the global collection file. Not a `clapi*` name. */
const val PRIVATE_STATE_FILENAME = "private-state.json"

@Serializable
data class StoredVariable(
    val name: String,
    val value: String = "",
    val enabled: Boolean = true,
    val secret: Boolean = false,
)

@Serializable
data class StoredHistoryItem(
    val requestId: String,
    val method: String,
    val name: String,
    val url: String,
    val timestamp: Long,
    val collectionId: String? = null,
    val folderPath: List<String> = emptyList(),
)

@Serializable
data class PrivateWorkspaceState(
    val globalVariables: List<StoredVariable> = emptyList(),
    val history: List<StoredHistoryItem> = emptyList(),
    val expandedState: Map<String, Boolean> = emptyMap(),
)

fun MutableKeyValue.toStoredVariable(): StoredVariable =
    StoredVariable(name = key.trim(), value = value, enabled = enabled, secret = secret)

fun StoredVariable.toMutableKeyValue(): MutableKeyValue =
    MutableKeyValue(key = name, value = value, enabled = enabled, secret = secret)

fun HistoryItem.toStoredHistoryItem(): StoredHistoryItem =
    StoredHistoryItem(
        requestId = requestId,
        method = method.name,
        name = name,
        url = url,
        timestamp = timestamp,
        collectionId = collectionId,
        folderPath = folderPath,
    )

fun StoredHistoryItem.toHistoryItem(): HistoryItem =
    HistoryItem(
        requestId = requestId,
        method = runCatching { HttpMethodType.valueOf(method.uppercase()) }.getOrDefault(HttpMethodType.GET),
        name = name,
        url = url,
        timestamp = timestamp,
        collectionId = collectionId,
        folderPath = folderPath,
    )
