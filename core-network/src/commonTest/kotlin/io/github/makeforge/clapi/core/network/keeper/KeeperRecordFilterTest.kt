package io.github.makeforge.clapi.core.network.keeper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeeperRecordFilterTest {

    private val userRecord = KeeperRecordSummary(
        recordUid = "uid-user-123",
        title = "clapi-test-user",
        type = "login",
        password = "secret_user_password",
        fields = listOf(
            KeeperRecordFieldSummary(type = "login", values = listOf("test_username")),
            KeeperRecordFieldSummary(type = "password", values = listOf("secret_user_password")),
            KeeperRecordFieldSummary(type = "url", values = listOf("https://clapi.api.com")),
        ),
    )

    private val serverRecord = KeeperRecordSummary(
        recordUid = "uid-server-456",
        title = "clapi-test-server",
        type = "serverCredentials",
        password = "server_root_password",
        fields = listOf(
            KeeperRecordFieldSummary(type = "login", values = listOf("admin")),
            KeeperRecordFieldSummary(type = "password", values = listOf("server_root_password")),
            KeeperRecordFieldSummary(
                type = "host",
                values = listOf("""{"hostName": "clapi.api.com", "port": "1234"}"""),
            ),
        ),
    )

    private val oauthRecord = KeeperRecordSummary(
        recordUid = "uid-oauth-789",
        title = "clapi-test-custom-client-credentials",
        type = "OAuth Client Credentials",
        customFields = listOf(
            KeeperRecordFieldSummary(type = "text", label = "clientId", values = listOf("clapi-client-id")),
            KeeperRecordFieldSummary(type = "secret", label = "clientSecret", values = listOf("clapi-client-secret-999")),
        ),
    )

    @Test
    fun matches_filter_by_title_and_uid() {
        assertTrue(KeeperRecordFilter.matchesFilter(userRecord, "clapi-test-user"))
        assertTrue(KeeperRecordFilter.matchesFilter(userRecord, "uid-user-123"))
        assertFalse(KeeperRecordFilter.matchesFilter(userRecord, "wrong-title"))
    }

    @Test
    fun matches_filter_with_custom_filters_for_login_record() {
        assertTrue(KeeperRecordFilter.matchesFilter(userRecord, "clapi-test-user", mapOf("url" to "https://clapi.api.com")))
        assertTrue(KeeperRecordFilter.matchesFilter(userRecord, "", mapOf("type" to "login")))
        assertFalse(KeeperRecordFilter.matchesFilter(userRecord, "clapi-test-user", mapOf("url" to "https://other.com")))
    }

    @Test
    fun matches_filter_with_dotted_host_property() {
        assertTrue(KeeperRecordFilter.matchesFilter(serverRecord, "clapi-test-server", mapOf("host.hostName" to "clapi.api.com")))
        assertTrue(KeeperRecordFilter.matchesFilter(serverRecord, "clapi-test-server", mapOf("host.port" to "1234")))
        assertFalse(KeeperRecordFilter.matchesFilter(serverRecord, "clapi-test-server", mapOf("host.port" to "9999")))
    }

    @Test
    fun matches_filter_with_custom_field_label() {
        assertTrue(KeeperRecordFilter.matchesFilter(oauthRecord, "clapi-test-custom-client-credentials", mapOf("clientId" to "clapi-client-id")))
        assertFalse(KeeperRecordFilter.matchesFilter(oauthRecord, "clapi-test-custom-client-credentials", mapOf("clientId" to "wrong-id")))
    }

    @Test
    fun extracts_target_field_values_correctly() {
        // Standard password default
        assertEquals("secret_user_password", KeeperRecordFilter.extractTargetFieldValue(userRecord, "password"))
        assertEquals("secret_user_password", KeeperRecordFilter.extractTargetFieldValue(userRecord, ""))

        // Standard login
        assertEquals("test_username", KeeperRecordFilter.extractTargetFieldValue(userRecord, "login"))

        // Standard url
        assertEquals("https://clapi.api.com", KeeperRecordFilter.extractTargetFieldValue(userRecord, "url"))

        // Dotted host subproperties
        assertEquals("clapi.api.com", KeeperRecordFilter.extractTargetFieldValue(serverRecord, "host.hostName"))
        assertEquals("1234", KeeperRecordFilter.extractTargetFieldValue(serverRecord, "host.port"))

        // Custom field labels
        assertEquals("clapi-client-id", KeeperRecordFilter.extractTargetFieldValue(oauthRecord, "clientId"))
        assertEquals("clapi-client-secret-999", KeeperRecordFilter.extractTargetFieldValue(oauthRecord, "clientSecret"))

        // Keeper Notation
        assertEquals("test_username", KeeperRecordFilter.extractTargetFieldValue(userRecord, "field/login"))
        assertEquals("secret_user_password", KeeperRecordFilter.extractTargetFieldValue(userRecord, "field/password"))
        assertEquals("clapi-client-id", KeeperRecordFilter.extractTargetFieldValue(oauthRecord, "custom_field/clientId"))

        // Non-existent target field
        assertNull(KeeperRecordFilter.extractTargetFieldValue(userRecord, "non_existent"))
    }
}
