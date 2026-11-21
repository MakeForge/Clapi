package io.github.makeforge.clapi.ui.shared.components

import io.github.makeforge.clapi.core.model.opencollection.ExternalSecretType
import io.github.makeforge.clapi.ui.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

fun externalSecretIcon(type: ExternalSecretType?): DrawableResource = when (type) {
    ExternalSecretType.HASHICORP_VAULT_SERVER, ExternalSecretType.HASHICORP_VAULT_CLOUD -> Res.drawable.hashicorp
    ExternalSecretType.KEEPASSXC -> Res.drawable.keepassxc
    ExternalSecretType.AWS_SECRETS_MANAGER -> Res.drawable.awssecrets
    ExternalSecretType.AZURE_KEY_VAULT -> Res.drawable.azurevault
    ExternalSecretType.GCP_SECRETS_MANAGER -> Res.drawable.gcpsecretmanager
    ExternalSecretType.KEEPER_SECURITY_CLOUD -> Res.drawable.keepersecurity
    else -> Res.drawable.ic_key
}

fun externalSecretIconByProvider(provider: String?): DrawableResource = when (provider) {
    "hashicorp-vault-server", "hashicorp-vault-cloud" -> Res.drawable.hashicorp
    "keepassxc" -> Res.drawable.keepassxc
    "aws-secrets-manager" -> Res.drawable.awssecrets
    "azure-key-vault" -> Res.drawable.azurevault
    "gcp-secrets-manager" -> Res.drawable.gcpsecretmanager
    "keeper-security-cloud" -> Res.drawable.keepersecurity
    else -> Res.drawable.ic_key
}

fun externalSecretSetupUrl(type: ExternalSecretType?): String? = when (type) {
    ExternalSecretType.KEEPER_SECURITY_CLOUD -> "https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#setup-secrets-manager"
    ExternalSecretType.AWS_SECRETS_MANAGER -> "https://aws.amazon.com/secrets-manager/"
    ExternalSecretType.AZURE_KEY_VAULT -> "https://learn.microsoft.com/en-us/azure/key-vault/general/quick-create-portal"
    ExternalSecretType.GCP_SECRETS_MANAGER -> "https://cloud.google.com/security/products/secret-manager"
    ExternalSecretType.HASHICORP_VAULT_SERVER, ExternalSecretType.HASHICORP_VAULT_CLOUD -> "https://developer.hashicorp.com/vault"
    ExternalSecretType.KEEPASSXC -> "https://keepassxc.org/docs/KeePassXC_GettingStarted"
    else -> null
}

fun externalSecretHelpUrl(provider: String?): String? = when (provider) {
    "keeper-security-cloud" -> "https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#setup-secrets-manager"
    "aws-secrets-manager" -> "https://docs.aws.amazon.com/secretsmanager/latest/userguide/create_secret.html"
    "azure-key-vault" -> "https://learn.microsoft.com/en-us/azure/key-vault/secrets/quick-create-portal"
    "gcp-secrets-manager" -> "https://docs.cloud.google.com/secret-manager/docs/create-secret-quickstart"
    "hashicorp-vault-server", "hashicorp-vault-cloud" -> "https://developer.hashicorp.com/vault/tutorials/get-started-hcp-vault-dedicated/create-secret"
    "keepassxc" -> "https://keepassxc.org/docs/KeePassXC_GettingStarted"
    else -> null
}
