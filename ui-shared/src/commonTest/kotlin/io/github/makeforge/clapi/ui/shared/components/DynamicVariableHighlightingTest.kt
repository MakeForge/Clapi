package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicVariableHighlightingTest {

    @Test
    fun dynamic_variables_are_included_in_allDefinedVariableNames_and_highlighted_orange() {
        val state = AppState().apply {
            environments.clear()
            val env = EnvState("Dev").apply {
                variables.add(
                    MutableKeyValue(
                        key = "number",
                        value = "",
                        isDynamic = true,
                        dynamicScript = "return 42;",
                        variableType = VariableType.NUMBER,
                    )
                )
            }
            environments.add(env)
            selectedEnvIndex = 0
        }

        val definedNames = state.allDefinedVariableNames()
        assertTrue(definedNames.contains("number"), "allDefinedVariableNames must contain user dynamic variable 'number'")

        val line = """{ "x": {{number}} }"""
        val ranges = variableRangesForLine(line, definedNames)

        assertEquals(1, ranges.size)
        assertEquals(VariableHighlightColor, ranges[0].second, "Dynamic variable 'number' must be highlighted orange (VariableHighlightColor), not red")
    }

    @Test
    fun builtin_dynamic_variables_are_highlighted_orange_without_explicit_definition() {
        val line = """{ "ts": {{${'$'}timestamp}} }"""
        val ranges = variableRangesForLine(line, emptySet())

        assertEquals(1, ranges.size)
        assertEquals(VariableHighlightColor, ranges[0].second, "Built-in dynamic variable '{{${'$'}timestamp}}' must be highlighted orange")
    }

    @Test
    fun truly_unresolved_variables_are_highlighted_red() {
        val line = """{ "missing": {{notDefinedVar}} }"""
        val ranges = variableRangesForLine(line, setOf("otherVar"))

        assertEquals(1, ranges.size)
        assertEquals(VariableUnresolvedColor, ranges[0].second, "Undefined variable must be highlighted red")
    }
}
