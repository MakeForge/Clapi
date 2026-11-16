package io.github.crustack.clapi.core.network.vault

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
}
