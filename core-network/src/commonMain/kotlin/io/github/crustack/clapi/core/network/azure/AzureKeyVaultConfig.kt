package io.github.crustack.clapi.core.network.azure

/**
 * Configuration for accessing secrets in Azure Key Vault.
 *
 * @param vaultName Azure Key Vault name or URL (e.g. "my-vault" or "https://localhost:8443")
 * @param secretName Name of the secret in Azure Key Vault (e.g. "payment-foo")
 * @param secretVersion Optional specific secret version
 * @param propertyKey Optional JSON property key to extract from multi-value secret
 * @param tenantId Microsoft Entra ID tenant ID (GUID)
 * @param clientId Application (client) ID
 * @param clientSecret Client secret for authentication
 * @param authorityHost Entra ID authority host (default "https://login.microsoftonline.com", or "bypass" for testing)
 * @param apiVersion Key Vault REST API version (default "7.4")
 */
data class AzureKeyVaultConfig(
    val vaultName: String,
    val secretName: String,
    val secretVersion: String? = null,
    val propertyKey: String? = null,
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
    val authorityHost: String = "https://login.microsoftonline.com",
    val apiVersion: String = "7.4",
)
