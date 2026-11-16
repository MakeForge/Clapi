# Contract: AWS Secrets Manager Client Interface

## Purpose
Defines the client contract for retrieving secrets from AWS Secrets Manager using AWS SigV4 signed HTTP requests over Ktor.

## Interface Definition

```kotlin
package io.github.makeforge.clapi.core.network.aws

interface AwsSecretsManagerClient {
    /**
     * Retrieves secret data from AWS Secrets Manager.
     *
     * @param config Connection and secret configuration.
     * @return Map of string keys to string values. If the secret is JSON, the map contains
     *         key-value pairs. If the secret is a plain string, the map contains a single entry
     *         with key "secret" (or the raw secret string).
     * @throws IllegalStateException when the secret cannot be retrieved, authentication fails,
     *         or network error occurs.
     */
    suspend fun fetchSecret(config: AwsSecretsManagerConfig): Map<String, String>

    suspend fun fetchSecret(
        secretId: String,
        accessKey: String,
        secretKey: String,
        region: String = "us-east-1",
        sessionToken: String? = null,
        endpointUrl: String? = null,
        versionStage: String? = "AWSCURRENT"
    ): Map<String, String>
}
```

## Protocol Details

- **Protocol**: HTTP/1.1 or HTTP/2 POST
- **Headers**:
  - `Content-Type`: `application/x-amz-json-1.1`
  - `X-Amz-Target`: `secretsmanager.GetSecretValue`
  - `Host`: `<endpoint-host>` or `secretsmanager.<region>.amazonaws.com`
  - `X-Amz-Date`: `YYYYMMDD'T'HHMMSS'Z'`
  - `X-Amz-Security-Token`: `<session-token>` (omitted if null or blank)
  - `Authorization`: `AWS4-HMAC-SHA256 Credential=<accessKey>/<dateStamp>/<region>/secretsmanager/aws4_request, SignedHeaders=<signedHeaders>, Signature=<signature>`
- **Request Body**:
  ```json
  {
    "SecretId": "<secretId>",
    "VersionStage": "AWSCURRENT"
  }
  ```
- **Response Format (HTTP 200)**:
  ```json
  {
    "ARN": "arn:aws:secretsmanager:us-east-1:123456789012:secret:example-123456",
    "Name": "example",
    "VersionId": "b3e34a65-...",
    "SecretString": "{\"client_id\":\"foo\",\"client_secret\":\"bar\"}",
    "VersionStages": ["AWSCURRENT"],
    "CreatedDate": 1583437198.0
  }
  ```
- **Error Response Handling**:
  - `ResourceNotFoundException`: Mapped to `IllegalStateException("External secret not found in AWS Secrets Manager (Secret ID: '<secretId>')")`
  - `UnrecognizedClientException` / `SignatureDoesNotMatch`: Mapped to `IllegalStateException("AWS authentication failed. Please check your AWS credentials.")`
