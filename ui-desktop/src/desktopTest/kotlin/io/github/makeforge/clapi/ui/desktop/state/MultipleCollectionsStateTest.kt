package io.github.makeforge.clapi.ui.desktop.state

import io.github.makeforge.clapi.core.model.AuthType
import io.github.makeforge.clapi.core.model.RequestKind
import io.github.makeforge.clapi.ui.shared.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultipleCollectionsStateTest {

    @Test
    fun testCreateMultipleCollectionsAndDisambiguation() {
        val state = AppState(withDemoData = false)
        val col1 = state.createNewCollection("Payments")
        assertEquals("Payments", col1.name)
        assertTrue(col1.isCollection)
        assertEquals(1, state.collections.size)

        val col2 = state.createNewCollection("Users")
        assertEquals("Users", col2.name)
        assertEquals(2, state.collections.size)

        // Conflict disambiguation
        val col3 = state.createNewCollection("Payments")
        assertEquals("Payments (1)", col3.name)
        assertEquals(3, state.collections.size)

        val disambiguated = state.disambiguateCollectionName("Users")
        assertEquals("Users (1)", disambiguated)
    }

    @Test
    fun testOpenAndSyncCollectionTab() {
        val state = AppState(withDemoData = false)
        val col = state.createNewCollection("Orders")
        state.openCollection(col.id)

        val activeTab = state.activeTab
        assertNotNull(activeTab)
        assertEquals(col.id, activeTab.id)
        assertEquals("Orders", activeTab.name)
        assertEquals(RequestKind.COLLECTION, activeTab.kind)
        assertTrue(activeTab.isCollectionTab)

        // Modify in tab and sync back
        activeTab.name = "Orders Service"
        activeTab.collectionSummary = "Handles customer purchases"
        activeTab.collectionVersion = "1.2.3"
        activeTab.authType = AuthType.BEARER
        activeTab.authToken = "order-token"

        val synced = state.syncTabToCollectionNode(activeTab)
        assertTrue(synced)

        val updatedCol = state.collections.first { it.id == col.id }
        assertEquals("Orders Service", updatedCol.name)
        assertEquals("Handles customer purchases", updatedCol.summary)
        assertEquals("1.2.3", updatedCol.version)
        assertEquals(AuthType.BEARER, updatedCol.authType)
        assertEquals("order-token", updatedCol.authToken)
    }

    @Test
    fun testDeleteCollectionClosesTabs() {
        val state = AppState(withDemoData = false, openDefaultTab = false)
        val col = state.createNewCollection("Analytics")
        state.openCollection(col.id)
        assertEquals(1, state.openTabs.size)

        val deleted = state.deleteCollection(col.id)
        assertNotNull(deleted)
        assertEquals(0, state.collections.size)
        assertEquals(0, state.openTabs.size)
    }
}
