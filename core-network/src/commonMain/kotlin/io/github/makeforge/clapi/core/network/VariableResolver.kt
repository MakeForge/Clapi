// Modified by MakeForge (2026)
package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable as UserDynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableResolutionException
import io.github.makeforge.clapi.core.scripting.ClapiDynamicVariableEngine
import io.github.makeforge.clapi.core.scripting.DynamicScriptContext
import io.github.makeforge.clapi.core.scripting.DynamicVariableEngine
import io.github.makeforge.clapi.core.scripting.RequestContext

private val variablePattern = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")
private val identifierPattern = Regex("^[a-zA-Z0-9_\\-\$]+$")
private val intPattern = Regex("^-?\\d+$")
private val floatPattern = Regex("^-?\\d+\\.\\d+$")

data class ParsedVariableToken(
    val rawToken: String,
    val name: String,
    val args: List<Any?> = emptyList(),
    val isFunctionCall: Boolean = false,
    val hasMalformedSyntax: Boolean = false,
)

object VariableResolver {
    val dynamicVariables: List<DynamicVariable> = listOf(
        TimestampDynamicVariable,
        TimestampMsFromNowDynamicVariable,
        TimestampMsBeforeNowDynamicVariable,
        IsoTimestampDynamicVariable,
        IsoTimestampFromNowMsDynamicVariable,
        IsoTimestampBeforeNowMsDynamicVariable,
        RandomIntDynamicVariable,
        RandomIntRangeDynamicVariable,
        GuidDynamicVariable,
    )

    val defaultDynamicEngine: DynamicVariableEngine = ClapiDynamicVariableEngine()

    fun resolve(
        value: String,
        variableLayers: List<Map<String, String>>,
        removeUnresolved: Boolean = false,
        userDynamicVariables: List<UserDynamicVariable> = emptyList(),
        requestContext: RequestContext? = null,
        dynamicEngine: DynamicVariableEngine = defaultDynamicEngine,
    ): String {
        if (value.isBlank()) {
            return value
        }

        val activeUserDynMap = userDynamicVariables
            .filter { it.disabled != true && it.name.isNotBlank() }
            .associateBy { it.name.trim() }

        return variablePattern.replace(value) { matchResult ->
            val token = matchResult.groupValues[1].trim()
            val fromLayers = variableLayers.firstNotNullOfOrNull { layer -> layer[token] }
            if (fromLayers != null) {
                return@replace fromLayers
            }

            if (activeUserDynMap.isNotEmpty()) {
                val parsed = parseVariableToken(token)
                val userDyn = activeUserDynMap[parsed.name]
                if (userDyn != null) {
                    if (parsed.hasMalformedSyntax) {
                        throw DynamicVariableResolutionException(
                            "Malformed dynamic variable invocation '{{$token}}' for variable '${userDyn.name}'"
                        )
                    }
                    val scriptContext = buildScriptContext(userDyn, parsed.args, requestContext)
                    val execResult = dynamicEngine.evaluateDynamicVariable(userDyn.script, scriptContext)
                    if (!execResult.success) {
                        throw DynamicVariableResolutionException(
                            "Dynamic variable '${userDyn.name}' execution failed: ${execResult.errorMessage ?: "Unknown error"}"
                        )
                    }
                    val retVal = execResult.returnValue ?: ""
                    validateDynamicReturnType(userDyn, retVal)
                    return@replace retVal
                }
            }

            resolveDynamicToken(token) ?: if (removeUnresolved) "" else matchResult.value
        }
    }

