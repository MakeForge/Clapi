# Research: AWS Secrets Manager Integration

## Unknowns & Research Inquiries

### 1. HTTP Client & AWS SigV4 Signing Implementation
- **Research Question**: How should Clapi authenticate and communicate with AWS Secrets Manager without pulling in large, heavyweight AWS Java SDK dependencies?
- **Decision**: Use a lightweight, dedicated `AwsSigV4Signer` utility coupled with Clapi's existing Ktor `HttpClient`.
- **Rationale**:
  - The AWS Java SDK (v1 or v2) is large (hundreds of megabytes with transitive dependencies), includes complex background threading/credentials providers, and slows desktop application startup.
  - AWS Secrets Manager exposes a straightforward JSON 1.1 RPC API (`x-amz-target: secretsmanager.GetSecretValue`).
  - `AwsSigV4Signer` uses standard JVM cryptography (`javax.crypto.Mac` and `java.security.MessageDigest` for HMAC-SHA256 and SHA-256) with zero external library overhead.
  - Fits directly into Clapi's Ktor architecture alongside `KtorHashiCorpVaultClient` and `KtorApiClient`.
- **Alternatives Considered**:
  - AWS Java SDK v2 (`software.amazon.awssdk:secretsmanager`): Massive jar footprint, high memory overhead, not KMP-friendly.
  - Third-party multiplatform AWS SDKs (e.g., aws-sdk-kotlin): Added external dependency tree and potential version conflicts with existing Ktor/Kotlin versions in the project.

### 2. Configuration Storage Architecture: Global Settings vs. Environment Declarations
- **Research Question**: Where should AWS credentials and secret declarations reside to balance security, usability, and OpenCollection portability?
- **Decision**:
  - **Global Settings (Secrets Manager Tab)**: AWS Region (`region`), Access Key ID (`accessKeyId`), Secret Access Key (`secretAccessKey`, encrypted via `PlatformStorage.putSecretString`), Session Token (`sessionToken`, encrypted), Endpoint URL Override (`endpointUrl`), and Version Stage (`versionStage`).
  - **Environment Declarations (External Secrets Tab)**: Variable Name (`name`), Secret ID / Name (`secretId` / `secretName`), Target Field (`targetField`), Custom Filters (`customFilters`), Description (`description`), and Enabled (`disabled`).
- **Rationale**:
  - Keeps sensitive credentials machine-local and encrypted on the user's machine, preventing accidental leakage when collections are exported or committed to Git.
  - Matches the established architecture for HashiCorp Vault (`Settings -> Secrets Manager` holds server URL and encrypted token, while `Environment -> External Secrets` holds variable mapping).
  - Matches the user's specific requirement: "Support direct user inputs in the UI / collection settings... in AWS one secret id can contain multiple key-value pairs, so user should be able to set custom filter and targetfield, similar to keepassxc, so keep the UI the same for AWS external secrets".
- **Alternatives Considered**:
  - Storing AWS access keys inside each environment: Risks committing AWS credentials to version control and violates OpenCollection portability principles.
  - Automatic environment variable fallback in v1: The user explicitly specified: *"AwsSecretsManagerConfig (has support for env variables, but ignore that for your implementation, it will be added later)"*. Direct UI inputs will be used first.

### 3. Secret Value Parsing & Multi-Value JSON Handling
- **Research Question**: AWS Secrets Manager allows secrets to be either JSON strings (key-value pairs) or arbitrary plain text strings / binary. How should secret retrieval and target field extraction work?
- **Decision**:
  - When a secret is fetched via `secretsmanager.GetSecretValue`, the response contains `SecretString` (or `SecretBinary`).
  - If `SecretString` is valid JSON representing a JSON Object, decode it into a map of `String -> String`.
  - If `targetField` is specified (e.g. `client_id`), look up `map[targetField]`. If found, return that value; if missing, throw a descriptive `IllegalStateException("Field '$targetField' not found in AWS secret '$secretId'...")`.
  - If `SecretString` is plain text (not a JSON Object), or if `targetField` is blank/defaulted to `secret`, return the raw secret string.
  - Support custom field filters: If custom filters are provided, evaluate them against the parsed key-value attributes after interpolating any active environment variables.
