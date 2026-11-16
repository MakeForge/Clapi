package io.github.crustack.core.model.opencollection

import kotlinx.serialization.Serializable

@Serializable
data class Script(
    val type: String, // "before-request", "after-response", "tests", "hooks"
    val code: String
)

@Serializable
data class Assertion(
    val expression: String,
    val operator: String,
    val value: String? = null,
    val disabled: Boolean? = null,
    val description: Description? = null
)

@Serializable
data class ActionSelector(
    val expression: String,
    val method: String = "jsonq"
)

@Serializable
data class ActionTargetVariable(
    val name: String,
    val scope: String // "runtime", "request", "folder", "collection", "environment"
)

@Serializable
data class ActionSetVariable(
    val type: String = "set-variable",
    val description: Description? = null,
    val phase: String? = null, // "before-request", "after-response"
    val selector: ActionSelector,
    val variable: ActionTargetVariable,
    val disabled: Boolean? = null
)

@Serializable
data class HttpRequestRuntime(
    val variables: List<VariableEntry> = emptyList(),
    val scripts: List<Script> = emptyList(),
    val assertions: List<Assertion> = emptyList(),
    val actions: List<ActionSetVariable> = emptyList()
)
