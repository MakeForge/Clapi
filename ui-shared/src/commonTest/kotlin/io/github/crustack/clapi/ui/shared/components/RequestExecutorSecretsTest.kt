package io.github.crustack.clapi.ui.shared.components

import io.github.crustack.clapi.ui.shared.components.executor.*

import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerClient
import io.github.crustack.clapi.core.network.aws.AwsSecretsManagerConfig
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcClient
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcEntry
import io.github.crustack.clapi.core.network.keepassxc.KeePassXcStatus
import io.github.crustack.clapi.core.network.vault.HashiCorpVaultClient
import io.github.crustack.clapi.core.network.vault.SecretResolutionService
import io.github.crustack.clapi.ui.shared.state.AppState
import io.github.crustack.clapi.ui.shared.state.EnvState
import io.github.crustack.clapi.ui.shared.state.MutableExternalSecret
import io.github.crustack.clapi.ui.shared.state.MutableKeyValue
import io.github.crustack.clapi.ui.shared.state.RequestTabState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestExecutorSecretsTest {

    @Test
    fun sendRequest_halts_and_sets_error_when_vault_not_configured() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            externalSecrets = listOf(
                MutableExternalSecret(name = "api_key", path = "secret/key", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?token={{_secret.api_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Vault URL is not configured") || tab.lastError!!.contains("Vault token is not configured"))
    }

    @Test
    fun sendRequest_halts_and_sets_error_when_secret_is_disabled() = runTest {
        val state = AppState()
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "token"

        val env = EnvState(
            name = "TestEnv",
            externalSecrets = listOf(
                MutableExternalSecret(name = "disabled_sec", path = "secret/key", enabled = false),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?token={{_secret.disabled_sec}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("disabled"))
    }

    @Test
    fun sendRequest_halts_and_sets_error_when_secret_not_defined_in_env() = runTest {
        val state = AppState()
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "token"

        val env = EnvState(name = "TestEnv")
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?token={{_secret.unknown_sec}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("not defined in the active environment"))
    }

    private class FakeTestVaultClient(
        private val secrets: Map<String, Map<String, String>>,
    ) : HashiCorpVaultClient {
        override suspend fun fetchSecret(
            vaultUrl: String,
            token: String,
            secretPath: String,
            mountPath: String,
            namespace: String?,
        ): Map<String, String> = secrets[secretPath] ?: emptyMap()
    }

    @Test
    fun resolveExternalSecretsForTab_resolves_secret_variable_from_process_env() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "MY_KEY", value = "", secret = true, enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{MY_KEY}}")
        val resolutionService = SecretResolutionService(FakeTestVaultClient(emptyMap()))

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = resolutionService,
            processEnvProvider = { if (it == "MY_KEY") "proc_key_123" else null },
        )

        assertNotNull(resolved)
        assertEquals("proc_key_123", resolved["MY_KEY"])
    }

    @Test
    fun resolveExternalSecretsForTab_falls_back_to_matching_external_secret() = runTest {
        val state = AppState()
        state.settings.vaultServerUrl = "http://127.0.0.1:8200"
        state.settings.vaultToken = "root-token"

        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "MY_KEY", value = "", secret = true, enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(name = "MY_KEY", path = "secret/my_key", enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{MY_KEY}}")
        val resolutionService = SecretResolutionService(
            FakeTestVaultClient(mapOf("secret/my_key" to mapOf("MY_KEY" to "vault_val_789"))),
        )

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = resolutionService,
            processEnvProvider = { null },
        )

        assertNotNull(resolved)
        assertEquals("vault_val_789", resolved["MY_KEY"])
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_secret_variable_cannot_be_resolved() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "UNRESOLVED_SECRET", value = "", secret = true, enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{UNRESOLVED_SECRET}}")
        val resolutionService = SecretResolutionService(FakeTestVaultClient(emptyMap()))

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            secretResolutionService = resolutionService,
            processEnvProvider = { null },
        )

        assertEquals(null, resolved)
        assertFalse(tab.isLoading)
        assertEquals(
            "Secret variable 'UNRESOLVED_SECRET' could not be resolved from process environment or external secrets.",
            tab.lastError,
        )
    }

    @Test
    fun sendRequest_halts_and_sets_error_when_secret_variable_fails_resolution() = runTest {
        val state = AppState()
        val env = EnvState(
            name = "TestEnv",
            variables = listOf(
                MutableKeyValue(key = "UNRESOLVED_SECRET", value = "", secret = true, enabled = true),
            ),
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/api?key={{UNRESOLVED_SECRET}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        sendRequest(this, state, tab)
        tab.currentJob?.join()

        assertFalse(tab.isLoading)
        assertNotNull(tab.lastError)
        assertEquals(
            "Secret variable 'UNRESOLVED_SECRET' could not be resolved from process environment or external secrets.",
            tab.lastError,
        )
    }

    private class FakeTestKeePassXcClient(
        private val entriesByUrl: Map<String, List<KeePassXcEntry>> = emptyMap(),
        private val status: KeePassXcStatus = KeePassXcStatus.Connected,
    ) : KeePassXcClient {
        override suspend fun checkStatus(): KeePassXcStatus = status
        override suspend fun queryLogins(url: String, customFilters: Map<String, String>): List<KeePassXcEntry> =
            entriesByUrl[url] ?: emptyList()
        override suspend fun countMatchingLogins(url: String, customFilters: Map<String, String>): Int =
            queryLogins(url, customFilters).size
        override suspend fun disconnect() {}
    }

    @Test
    fun resolveExternalSecretsForTab_resolves_keepassxc_secret_and_interpolates_filter_variables() = runTest {
        val fakeKpa = FakeTestKeePassXcClient(
            entriesByUrl = mapOf(
                "https://api.payment.internal" to listOf(
                    KeePassXcEntry(
                        name = "Payment Service",
                        login = "service_user",
                        password = "resolved-keepass-password-42",
                        uuid = "uuid-123",
                        stringFields = mapOf("tier" to "prod"),
                    )
                )
            )
        )

        val state = AppState()
        val env = EnvState(
            name = "ProdEnv",
            externalSecretsProvider = "keepassxc",
            variables = listOf(
                MutableKeyValue(key = "stage", value = "prod", enabled = true, secret = false),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    url = "https://api.payment.internal",
                    targetField = "password",
                    customFilters = mapOf("tier" to "{{stage}}"),
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            keepassClient = fakeKpa,
        )

        assertNotNull(resolved)
        assertEquals("resolved-keepass-password-42", resolved["_secret.payment_key"])
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_keepassxc_database_is_locked() = runTest {
        val fakeKpa = FakeTestKeePassXcClient(status = KeePassXcStatus.DatabaseLocked)

        val state = AppState()
        val env = EnvState(
            name = "LockedEnv",
            externalSecretsProvider = "keepassxc",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    url = "https://api.payment.internal",
                    targetField = "password",
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            keepassClient = fakeKpa,
        )

        assertEquals(null, resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("locked", ignoreCase = true))
    }

    private class FakeTestAwsSecretsManagerClient(
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
    fun resolveExternalSecretsForTab_resolves_aws_secrets_and_interpolates_filter_variables() = runTest {
        val fakeAws = FakeTestAwsSecretsManagerClient(
            mapOf(
                "local/payment/foo" to mapOf(
                    "stage" to "sandbox",
                    "client_secret" to "live_secret_999",
                )
            )
        )

        val state = AppState()
        state.settings.awsAccessKeyId = "test_key"
        state.settings.awsSecretAccessKey = "test_secret"

        val env = EnvState(
            name = "AwsEnv",
            externalSecretsProvider = "aws-secrets-manager",
            variables = listOf(
                MutableKeyValue(key = "stage", value = "sandbox", enabled = true),
            ),
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    path = "local/payment/foo",
                    targetField = "client_secret",
                    customFilters = mapOf("stage" to "{{stage}}"),
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            awsClient = fakeAws,
        )

        assertNotNull(resolved)
        assertEquals("live_secret_999", resolved["_secret.payment_key"])
        assertEquals(1, fakeAws.calls.size)
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_aws_credentials_missing() = runTest {
        val state = AppState()
        // No AWS access key set
        val env = EnvState(
            name = "AwsEnv",
            externalSecretsProvider = "aws-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    path = "local/payment/foo",
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
        )

        assertEquals(null, resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("AWS Access Key ID is not configured"))
    }

    @Test
    fun resolveExternalSecretsForTab_sets_error_when_aws_secret_target_field_missing() = runTest {
        val fakeAws = FakeTestAwsSecretsManagerClient(
            mapOf(
                "local/payment/foo" to mapOf(
                    "client_id" to "123",
                )
            )
        )

        val state = AppState()
        state.settings.awsAccessKeyId = "test_key"
        state.settings.awsSecretAccessKey = "test_secret"

        val env = EnvState(
            name = "AwsEnv",
            externalSecretsProvider = "aws-secrets-manager",
            externalSecrets = listOf(
                MutableExternalSecret(
                    name = "payment_key",
                    path = "local/payment/foo",
                    targetField = "client_secret",
                    enabled = true,
                )
            )
        )
        state.environments.add(env)
        state.selectedEnvIndex = 0

        val tab = RequestTabState(url = "https://example.com/charge?token={{_secret.payment_key}}")
        state.openTabs.add(tab)
        state.activeTabIndex = 0

        val resolved = resolveExternalSecretsForTab(
            state = state,
            tab = tab,
            textsToScan = listOf(tab.url),
            awsClient = fakeAws,
        )

        assertEquals(null, resolved)
        assertNotNull(tab.lastError)
        assertTrue(tab.lastError!!.contains("Field 'client_secret' not found in AWS secret"))
    }
}
