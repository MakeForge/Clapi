package io.github.crustack.clapi.core.storage.opencollection

import io.github.crustack.clapi.core.model.opencollection.CollectionConfig
import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.model.opencollection.ExternalSecrets
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.RegularVariable
import io.github.crustack.clapi.core.model.opencollection.VariableValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenCollectionExternalSecretsTest {

    private fun createDocumentWithExternalSecrets(): OpenCollectionDocument {
        return OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "Payments API",
                version = "1.0.0",
            ),
            bundled = true,
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Staging",
                        variables = listOf(
                            RegularVariable(name = "baseUrl", value = VariableValue.of("https://staging.example.com")),
                        ),
                        externalSecrets = ExternalSecrets(
                            type = "hashicorp-vault-server",
                            variables = listOf(
                                HashicorpVaultExternalSecret(
                                    name = "client_id",
                                    path = "payment/foo",
                                    description = Description("Gateway Client ID"),
                                    disabled = false,
                                ),
                                HashicorpVaultExternalSecret(
                                    name = "client_secret",
                                    path = "payment/foo",
                                    disabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            items = emptyList(),
        )
    }

    @Test
    fun roundtrip_json_serialization_preserves_external_secrets_structure() {
        val doc = createDocumentWithExternalSecrets()
        val serializedJson = OpenCollectionSerializer.serializeJson(doc)

        // Verify JSON contains externalSecrets and keys
        assertTrue(serializedJson.contains("\"externalSecrets\""))
        assertTrue(serializedJson.contains("\"hashicorp-vault-server\""))
        assertTrue(serializedJson.contains("\"client_id\""))
        assertTrue(serializedJson.contains("\"client_secret\""))
        assertTrue(serializedJson.contains("\"payment/foo\""))

        // Verify zero credential leakage
        assertFalse(serializedJson.contains("root-dev-token"))
        assertFalse(serializedJson.contains("s.my-secret-vault-token"))
        assertFalse(serializedJson.contains("vaultServerUrl"))

        // Deserialize back and assert equality
        val deserialized = OpenCollectionSerializer.deserializeJson(serializedJson)
        val env = deserialized.config?.environments?.firstOrNull()
        assertNotNull(env)
        assertEquals("Staging", env.name)
        val extSecrets = env.externalSecrets
        assertNotNull(extSecrets)
        assertEquals(ExternalSecretType.HASHICORP_VAULT_SERVER, extSecrets.type)
        assertEquals(2, extSecrets.variables.size)

        val v1 = extSecrets.variables[0] as HashicorpVaultExternalSecret
        assertEquals("client_id", v1.name)
        assertEquals("payment/foo", v1.path)
        assertEquals("Gateway Client ID", v1.description?.content)
        assertFalse(v1.disabled == true)

        val v2 = extSecrets.variables[1] as HashicorpVaultExternalSecret
        assertEquals("client_secret", v2.name)
        assertEquals("payment/foo", v2.path)
        assertEquals(true, v2.disabled)
    }

    @Test
    fun roundtrip_yaml_serialization_preserves_external_secrets_structure() {
        val doc = createDocumentWithExternalSecrets()
        val serializedYaml = OpenCollectionSerializer.serializeYaml(doc)

        // Verify YAML contains externalSecrets and structure
        assertTrue(serializedYaml.contains("externalSecrets:"))
        assertTrue(serializedYaml.contains("type: hashicorp-vault-server"))
        assertTrue(serializedYaml.contains("client_id"))
        assertTrue(serializedYaml.contains("payment/foo"))

        // Deserialize back and assert equality
        val deserialized = OpenCollectionSerializer.deserializeYaml(serializedYaml)
        val env = deserialized.config?.environments?.firstOrNull()
        assertNotNull(env)
        val extSecrets = env.externalSecrets
        assertNotNull(extSecrets)
        assertEquals(ExternalSecretType.HASHICORP_VAULT_SERVER, extSecrets.type)
        assertEquals(2, extSecrets.variables.size)

        val v1 = extSecrets.variables[0] as HashicorpVaultExternalSecret
        assertEquals("client_id", v1.name)
        assertEquals("payment/foo", v1.path)
    }

    @Test
    fun zero_credential_leakage_guarantees_tokens_never_serialized() {
        val doc = createDocumentWithExternalSecrets()
        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)

        val forbiddenKeywords = listOf(
            "vaultToken", "X-Vault-Token", "root-dev-token", "vaultServerUrl",
            "clapi_secrets", "vaultMountPath", "vaultNamespace",
        )

        for (kw in forbiddenKeywords) {
            assertFalse(jsonOutput.contains(kw), "JSON export leaked credential keyword: $kw")
            assertFalse(yamlOutput.contains(kw), "YAML export leaked credential keyword: $kw")
        }
    }
}
