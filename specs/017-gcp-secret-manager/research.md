# Research: GCP Secret Manager Integration

## Unknowns & Research Inquiries

### 1. HTTP Client & GCP Authentication Architecture
- **Research Question**: How should Clapi authenticate and retrieve secrets from GCP Secret Manager without introducing bulky Google Cloud SDK dependencies?
- **Decision**: Use a lightweight, dedicated `KtorGcpSecretManagerClient` built on Clapi's existing Ktor `HttpClient` infrastructure, accessing the official Google Secret Manager REST API v1 (`GET https://secretmanager.googleapis.com/v1/projects/{projectId}/secrets/{secretId}/versions/{version}:access`) with an `Authorization: Bearer {accessToken}` header.
- **Rationale**:
  - The official Google Cloud Java SDK (`com.google.cloud:google-cloud-secretmanager`) pulls in heavy transitive dependencies (gRPC Java, Protobuf Java, Google Auth Library, Guava, Netty), which would bloat the desktop application distribution and introduce potential classpath conflicts.
  - Google Secret Manager exposes a clean, stable, public REST API with standard JSON responses: `GET /v1/projects/{project}/secrets/{secretId}/versions/{version}:access`.
  - Authentication in REST is straightforward: a standard HTTP `Authorization: Bearer <token>` header where `<token>` is a valid Google OAuth2 access token or service account token.
  - Fits directly alongside Clapi's existing Ktor-based secret clients (`KtorHashiCorpVaultClient`, `KtorAwsSecretsManagerClient`, `KtorAzureKeyVaultClient`).
  - Aligns with the reference implementation in `scratch/gcp/src/gcp/GcpSecretManagerConfig.kt`.
- **Alternatives Considered**:
  - Official Google Cloud SDK for Java: Rejected due to 40MB+ dependency overhead, complex auth machinery, and lack of Kotlin Multiplatform compatibility.
  - Google Cloud CLI (`gcloud secrets versions access`): Rejected because users may not have `gcloud` installed, subprocess invocation introduces high latency (~500ms+), and it cannot run in containerized or minimal desktop environments.

### 2. Configuration Storage Architecture: Global Settings vs. Environment Declarations
- **Research Question**: Where should GCP credentials and secret declarations reside to balance security, usability, and OpenCollection portability?
- **Decision**:
  - **Global Settings (Secrets Manager Tab)**:
    - `gcpProjectId`: Default Google Cloud project ID or number (e.g., `my-cloud-project` or `123456789012`), persisted in local application preferences.
    - `gcpAccessToken`: Bearer access token, encrypted in secure platform storage via `PlatformStorage.putSecretString`.
    - `gcpEndpointUrl`: Optional endpoint override (defaulting to `https://secretmanager.googleapis.com`), persisted in preferences for local emulator testing (e.g., `http://localhost:8085`).
  - **Environment Declarations (External Secrets Tab)**:
    - Variable Name (`name`): The reference key used in requests as `{{_secret.<name>}}`.
    - Secret ID (`secretId`): The GCP secret resource name (e.g., `payment-foo`, `plain-token`).
    - Version (`version`): Specific version number or alias (e.g., `1`, `staging`), defaulting to `"latest"`.
    - Target Field / Property Key (`targetField`): JSON property key to extract if multi-value (e.g., `client_id`, `client_secret`).
    - Project ID Override (`projectId`): Optional per-secret project override if different from global settings.
    - Custom Filters (`customFilters`): Key-value filtering rules with variable interpolation support.
    - Description (`description`): Explanatory documentation for team members.
    - Enabled status (`disabled`): Toggle to enable or disable the secret variable.
- **Rationale**:
  - Sensitive tokens (`gcpAccessToken`) remain machine-local and encrypted on the user's workstation.
  - Matches the established architecture for HashiCorp Vault, KeePassXC, AWS Secrets Manager, and Azure Key Vault.
  - Prevents credential leakage when collection documents are committed to version control or shared across teams.
  - Direct UI configuration satisfies the user constraint: *"GcpSecretManagerConfig (has support for env variables, but ignore that for your implementation, it will be added later)"*.
- **Alternatives Considered**:
  - Storing access tokens in OpenCollection environment files: Rejected due to severe credential leakage risks.
  - Automatic fallback to process environment variables (`GOOGLE_APPLICATION_CREDENTIALS`, `GCP_ACCESS_TOKEN`): Deferred to a future release per explicit user instructions.

