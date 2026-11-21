package io.github.makeforge.clapi.core.network.dynamicvar

import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.core.scripting.DynamicVariableEngine

class UserScriptDynamicVariableProvider(
    private val defaultEngine: DynamicVariableEngine = VariableResolver.defaultDynamicEngine,
) : DynamicVariableProvider {

    override val name: String = "UserScriptDynamicVariableProvider"

    override fun supports(token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("_auth.") || trimmed.startsWith("_secret.")) return false
        return true
    }

    override suspend fun resolve(
        tokens: Set<String>,
        context: DynamicVariableContext,
    ): Map<String, String> {
        if (context.userDynamicVariables.isEmpty()) return emptyMap()

        val activeUserDynMap = context.userDynamicVariables
            .filter { it.disabled != true && it.name.isNotBlank() }
            .associateBy { it.name.trim() }
        if (activeUserDynMap.isEmpty()) return emptyMap()

        val engine = context.dynamicEngine ?: defaultEngine
        val resolved = LinkedHashMap<String, String>()

        for (rawToken in tokens) {
            val parsed = VariableResolver.parseVariableToken(rawToken)
            val dynamicVar = activeUserDynMap[parsed.name] ?: continue

            if (parsed.hasMalformedSyntax) {
                val msg = "Malformed dynamic variable invocation '{{$rawToken}}' for variable '${dynamicVar.name}'"
                context.log?.invoke(msg, true)
                throw DynamicVariableResolutionException(msg)
            }

            val scriptContext = VariableResolver.buildScriptContext(
                dynamicVar = dynamicVar,
                parsedArgs = parsed.args,
                requestContext = context.requestContext,
            )

            val execResult = engine.executeDynamicVariable(
                script = dynamicVar.script,
                context = scriptContext,
            )

            execResult.consoleLogs.forEach { logLine ->
                context.log?.invoke("[DynamicVar:${dynamicVar.name}] $logLine", false)
            }

            if (!execResult.success) {
                val msg = "Dynamic variable '${dynamicVar.name}' execution failed: ${execResult.errorMessage ?: "Unknown error"}"
                context.log?.invoke(msg, true)
                throw DynamicVariableResolutionException(msg)
            }

            val retVal = execResult.returnValue ?: ""
            try {
                VariableResolver.validateDynamicReturnType(dynamicVar, retVal)
            } catch (e: DynamicVariableResolutionException) {
                context.log?.invoke(e.message ?: "Dynamic variable type mismatch", true)
                throw e
            }

            resolved[rawToken.trim()] = retVal
        }

        return resolved
    }
}
