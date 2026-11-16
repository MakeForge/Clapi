# Contract: HashiCorp Vault HTTP Client

## Interface: `HashiCorpVaultClient`

Located in `core-network` (`io.github.crustack.clapi.core.network.vault`).

```kotlin
package io.github.crustack.clapi.core.network.vault

interface HashiCorpVaultClient {
    /**
     * Fetches a secret map from HashiCorp Vault KV v2 secret engine.
     *
     * @param vaultUrl Base URL of Vault server (e.g., "http://127.0.0.1:8200").
     * @param token Vault authentication token for X-Vault-Token header.
     * @param secretPath Path of the secret (e.g., "payment/foo").
     * @param mountPath KV v2 mount point (defaults to "secret").
     * @param namespace Vault enterprise namespace (optional).
     * @return Map of string keys to string values extracted from data.data envelope.
     * @throws IllegalStateException when response status is not 2xx or response cannot be parsed.
     */
    suspend fun fetchSecret(
        vaultUrl: String,
        token: String,
        secretPath: String,
        mountPath: String = "secret",
        namespace: String? = null,
    ): Map<String, String>
}
```

## HTTP Protocol Contract

### Request:
- **Method**: `GET`
- **URL**: `{vaultUrl}/v1/{mountPath}/data/{secretPath}`
- **Headers**:
  - `X-Vault-Token: {token}`
  - `X-Vault-Namespace: {namespace}` *(omitted if namespace is null or blank)*
  - `Accept: application/json`
  - `Content-Type: application/json`

### Success Response (HTTP 200):
```json
{
  "request_id": "c138f6d2-...",
  "data": {
    "data": {
      "client_id": "foo_client_123",
      "client_secret": "sk_live_secret_456"
    },
    "metadata": {
      "created_time": "2026-11-08T22:00:00Z",
      "version": 1
    }
  }
}
```

### Extracted Result:
```kotlin
mapOf(
    "client_id" to "foo_client_123",
    "client_secret" to "sk_live_secret_456"
)
```

### Error Responses:
- **HTTP 401 / 403**: Throws `IllegalStateException("Vault authentication failed [HTTP {code}]: permission denied")`
- **HTTP 404**: Throws `IllegalStateException("Vault secret not found [HTTP 404] at path '{mountPath}/data/{secretPath}'")`
- **Network Timeout / Connection Refused**: Throws appropriate network exception with clear diagnostic message.
