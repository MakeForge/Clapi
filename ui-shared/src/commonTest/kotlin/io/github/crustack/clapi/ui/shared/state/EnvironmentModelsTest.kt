package io.github.crustack.clapi.ui.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EnvironmentModelsTest {

    @Test
    fun toVariableMap_excludes_blank_secret_true_variables_so_lower_layers_are_not_shadowed() {
        val env = EnvState(
            name = "Production",
            variables = listOf(
                MutableKeyValue(key = "BASE_URL", value = "https://api.prod.example.com", enabled = true, secret = false),
                MutableKeyValue(key = "API_SECRET", value = "", enabled = true, secret = true),
                MutableKeyValue(key = "WHITESPACE_SECRET", value = "   ", enabled = true, secret = true),
                MutableKeyValue(key = "EMPTY_REGULAR", value = "", enabled = true, secret = false),
            ),
        )

        val map = env.toVariableMap()
        assertEquals("https://api.prod.example.com", map["BASE_URL"])
        assertEquals("", map["EMPTY_REGULAR"])
        assertFalse(map.containsKey("API_SECRET"), "Blank secret:true variable must be excluded from toVariableMap()")
        assertFalse(map.containsKey("WHITESPACE_SECRET"), "Whitespace-only secret:true variable must be excluded")
    }

    @Test
    fun toVariableMap_includes_secret_true_variables_when_non_blank_value_is_present() {
        val env = EnvState(
            name = "Staging",
            variables = listOf(
                MutableKeyValue(key = "API_SECRET", value = "local-override-secret", enabled = true, secret = true),
                MutableKeyValue(key = "DISABLED_SECRET", value = "should-not-appear", enabled = false, secret = true),
            ),
        )

        val map = env.toVariableMap()
        assertEquals("local-override-secret", map["API_SECRET"])
        assertFalse(map.containsKey("DISABLED_SECRET"))
    }
}