    fun validateDynamicReturnType(dynamicVar: UserDynamicVariable, returnValue: String) {
        val trimmed = returnValue.trim()
        when (dynamicVar.type) {
            VariableType.NUMBER -> {
                if (trimmed.toDoubleOrNull() == null) {
                    throw DynamicVariableResolutionException(
                        "Dynamic variable '${dynamicVar.name}' declared type Number but returned non-numeric value: '$returnValue'"
                    )
                }
            }
            VariableType.BOOLEAN -> {
                if (trimmed != "true" && trimmed != "false") {
                    throw DynamicVariableResolutionException(
                        "Dynamic variable '${dynamicVar.name}' declared type Boolean but returned non-boolean value: '$returnValue'"
                    )
                }
            }
            VariableType.OBJECT -> {
                val isJsonObjOrArr = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]"))
                if (!isJsonObjOrArr) {
                    throw DynamicVariableResolutionException(
                        "Dynamic variable '${dynamicVar.name}' declared type Object/Array but returned non-object value: '$returnValue'"
                    )
                }
            }
            VariableType.STRING, VariableType.NULL -> Unit
        }
    }

    fun resolveDynamicToken(token: String): String? {
        val trimmed = token.trim()
        for (handler in dynamicVariables) {
            if (handler.matches(trimmed)) {
                val resolved = handler.resolve(trimmed)
                if (resolved != null) return resolved
            }
        }
        return null
    }

    fun isDynamicToken(token: String): Boolean {
        val trimmed = token.trim()
        return dynamicVariables.any { it.matches(trimmed) }
    }

    /**
     * Parses a template token inside `{{...}}` into its identifier and optional argument list
     * according to `contracts/dynamic-variable-template-syntax-contract.md`.
     */
    fun parseVariableToken(rawToken: String): ParsedVariableToken {
        val trimmed = rawToken.trim()
        val openParenIdx = trimmed.indexOf('(')
        val closeParenIdx = trimmed.lastIndexOf(')')

        if (openParenIdx == -1 && closeParenIdx == -1) {
            val validId = identifierPattern.matches(trimmed)
            return ParsedVariableToken(
                rawToken = trimmed,
                name = trimmed,
                args = emptyList(),
                isFunctionCall = false,
                hasMalformedSyntax = !validId,
            )
        }

        if (openParenIdx <= 0 || closeParenIdx != trimmed.length - 1 || closeParenIdx < openParenIdx) {
            val candidateName = if (openParenIdx > 0) trimmed.substring(0, openParenIdx).trim() else trimmed
            return ParsedVariableToken(
                rawToken = trimmed,
                name = candidateName,
                args = emptyList(),
                isFunctionCall = true,
                hasMalformedSyntax = true,
            )
        }

        val name = trimmed.substring(0, openParenIdx).trim()
        if (!identifierPattern.matches(name)) {
            return ParsedVariableToken(
                rawToken = trimmed,
                name = name,
                args = emptyList(),
                isFunctionCall = true,
                hasMalformedSyntax = true,
            )
        }

        val argsBody = trimmed.substring(openParenIdx + 1, closeParenIdx).trim()
        if (argsBody.isEmpty()) {
            return ParsedVariableToken(
                rawToken = trimmed,
                name = name,
                args = emptyList(),
                isFunctionCall = true,
                hasMalformedSyntax = false,
            )
        }

        val parsedArgs = parseArgumentList(argsBody)
            ?: return ParsedVariableToken(
                rawToken = trimmed,
                name = name,
                args = emptyList(),
                isFunctionCall = true,
                hasMalformedSyntax = true,
            )

        return ParsedVariableToken(
            rawToken = trimmed,
            name = name,
            args = parsedArgs,
            isFunctionCall = true,
            hasMalformedSyntax = false,
        )
    }

    private fun parseArgumentList(argsBody: String): List<Any?>? {
        val result = mutableListOf<Any?>()
        var i = 0
        val len = argsBody.length

        while (i < len) {
            while (i < len && argsBody[i].isWhitespace()) i++
            if (i >= len) return null // Trailing comma followed only by whitespace

            val ch = argsBody[i]
            if (ch == '"' || ch == '\'') {
                val quote = ch
                i++
                val sb = StringBuilder()
                var closed = false
                while (i < len) {
                    val c = argsBody[i]
                    if (c == '\\') {
                        if (i + 1 >= len) return null
                        val next = argsBody[i + 1]
                        when (next) {
                            '"' -> sb.append('"')
                            '\'' -> sb.append('\'')
                            '\\' -> sb.append('\\')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            else -> sb.append(next)
                        }
                        i += 2
                    } else if (c == quote) {
                        closed = true
                        i++
                        break
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                if (!closed) return null
                result.add(sb.toString())

                while (i < len && argsBody[i].isWhitespace()) i++
                if (i < len) {
                    if (argsBody[i] != ',') return null
                    i++
                    if (i >= len) return null // Trailing comma
                }
            } else {
                val start = i
                while (i < len && argsBody[i] != ',') {
                    val c = argsBody[i]
                    if (c == '"' || c == '\'' || c == '(' || c == ')') return null
                    i++
                }
                val literalStr = argsBody.substring(start, i).trim()
                if (literalStr.isEmpty()) return null
                val parsedLiteral: Any? = when {
                    literalStr == "true" -> true
                    literalStr == "false" -> false
                    literalStr == "null" -> null
                    intPattern.matches(literalStr) -> {
                        val longVal = literalStr.toLongOrNull() ?: return null
                        if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt() else longVal
                    }
                    floatPattern.matches(literalStr) -> literalStr.toDoubleOrNull() ?: return null
                    else -> return null
                }
                result.add(parsedLiteral)

                if (i < len) {
                    // argsBody[i] == ','
                    i++
                    if (i >= len) return null // Trailing comma
                }
            }
        }
        return result
    }

    fun buildScriptContext(
        dynamicVar: UserDynamicVariable,
        parsedArgs: List<Any?>,
        requestContext: RequestContext? = null,
    ): DynamicScriptContext {
        val namedArgs = LinkedHashMap<String, Any?>()
        dynamicVar.parameters.forEachIndexed { index, param ->
            val value = if (index < parsedArgs.size) {
                parsedArgs[index]
            } else {
                parseDefaultValue(param.defaultValue)
            }
            namedArgs[param.name] = value
        }
        val effectivePositional = if (parsedArgs.isNotEmpty()) {
            parsedArgs
        } else {
            dynamicVar.parameters.map { parseDefaultValue(it.defaultValue) }
        }
        return DynamicScriptContext(
            request = requestContext,
            args = namedArgs,
            positionalArgs = effectivePositional,
        )
    }

    fun parseDefaultValue(defaultVal: String?): Any? {
        if (defaultVal == null) return null
        val trimmed = defaultVal.trim()
        return when {
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" -> null
            intPattern.matches(trimmed) -> {
                val l = trimmed.toLongOrNull()
                if (l != null && l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l ?: trimmed
            }
            floatPattern.matches(trimmed) -> trimmed.toDoubleOrNull() ?: trimmed
            (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) ->
                trimmed.substring(1, trimmed.length - 1)
            (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2) ->
                trimmed.substring(1, trimmed.length - 1)
            else -> defaultVal
        }
    }
}
