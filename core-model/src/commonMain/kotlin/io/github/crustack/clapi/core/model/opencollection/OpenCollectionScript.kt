package io.github.crustack.clapi.core.model.opencollection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScriptType(val value: String) {
    @SerialName("before-request")
    BEFORE_REQUEST("before-request"),
    @SerialName("after-response")
    AFTER_RESPONSE("after-response"),
    @SerialName("tests")
    TESTS("tests"),
    @SerialName("hooks")
    HOOKS("hooks"),
    @SerialName("grpc:before-call-start")
    GRPC_BEFORE_CALL_START("grpc:before-call-start"),
    @SerialName("grpc:after-call-end")
    GRPC_AFTER_CALL_END("grpc:after-call-end"),
    @SerialName("grpc:before-message-send")
    GRPC_BEFORE_MESSAGE_SEND("grpc:before-message-send"),
    @SerialName("grpc:after-message-receive")
    GRPC_AFTER_MESSAGE_RECEIVE("grpc:after-message-receive");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ScriptType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class Script(
    val type: ScriptType,
    val code: String
) {
    constructor(type: String, code: String) : this(
        type = ScriptType.fromValue(type) ?: ScriptType.BEFORE_REQUEST,
        code = code
    )
}

@Serializable
data class Assertion(
    val expression: String,
    val operator: String,
    val value: String? = null,
    val disabled: Boolean? = null,
    val description: Description? = null
)

@Serializable
enum class ActionSelectorMethod(val value: String) {
    @SerialName("jsonq")
    JSONQ("jsonq");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ActionSelectorMethod? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class ActionSelector(
    val expression: String,
    val method: ActionSelectorMethod = ActionSelectorMethod.JSONQ
) {
    constructor(expression: String, method: String) : this(
        expression = expression,
        method = ActionSelectorMethod.fromValue(method) ?: ActionSelectorMethod.JSONQ
    )
}

@Serializable
enum class ActionVariableScope(val value: String) {
    @SerialName("runtime")
    RUNTIME("runtime"),
    @SerialName("request")
    REQUEST("request"),
    @SerialName("folder")
    FOLDER("folder"),
    @SerialName("collection")
    COLLECTION("collection"),
    @SerialName("environment")
    ENVIRONMENT("environment");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ActionVariableScope? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class ActionTargetVariable(
    val name: String,
    val scope: ActionVariableScope
) {
    constructor(name: String, scope: String) : this(
        name = name,
        scope = ActionVariableScope.fromValue(scope) ?: ActionVariableScope.RUNTIME
    )
}

@Serializable
enum class ActionType(val value: String) {
    @SerialName("set-variable")
    SET_VARIABLE("set-variable");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ActionType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
enum class ActionPhase(val value: String) {
    @SerialName("before-request")
    BEFORE_REQUEST("before-request"),
    @SerialName("after-response")
    AFTER_RESPONSE("after-response");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): ActionPhase? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class ActionSetVariable(
    val type: ActionType = ActionType.SET_VARIABLE,
    val description: Description? = null,
    val phase: ActionPhase? = null,
    val selector: ActionSelector,
    val variable: ActionTargetVariable,
    val disabled: Boolean? = null
) {
    constructor(
        type: String = "set-variable",
        description: Description? = null,
        phase: String? = null,
        selector: ActionSelector,
        variable: ActionTargetVariable,
        disabled: Boolean? = null
    ) : this(
        type = ActionType.fromValue(type) ?: ActionType.SET_VARIABLE,
        description = description,
        phase = phase?.let { ActionPhase.fromValue(it) },
        selector = selector,
        variable = variable,
        disabled = disabled
    )
}

@Serializable
data class HttpRequestRuntime(
    val variables: List<VariableEntry> = emptyList(),
    val scripts: List<Script> = emptyList(),
    val assertions: List<Assertion> = emptyList(),
    val actions: List<ActionSetVariable> = emptyList()
)
