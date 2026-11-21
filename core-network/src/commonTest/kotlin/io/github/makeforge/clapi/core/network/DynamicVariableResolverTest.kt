package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable as UserDynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableResolutionException
import io.github.makeforge.clapi.core.scripting.RequestContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DynamicVariableResolverTest {

    @Test
    fun resolves_non_parameterized_user_dynamic_variable() {
        val dynVar = UserDynamicVariable(
            name = "computedId",
            script = "return 'ID_' + (20 + 22);",
        )
        val resolved = VariableResolver.resolve(
            value = "https://api.example.com/items/{{computedId}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(dynVar),
        )
        assertEquals("https://api.example.com/items/ID_42", resolved)
    }

    @Test
    fun resolves_parameterized_seconds_from_now_dynamic_variable() {
        val secondsFromNow = UserDynamicVariable(
            name = "secondsFromNow",
            script = """
                const baseEpochMs = 1700000000000;
                const offsetSec = Number(args.seconds || 0);
                return new Date(baseEpochMs + offsetSec * 1000).toISOString();
            """.trimIndent(),
            parameters = listOf(
                DynamicVariableParameter(name = "seconds", defaultValue = "0")
            ),
        )

        val resolvedWith10 = VariableResolver.resolve(
            value = "ts={{secondsFromNow(10)}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(secondsFromNow),
        )
        assertEquals("ts=2023-11-14T22:13:30.000Z", resolvedWith10)

        val resolvedDefault = VariableResolver.resolve(
            value = "ts={{secondsFromNow}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(secondsFromNow),
        )
        assertEquals("ts=2023-11-14T22:13:20.000Z", resolvedDefault)
    }

    @Test
    fun executes_freshly_for_each_request_resolution() {
        val randomNonce = UserDynamicVariable(
            name = "randomNonce",
            script = "return Math.random().toString(36).substring(2) + '_' + Date.now();",
        )
        val first = VariableResolver.resolve(
            value = "{{randomNonce}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(randomNonce),
        )
        val second = VariableResolver.resolve(
            value = "{{randomNonce}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(randomNonce),
        )
        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun disabled_dynamic_variable_is_not_resolved() {
        val disabledVar = UserDynamicVariable(
            name = "disabledToken",
            script = "return 'SHOULD_NOT_RUN';",
            disabled = true,
        )
        val resolved = VariableResolver.resolve(
            value = "Bearer {{disabledToken}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(disabledVar),
        )
        assertEquals("Bearer {{disabledToken}}", resolved)
    }

    @Test
    fun injects_request_context_into_dynamic_variable_script() {
        val signVar = UserDynamicVariable(
            name = "reqSignature",
            script = "return context.request.method + ':' + context.request.url + ':' + context.request.headers['X-Env'];",
        )
        val reqCtx = RequestContext(
            url = "https://api.example.com/pay",
            method = "POST",
            headers = mapOf("X-Env" to "prod"),
            name = "Payment Request",
        )
        val resolved = VariableResolver.resolve(
            value = "Sig {{reqSignature}}",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(signVar),
            requestContext = reqCtx,
        )
        assertEquals("Sig POST:https://api.example.com/pay:prod", resolved)
    }

    @Test
    fun throws_descriptive_exception_when_dynamic_script_fails() {
        val brokenVar = UserDynamicVariable(
            name = "brokenVar",
            script = "throw new Error('Token generation failed');",
        )
        val ex = assertFailsWith<DynamicVariableResolutionException> {
            VariableResolver.resolve(
                value = "{{brokenVar}}",
                variableLayers = emptyList(),
                userDynamicVariables = listOf(brokenVar),
            )
        }
        assertTrue(ex.message!!.contains("brokenVar"))
        assertTrue(ex.message!!.contains("Token generation failed"))
    }
}
