package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable as UserDynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicVariableSyntaxTest {

    @Test
    fun parses_plain_identifier_token() {
        val parsed = VariableResolver.parseVariableToken("secondsFromNow")
        assertEquals("secondsFromNow", parsed.name)
        assertTrue(parsed.args.isEmpty())
        assertFalse(parsed.isFunctionCall)
        assertFalse(parsed.hasMalformedSyntax)
    }

    @Test
    fun parses_empty_parentheses_function_call() {
        val parsed = VariableResolver.parseVariableToken("secondsFromNow()")
        assertEquals("secondsFromNow", parsed.name)
        assertTrue(parsed.args.isEmpty())
        assertTrue(parsed.isFunctionCall)
        assertFalse(parsed.hasMalformedSyntax)
    }

    @Test
    fun parses_single_and_negative_integer_arguments() {
        val p1 = VariableResolver.parseVariableToken("secondsFromNow(10)")
        assertEquals("secondsFromNow", p1.name)
        assertEquals(listOf<Any?>(10), p1.args)
        assertTrue(p1.isFunctionCall)
        assertFalse(p1.hasMalformedSyntax)

        val p2 = VariableResolver.parseVariableToken("secondsFromNow(-5)")
        assertEquals("secondsFromNow", p2.name)
        assertEquals(listOf<Any?>(-5), p2.args)
        assertFalse(p2.hasMalformedSyntax)
    }

    @Test
    fun parses_multiple_mixed_primitive_arguments() {
        val parsed = VariableResolver.parseVariableToken("""formatOrder("ORD-", 42, 'v2', true, false, null, 3.14)""")
        assertEquals("formatOrder", parsed.name)
        assertEquals(
            listOf<Any?>("ORD-", 42, "v2", true, false, null, 3.14),
            parsed.args,
        )
        assertTrue(parsed.isFunctionCall)
        assertFalse(parsed.hasMalformedSyntax)
    }

    @Test
    fun parses_escaped_quotes_and_commas_inside_string_arguments() {
        val parsed = VariableResolver.parseVariableToken("""greet("Hello, \"World\"!", 'It\'s fine')""")
        assertEquals("greet", parsed.name)
        assertEquals(listOf<Any?>("""Hello, "World"!""", "It's fine"), parsed.args)
        assertFalse(parsed.hasMalformedSyntax)
    }

    @Test
    fun flags_malformed_function_call_syntax() {
        assertTrue(VariableResolver.parseVariableToken("secondsFromNow(10").hasMalformedSyntax)
        assertTrue(VariableResolver.parseVariableToken("secondsFromNow(\"unclosed)").hasMalformedSyntax)
        assertTrue(VariableResolver.parseVariableToken("secondsFromNow(10, )").hasMalformedSyntax)
        assertTrue(VariableResolver.parseVariableToken("secondsFromNow(, 10)").hasMalformedSyntax)
        assertTrue(VariableResolver.parseVariableToken("(10)").hasMalformedSyntax)
    }

    @Test
    fun maps_positional_and_default_arguments_in_buildScriptContext() {
        val dynVar = UserDynamicVariable(
            name = "formatTag",
            script = "return prefix + ':' + count;",
            parameters = listOf(
                DynamicVariableParameter(name = "prefix", defaultValue = "TAG"),
                DynamicVariableParameter(name = "count", defaultValue = "1"),
                DynamicVariableParameter(name = "optionalNote", defaultValue = null),
            ),
        )

        val ctxPartial = VariableResolver.buildScriptContext(dynVar, listOf("CUSTOM"))
        assertEquals("CUSTOM", ctxPartial.args["prefix"])
        assertEquals(1, ctxPartial.args["count"])
        assertNull(ctxPartial.args["optionalNote"])
    }
}
