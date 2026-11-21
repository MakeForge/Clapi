# Contract: GCP Secret Manager Client Interface

## Purpose

Defines the contract for communicating with the Google Cloud Platform (GCP) Secret Manager REST API v1.

---

## 1. Client Interface Definition

```kotlin
package io.github.crustack.clapi.core.network.gcp

interface GcpSecretManagerClient {

    /**
     * Retrieves secret data from GCP Secret Manager using a configuration object.
     *
     * @param config The complete connection and secret specification.
     * @return Map of attribute key-value pairs (or mapOf("secret" to value) for plaintext).
     * @throws IllegalStateException when authentication fails, the secret is not found, or network errors occur.
     * @throws NoSuchElementException when a specified propertyKey/targetField does not exist in the decoded secret.
     */
    suspend fun fetchSecret(config: GcpSecretManagerConfig): Map<String, String>

    /**
     * Retrieves secret data from GCP Secret Manager using explicit parameters.
     */
    suspend fun fetchSecret(
        projectId: String,
        secretId: String,
        version: String = "latest",
        propertyKey: String? = null,
        accessToken: String,
        endpointUrl: String = "https://secretmanager.googleapis.com",
    ): Map<String, String>
}
```

---

## 2. Protocol & Wire Behavior

### 2.1 GCP Secret Version Access Request
- **Endpoint**: `GET {endpointUrl}/v1/projects/{projectId}/secrets/{secretId}/versions/{version}:access`
- **Path Parameters**:
  - `projectId`: Google Cloud project ID (e.g., `my-cloud-project` or `my-test-project`)
  - `secretId`: Secret resource ID (e.g., `payment-foo`, `plain-token`)
  - `version`: Target version snapshot (e.g., `"1"`) or alias (e.g., `"latest"`)
- **Headers**:
  - `Authorization`: `Bearer {accessToken}`
  - `Accept`: `application/json`

### 2.2 Response Format
- **Status**: `200 OK`
- **Body**:
  ```json
  {
    "name": "projects/my-test-project/secrets/payment-foo/versions/1",
    "payload": {
      "data": "eyJjbGllbnRfaWQiOiJnY3BfY2xpZW50Xzc4OSIsImNsaWVudF9zZWNyZXQiOiJza19nY3Bfc2VjcmV0Xzk5OSJ9"
    }
  }
  ```

### 2.3 Payload Decoding
- Base64 decode `payload.data` into a UTF-8 string.
- If valid JSON object, parse into `Map<String, String>` where keys map to primitive string values (or serialized JSON strings for nested objects).
- If not valid JSON object, wrap as `mapOf("secret" to decodedText)`.
- If `propertyKey` / `targetField` is set, extract only that key. If key is missing, throw `NoSuchElementException("Key '$propertyKey' not found in GCP secret '$secretId'")`.

---

## 3. Error Handling Contract

| HTTP Status | Error Type / Condition | Thrown Exception Message |
|:---|:---|:---|
| `401 Unauthorized` | Invalid or expired Google access token | `GCP Secret Manager request failed [HTTP 401]: {errorBody}` |
| `403 Forbidden` | Insufficient IAM permissions (`secretmanager.versions.access`) | `GCP Secret Manager request failed [HTTP 403]: {errorBody}` |
| `404 Not Found` | Secret ID or version does not exist in project | `GCP Secret Manager request failed [HTTP 404]: {errorBody}` |
| `200 OK` (Invalid Payload) | Response contains null payload data | `GCP Secret Manager response did not contain payload data` |
| `200 OK` (Missing Field) | Target property key missing from JSON payload | `Key '{propertyKey}' not found in GCP secret` |
