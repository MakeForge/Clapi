package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.network.keeper.KeeperRecordFieldSummary
import io.github.makeforge.clapi.core.network.keeper.KeeperRecordFilter
import io.github.makeforge.clapi.core.network.keeper.KeeperRecordSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SecretManagerStatusColumnTest {

    @Test
    fun keeper_record_matches_filter_and_extracts_target_field() {
        val record = KeeperRecordSummary(
            recordUid = "UID123",
            title = "My Database Server",
            type = "login",
            password = "super_secret_db_pass",
            fields = listOf(
                KeeperRecordFieldSummary(type = "login", values = listOf("postgres")),
                KeeperRecordFieldSummary(type = "host", values = listOf("{\"hostName\": \"db.internal\"}")),
            ),
        )

        // Title matches
        val matches = KeeperRecordFilter.matchesFilter(record, "My Database Server")
        assertEquals(true, matches)

        // UID matches
        val uidMatches = KeeperRecordFilter.matchesFilter(record, "UID123")
        assertEquals(true, uidMatches)

        // Case-insensitive title matches
        val caseMatches = KeeperRecordFilter.matchesFilter(record, "my database server")
        assertEquals(true, caseMatches)

        // Password field extraction
        val pass = KeeperRecordFilter.extractTargetFieldValue(record, "password")
        assertEquals("super_secret_db_pass", pass)

        // Blank target defaults to password
        val defaultPass = KeeperRecordFilter.extractTargetFieldValue(record, "")
        assertEquals("super_secret_db_pass", defaultPass)

        // Login field extraction
        val login = KeeperRecordFilter.extractTargetFieldValue(record, "login")
        assertEquals("postgres", login)

        // Non-existent field returns null
        val missing = KeeperRecordFilter.extractTargetFieldValue(record, "non_existent_field")
        assertNull(missing)
    }

    @Test
    fun keeper_record_filter_with_target_field_validation() {
        val records = listOf(
            KeeperRecordSummary(
                recordUid = "REC1",
                title = "Auth Service",
                type = "login",
                password = "auth_password",
                fields = listOf(
                    KeeperRecordFieldSummary(type = "login", values = listOf("auth_user")),
                ),
            ),
            KeeperRecordSummary(
                recordUid = "REC2",
                title = "Payment API",
                type = "login",
                password = null,
                fields = listOf(
                    KeeperRecordFieldSummary(type = "login", values = listOf("pay_admin")),
                ),
            ),
        )

        // 1. Matches Auth Service with password field
        val matchedAuth = KeeperRecordFilter.filterRecords(records, "Auth Service")
        assertEquals(1, matchedAuth.size)
        assertNotNull(KeeperRecordFilter.extractTargetFieldValue(matchedAuth.first(), "password"))

        // 2. Matches Payment API with login field, but password field is absent
        val matchedPay = KeeperRecordFilter.filterRecords(records, "Payment API")
        assertEquals(1, matchedPay.size)
        assertNotNull(KeeperRecordFilter.extractTargetFieldValue(matchedPay.first(), "login"))
        assertNull(KeeperRecordFilter.extractTargetFieldValue(matchedPay.first(), "password"))

        // 3. No match for unknown record
        val matchedUnknown = KeeperRecordFilter.filterRecords(records, "Unknown Service")
        assertEquals(0, matchedUnknown.size)
    }
}