- **Rationale**:
  - Matches KeePassXC's target field pattern while naturally handling both common AWS Secrets Manager use cases (JSON key-value bundles vs. raw tokens/certs).
  - Provides clear diagnostic errors when a developer makes a typo in a target field name.
- **Alternatives Considered**:
  - Requiring all secrets to be JSON: Breaks users storing raw strings or single API keys in AWS Secrets Manager.
  - Silent fallback to empty string on missing target field: Hides misconfigurations and results in obscure downstream 401 Unauthorized API failures.

### 4. OpenCollection Data Model & Polymorphic Serialization
- **Research Question**: How should `AwsSecretsManagerExternalSecret` integrate with `core-model` and `core-storage` without breaking existing schemas or deserialization?
- **Decision**:
  - In `core-model` (`OpenCollectionEnvironment.kt`), define `AwsSecretsManagerExternalSecret` implementing `ExternalSecretVariable`:
    ```kotlin
    @Serializable
    data class AwsSecretsManagerExternalSecret(
        override val name: String,
        val secretName: String, // Aliased to secretId
        val targetField: String = "",
        val customFilters: Map<String, String> = emptyMap(),
        override val description: Description? = null,
        override val disabled: Boolean? = null,
    ) : ExternalSecretVariable
    ```
  - In `ExternalSecretVariableSerializer`:
    - Disambiguate polymorphic deserialization based on unique properties:
      - If object has `url` -> `KeePassXcExternalSecret`
      - If object has `secretName` or `secretId` -> `AwsSecretsManagerExternalSecret`
      - Otherwise -> `HashicorpVaultExternalSecret` (or fallback)
  - Ensure serializer and roundtrip tests in `core-storage` verify JSON and YAML serialization without credential leakage.
- **Rationale**:
  - Conforms to OpenCollection v1.0.0 schema which defines `AwsSecretsManagerExternalSecret` with `secretName`.
  - Adding `targetField` and `customFilters` extends the model smoothly for Clapi's multi-value secret resolution while remaining backward-compatible.
- **Alternatives Considered**:
  - Reusing `HashicorpVaultExternalSecret` with path: Confuses users and breaks schema validation for OpenCollection documents.

### 5. Secret Resolution Performance & Deduplication
- **Research Question**: When a request references multiple variables from the same AWS secret (e.g., `{{_secret.client_id}}` and `{{_secret.client_secret}}`), how do we prevent redundant AWS API calls?
- **Decision**: Group required secret declarations by `secretId` prior to querying AWS Secrets Manager. Fetch the secret once per `secretId`, parse the resulting payload, and resolve all requested target fields from the cached secret payload for that request execution.
- **Rationale**:
  - Minimizes network latency and prevents AWS API throttling / rate limiting.
  - Matches the batching pattern already implemented in `SecretResolutionService` for HashiCorp Vault (`secretsByPath`) and KeePassXC (`secretsByUrl`).
- **Alternatives Considered**:
  - Fetching secret on each variable substitution: Inefficient, causing N network roundtrips for N variables from the same secret.

### 6. Automated QA / E2E LocalStack Integration Testing
- **Research Question**: How should automated QA tests run against AWS Secrets Manager without real AWS accounts?
- **Decision**:
  - Integrate LocalStack Secrets Manager into the `qa-tests` Docker setup using the pre-configured compose definition (`scratch/aws/aws-compose.yml`), updating `secret-managers-compose.yml` or adding `aws-compose.yml`.
  - Include an init seeder container or startup script seeding `local/payment/foo` with `{"client_id":"foo_client_123","client_secret":"sk_live_secret_456"}`.
  - Provide an E2E test in `qa-tests` running against `http://localhost:4566` verifying end-to-end secret resolution and variable substitution during live HTTP request execution.
- **Rationale**:
  - Provides reliable, offline, deterministic testing identical to the existing HashiCorp Vault tests in `ExternalSecretsIntegrationTest.kt`.
- **Alternatives Considered**:
  - Mock HTTP responses only: Does not verify real SigV4 signing, CanonicalRequest generation, and HTTP header handling.
