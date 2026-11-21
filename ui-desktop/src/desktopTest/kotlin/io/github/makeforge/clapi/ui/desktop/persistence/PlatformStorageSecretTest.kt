package io.github.makeforge.clapi.ui.desktop.persistence

import io.github.makeforge.clapi.ui.shared.platform.PlatformStorage
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PlatformStorageSecretTest {

    private val testKey = "test.secret.key"
    private val altKey = "test.secret.alt"

    @Before
    fun setUp() {
        PlatformStorage.remove(testKey)
        PlatformStorage.remove(altKey)
    }

    @After
    fun tearDown() {
        PlatformStorage.remove(testKey)
        PlatformStorage.remove(altKey)
    }

    @Test
    fun returns_null_when_secret_key_does_not_exist() {
        assertNull(PlatformStorage.getSecretString("non_existent_key_12345"))
    }

    @Test
    fun roundtrip_put_and_get_secret_string() {
        val secretValue = "s.m948FjK39slkdf02-VaultToken"
        PlatformStorage.putSecretString(testKey, secretValue)

        val retrieved = PlatformStorage.getSecretString(testKey)
        assertEquals(secretValue, retrieved)
    }

    @Test
    fun overwrite_secret_string() {
        PlatformStorage.putSecretString(testKey, "first-secret")
        assertEquals("first-secret", PlatformStorage.getSecretString(testKey))

        PlatformStorage.putSecretString(testKey, "second-secret-updated")
        assertEquals("second-secret-updated", PlatformStorage.getSecretString(testKey))
    }

    @Test
    fun remove_clears_secret_string() {
        PlatformStorage.putSecretString(testKey, "secret-to-be-removed")
        assertEquals("secret-to-be-removed", PlatformStorage.getSecretString(testKey))

        PlatformStorage.remove(testKey)
        assertNull(PlatformStorage.getSecretString(testKey))
    }

    @Test
    fun secret_is_not_stored_as_plain_text_in_regular_storage() {
        val sensitiveValue = "super-secret-vault-root-token"
        PlatformStorage.putSecretString(testKey, sensitiveValue)

        // The regular plaintext getString must NOT contain the sensitive value
        val plainValue = PlatformStorage.getString(testKey)
        assertNotEquals(sensitiveValue, plainValue)
    }

    @Test
    fun handles_empty_and_special_character_strings() {
        val emptySecret = ""
        PlatformStorage.putSecretString(testKey, emptySecret)
        assertEquals(emptySecret, PlatformStorage.getSecretString(testKey))

        val specialCharsSecret = "p@\$\$w0rd!#%&*()[]{}<>:;\"'\\/|~`+=_-^€£¥"
        PlatformStorage.putSecretString(altKey, specialCharsSecret)
        assertEquals(specialCharsSecret, PlatformStorage.getSecretString(altKey))
    }
}
