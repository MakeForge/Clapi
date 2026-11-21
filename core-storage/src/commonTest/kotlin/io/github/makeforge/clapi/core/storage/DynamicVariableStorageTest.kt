package io.github.makeforge.clapi.core.storage

import io.github.makeforge.clapi.core.model.opencollection.CollectionConfig
import io.github.makeforge.clapi.core.model.opencollection.CollectionInfo
import io.github.makeforge.clapi.core.model.opencollection.Description
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariable
import io.github.makeforge.clapi.core.model.opencollection.DynamicVariableParameter
import io.github.makeforge.clapi.core.model.opencollection.Environment
import io.github.makeforge.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.makeforge.clapi.core.model.opencollection.RegularVariable
import io.github.makeforge.clapi.core.model.opencollection.RequestDefaults
import io.github.makeforge.clapi.core.model.opencollection.SecretVariable
import io.github.makeforge.clapi.core.model.opencollection.VariableType
import io.github.makeforge.clapi.core.model.opencollection.VariableValue
import io.github.makeforge.clapi.core.storage.opencollection.OpenCollectionSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DynamicVariableStorageTest {

    private fun sampleDocument(): OpenCollectionDocument {
        val envDynamic = DynamicVariable(
            name = "secondsFromNow",
            type = VariableType.NUMBER,
            script = "const offset = Number(args.seconds || 0);\nreturn 1700000000 + offset;",
            parameters = listOf(
                DynamicVariableParameter(
                    name = "seconds",
                    description = "Offset in seconds added to current time",
                    defaultValue = "0",
                )
            ),
            description = Description("Generates an epoch timestamp with an optional seconds offset"),
            disabled = false,
        )
        val colDynamic = DynamicVariable(
            name = "requestSig",
            type = VariableType.STRING,
            script = "return context.request.method + ':' + context.request.url;",
        )
        return OpenCollectionDocument(
            info = CollectionInfo(name = "Dynamic Variables Test Collection"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Production",
                        variables = listOf(
                            RegularVariable(name = "baseUrl", value = VariableValue.of("https://api.example.com")),
                            SecretVariable(name = "apiKey"),
                            envDynamic,
                        ),
                    )
                )
            ),
            request = RequestDefaults(
                variables = listOf(colDynamic),
            ),
        )
    }

    @Test
    fun roundtrips_dynamic_variables_in_opencollection_json() {
        val doc = sampleDocument()
        val jsonText = OpenCollectionSerializer.serializeJson(doc)
        assertTrue(jsonText.contains("\"type\": \"number\""))
        assertTrue(jsonText.contains("\"secondsFromNow\""))

        val decoded = OpenCollectionSerializer.deserializeJson(jsonText)
        val envVars = decoded.config?.environments?.firstOrNull()?.variables.orEmpty()
        assertEquals(3, envVars.size)
        assertIs<RegularVariable>(envVars[0])
        assertIs<SecretVariable>(envVars[1])
        val dyn = assertIs<DynamicVariable>(envVars[2])
        assertEquals("secondsFromNow", dyn.name)
        assertEquals(VariableType.NUMBER, dyn.type)
        assertTrue(dyn.script.contains("1700000000"))
        assertEquals(1, dyn.parameters.size)
        assertEquals("seconds", dyn.parameters[0].name)
        assertEquals("0", dyn.parameters[0].defaultValue)
        assertEquals("Generates an epoch timestamp with an optional seconds offset", dyn.description?.content)

        val colVars = decoded.request?.variables.orEmpty()
        assertEquals(1, colVars.size)
        val colDyn = assertIs<DynamicVariable>(colVars[0])
        assertEquals("requestSig", colDyn.name)
        assertEquals(VariableType.STRING, colDyn.type)
        assertEquals("return context.request.method + ':' + context.request.url;", colDyn.script)
    }

    @Test
    fun roundtrips_dynamic_variables_in_opencollection_yaml() {
        val doc = sampleDocument()
        val yamlText = OpenCollectionSerializer.serializeYaml(doc)
        assertTrue(yamlText.contains("type: number") || yamlText.contains("type: \"number\""))
        assertTrue(yamlText.contains("secondsFromNow"))

        val decoded = OpenCollectionSerializer.deserializeYaml(yamlText)
        val envVars = decoded.config?.environments?.firstOrNull()?.variables.orEmpty()
        assertEquals(3, envVars.size)
        val dyn = assertIs<DynamicVariable>(envVars[2])
        assertEquals("secondsFromNow", dyn.name)
        assertEquals(VariableType.NUMBER, dyn.type)
        assertTrue(dyn.script.contains("1700000000"))
        assertEquals(1, dyn.parameters.size)
        assertEquals("seconds", dyn.parameters[0].name)
        assertEquals("Offset in seconds added to current time", dyn.parameters[0].description)
        assertEquals("0", dyn.parameters[0].defaultValue)

        val colVars = decoded.request?.variables.orEmpty()
        assertEquals(1, colVars.size)
        val colDyn = assertIs<DynamicVariable>(colVars[0])
        assertEquals("requestSig", colDyn.name)
        assertEquals(VariableType.STRING, colDyn.type)
    }
}
