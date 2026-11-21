package io.github.crustack.clapi.core.network.keepassxc

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PureJavaKeePassXcClientDesktopTest {

    private class FakeAssociationStorage : KeePassXcAssociationStorage {
        var id: String? = null
        var key: String? = null

        override fun getAssociateId(): String? = id
        override fun getIdKeyPairPublicKey(): String? = key
        override fun saveAssociation(associateId: String, idKeyPairPublicKey: String) {
            id = associateId
            key = idKeyPairPublicKey
        }
        override fun clearAssociation() {
            id = null
            key = null
        }
    }

    @Test
    fun resolveKeePassXcProxyPath_returns_non_empty_path() {
        val path = resolveKeePassXcProxyPath()
        assertNotNull(path)
        assertTrue(path.isNotBlank())
        assertTrue(path.contains("org.keepassxc.KeePassXC.BrowserServer"))
    }

    @Test
    fun client_getAssociateId_and_unassociate_works_with_storage() = runTest {
        val storage = FakeAssociationStorage().apply {
            id = "TestClapiId"
            key = "TestPubKey"
        }
        val client = PureJavaKeePassXcClient(
            associationStorage = storage,
            proxyAccessProvider = { throw UnsupportedOperationException("No real proxy") },
        )

        assertEquals("TestClapiId", client.getAssociateId())
        assertEquals(resolveKeePassXcProxyPath(), client.getProxyPath())

        client.unassociate()

        assertNull(storage.getAssociateId())
        assertNull(storage.getIdKeyPairPublicKey())
        assertNull(client.getAssociateId())
    }
}
