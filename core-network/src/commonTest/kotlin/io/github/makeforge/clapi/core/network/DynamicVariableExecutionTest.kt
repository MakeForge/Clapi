package io.github.makeforge.clapi.core.network

import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable as UserDynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.network.dynamicvar.DynamicVariableResolutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicVariableExecutionTest {

    @Test
    fun valid_number_dynamic_variable_resolves_successfully() {
        val numVar = UserDynamicVariable(
            name = "validNum",
            script = "return 42;",
            type = VariableType.NUMBER,
        )
        val resolved = VariableResolver.resolve(
            value = """{"count": {{validNum}}}""",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(numVar),
        )
        assertEquals("""{"count": 42}""", resolved)
    }

    @Test
    fun invalid_number_dynamic_variable_throws_descriptive_error() {
        val invalidNumVar = UserDynamicVariable(
            name = "invalidNum",
            script = "return 'not_a_number';",
            type = VariableType.NUMBER,
        )
        val ex = assertFailsWith<DynamicVariableResolutionException> {
            VariableResolver.resolve(
                value = """{"count": {{invalidNum}}}""",
                variableLayers = emptyList(),
                userDynamicVariables = listOf(invalidNumVar),
            )
        }
        assertTrue(ex.message!!.contains("invalidNum"))
        assertTrue(ex.message!!.contains("Number"))
        assertTrue(ex.message!!.contains("not_a_number"))
    }

    @Test
    fun valid_boolean_dynamic_variable_resolves_successfully() {
        val boolVar = UserDynamicVariable(
            name = "isActive",
            script = "return true;",
            type = VariableType.BOOLEAN,
        )
        val resolved = VariableResolver.resolve(
            value = """{"active": {{isActive}}}""",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(boolVar),
        )
        assertEquals("""{"active": true}""", resolved)
    }

    @Test
    fun invalid_boolean_dynamic_variable_throws_descriptive_error() {
        val invalidBoolVar = UserDynamicVariable(
            name = "invalidBool",
            script = "return 'yes';",
            type = VariableType.BOOLEAN,
        )
        val ex = assertFailsWith<DynamicVariableResolutionException> {
            VariableResolver.resolve(
                value = """{"active": {{invalidBool}}}""",
                variableLayers = emptyList(),
                userDynamicVariables = listOf(invalidBoolVar),
            )
        }
        assertTrue(ex.message!!.contains("invalidBool"))
        assertTrue(ex.message!!.contains("Boolean"))
    }

    @Test
    fun valid_object_dynamic_variable_resolves_successfully() {
        val objVar = UserDynamicVariable(
            name = "metaObj",
            script = """return JSON.stringify({ role: "admin", level: 5 });""",
            type = VariableType.OBJECT,
        )
        val resolved = VariableResolver.resolve(
            value = """{"meta": {{metaObj}}}""",
            variableLayers = emptyList(),
            userDynamicVariables = listOf(objVar),
        )
        assertEquals("""{"meta": {"role":"admin","level":5}}""", resolved)
    }

    @Test
    fun invalid_object_dynamic_variable_throws_descriptive_error() {
        val invalidObjVar = UserDynamicVariable(
            name = "invalidObj",
            script = "return 'plain string';",
            type = VariableType.OBJECT,
        )
        val ex = assertFailsWith<DynamicVariableResolutionException> {
            VariableResolver.resolve(
                value = """{"meta": {{invalidObj}}}""",
                variableLayers = emptyList(),
                userDynamicVariables = listOf(invalidObjVar),
            )
        }
        assertTrue(ex.message!!.contains("invalidObj"))
        assertTrue(ex.message!!.contains("Object/Array"))
    }
}
