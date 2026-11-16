package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerConfig
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultClient
import io.github.crustack.clapi.core.network.azure.AzureKeyVaultConfig
import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerClient
import io.github.crustack.clapi.core.network.gcp.GcpSecretManagerConfig
import io.github.crustack.clapi.core.network.keeper.KeeperClient
import io.github.crustack.clapi.core.network.keeper.KeeperConnectionStatusState
import io.github.crustack.clapi.core.network.keeper.KeeperRecordFieldSummary
import io.github.crustack.clapi.core.network.keeper.KeeperRecordSummary
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClient
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcEntry
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecretResolutionServiceTest {

    private class FakeVaultClient(
        private val responses: Map<String, Map<String, String>>,
    ) : HashiCorpVaultClient {
        val calls = mutableListOf<String>()

        override suspend fun fetchSecret(
            vaultUrl: String,
            token: String,
            secretPath: String,
            mountPath: String,
            namespace: String?,
        ): Map<String, String> {
            calls.add(secretPath)
            return responses[secretPath]
                ?: throw IllegalStateException("Secret path not found: $secretPath")
        }
    }

    @Test
    fun extractSecretReferences_extracts_only_secret_variable_tokens() {
        val service = SecretResolutionService(FakeVaultClient(emptyMap()))
        val input = listOf(
            "https://api.example.com/{{_secret.client_id}}?env={{baseUrl}}",
            "Bearer {{_secret.client_secret}}",
            "{{ not_a_secret }} and {{ _secret.client_id }}",
        )

        val result = service.extractSecretReferences(input)
        assertEquals(setOf("client_id", "client_secret"), result)
    }

    @Test
    fun resolveSecrets_batches_calls_by_path() = runTest {
        val fakeClient = FakeVaultClient(
            mapOf(
                "payment/foo" to mapOf(
                    "client_id" to "id_123",
                    "client_secret" to "sec_456",
                ),
            ),
        )
        val service = SecretResolutionService(fakeClient)

        val declarations = listOf(
            SecretDeclaration(name = "client_id", path = "payment/foo", enabled = true),
            SecretDeclaration(name = "client_secret", path = "payment/foo", enabled = true),
        )

        val resolved = service.resolveSecrets(
            requiredSecretNames = setOf("client_id", "client_secret"),
            declarations = declarations,
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "root-token",
        )

        // Only one fetch call made for path "payment/foo"
        assertEquals(1, fakeClient.calls.size)
        assertEquals("payment/foo", fakeClient.calls[0])

        // Results mapped by _secret.<name>
        assertEquals("id_123", resolved["_secret.client_id"])
        assertEquals("sec_456", resolved["_secret.client_secret"])
    }

    @Test
    fun resolveSecrets_falls_back_to_single_key_if_secret_name_not_exact() = runTest {
        val fakeClient = FakeVaultClient(
            mapOf("auth/token" to mapOf("access_token" to "token_999")),
        )
        val service = SecretResolutionService(fakeClient)

        val declarations = listOf(
            SecretDeclaration(name = "my_jwt", path = "auth/token", enabled = true),
        )

        val resolved = service.resolveSecrets(
            requiredSecretNames = setOf("my_jwt"),
            declarations = declarations,
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "root-token",
        )

        assertEquals("token_999", resolved["_secret.my_jwt"])
    }

    @Test
    fun resolveSecrets_throws_when_vault_url_is_missing() = runTest {
        val service = SecretResolutionService(FakeVaultClient(emptyMap()))
        val declarations = listOf(
            SecretDeclaration(name = "token", path = "kv/data", enabled = true),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveSecrets(
                requiredSecretNames = setOf("token"),
                declarations = declarations,
                vaultUrl = "",
                vaultToken = "tok",
            )
        }
        assertTrue(ex.message?.contains("Vault URL is not configured") == true)
    }

    @Test
    fun resolveSecrets_throws_when_vault_token_is_missing() = runTest {
        val service = SecretResolutionService(FakeVaultClient(emptyMap()))
        val declarations = listOf(
            SecretDeclaration(name = "token", path = "kv/data", enabled = true),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveSecrets(
                requiredSecretNames = setOf("token"),
                declarations = declarations,
                vaultUrl = "http://127.0.0.1:8200",
                vaultToken = null,
            )
        }
        assertTrue(ex.message?.contains("Vault token is not configured") == true)
    }

    @Test
    fun resolveSecrets_throws_when_secret_is_disabled() = runTest {
        val service = SecretResolutionService(FakeVaultClient(emptyMap()))
        val declarations = listOf(
            SecretDeclaration(name = "disabled_var", path = "kv/data", enabled = false),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveSecrets(
                requiredSecretNames = setOf("disabled_var"),
                declarations = declarations,
                vaultUrl = "http://127.0.0.1:8200",
                vaultToken = "tok",
            )
        }
        assertTrue(ex.message?.contains("disabled") == true)
    }

    @Test
    fun resolveSecrets_throws_when_secret_is_not_declared() = runTest {
        val service = SecretResolutionService(FakeVaultClient(emptyMap()))

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveSecrets(
                requiredSecretNames = setOf("unknown_sec"),
                declarations = emptyList(),
                vaultUrl = "http://127.0.0.1:8200",
                vaultToken = "tok",
            )
        }
        assertTrue(ex.message?.contains("not defined") == true)
    }

    @Test
    fun resolveSecrets_throws_when_key_is_missing_in_multi_key_vault_payload() = runTest {
        val fakeClient = FakeVaultClient(
            mapOf("kv/keys" to mapOf("key1" to "v1", "key2" to "v2")),
        )
        val service = SecretResolutionService(fakeClient)
        val declarations = listOf(
            SecretDeclaration(name = "key3", path = "kv/keys", enabled = true),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveSecrets(
                requiredSecretNames = setOf("key3"),
                declarations = declarations,
                vaultUrl = "http://127.0.0.1:8200",
                vaultToken = "tok",
            )
        }
        assertTrue(ex.message?.contains("Secret key 'key3' was not found") == true)
    }

    @Test
    fun resolveSecrets_returns_empty_when_no_secrets_required() = runTest {
        val fakeClient = FakeVaultClient(emptyMap())
        val service = SecretResolutionService(fakeClient)

        val resolved = service.resolveSecrets(
            requiredSecretNames = emptySet(),
            declarations = emptyList(),
            vaultUrl = null,
            vaultToken = null,
        )

        assertTrue(resolved.isEmpty())
        assertTrue(fakeClient.calls.isEmpty())
    }

    @Test
    fun resolveAllSecrets_resolves_secret_variable_from_process_env_first() = runTest {
        val fakeClient = FakeVaultClient(
            mapOf("kv/secret" to mapOf("MY_API_KEY" to "vault_key")),
        )
        val service = SecretResolutionService(fakeClient)
        val declarations = listOf(
            SecretDeclaration(name = "MY_API_KEY", path = "kv/secret", enabled = true),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("Bearer {{MY_API_KEY}}"),
            declarations = declarations,
            secretVariableNames = setOf("MY_API_KEY"),
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "token",
            processEnvProvider = { if (it == "MY_API_KEY") "env_secret_123" else null },
        )

        assertEquals("env_secret_123", resolved["MY_API_KEY"])
        assertTrue(fakeClient.calls.isEmpty(), "Process env should take precedence without querying Vault")
    }

    @Test
    fun resolveAllSecrets_falls_back_to_matching_external_secret_when_not_in_process_env() = runTest {
        val fakeClient = FakeVaultClient(
            mapOf("kv/secret" to mapOf("MY_API_KEY" to "vault_key_456")),
        )
        val service = SecretResolutionService(fakeClient)
        val declarations = listOf(
            SecretDeclaration(name = "MY_API_KEY", path = "kv/secret", enabled = true),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("Bearer {{MY_API_KEY}}"),
            declarations = declarations,
            secretVariableNames = setOf("MY_API_KEY"),
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "token",
            processEnvProvider = { null },
        )

        assertEquals("vault_key_456", resolved["MY_API_KEY"])
        assertEquals(listOf("kv/secret"), fakeClient.calls)
    }

    @Test
    fun resolveAllSecrets_throws_when_matching_external_secret_is_disabled() = runTest {
        val fakeClient = FakeVaultClient(emptyMap())
        val service = SecretResolutionService(fakeClient)
        val declarations = listOf(
            SecretDeclaration(name = "MY_API_KEY", path = "kv/secret", enabled = false),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveAllSecrets(
                texts = listOf("Bearer {{MY_API_KEY}}"),
                declarations = declarations,
                secretVariableNames = setOf("MY_API_KEY"),
                vaultUrl = "http://127.0.0.1:8200",
                vaultToken = "token",
                processEnvProvider = { null },
            )
        }

        assertTrue(ex.message?.contains("is disabled") == true)
    }

    @Test
    fun resolveAllSecrets_throws_when_secret_variable_matches_neither_process_env_nor_external_secret() = runTest {
        val fakeClient = FakeVaultClient(emptyMap())
        val service = SecretResolutionService(fakeClient)
        val declarations = listOf(
            SecretDeclaration(name = "OTHER_SECRET", path = "kv/other", enabled = true),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveAllSecrets(
                texts = listOf("Bearer {{UNKNOWN_SECRET}}"),
                declarations = declarations,
                secretVariableNames = setOf("UNKNOWN_SECRET"),
                vaultUrl = "http://127.0.0.1:8200",
                vaultToken = "token",
                processEnvProvider = { null },
            )
        }

        assertTrue(
            ex.message?.contains("Secret variable 'UNKNOWN_SECRET' could not be resolved from process environment or external secrets.") == true,
            "Unexpected error message: ${ex.message}",
        )
    }

    @Test
    fun resolveAllSecrets_ignores_unused_secret_variables() = runTest {
        val fakeClient = FakeVaultClient(emptyMap())
        val service = SecretResolutionService(fakeClient)
        val declarations = emptyList<SecretDeclaration>()

        val resolved = service.resolveAllSecrets(
            texts = listOf("https://example.com/api?user={{username}}"),
            declarations = declarations,
            secretVariableNames = setOf("UNUSED_SECRET"),
            vaultUrl = null,
            vaultToken = null,
            processEnvProvider = { null },
        )

        assertTrue(resolved.isEmpty())
        assertTrue(fakeClient.calls.isEmpty())
    }

    @Test
    fun resolveAllSecrets_resolves_both_direct_secret_and_secret_variable_with_batched_vault_call() = runTest {
        val fakeClient = FakeVaultClient(
            mapOf(
                "payment/stripe" to mapOf(
                    "api_key" to "key_val",
                    "webhook_secret" to "wh_val",
                ),
            ),
        )
        val service = SecretResolutionService(fakeClient)
        val declarations = listOf(
            SecretDeclaration(name = "api_key", path = "payment/stripe", enabled = true),
            SecretDeclaration(name = "webhook_secret", path = "payment/stripe", enabled = true),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("https://api.example.com/{{api_key}}", "X-Webhook: {{_secret.webhook_secret}}"),
            declarations = declarations,
            secretVariableNames = setOf("api_key"),
            vaultUrl = "http://127.0.0.1:8200",
            vaultToken = "token",
            processEnvProvider = { null },
        )

        assertEquals("key_val", resolved["api_key"])
        assertEquals("wh_val", resolved["_secret.webhook_secret"])
        assertEquals(listOf("payment/stripe"), fakeClient.calls, "Should query path only once")
    }

    private class FakeKeePassXcClient(
        private val entriesByUrl: Map<String, List<KeePassXcEntry>> = emptyMap(),
        private val status: KeePassXcStatus = KeePassXcStatus.Connected,
    ) : KeePassXcClient {
        val queryCalls = mutableListOf<String>()

        override suspend fun checkStatus(): KeePassXcStatus = status

        override suspend fun queryLogins(
            url: String,
            customFilters: Map<String, String>,
        ): List<KeePassXcEntry> {
            queryCalls.add(url)
            return entriesByUrl[url] ?: emptyList()
        }

        override suspend fun countMatchingLogins(
            url: String,
            customFilters: Map<String, String>,
        ): Int = queryLogins(url, customFilters).size

        override suspend fun disconnect() {}
    }

    @Test
    fun resolveKeePassXcSecrets_resolves_password_by_url() = runTest {
        val fakeKpa = FakeKeePassXcClient(
            entriesByUrl = mapOf(
                "https://api.gateway.internal" to listOf(
                    KeePassXcEntry(
                        name = "Gateway",
                        login = "service_acc",
                        password = "super-secret-pwd",
                        uuid = "uuid-1",
                    )
                )
            )
        )
        val service = SecretResolutionService()
        val declarations = listOf(
            SecretDeclaration(
                name = "gateway_token",
                providerType = "keepassxc",
                url = "https://api.gateway.internal",
                targetField = "password",
                enabled = true,
            )
        )

        val resolved = service.resolveKeePassXcSecrets(
            requiredSecretNames = setOf("gateway_token"),
            declarations = declarations,
            client = fakeKpa,
        )

        assertEquals("super-secret-pwd", resolved["_secret.gateway_token"])
        assertEquals(listOf("https://api.gateway.internal"), fakeKpa.queryCalls)
    }

    @Test
    fun resolveKeePassXcSecrets_resolves_custom_target_field_and_interpolates_filter_variables() = runTest {
        val fakeKpa = FakeKeePassXcClient(
            entriesByUrl = mapOf(
                "https://api.gateway.internal" to listOf(
                    KeePassXcEntry(
                        name = "Staging Gateway",
                        login = "stage_acc",
                        password = "stage-secret-pwd",
                        uuid = "uuid-stage",
                        stringFields = mapOf("tier" to "staging", "api_key" to "key-staging-777"),
                    ),
                    KeePassXcEntry(
                        name = "Prod Gateway",
                        login = "prod_acc",
                        password = "prod-secret-pwd",
                        uuid = "uuid-prod",
                        stringFields = mapOf("tier" to "prod", "api_key" to "key-prod-999"),
                    )
                )
            )
        )
        val service = SecretResolutionService()
        val declarations = listOf(
            SecretDeclaration(
                name = "prod_api_key",
                providerType = "keepassxc",
                url = "https://api.gateway.internal",
                targetField = "api_key",
                customFilters = mapOf("tier" to "{{env_tier}}"),
                enabled = true,
            )
        )

        val resolved = service.resolveKeePassXcSecrets(
            requiredSecretNames = setOf("prod_api_key"),
            declarations = declarations,
            variableValues = mapOf("env_tier" to "prod"),
            client = fakeKpa,
        )

        assertEquals("key-prod-999", resolved["_secret.prod_api_key"])
    }

    @Test
    fun resolveKeePassXcSecrets_throws_on_locked_database() = runTest {
        val fakeKpa = FakeKeePassXcClient(status = KeePassXcStatus.DatabaseLocked)
        val service = SecretResolutionService()
        val declarations = listOf(
            SecretDeclaration(
                name = "token",
                providerType = "keepassxc",
                url = "https://api.example.com",
                enabled = true,
            )
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveKeePassXcSecrets(
                requiredSecretNames = setOf("token"),
                declarations = declarations,
                client = fakeKpa,
            )
        }
        assertTrue(ex.message!!.contains("locked", ignoreCase = true))
    }

    @Test
    fun resolveAllSecrets_resolves_keepassxc_secrets_via_direct_and_secret_variable() = runTest {
        val fakeKpa = FakeKeePassXcClient(
            entriesByUrl = mapOf(
                "https://api.keepass.com" to listOf(
                    KeePassXcEntry(
                        name = "KP Entry",
                        login = "kp_user",
                        password = "kp_secret_password",
                        uuid = "kp-uuid",
                    )
                )
            )
        )
        val service = SecretResolutionService()
        val declarations = listOf(
            SecretDeclaration(
                name = "my_secret",
                providerType = "keepassxc",
                url = "https://api.keepass.com",
                targetField = "password",
                enabled = true,
            )
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("Bearer {{_secret.my_secret}}"),
            declarations = declarations,
            keepassClient = fakeKpa,
        )

        assertEquals("kp_secret_password", resolved["_secret.my_secret"])
    }

    private class FakeAwsSecretsManagerClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : AwsSecretsManagerClient {
        val calls = mutableListOf<String>()

        override suspend fun fetchSecret(config: AwsSecretsManagerConfig): Map<String, String> {
            calls.add(config.secretId)
            return secrets[config.secretId]
                ?: throw IllegalStateException("External secret not found in AWS Secrets Manager (Secret ID: '${config.secretId}')")
        }

        override suspend fun fetchSecret(
            secretId: String,
            accessKey: String,
            secretKey: String,
            region: String,
            sessionToken: String?,
            endpointUrl: String?,
            versionStage: String?,
        ): Map<String, String> {
            calls.add(secretId)
            return secrets[secretId]
                ?: throw IllegalStateException("External secret not found in AWS Secrets Manager (Secret ID: '$secretId')")
        }
    }

    @Test
    fun resolveAwsSecrets_batches_calls_by_secret_id() = runTest {
        val fakeAwsClient = FakeAwsSecretsManagerClient(
            mapOf(
                "local/payment/foo" to mapOf(
                    "client_id" to "id_123",
                    "client_secret" to "sec_456",
                ),
            ),
        )
        val service = SecretResolutionService(awsClient = fakeAwsClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "client_id",
                path = "local/payment/foo",
                targetField = "client_id",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
            SecretDeclaration(
                name = "client_secret",
                path = "local/payment/foo",
                targetField = "client_secret",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val resolved = service.resolveAwsSecrets(
            requiredSecretNames = setOf("client_id", "client_secret"),
            declarations = declarations,
            accessKeyId = "test-access-key",
            secretAccessKey = "test-secret-key",
            client = fakeAwsClient,
        )

        // Only one fetch call made for secret ID "local/payment/foo"
        assertEquals(1, fakeAwsClient.calls.size)
        assertEquals("local/payment/foo", fakeAwsClient.calls[0])

        assertEquals("id_123", resolved["_secret.client_id"])
        assertEquals("sec_456", resolved["_secret.client_secret"])
    }

    @Test
    fun resolveAwsSecrets_resolves_raw_plain_text_when_target_field_is_blank_or_secret() = runTest {
        val fakeAwsClient = FakeAwsSecretsManagerClient(
            mapOf(
                "prod/api/raw_token" to mapOf("secret" to "plain-token-999"),
            ),
        )
        val service = SecretResolutionService(awsClient = fakeAwsClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "raw_token",
                path = "prod/api/raw_token",
                targetField = "",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val resolved = service.resolveAwsSecrets(
            requiredSecretNames = setOf("raw_token"),
            declarations = declarations,
            accessKeyId = "test-access-key",
            secretAccessKey = "test-secret-key",
            client = fakeAwsClient,
        )

        assertEquals("plain-token-999", resolved["_secret.raw_token"])
    }

    @Test
    fun resolveAwsSecrets_interpolates_and_evaluates_custom_filters() = runTest {
        val fakeAwsClient = FakeAwsSecretsManagerClient(
            mapOf(
                "app/credentials" to mapOf(
                    "stage" to "staging",
                    "api_key" to "staging_key_123",
                ),
            ),
        )
        val service = SecretResolutionService(awsClient = fakeAwsClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "api_key",
                path = "app/credentials",
                targetField = "api_key",
                customFilters = mapOf("stage" to "{{target_stage}}"),
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val resolved = service.resolveAwsSecrets(
            requiredSecretNames = setOf("api_key"),
            declarations = declarations,
            accessKeyId = "test-access-key",
            secretAccessKey = "test-secret-key",
            variableValues = mapOf("target_stage" to "staging"),
            client = fakeAwsClient,
        )

        assertEquals("staging_key_123", resolved["_secret.api_key"])
    }

    @Test
    fun resolveAwsSecrets_throws_when_custom_filter_does_not_match() = runTest {
        val fakeAwsClient = FakeAwsSecretsManagerClient(
            mapOf(
                "app/credentials" to mapOf(
                    "stage" to "production",
                    "api_key" to "prod_key_123",
                ),
            ),
        )
        val service = SecretResolutionService(awsClient = fakeAwsClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "api_key",
                path = "app/credentials",
                targetField = "api_key",
                customFilters = mapOf("stage" to "staging"),
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveAwsSecrets(
                requiredSecretNames = setOf("api_key"),
                declarations = declarations,
                accessKeyId = "test-access-key",
                secretAccessKey = "test-secret-key",
                client = fakeAwsClient,
            )
        }
        assertTrue(ex.message!!.contains("did not match custom filter"))
    }

    @Test
    fun resolveAwsSecrets_throws_when_target_field_not_found() = runTest {
        val fakeAwsClient = FakeAwsSecretsManagerClient(
            mapOf(
                "app/credentials" to mapOf(
                    "client_id" to "id_123",
                ),
            ),
        )
        val service = SecretResolutionService(awsClient = fakeAwsClient)

        val declarations = listOf(
            SecretDeclaration(
                name = "missing_field",
                path = "app/credentials",
                targetField = "non_existent",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveAwsSecrets(
                requiredSecretNames = setOf("missing_field"),
                declarations = declarations,
                accessKeyId = "test-access-key",
                secretAccessKey = "test-secret-key",
                client = fakeAwsClient,
            )
        }
        assertTrue(ex.message!!.contains("Field 'non_existent' not found in AWS secret"))
    }

    @Test
    fun resolveAwsSecrets_throws_when_credentials_missing() = runTest {
        val service = SecretResolutionService()
        val declarations = listOf(
            SecretDeclaration(
                name = "api_key",
                path = "app/credentials",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val ex = assertFailsWith<IllegalStateException> {
            service.resolveAwsSecrets(
                requiredSecretNames = setOf("api_key"),
                declarations = declarations,
                accessKeyId = null,
                secretAccessKey = null,
            )
        }
        assertTrue(ex.message!!.contains("AWS Access Key ID is not configured"))
    }

    @Test
    fun resolveAllSecrets_resolves_aws_secrets_via_direct_and_secret_variable() = runTest {
        val fakeAwsClient = FakeAwsSecretsManagerClient(
            mapOf(
                "aws/tokens" to mapOf(
                    "direct_token" to "direct_val_111",
                    "sec_var_token" to "sec_var_val_222",
                ),
            ),
        )
        val service = SecretResolutionService(awsClient = fakeAwsClient)
        val declarations = listOf(
            SecretDeclaration(
                name = "direct_token",
                path = "aws/tokens",
                targetField = "direct_token",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
            SecretDeclaration(
                name = "AWS_SECRET_VAR",
                path = "aws/tokens",
                targetField = "sec_var_token",
                enabled = true,
                providerType = "aws-secrets-manager",
            ),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("{{_secret.direct_token}} and {{AWS_SECRET_VAR}}"),
            declarations = declarations,
            secretVariableNames = setOf("AWS_SECRET_VAR"),
            awsAccessKeyId = "test-key",
            awsSecretAccessKey = "test-secret",
            awsClient = fakeAwsClient,
            processEnvProvider = { null }, // no process env override
        )

        assertEquals("direct_val_111", resolved["_secret.direct_token"])
        assertEquals("sec_var_val_222", resolved["AWS_SECRET_VAR"])
    }

    @Test
    fun resolveAllSecrets_with_SecretResolutionContext_dispatches_all_six_providers_and_resolves_spaced_custom_filters() = runTest {
        val fakeVault = FakeVaultClient(mapOf("kv/v" to mapOf("v_sec" to "vault-val")))
        val fakeAws = FakeAwsSecretsManagerClient(
            mapOf("aws/sec" to mapOf("env" to "staging", "key" to "aws-val")),
        )
        val fakeAzure = object : AzureKeyVaultClient {
            override suspend fun fetchSecret(config: AzureKeyVaultConfig): Map<String, String> =
                mapOf("env" to "staging", "key" to "azure-val")
        }
        val fakeGcp = object : GcpSecretManagerClient {
            override suspend fun fetchSecret(config: GcpSecretManagerConfig): Map<String, String> =
                mapOf("env" to "staging", "key" to "gcp-val")
        }
        val fakeKeeper = object : KeeperClient {
            override suspend fun checkStatus(): KeeperConnectionStatusState = KeeperConnectionStatusState.Connected
            override suspend fun testConnection(): Boolean = true
            override suspend fun getRecords(recordsFilter: List<String>): List<KeeperRecordSummary> = listOf(
                KeeperRecordSummary(
                    recordUid = "uid-1",
                    title = "KeeperRec",
                    type = "login",
                    password = "keeper-val",
                    customFields = listOf(
                        KeeperRecordFieldSummary(type = "text", label = "env", values = listOf("staging")),
                    ),
                )
            )
            override suspend fun countMatchingRecords(recordTitle: String, customFilters: Map<String, String>): Int = 1
            override suspend fun disconnect() {}
        }
        val fakeKpa = FakeKeePassXcClient(
            entriesByUrl = mapOf(
                "https://kpa.local" to listOf(
                    KeePassXcEntry(
                        name = "KPA",
                        login = "u",
                        password = "kpa-val",
                        uuid = "u1",
                        stringFields = mapOf("env" to "staging"),
                    )
                )
            )
        )

        val service = SecretResolutionService(
            vaultClient = fakeVault,
            awsClient = fakeAws,
            azureClient = fakeAzure,
            gcpClient = fakeGcp,
        )

        val declarations = listOf(
            SecretDeclaration(
                name = "v_sec",
                path = "kv/v",
                providerType = "hashicorp-vault-server",
            ),
            SecretDeclaration(
                name = "aws_sec",
                path = "aws/sec",
                targetField = "key",
                customFilters = mapOf("env" to "{{ target_env }}"), // whitespace-padded variable token (BUG-08)
                providerType = "aws-secrets-manager",
            ),
            SecretDeclaration(
                name = "az_sec",
                path = "az/sec",
                targetField = "key",
                customFilters = mapOf("env" to "{{ target_env }}"),
                providerType = "azure-key-vault",
            ),
            SecretDeclaration(
                name = "gcp_sec",
                path = "gcp/sec",
                targetField = "key",
                customFilters = mapOf("env" to "{{ target_env }}"),
                providerType = "gcp-secrets-manager",
            ),
            SecretDeclaration(
                name = "keeper_sec",
                path = "KeeperRec",
                targetField = "password",
                customFilters = mapOf("env" to "{{ target_env }}"),
                providerType = "keeper-security-cloud",
            ),
            SecretDeclaration(
                name = "kpa_sec",
                url = "https://kpa.local",
                targetField = "password",
                customFilters = mapOf("env" to "{{ target_env }}"),
                providerType = "keepassxc",
            ),
        )

        val context = SecretResolutionContext(
            declarations = declarations,
            vault = VaultProviderConfig(url = "http://127.0.0.1:8200", token = "v-tok"),
            aws = AwsProviderConfig(accessKeyId = "ak", secretAccessKey = "sk"),
            azure = AzureProviderConfig(vaultName = "my-vault", tenantId = "t", clientId = "c", clientSecret = "s"),
            gcp = GcpProviderConfig(projectId = "my-proj", accessToken = "gcp-tok"),
            keeper = KeeperProviderConfig(clientOverride = fakeKeeper),
            keepassXc = KeepassXcProviderConfig(clientOverride = fakeKpa),
            variableLayers = listOf(
                mapOf("target_env" to "staging"),
                mapOf("target_env" to "overridden-lower-layer"),
            ),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf(
                "{{_secret.v_sec}} {{_secret.aws_sec}} {{_secret.az_sec}} {{_secret.gcp_sec}} {{_secret.keeper_sec}} {{_secret.kpa_sec}}"
            ),
            context = context,
        )

        assertEquals("vault-val", resolved["_secret.v_sec"])
        assertEquals("aws-val", resolved["_secret.aws_sec"])
        assertEquals("azure-val", resolved["_secret.az_sec"])
        assertEquals("gcp-val", resolved["_secret.gcp_sec"])
        assertEquals("keeper-val", resolved["_secret.keeper_sec"])
        assertEquals("kpa-val", resolved["_secret.kpa_sec"])
    }
}
