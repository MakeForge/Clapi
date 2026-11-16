package io.github.makeforge.clapi.ui.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.makeforge.clapi.core.model.BodyType
import io.github.makeforge.clapi.core.model.FormEntryType
import io.github.makeforge.clapi.core.model.KeyValueEntry
import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.ui.shared.platform.generateUuid

/**
 * Mutable row for structured form-data and x-www-form-urlencoded editors.
 * [type] distinguishes text-value from file-attachment rows.
 */
class MutableFormDataRow(
    key: String = "",
    type: FormEntryType = FormEntryType.TEXT,
    value: String = "",
    description: String = "",
    enabled: Boolean = true,
    bytesBase64: String = "",
    val uid: String = generateUuid(),
) {
    var key         by mutableStateOf(key)
    var type        by mutableStateOf(type)
    var value       by mutableStateOf(value)
    var description by mutableStateOf(description)
    var enabled     by mutableStateOf(enabled)
    var bytesBase64 by mutableStateOf(bytesBase64)
}

/** Mutable key-value pair used in param / header / variable editors. */
class MutableKeyValue(
    key: String = "",
    value: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    kind: HeaderKind = HeaderKind.USER,
    keyLocked: Boolean = false,
    isDynamic: Boolean = false,
    dynamicScript: String = "",
    dynamicParameters: List<DynamicVariableParameter> = emptyList(),
    description: String = "",
    /** Stable unique identifier used as LazyColumn item key (fixes M-7 index-key bug). */
    val uid: String = generateUuid(),
) {
    var key           by mutableStateOf(key)
    var value         by mutableStateOf(value)
    var enabled       by mutableStateOf(enabled)
    var secret        by mutableStateOf(secret)
    var kind          by mutableStateOf(kind)
    var keyLocked     by mutableStateOf(keyLocked)
    var isDynamic     by mutableStateOf(isDynamic)
    var dynamicScript by mutableStateOf(dynamicScript)
    val dynamicParameters: SnapshotStateList<DynamicVariableParameter> =
        mutableStateListOf<DynamicVariableParameter>().apply { addAll(dynamicParameters) }
    var description   by mutableStateOf(description)

    fun signatureLabel(): String {
        val trimmedKey = key.trim().ifEmpty { "fn" }
        return if (dynamicParameters.isEmpty()) {
            trimmedKey
        } else {
            "$trimmedKey(${dynamicParameters.joinToString(", ") { it.name }})"
        }
    }

    fun toDynamicVariable(): DynamicVariable = DynamicVariable(
        name = key.trim(),
        script = dynamicScript,
        parameters = dynamicParameters.toList(),
        description = description.takeIf { it.isNotBlank() }?.let { Description(it) },
        disabled = if (enabled) null else true,
    )

    fun toKeyValueEntry(): KeyValueEntry = KeyValueEntry(
        key = key,
        value = value,
        enabled = enabled,
        secret = secret,
        isDynamic = isDynamic,
        dynamicScript = dynamicScript,
        dynamicParameters = dynamicParameters.toList(),
        description = description,
    )
}

fun KeyValueEntry.toMutableKeyValue(): MutableKeyValue = MutableKeyValue(
    key = key,
    value = value,
    enabled = enabled,
    secret = secret,
    isDynamic = isDynamic,
    dynamicScript = dynamicScript,
    dynamicParameters = dynamicParameters,
    description = description,
)

object SystemHeaderRules {
    const val CONTENT_TYPE = "Content-Type"
    const val ACCEPT = "Accept"
    const val USER_AGENT = "User-Agent"

    val nonDeletableKeys: Set<String> = setOf(CONTENT_TYPE, ACCEPT, USER_AGENT)

    fun isSystemHeader(key: String): Boolean = key in nonDeletableKeys

    fun defaultContentTypeFor(bodyType: BodyType): String = when (bodyType) {
        BodyType.JSON       -> "application/json"
        BodyType.XML        -> "application/xml"
        BodyType.HTML       -> "text/html"
        BodyType.JAVASCRIPT -> "application/javascript"
        BodyType.FORM_DATA  -> "multipart/form-data"
        BodyType.X_WWW_FORM_URLENCODED -> "application/x-www-form-urlencoded"
        BodyType.GRAPHQL    -> "application/json"
        BodyType.RAW_TEXT   -> "text/plain"
        BodyType.BINARY     -> "application/octet-stream"
        BodyType.NONE       -> "application/json"
    }
}

const val SSE_ACCEPT_MEDIA_TYPE = "text/event-stream"

fun isSseAccept(key: String, value: String, enabled: Boolean = true): Boolean =
    enabled &&
        key.equals(SystemHeaderRules.ACCEPT, ignoreCase = true) &&
        value.contains(SSE_ACCEPT_MEDIA_TYPE, ignoreCase = true)

fun RequestTabState.hasSseAccept(): Boolean =
    headers.any { isSseAccept(it.key, it.value, it.enabled) }

fun CollectionNode.hasSseAccept(): Boolean =
    userHeaders.any { isSseAccept(it.first, it.second) }