### 3. Secret Value Decoding & Payload Parsing
- **Research Question**: How does GCP Secret Manager deliver secret data, and how should single-value and multi-value secrets be parsed?
- **Decision**:
  - The GCP Secret Manager `:access` endpoint returns a JSON payload containing:
    ```json
    {
      "name": "projects/{project}/secrets/{secretId}/versions/{version}",
      "payload": {
        "data": "<base64-encoded-string>"
      }
    }
    ```
  - The `payload.data` field is Base64 encoded; the client must decode it into UTF-8 text using standard Base64 decoding (`Base64.getDecoder().decode(data)` on JVM, multiplatform compatible).
  - Attempt to parse the decoded UTF-8 text as a JSON object:
    - If valid JSON object: Decode into a `Map<String, String>` of key-value pairs (serializing nested objects/primitives appropriately).
    - If plain text or non-object JSON: Map under key `"secret"` (`mapOf("secret" to decodedText)`).
  - Target field resolution:
    - If `targetField` is specified, extract `secretData[targetField]`. If not found, throw descriptive `NoSuchElementException` or `IllegalStateException`.
    - If `targetField` is blank:
      - If `secretData` contains a key matching the variable name, use it.
      - If single-entry or `"secret"` key, use it.
      - If multiple keys exist without a target field specified, throw an error prompting the user to specify a target field.
  - Custom filters:
    - Evaluate declared `customFilters` against the parsed key-value attributes after interpolating active environment variables.
- **Rationale**:
  - Handles both structured configuration bundles (e.g. database credentials, OAuth client configs) and single plaintext tokens/certificates.
  - Consistent with the existing behavior across AWS Secrets Manager and Azure Key Vault in Clapi.
- **Alternatives Considered**:
  - Forcing all GCP secrets to be formatted as JSON: Rejected because many secrets in GCP Secret Manager are simple strings, PEM keys, or tokens.

### 4. Query Deduplication and Batching
- **Research Question**: How should secret resolution handle multiple variables that reference different target keys within the same GCP secret resource?
- **Decision**:
  - During request pre-flight execution in `SecretResolutionService.resolveGcpSecrets`:
    - Group required secrets by `(projectId, secretId, version)`.
    - Fetch the secret payload from GCP Secret Manager exactly once per unique group.
    - Extract and bind target fields from the cached response map for each variable.
- **Rationale**:
  - Minimizes network latency, bandwidth, and GCP API quota consumption (cost efficiency).
  - Guarantees SC-003: 100% of variables sharing the same secret resource are resolved within a single HTTP request.
- **Alternatives Considered**:
  - Independent HTTP request per variable: Rejected due to redundant network round-trips and quota exhaustion.

### 5. Local Container Testing & QA Integration
- **Research Question**: How to provide reproducible, offline, and automated end-to-end integration tests without incurring Google Cloud billing or requiring active GCP credentials?
- **Decision**:
  - Use the lightweight REST emulator container `ghcr.io/blackwell-systems/gcp-secret-manager-emulator-rest:1.9`.
  - Wire it into `qa-tests/docker/secret-managers-compose.yml` (and/or provide `qa-tests/docker/gcp-compose.yml`) exposing port `8085:8080`.
  - Include a `gcp-seeder` service using `curlimages/curl` that creates:
    - JSON secret: `payment-foo` with payload `{"client_id":"gcp_client_789","client_secret":"sk_gcp_secret_999"}`
    - Plaintext secret: `plain-token` with payload `"gcp-plain-token-value-12345"`
  - Provide automated QA integration tests in `qa-tests/src/test/kotlin/io/github/crustack/clapi/qa/GcpSecretManagerIntegrationTest.kt` verifying:
    - Fetching JSON secret key-values
    - Target field filtering
    - Plaintext secret retrieval
    - Request execution with secret interpolation
    - Negative error handling (missing key, invalid secret ID)
- **Rationale**:
  - The emulator mirrors the official Google Secret Manager REST API v1.
  - Matches the reference setup provided in `scratch/gcp/gcp-compose.yml` and `scratch/gcp/test/gcp/GcpSecretManagerE2ETest.kt`.
  - Completely self-contained and reproducible in local dev and CI pipelines.
- **Alternatives Considered**:
  - Mockito/MockEngine only: Rejected because end-to-end testing against real HTTP containers validates full serialization, Base64 decoding, and network integration.

### 6. OpenCollection Model & Serialization
- **Research Question**: How to integrate GCP Secret Manager into the OpenCollection model and serialization hierarchy?
- **Decision**:
  - `ExternalSecretType` enum in `core-model`: `@SerialName("gcp-secrets-manager") GCP_SECRETS_MANAGER("gcp-secrets-manager")` (already present).
  - Add `GcpSecretsManagerExternalSecret` data class in `core-model/src/commonMain/kotlin/io/github/crustack/clapi/core/model/opencollection/OpenCollectionEnvironment.kt`:
    ```kotlin
    @Serializable
    data class GcpSecretsManagerExternalSecret(
        override val name: String,
        val secretId: String,
        val version: String = "latest",
        val targetField: String = "",
        val projectId: String = "",
        val customFilters: Map<String, String> = emptyMap(),
        override val description: Description? = null,
        override val disabled: Boolean? = null,
    ) : ExternalSecretVariable
    ```
  - Update `ExternalSecretVariableSerializer`:
    - Check `obj["type"]?.jsonPrimitive?.contentOrNull == "gcp-secrets-manager" || obj.containsKey("secretId") && !obj.containsKey("vaultName")` or check distinct discriminator keys.
  - Update `opencollection_v1.0.0.json` schema to include `secretId`, `version`, `targetField`, `projectId`, and `customFilters` for `GcpSecretsManagerExternalSecret`.
- **Rationale**:
  - Full adherence to OpenCollection specification and type safety across Kotlin Multiplatform targets.
