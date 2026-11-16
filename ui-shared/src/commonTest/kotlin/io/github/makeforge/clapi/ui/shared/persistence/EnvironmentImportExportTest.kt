package io.github.makeforge.clapi.ui.shared.persistence

import io.github.makeforge.clapi.ui.shared.state.AppState
import io.github.makeforge.clapi.ui.shared.state.EnvState
import io.github.makeforge.clapi.ui.shared.state.MutableExternalSecret
import io.github.makeforge.clapi.ui.shared.state.MutableKeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvironmentImportExportTest {

    @Test
    fun testExportSanitizesSecretVariableValues() {
        val env = EnvState(
            name = "SecureEnv",
            color = "#EF4444",
            extends = "BaseEnv",
            variables = listOf(
                MutableKeyValue("PUBLIC_URL", "https://api.example.com", enabled = true, secret = false),
                MutableKeyValue("TOP_SECRET_API_KEY", "super-secret-token-12345", enabled = true, secret = true),
            ),
        )

        val exportedJson = ImportExportRepository.exportEnvironmentToString(env)
        val root = Json.parseToJsonElement(exportedJson).jsonObject

        assertEquals("clapiEnvironment", root["type"]?.jsonPrimitive?.content)
        assertEquals("SecureEnv", root["name"]?.jsonPrimitive?.content)
        assertEquals("#EF4444", root["color"]?.jsonPrimitive?.content)
        assertEquals("BaseEnv", root["extends"]?.jsonPrimitive?.content)

        val vars = root["variables"]?.jsonObject
        assertNotNull(vars)
        assertEquals("https://api.example.com", vars["PUBLIC_URL"]?.jsonPrimitive?.content)
        // Value for secret: true must be blank in exported json!
        assertEquals("", vars["TOP_SECRET_API_KEY"]?.jsonPrimitive?.content)
        assertFalse(exportedJson.contains("super-secret-token-12345"), "Plaintext secret token must not be exported")
    }

    @Test
    fun testExportAndImportPreservesExternalSecretsConfig() {
        val env = EnvState(
            name = "CloudSecretsEnv",
            color = "#10B981",
            extends = "Staging",
            externalSecretsProvider = "aws-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "DB_PASS",
                    path = "prod/app/db",
                    targetField = "password",
                    description = "RDS DB Password",
                    enabled = true,
                    customFilters = mapOf("env" to "prod"),
                ),
            ),
        )

        val exportedJson = ImportExportRepository.exportEnvironmentToString(env)
        assertTrue(exportedJson.contains("aws-secrets-manager"))
        assertTrue(exportedJson.contains("prod/app/db"))
        assertTrue(exportedJson.contains("DB_PASS"))

        val state = AppState(openDefaultTab = false)
        val importedName = ImportExportRepository.importEnvironmentFromString(state, exportedJson)
        assertEquals("CloudSecretsEnv", importedName)

        val importedEnv = state.environments.first { it.name == importedName }
        assertEquals("#10B981", importedEnv.color)
        assertEquals("Staging", importedEnv.extends)
        assertEquals("aws-secrets-manager", importedEnv.externalSecretsProvider)
        assertEquals(1, importedEnv.externalSecrets.size)

        val secret = importedEnv.externalSecrets[0]
        assertEquals("DB_PASS", secret.name)
        assertEquals("prod/app/db", secret.path)
        assertEquals("password", secret.targetField)
        assertEquals("RDS DB Password", secret.description)
        assertTrue(secret.enabled)
        assertEquals("prod", secret.customFilters["env"])
    }
}
