package io.github.makeforge.clapi.core.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DynamicVariableEngineTest {

    private val engine = ClapiDynamicVariableEngine()

    @Test
    fun evaluates_explicit_return_statement() = runTest {
        val result = engine.executeDynamicVariable(
            script = "const a = 21;\nreturn 'VAL_' + (a * 2);",
        )
        assertTrue(result.success, "Expected success but got error: ${result.errorMessage}")
        assertEquals("VAL_42", result.returnValue)
    }

    @Test
    fun evaluates_bare_expression_without_return_statement() = runTest {
        val result = engine.executeDynamicVariable(
            script = "10 + 32",
        )
        assertTrue(result.success)
        assertEquals("42", result.returnValue)
    }

    @Test
    fun coerces_number_boolean_and_object_return_values() = runTest {
        val numRes = engine.executeDynamicVariable("return 123.45;")
        assertTrue(numRes.success)
        assertEquals("123.45", numRes.returnValue)

        val boolRes = engine.executeDynamicVariable("return true;")
        assertTrue(boolRes.success)
        assertEquals("true", boolRes.returnValue)

        val objRes = engine.executeDynamicVariable("return { id: 1, active: true };")
        assertTrue(objRes.success)
        assertEquals("""{"id":1,"active":true}""", objRes.returnValue)

        val nullRes = engine.executeDynamicVariable("return null;")
        assertTrue(nullRes.success)
        assertEquals("", nullRes.returnValue)
    }

    @Test
    fun binds_named_and_positional_parameters() = runTest {
        val ctx = DynamicScriptContext(
            args = mapOf("seconds" to 10, "prefix" to "TS_"),
            positionalArgs = listOf(10, "TS_"),
        )
        val resultNamed = engine.executeDynamicVariable(
            script = "return prefix + (seconds * 1000);",
            context = ctx,
        )
        assertTrue(resultNamed.success, "Named binding failed: ${resultNamed.errorMessage}")
        assertEquals("TS_10000", resultNamed.returnValue)

        val resultArgsObj = engine.executeDynamicVariable(
            script = "return args.prefix + (args.seconds * 2) + '_' + args[0];",
            context = ctx,
        )
        assertTrue(resultArgsObj.success)
        assertEquals("TS_20_10", resultArgsObj.returnValue)
    }

    @Test
    fun captures_console_logs() = runTest {
        val result = engine.executeDynamicVariable(
            script = """
                console.log("step", 1);
                console.warn("careful");
                return "done";
            """.trimIndent(),
        )
        assertTrue(result.success)
        assertEquals("done", result.returnValue)
        assertEquals(listOf("step 1", "careful"), result.consoleLogs)
    }

    @Test
    fun reports_syntax_and_runtime_errors_cleanly() = runTest {
        val syntaxErr = engine.executeDynamicVariable("function broken(")
        assertFalse(syntaxErr.success)
        assertNotNull(syntaxErr.errorMessage)

        val runtimeErr = engine.executeDynamicVariable("throw new Error('Custom script failure');")
        assertFalse(runtimeErr.success)
        assertTrue(runtimeErr.errorMessage!!.contains("Custom script failure"))
    }

    @Test
    fun enforces_execution_timeout_on_infinite_loop() = runTest {
        val result = engine.executeDynamicVariable(
            script = "while (true) {}",
            timeoutMs = 200L,
        )
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(
            result.errorMessage!!.contains("timed out", ignoreCase = true),
            "Expected timeout message but got: ${result.errorMessage}",
        )
    }

    @Test
    fun supports_btoa_and_atob_helpers() = runTest {
        val result = engine.executeDynamicVariable(
            script = "const encoded = btoa('clapi:secret'); return encoded + ':' + atob(encoded);",
        )
        assertTrue(result.success, "Error: ${result.errorMessage}")
        assertEquals("Y2xhcGk6c2VjcmV0:clapi:secret", result.returnValue)
    }
}
