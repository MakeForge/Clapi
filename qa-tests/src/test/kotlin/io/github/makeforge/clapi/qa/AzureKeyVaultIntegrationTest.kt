package io.github.makeforge.clapi.qa

import io.github.makeforge.clapi.core.network.azure.KtorAzureKeyVaultClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AzureKeyVaultIntegrationTest {

    companion object {
        private lateinit var httpClient: HttpClient
        private lateinit var client: KtorAzureKeyVaultClient

        @BeforeClass
        @JvmStatic
        fun setUp() {
            LowkeyVaultTestFixture.ensureRunning()
            httpClient = LowkeyVaultTestFixture.createInsecureHttpClient()
            client = LowkeyVaultTestFixture.createAzureClient(httpClient)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            httpClient.close()
        }
    }

    @Test
    fun testFetchJsonSecretAllKeys() = runBlocking {
        val config = LowkeyVaultTestFixture.createBaseConfig(secretName = "payment-foo")
        val secrets = client.fetchSecret(config)

        assertNotNull(secrets)
        assertEquals(2, secrets.size)
        assertEquals("azure_client_999", secrets["client_id"])
        assertEquals("sk_azure_secret_888", secrets["client_secret"])
    }

    @Test
    fun testFetchJsonSecretWithPropertyKey() = runBlocking {
        val config = LowkeyVaultTestFixture.createBaseConfig(
            secretName = "payment-foo",
            propertyKey = "client_secret",
        )
        val secrets = client.fetchSecret(config)

        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("sk_azure_secret_888", secrets["client_secret"])
    }

    @Test
    fun testFetchPlainTextSecret() = runBlocking {
        val config = LowkeyVaultTestFixture.createBaseConfig(secretName = "plain-token")
        val secrets = client.fetchSecret(config)

        assertNotNull(secrets)
        assertEquals(1, secrets.size)
        assertEquals("super-secret-plain-token-12345", secrets["secret"])
    }

    @Test
    fun testMissingSecretThrowsException() {
        val config = LowkeyVaultTestFixture.createBaseConfig(secretName = "non-existent-secret-xyz")
        assertFailsWith<IllegalStateException> {
            runBlocking {
                client.fetchSecret(config)
            }
        }
    }
}
