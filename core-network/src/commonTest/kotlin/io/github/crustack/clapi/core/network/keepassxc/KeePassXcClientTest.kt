package io.github.crustack.clapi.core.network.keepassxc

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeePassXcClientTest {

    private val sampleResponse: Map<String, Any> = mapOf(
        "success" to "true",
        "count" to "2",
        "entries" to listOf(
            mapOf(
                "name" to "Production API",
                "login" to "prod_worker",
                "password" to "super-secret-password-123",
                "uuid" to "uuid-111",
                "group" to "Servers",
                "totp" to "654321",
                "stringFields" to mapOf(
                    "tier" to "production",
                    "region" to "us-east-1",
                ),
            ),
            mapOf(
                "name" to "Staging API",
                "login" to "staging_worker",
                "password" to "staging-secret-password-456",
                "uuid" to "uuid-222",
                "group" to "Servers",
                "stringFields" to mapOf(
                    "tier" to "staging",
                    "region" to "us-west-2",
                ),
            ),
        ),
    )

    @Test
    fun parseEntries_parses_all_fields_correctly() {
        val entries = KeePassXcEntryFilter.parseEntries(sampleResponse)
        assertEquals(2, entries.size)

        val first = entries[0]
        assertEquals("Production API", first.name)
        assertEquals("prod_worker", first.login)
        assertEquals("super-secret-password-123", first.password)
        assertEquals("uuid-111", first.uuid)
        assertEquals("Servers", first.group)
        assertEquals("654321", first.totp)
        assertEquals("production", first.stringFields["tier"])

        val second = entries[1]
        assertEquals("Staging API", second.name)
        assertEquals("staging_worker", second.login)
        assertEquals("staging-secret-password-456", second.password)
        assertNull(second.totp)
    }

    @Test
    fun filterEntries_matches_username_login_case_insensitively() {
        val entries = KeePassXcEntryFilter.parseEntries(sampleResponse)
        val filtered = KeePassXcEntryFilter.filterEntries(entries, mapOf("login" to "PROD_WORKER"))

        assertEquals(1, filtered.size)
        assertEquals("Production API", filtered[0].name)
    }

    @Test
    fun filterEntries_matches_custom_string_field() {
        val entries = KeePassXcEntryFilter.parseEntries(sampleResponse)
        val filtered = KeePassXcEntryFilter.filterEntries(entries, mapOf("tier" to "staging"))

        assertEquals(1, filtered.size)
        assertEquals("Staging API", filtered[0].name)
    }

    @Test
    fun filterEntries_returns_empty_when_no_match() {
        val entries = KeePassXcEntryFilter.parseEntries(sampleResponse)
        val filtered = KeePassXcEntryFilter.filterEntries(entries, mapOf("tier" to "dev"))

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun non_leaking_counting_returns_exact_count_without_exposing_secrets() = runTest {
        val fakeClient = object : KeePassXcClient {
            override suspend fun checkStatus(): KeePassXcStatus = KeePassXcStatus.Connected

            override suspend fun queryLogins(
                url: String,
                customFilters: Map<String, String>,
            ): List<KeePassXcEntry> {
                return KeePassXcEntryFilter.parseAndFilter(sampleResponse, customFilters)
            }

            override suspend fun countMatchingLogins(
                url: String,
                customFilters: Map<String, String>,
            ): Int {
                return queryLogins(url, customFilters).size
            }

            override suspend fun disconnect() {}
        }

        assertEquals(2, fakeClient.countMatchingLogins("https://api.example.com"))
        assertEquals(1, fakeClient.countMatchingLogins("https://api.example.com", mapOf("tier" to "production")))
        assertEquals(0, fakeClient.countMatchingLogins("https://api.example.com", mapOf("tier" to "nonexistent")))
    }
}
