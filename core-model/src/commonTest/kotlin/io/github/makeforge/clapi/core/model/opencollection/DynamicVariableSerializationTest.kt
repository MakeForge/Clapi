package io.github.makeforge.clapi.core.model.opencollection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DynamicVariableSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun variableType_fromValue_resolves_dynamic() {
        assertEquals(VariableType.DYNAMIC, VariableType.fromValue("dynamic"))
        assertEquals(VariableType.DYNAMIC, VariableType.fromValue("DYNAMIC"))
        assertEquals("dynamic", VariableType.DYNAMIC.toString())
    }

    @Test
    fun dynamicVariable_serializes_and_deserializes_roundtrip() {
        val dynamicVar = DynamicVariable(
            name = "secondsFromNow",
            script = "const offset = Number(args.seconds || 0);\nreturn new Date(Date.now() + offset * 1000).toISOString();",
            parameters = listOf(
                DynamicVariableParameter(
                    name = "seconds",
                    description = "Offset in seconds",
                    defaultValue = "0",
                )
            ),
            description = Description("ISO timestamp offset from now"),
            disabled = false,
        )

        val encoded = json.encodeToString(VariableEntrySerializer, dynamicVar)
        val jsonObj = json.parseToJsonElement(encoded).jsonObject
        assertEquals("dynamic", jsonObj["type"]?.jsonPrimitive?.content)
        assertTrue(encoded.contains("\"secondsFromNow\""))
        assertTrue(encoded.contains("\"script\""))

        val decoded = json.decodeFromString(VariableEntrySerializer, encoded)
        assertIs<DynamicVariable>(decoded)
        assertEquals("secondsFromNow", decoded.name)
        assertEquals(VariableType.DYNAMIC, decoded.type)
        assertEquals(dynamicVar.script, decoded.script)
        assertEquals(1, decoded.parameters.size)
        assertEquals("seconds", decoded.parameters[0].name)
        assertEquals("Offset in seconds", decoded.parameters[0].description)
        assertEquals("0", decoded.parameters[0].defaultValue)
        assertEquals("ISO timestamp offset from now", decoded.description?.content)
        assertEquals(false, decoded.disabled)
    }

    @Test
    fun environment_with_mixed_variables_deserializes_all_types() {
        val rawJson = """
            {
              "name": "Staging",
              "variables": [
                {
                  "name": "baseUrl",
                  "value": "https://staging.example.com"
                },
                {
                  "name": "apiKey",
                  "secret": true
                },
                {
                  "name": "nonce",
                  "type": "dynamic",
                  "script": "return Math.random().toString(36).substring(2);",
                  "parameters": [
                    { "name": "prefix", "defaultValue": "req_" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val env = json.decodeFromString(Environment.serializer(), rawJson)
        assertEquals(3, env.variables.size)
        assertIs<RegularVariable>(env.variables[0])
        assertIs<SecretVariable>(env.variables[1])
        val dyn = assertIs<DynamicVariable>(env.variables[2])
        assertEquals("nonce", dyn.name)
        assertEquals("return Math.random().toString(36).substring(2);", dyn.script)
        assertEquals(1, dyn.parameters.size)
        assertEquals("prefix", dyn.parameters[0].name)
        assertEquals("req_", dyn.parameters[0].defaultValue)
    }

    @Test
    fun dynamicVariable_deserializes_when_only_script_property_present() {
        val rawJson = """
            {
              "name": "legacyDynamic",
              "script": "return 42;"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(VariableEntrySerializer, rawJson)
        val dyn = assertIs<DynamicVariable>(decoded)
        assertEquals("legacyDynamic", dyn.name)
        assertEquals("return 42;", dyn.script)
        assertEquals(VariableType.DYNAMIC, dyn.type)
    }
}
