package io.github.crustack.clapi.core.storage.opencollection

import io.github.crustack.clapi.core.model.opencollection.AwsSecretsManagerExternalSecret
import io.github.crustack.clapi.core.model.opencollection.AzureKeyVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.GcpSecretsManagerExternalSecret
import io.github.crustack.clapi.core.model.opencollection.KeeperSecurityExternalSecret
import io.github.crustack.clapi.core.model.opencollection.CollectionConfig
import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.Description
import io.github.crustack.clapi.core.model.opencollection.Environment
import io.github.crustack.clapi.core.model.opencollection.ExternalSecretType
import io.github.crustack.clapi.core.model.opencollection.ExternalSecrets
import io.github.crustack.clapi.core.model.opencollection.HashicorpVaultExternalSecret
import io.github.crustack.clapi.core.model.opencollection.KeePassXcExternalSecret
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
    fun roundtrip_keepassxc_external_secrets_json_and_yaml() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "KeePassXC Collection",
                version = "1.0.0",
            ),
            bundled = true,
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Production",
                        externalSecrets = ExternalSecrets(
                            type = "keepassxc",
                            variables = listOf(
                                KeePassXcExternalSecret(
                                    name = "api_token",
                                    url = "https://api.example.com",
                                    targetField = "password",
                                    customFilters = mapOf("login" to "service_user", "tier" to "prod"),
                                    description = Description("KeePassXC API Token"),
                                    disabled = false,
                                ),
                                KeePassXcExternalSecret(
                                    name = "db_user",
                                    url = "https://db.internal",
                                    targetField = "login",
                                    disabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            items = emptyList(),
        )

        // Test JSON roundtrip
        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        assertTrue(jsonOutput.contains("\"keepassxc\""))
        assertTrue(jsonOutput.contains("\"https://api.example.com\""))
        assertTrue(jsonOutput.contains("\"service_user\""))
        assertTrue(jsonOutput.contains("\"targetField\""))

        val deserializedJson = OpenCollectionSerializer.deserializeJson(jsonOutput)
        val envJson = deserializedJson.config?.environments?.firstOrNull()
        assertNotNull(envJson)
        val extJson = envJson.externalSecrets
        assertNotNull(extJson)
        assertEquals(ExternalSecretType.KEEPASSXC, extJson.type)
        assertEquals(2, extJson.variables.size)
        val k1 = extJson.variables[0] as KeePassXcExternalSecret
        assertEquals("api_token", k1.name)
        assertEquals("https://api.example.com", k1.url)
        assertEquals("password", k1.targetField)
        assertEquals("service_user", k1.customFilters["login"])
        assertEquals("prod", k1.customFilters["tier"])
        assertEquals("KeePassXC API Token", k1.description?.content)
        assertEquals(false, k1.disabled)

        val k2 = extJson.variables[1] as KeePassXcExternalSecret
        assertEquals("db_user", k2.name)
        assertEquals("https://db.internal", k2.url)
        assertEquals("login", k2.targetField)
        assertEquals(true, k2.disabled)

        // Test YAML roundtrip
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)
        assertTrue(yamlOutput.contains("type: keepassxc"))
        assertTrue(yamlOutput.contains("api_token"))
        assertTrue(yamlOutput.contains("https://api.example.com"))

        val deserializedYaml = OpenCollectionSerializer.deserializeYaml(yamlOutput)
        val envYaml = deserializedYaml.config?.environments?.firstOrNull()
        assertNotNull(envYaml)
        val extYaml = envYaml.externalSecrets
        assertNotNull(extYaml)
        assertEquals(ExternalSecretType.KEEPASSXC, extYaml.type)
        assertEquals(2, extYaml.variables.size)
        val y1 = extYaml.variables[0] as KeePassXcExternalSecret
        assertEquals("api_token", y1.name)
        assertEquals("https://api.example.com", y1.url)
        assertEquals("password", y1.targetField)
        assertEquals("service_user", y1.customFilters["login"])
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

    @Test
    fun roundtrip_aws_secrets_manager_external_secrets_json_and_yaml() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "AWS Collection",
                version = "1.0.0",
            ),
            bundled = true,
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "AWS Production",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.AWS_SECRETS_MANAGER,
                            variables = listOf(
                                AwsSecretsManagerExternalSecret(
                                    name = "client_id",
                                    secretName = "local/payment/foo",
                                    targetField = "client_id",
                                    customFilters = mapOf("stage" to "prod"),
                                    description = Description("Payment Client ID"),
                                    disabled = false,
                                ),
                                AwsSecretsManagerExternalSecret(
                                    name = "client_secret",
                                    secretName = "local/payment/foo",
                                    targetField = "client_secret",
                                    disabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            items = emptyList(),
        )

        // Test JSON roundtrip
        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        assertTrue(jsonOutput.contains("\"aws-secrets-manager\""))
        assertTrue(jsonOutput.contains("\"local/payment/foo\""))
        assertTrue(jsonOutput.contains("\"client_id\""))
        assertTrue(jsonOutput.contains("\"targetField\""))
        assertTrue(jsonOutput.contains("\"customFilters\""))

        val deserializedJson = OpenCollectionSerializer.deserializeJson(jsonOutput)
        val envJson = deserializedJson.config?.environments?.firstOrNull()
        assertNotNull(envJson)
        val extJson = envJson.externalSecrets
        assertNotNull(extJson)
        assertEquals(ExternalSecretType.AWS_SECRETS_MANAGER, extJson.type)
        assertEquals(2, extJson.variables.size)

        val a1 = extJson.variables[0] as AwsSecretsManagerExternalSecret
        assertEquals("client_id", a1.name)
        assertEquals("local/payment/foo", a1.secretName)
        assertEquals("client_id", a1.targetField)
        assertEquals("prod", a1.customFilters["stage"])
        assertEquals("Payment Client ID", a1.description?.content)
        assertEquals(false, a1.disabled)

        val a2 = extJson.variables[1] as AwsSecretsManagerExternalSecret
        assertEquals("client_secret", a2.name)
        assertEquals("local/payment/foo", a2.secretName)
        assertEquals("client_secret", a2.targetField)
        assertEquals(true, a2.disabled)

        // Test YAML roundtrip
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)
        assertTrue(yamlOutput.contains("type: aws-secrets-manager"))
        assertTrue(yamlOutput.contains("client_id"))
        assertTrue(yamlOutput.contains("local/payment/foo"))

        val deserializedYaml = OpenCollectionSerializer.deserializeYaml(yamlOutput)
        val envYaml = deserializedYaml.config?.environments?.firstOrNull()
        assertNotNull(envYaml)
        val extYaml = envYaml.externalSecrets
        assertNotNull(extYaml)
        assertEquals(ExternalSecretType.AWS_SECRETS_MANAGER, extYaml.type)
        assertEquals(2, extYaml.variables.size)

        val y1 = extYaml.variables[0] as AwsSecretsManagerExternalSecret
        assertEquals("client_id", y1.name)
        assertEquals("local/payment/foo", y1.secretName)
        assertEquals("client_id", y1.targetField)
        assertEquals("prod", y1.customFilters["stage"])
    }

    @Test
    fun zero_credential_leakage_guarantees_aws_keys_never_serialized() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "AWS Credentials Leak Check"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "AWS Env",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.AWS_SECRETS_MANAGER,
                            variables = listOf(
                                AwsSecretsManagerExternalSecret(
                                    name = "payment_key",
                                    secretName = "local/payment/foo",
                                    targetField = "client_secret",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)

        val forbiddenAwsKeywords = listOf(
            "accessKeyId", "secretAccessKey", "sessionToken", "awsSecretAccessKey",
            "awsSessionToken", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY",
            "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            "awsEndpointUrl",
        )

        for (kw in forbiddenAwsKeywords) {
            assertFalse(jsonOutput.contains(kw), "JSON export leaked AWS credential keyword: $kw")
            assertFalse(yamlOutput.contains(kw), "YAML export leaked AWS credential keyword: $kw")
        }
    }

    @Test
    fun roundtrip_json_and_yaml_serialization_preserves_azure_external_secrets_structure() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Azure Secrets API"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Azure Staging",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.AZURE_KEY_VAULT,
                            variables = listOf(
                                AzureKeyVaultExternalSecret(
                                    name = "client_id",
                                    secretName = "payment-foo",
                                    vaultName = "https://custom.vault.azure.net",
                                    targetField = "client_id",
                                    customFilters = mapOf("stage" to "prod"),
                                    description = Description("Gateway Client ID"),
                                    disabled = false,
                                ),
                                AzureKeyVaultExternalSecret(
                                    name = "client_secret",
                                    secretName = "payment-foo",
                                    vaultName = "",
                                    targetField = "client_secret",
                                    disabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        // JSON Roundtrip
        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        assertTrue(jsonOutput.contains("\"azure-key-vault\""))
        assertTrue(jsonOutput.contains("\"payment-foo\""))
        assertTrue(jsonOutput.contains("\"https://custom.vault.azure.net\""))
        assertTrue(jsonOutput.contains("\"client_id\""))

        val deserializedJson = OpenCollectionSerializer.deserializeJson(jsonOutput)
        val envJson = deserializedJson.config?.environments?.firstOrNull()
        assertNotNull(envJson)
        val extJson = envJson.externalSecrets
        assertNotNull(extJson)
        assertEquals(ExternalSecretType.AZURE_KEY_VAULT, extJson.type)
        assertEquals(2, extJson.variables.size)

        val j1 = extJson.variables[0] as AzureKeyVaultExternalSecret
        assertEquals("client_id", j1.name)
        assertEquals("payment-foo", j1.secretName)
        assertEquals("https://custom.vault.azure.net", j1.vaultName)
        assertEquals("client_id", j1.targetField)
        assertEquals("prod", j1.customFilters["stage"])
        assertEquals("Gateway Client ID", j1.description?.content)
        assertFalse(j1.disabled == true)

        val j2 = extJson.variables[1] as AzureKeyVaultExternalSecret
        assertEquals("client_secret", j2.name)
        assertEquals("payment-foo", j2.secretName)
        assertEquals(true, j2.disabled)

        // YAML Roundtrip
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)
        assertTrue(yamlOutput.contains("azure-key-vault"))
        assertTrue(yamlOutput.contains("payment-foo"))
        assertTrue(yamlOutput.contains("https://custom.vault.azure.net"))

        val deserializedYaml = OpenCollectionSerializer.deserializeYaml(yamlOutput)
        val envYaml = deserializedYaml.config?.environments?.firstOrNull()
        assertNotNull(envYaml)
        val extYaml = envYaml.externalSecrets
        assertNotNull(extYaml)
        assertEquals(ExternalSecretType.AZURE_KEY_VAULT, extYaml.type)
        assertEquals(2, extYaml.variables.size)

        val y1 = extYaml.variables[0] as AzureKeyVaultExternalSecret
        assertEquals("client_id", y1.name)
        assertEquals("payment-foo", y1.secretName)
        assertEquals("https://custom.vault.azure.net", y1.vaultName)
        assertEquals("client_id", y1.targetField)
        assertEquals("prod", y1.customFilters["stage"])
    }

    @Test
    fun zero_credential_leakage_guarantees_azure_credentials_never_serialized() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Azure Credentials Leak Check"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Azure Env",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.AZURE_KEY_VAULT,
                            variables = listOf(
                                AzureKeyVaultExternalSecret(
                                    name = "payment_key",
                                    secretName = "payment-foo",
                                    targetField = "client_secret",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)

        val forbiddenAzureKeywords = listOf(
            "azureClientSecret", "clientSecret", "tenantId", "clientId",
            "AZURE_CLIENT_SECRET", "AZURE_TENANT_ID", "AZURE_CLIENT_ID",
            "super-secret-azure-key", "00000000-0000-0000-0000-000000000000",
        )

        for (kw in forbiddenAzureKeywords) {
            assertFalse(jsonOutput.contains(kw), "JSON export leaked Azure credential keyword: $kw")
            assertFalse(yamlOutput.contains(kw), "YAML export leaked Azure credential keyword: $kw")
        }
    }

    @Test
    fun roundtrip_json_and_yaml_serialization_preserves_gcp_external_secrets_structure() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "GCP Secrets API"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "GCP Staging",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.GCP_SECRETS_MANAGER,
                            variables = listOf(
                                GcpSecretsManagerExternalSecret(
                                    name = "payment_client_id",
                                    secretId = "payment-foo",
                                    projectId = "my-custom-project",
                                    version = "latest",
                                    targetField = "client_id",
                                    customFilters = mapOf("env" to "staging"),
                                    description = Description("Payment Gateway Client ID"),
                                    disabled = false,
                                ),
                                GcpSecretsManagerExternalSecret(
                                    name = "payment_client_secret",
                                    secretId = "payment-foo",
                                    projectId = "",
                                    version = "latest",
                                    targetField = "client_secret",
                                    disabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        // JSON Roundtrip
        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        assertTrue(jsonOutput.contains("\"gcp-secrets-manager\""))
        assertTrue(jsonOutput.contains("\"payment-foo\""))
        assertTrue(jsonOutput.contains("\"my-custom-project\""))
        assertTrue(jsonOutput.contains("\"client_id\""))
        assertTrue(jsonOutput.contains("\"latest\""))

        val deserializedJson = OpenCollectionSerializer.deserializeJson(jsonOutput)
        val envJson = deserializedJson.config?.environments?.firstOrNull()
        assertNotNull(envJson)
        val extJson = envJson.externalSecrets
        assertNotNull(extJson)
        assertEquals(ExternalSecretType.GCP_SECRETS_MANAGER, extJson.type)
        assertEquals(2, extJson.variables.size)

        val j1 = extJson.variables[0] as GcpSecretsManagerExternalSecret
        assertEquals("payment_client_id", j1.name)
        assertEquals("payment-foo", j1.secretId)
        assertEquals("my-custom-project", j1.projectId)
        assertEquals("latest", j1.version)
        assertEquals("client_id", j1.targetField)
        assertEquals("staging", j1.customFilters["env"])
        assertEquals("Payment Gateway Client ID", j1.description?.content)
        assertFalse(j1.disabled == true)

        val j2 = extJson.variables[1] as GcpSecretsManagerExternalSecret
        assertEquals("payment_client_secret", j2.name)
        assertEquals("payment-foo", j2.secretId)
        assertEquals("client_secret", j2.targetField)
        assertEquals(true, j2.disabled)

        // YAML Roundtrip
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)
        assertTrue(yamlOutput.contains("gcp-secrets-manager"))
        assertTrue(yamlOutput.contains("payment-foo"))
        assertTrue(yamlOutput.contains("my-custom-project"))

        val deserializedYaml = OpenCollectionSerializer.deserializeYaml(yamlOutput)
        val envYaml = deserializedYaml.config?.environments?.firstOrNull()
        assertNotNull(envYaml)
        val extYaml = envYaml.externalSecrets
        assertNotNull(extYaml)
        assertEquals(ExternalSecretType.GCP_SECRETS_MANAGER, extYaml.type)
        assertEquals(2, extYaml.variables.size)

        val y1 = extYaml.variables[0] as GcpSecretsManagerExternalSecret
        assertEquals("payment_client_id", y1.name)
        assertEquals("payment-foo", y1.secretId)
        assertEquals("my-custom-project", y1.projectId)
        assertEquals("latest", y1.version)
        assertEquals("client_id", y1.targetField)
        assertEquals("staging", y1.customFilters["env"])
    }

    @Test
    fun zero_credential_leakage_guarantees_gcp_credentials_never_serialized() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "GCP Credentials Leak Check"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "GCP Env",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.GCP_SECRETS_MANAGER,
                            variables = listOf(
                                GcpSecretsManagerExternalSecret(
                                    name = "payment_key",
                                    secretId = "payment-foo",
                                    targetField = "client_secret",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)

        val forbiddenGcpKeywords = listOf(
            "gcpAccessToken", "accessToken", "ya29.", "gcpEndpointUrl",
            "Authorization", "Bearer",
        )

        for (kw in forbiddenGcpKeywords) {
            assertFalse(jsonOutput.contains(kw), "JSON export leaked GCP credential keyword: $kw")
            assertFalse(yamlOutput.contains(kw), "YAML export leaked GCP credential keyword: $kw")
        }
    }

    @Test
    fun roundtrip_keeper_external_secrets_preserves_structure_in_json_and_yaml() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Keeper API"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Keeper Env",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.KEEPER_SECURITY_CLOUD,
                            variables = listOf(
                                KeeperSecurityExternalSecret(
                                    name = "user_pass",
                                    recordTitle = "clapi-test-user",
                                    targetField = "password",
                                    customFilters = mapOf("url" to "https://clapi.api.com"),
                                    description = Description("User Password"),
                                    disabled = false,
                                ),
                                KeeperSecurityExternalSecret(
                                    name = "oauth_secret",
                                    recordTitle = "clapi-test-custom-client-credentials",
                                    targetField = "clientSecret",
                                    customFilters = mapOf("clientId" to "clapi-client-id"),
                                    disabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        // JSON Roundtrip
        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        assertTrue(jsonOutput.contains("keeper-security-cloud"))
        assertTrue(jsonOutput.contains("clapi-test-user"))
        assertTrue(jsonOutput.contains("clientSecret"))

        val deserializedJson = OpenCollectionSerializer.deserializeJson(jsonOutput)
        val envJson = deserializedJson.config?.environments?.firstOrNull()
        assertNotNull(envJson)
        val extJson = envJson.externalSecrets
        assertNotNull(extJson)
        assertEquals(ExternalSecretType.KEEPER_SECURITY_CLOUD, extJson.type)
        assertEquals(2, extJson.variables.size)

        val j1 = extJson.variables[0] as KeeperSecurityExternalSecret
        assertEquals("user_pass", j1.name)
        assertEquals("clapi-test-user", j1.recordTitle)
        assertEquals("password", j1.targetField)
        assertEquals("https://clapi.api.com", j1.customFilters["url"])
        assertEquals("User Password", j1.description?.content)
        assertEquals(false, j1.disabled)

        val j2 = extJson.variables[1] as KeeperSecurityExternalSecret
        assertEquals("oauth_secret", j2.name)
        assertEquals("clapi-test-custom-client-credentials", j2.recordTitle)
        assertEquals("clientSecret", j2.targetField)
        assertEquals("clapi-client-id", j2.customFilters["clientId"])
        assertEquals(true, j2.disabled)

        // YAML Roundtrip
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)
        assertTrue(yamlOutput.contains("keeper-security-cloud"))
        assertTrue(yamlOutput.contains("clapi-test-user"))

        val deserializedYaml = OpenCollectionSerializer.deserializeYaml(yamlOutput)
        val envYaml = deserializedYaml.config?.environments?.firstOrNull()
        assertNotNull(envYaml)
        val extYaml = envYaml.externalSecrets
        assertNotNull(extYaml)
        assertEquals(ExternalSecretType.KEEPER_SECURITY_CLOUD, extYaml.type)
        assertEquals(2, extYaml.variables.size)

        val y1 = extYaml.variables[0] as KeeperSecurityExternalSecret
        assertEquals("user_pass", y1.name)
        assertEquals("clapi-test-user", y1.recordTitle)
        assertEquals("password", y1.targetField)
    }

    @Test
    fun zero_credential_leakage_guarantees_keeper_credentials_never_serialized() {
        val doc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(name = "Keeper Credentials Leak Check"),
            config = CollectionConfig(
                environments = listOf(
                    Environment(
                        name = "Keeper Env",
                        externalSecrets = ExternalSecrets(
                            type = ExternalSecretType.KEEPER_SECURITY_CLOUD,
                            variables = listOf(
                                KeeperSecurityExternalSecret(
                                    name = "my_secret",
                                    recordTitle = "clapi-test-user",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val jsonOutput = OpenCollectionSerializer.serializeJson(doc)
        val yamlOutput = OpenCollectionSerializer.serializeYaml(doc)

        val forbiddenKeeperKeywords = listOf(
            "oneTimeToken", "one_time_token", "appKey", "privateKey", "clientKey",
        )

        for (kw in forbiddenKeeperKeywords) {
            assertFalse(jsonOutput.contains(kw), "JSON export leaked Keeper credential keyword: $kw")
            assertFalse(yamlOutput.contains(kw), "YAML export leaked Keeper credential keyword: $kw")
        }
    }
}
