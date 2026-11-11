# Contract: Azure Key Vault Client Interface

## Purpose

Defines the contract for communicating with Microsoft Azure Key Vault REST API and Microsoft Entra ID OAuth2 token service.

---

## 1. Client Interface Definition

```kotlin
package io.github.crustack.clapi.core.network.azure

interface AzureKeyVaultClient {

    /**
     * Retrieves secret data from Azure Key Vault using a configuration object.
     *
     * @param config The complete connection and secret specification.
     * @return Map of attribute key-value pairs (or mapOf("secret" to value) for plaintext).
     * @throws IllegalStateException when authentication fails, the secret is not found, or network errors occur.
     */
    suspend fun fetchSecret(config: AzureKeyVaultConfig): Map<String, String>

    /**
     * Retrieves secret data from Azure Key Vault using explicit parameters.
     */
    suspend fun fetchSecret(
        vaultName: String,
        secretName: String,
        tenantId: String,
        clientId: String,
        clientSecret: String,
        authorityHost: String = "https://login.microsoftonline.com",
        secretVersion: String? = null,
        propertyKey: String? = null,
        apiVersion: String = "7.4",
    ): Map<String, String>
}
```

---

## 2. Protocol & Wire Behavior

### 2.1 Microsoft Entra ID Token Acquisition
- **Endpoint**: `POST {authorityHost}/{tenantId}/oauth2/v2.0/token`
- **Content-Type**: `application/x-www-form-urlencoded`
- **Form Parameters**:
  - `grant_type`: `"client_credentials"`
  - `client_id`: `{clientId}`
  - `client_secret`: `{clientSecret}`
  - `scope`: `"https://vault.azure.net/.default"`
- **Response**: `200 OK`
  ```json
  {
    "access_token": "eyJ0eXAi...",
    "token_type": "Bearer",
    "expires_in": 3599
  }
  ```
- **Bypass Mode**: When `authorityHost` is `"bypass"`, HTTP token acquisition is skipped and `"dummy-token"` is returned.

### 2.2 Key Vault Secret Retrieval
- **Endpoint**: `GET {vaultUrl}/secrets/{secretName}/{version}?api-version={apiVersion}`
  - If `version` is omitted or blank: `GET {vaultUrl}/secrets/{secretName}?api-version={apiVersion}`
- **Headers**:
  - `Authorization`: `Bearer {access_token}`
  - `Accept`: `application/json`
- **Response**: `200 OK`
  ```json
  {
    "value": "{\"client_id\":\"foo\",\"client_secret\":\"bar\"}",
    "id": "https://my-vault.vault.azure.net/secrets/payment-foo/version-id",
    "contentType": "application/json"
  }
  ```

---

## 3. Error Handling Contract

| HTTP Status | Error Type / Condition | Thrown Exception Message |
|:---|:---|:---|
| `401 Unauthorized` / `403 Forbidden` | Entra ID token failure | `Failed to authenticate with Azure Entra ID [HTTP {status}]: {errorBody}` |
| `404 Not Found` | Secret does not exist in Key Vault | `External secret not found in Azure Key Vault (Secret Name: '{secretName}')` |
| `401 / 403` on Vault | Invalid token or insufficient RBAC | `Azure Key Vault authentication failed. Please check your credentials.` |
| Target key not found | JSON secret does not contain requested key | `Field '{propertyKey}' not found in Azure secret '{secretName}'. Available keys: {keys}` |
