package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.core.network.VariableResolver
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SecretManagerIconsAndUrlsTest {

    @Test
    fun testSetupUrlsForSecretManagers() {
        assertEquals(
            "https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#setup-secrets-manager",
            externalSecretSetupUrl(ExternalSecretType.KEEPER_SECURITY_CLOUD)
        )
        assertEquals(
            "https://aws.amazon.com/secrets-manager/",
            externalSecretSetupUrl(ExternalSecretType.AWS_SECRETS_MANAGER)
        )
        assertEquals(
            "https://learn.microsoft.com/en-us/azure/key-vault/general/quick-create-portal",
            externalSecretSetupUrl(ExternalSecretType.AZURE_KEY_VAULT)
        )
        assertEquals(
            "https://cloud.google.com/security/products/secret-manager",
            externalSecretSetupUrl(ExternalSecretType.GCP_SECRETS_MANAGER)
        )
        assertEquals(
            "https://developer.hashicorp.com/vault",
            externalSecretSetupUrl(ExternalSecretType.HASHICORP_VAULT_SERVER)
        )
        assertEquals(
            "https://keepassxc.org/docs/KeePassXC_GettingStarted",
            externalSecretSetupUrl(ExternalSecretType.KEEPASSXC)
        )
    }

    @Test
    fun testHelpUrlsForSecretManagers() {
        assertEquals(
            "https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#setup-secrets-manager",
            externalSecretHelpUrl("keeper-security-cloud")
        )
        assertEquals(
            "https://docs.aws.amazon.com/secretsmanager/latest/userguide/create_secret.html",
            externalSecretHelpUrl("aws-secrets-manager")
        )
        assertEquals(
            "https://learn.microsoft.com/en-us/azure/key-vault/secrets/quick-create-portal",
            externalSecretHelpUrl("azure-key-vault")
        )
        assertEquals(
            "https://docs.cloud.google.com/secret-manager/docs/create-secret-quickstart",
            externalSecretHelpUrl("gcp-secrets-manager")
        )
        assertEquals(
            "https://developer.hashicorp.com/vault/tutorials/get-started-hcp-vault-dedicated/create-secret",
            externalSecretHelpUrl("hashicorp-vault-server")
        )
        assertEquals(
            "https://keepassxc.org/docs/KeePassXC_GettingStarted",
            externalSecretHelpUrl("keepassxc")
        )
    }

    @Test
    fun testIconsForSecretManagers() {
        assertEquals(Res.drawable.awssecrets, externalSecretIcon(ExternalSecretType.AWS_SECRETS_MANAGER))
        assertEquals(Res.drawable.azurevault, externalSecretIcon(ExternalSecretType.AZURE_KEY_VAULT))
        assertEquals(Res.drawable.gcpsecretmanager, externalSecretIcon(ExternalSecretType.GCP_SECRETS_MANAGER))
        assertEquals(Res.drawable.hashicorp, externalSecretIcon(ExternalSecretType.HASHICORP_VAULT_SERVER))
        assertEquals(Res.drawable.keepassxc, externalSecretIcon(ExternalSecretType.KEEPASSXC))
        assertEquals(Res.drawable.keepersecurity, externalSecretIcon(ExternalSecretType.KEEPER_SECURITY_CLOUD))

        assertEquals(Res.drawable.awssecrets, externalSecretIconByProvider("aws-secrets-manager"))
        assertEquals(Res.drawable.azurevault, externalSecretIconByProvider("azure-key-vault"))
        assertEquals(Res.drawable.gcpsecretmanager, externalSecretIconByProvider("gcp-secrets-manager"))
        assertEquals(Res.drawable.hashicorp, externalSecretIconByProvider("hashicorp-vault-server"))
        assertEquals(Res.drawable.keepassxc, externalSecretIconByProvider("keepassxc"))
        assertEquals(Res.drawable.keepersecurity, externalSecretIconByProvider("keeper-security-cloud"))
    }

    @Test
    fun testPreviewResolvedUrlResolution() {
        val layers = listOf(
            mapOf("host" to "api.example.com", "port" to "8080", "userId" to "42")
        )
        val rawUrl = "https://{{host}}:{{port}}/v1/users/{{userId}}"
        val resolved = VariableResolver.resolve(
            value = rawUrl,
            variableLayers = layers,
            removeUnresolved = false,
        )
        assertEquals("https://api.example.com:8080/v1/users/42", resolved)
    }
}
