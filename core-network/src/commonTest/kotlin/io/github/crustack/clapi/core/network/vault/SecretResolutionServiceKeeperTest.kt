package io.github.crustack.clapi.core.network.vault

import io.github.crustack.clapi.core.network.keeper.KeeperClient
import io.github.crustack.clapi.core.network.keeper.KeeperConnectionStatusState
import io.github.crustack.clapi.core.network.keeper.KeeperRecordFieldSummary
import io.github.crustack.clapi.core.network.keeper.KeeperRecordSummary
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecretResolutionServiceKeeperTest {

    private class FakeKeeperClient(
        private val records: List<KeeperRecordSummary>,
    ) : KeeperClient {
        val getRecordsCalls = mutableListOf<List<String>>()

        override suspend fun checkStatus(): KeeperConnectionStatusState = KeeperConnectionStatusState.Connected
        override suspend fun testConnection(): Boolean = true
        override suspend fun getRecords(recordsFilter: List<String>): List<KeeperRecordSummary> {
            getRecordsCalls.add(recordsFilter)
            if (recordsFilter.isEmpty()) return records
            return records.filter { r ->
                recordsFilter.any { f ->
                    r.recordUid.equals(f, ignoreCase = true) || r.title.equals(f, ignoreCase = true)
                }
            }
        }
        override suspend fun countMatchingRecords(recordTitle: String, customFilters: Map<String, String>): Int = records.size
        override suspend fun disconnect() {}
    }

    private val testRecords = listOf(
        KeeperRecordSummary(
            recordUid = "uid-1",
            title = "clapi-test-user",
            type = "login",
            password = "test-user-password-123",
            fields = listOf(
                KeeperRecordFieldSummary(type = "login", values = listOf("test-user")),
                KeeperRecordFieldSummary(type = "url", values = listOf("https://clapi.api.com")),
            ),
        ),
        KeeperRecordSummary(
            recordUid = "uid-2",
            title = "clapi-test-server",
            type = "serverCredentials",
            password = "server-password-456",
            fields = listOf(
                KeeperRecordFieldSummary(type = "login", values = listOf("clapi-user")),
                KeeperRecordFieldSummary(
                    type = "host",
                    values = listOf("""{"hostName": "clapi.api.com", "port": "1234"}"""),
                ),
            ),
        ),
        KeeperRecordSummary(
            recordUid = "uid-3",
            title = "clapi-test-custom-client-credentials",
            type = "OAuth Client Credentials",
            customFields = listOf(
                KeeperRecordFieldSummary(type = "text", label = "clientId", values = listOf("clapi-client-id")),
                KeeperRecordFieldSummary(type = "secret", label = "clientSecret", values = listOf("clapie-client-secret")),
            ),
        ),
    )

    @Test
    fun resolveKeeperSecrets_resolves_standard_login_and_server_secrets() = runTest {
        val fakeClient = FakeKeeperClient(testRecords)
        val service = SecretResolutionService()

        val declarations = listOf(
            SecretDeclaration(
                name = "user_pass",
                path = "clapi-test-user",
                targetField = "password",
                providerType = "keeper-security-cloud",
            ),
            SecretDeclaration(
                name = "server_host",
                path = "clapi-test-server",
                targetField = "host.hostName",
                providerType = "keeper-security-cloud",
            ),
        )

        val resolved = service.resolveKeeperSecrets(
            requiredSecretNames = setOf("user_pass", "server_host"),
            declarations = declarations,
            configJson = "{}",
            client = fakeClient,
        )

        assertEquals("test-user-password-123", resolved["_secret.user_pass"])
        assertEquals("clapi.api.com", resolved["_secret.server_host"])
    }

    @Test
    fun resolveKeeperSecrets_resolves_custom_fields_with_filters() = runTest {
        val fakeClient = FakeKeeperClient(testRecords)
        val service = SecretResolutionService()

        val declarations = listOf(
            SecretDeclaration(
                name = "client_secret",
                path = "clapi-test-custom-client-credentials",
                targetField = "clientSecret",
                customFilters = mapOf("clientId" to "{{my_client_id}}"),
                providerType = "keeper-security-cloud",
            ),
        )

        val resolved = service.resolveKeeperSecrets(
            requiredSecretNames = setOf("client_secret"),
            declarations = declarations,
            configJson = "{}",
            variableValues = mapOf("my_client_id" to "clapi-client-id"),
            client = fakeClient,
        )

        assertEquals("clapie-client-secret", resolved["_secret.client_secret"])
    }

    @Test
    fun resolveKeeperSecrets_throws_when_record_or_field_not_found() = runTest {
        val fakeClient = FakeKeeperClient(testRecords)
        val service = SecretResolutionService()

        val missingRecordDecl = listOf(
            SecretDeclaration(
                name = "non_existent",
                path = "unknown-record",
                targetField = "password",
                providerType = "keeper-security-cloud",
            ),
        )

        assertFailsWith<IllegalStateException> {
            service.resolveKeeperSecrets(
                requiredSecretNames = setOf("non_existent"),
                declarations = missingRecordDecl,
                configJson = "{}",
                client = fakeClient,
            )
        }

        val missingFieldDecl = listOf(
            SecretDeclaration(
                name = "bad_field",
                path = "clapi-test-user",
                targetField = "non_existent_field",
                providerType = "keeper-security-cloud",
            ),
        )

        assertFailsWith<IllegalStateException> {
            service.resolveKeeperSecrets(
                requiredSecretNames = setOf("bad_field"),
                declarations = missingFieldDecl,
                configJson = "{}",
                client = fakeClient,
            )
        }
    }

    @Test
    fun resolveAllSecrets_routes_keeper_declarations() = runTest {
        val fakeClient = FakeKeeperClient(testRecords)
        val service = SecretResolutionService()

        val declarations = listOf(
            SecretDeclaration(
                name = "app_secret",
                path = "clapi-test-custom-client-credentials",
                targetField = "clientSecret",
                providerType = "keeper-security-cloud",
            ),
        )

        val resolved = service.resolveAllSecrets(
            texts = listOf("Bearer {{_secret.app_secret}}", "{{oauth_pass}}"),
            declarations = declarations,
            secretVariableNames = setOf("oauth_pass"),
            keeperConfigJson = "{}",
            keeperClient = fakeClient,
            processEnvProvider = { if (it == "oauth_pass") "env-oauth-val" else null },
        )

        assertEquals("clapie-client-secret", resolved["_secret.app_secret"])
        assertEquals("env-oauth-val", resolved["oauth_pass"])
    }
}
