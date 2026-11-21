# Research: Azure Key Vault Integration

## Unknowns & Research Inquiries

### 1. HTTP Client & Azure Authentication Architecture
- **Research Question**: How should Clapi authenticate and communicate with Azure Key Vault without including large external Azure SDK dependencies?
- **Decision**: Use a dedicated `KtorAzureKeyVaultClient` using Clapi's existing Ktor `HttpClient` infrastructure, implementing Microsoft Entra ID (Azure AD) OAuth2 client credentials flow directly.
- **Rationale**:
  - The official Azure Java SDK (`com.azure:azure-security-keyvault-secrets` and `com.azure:azure-identity`) brings in heavy transitive dependencies (Project Reactor, Netty, Netty native transports, Jackson), increasing startup latency and application binary size by ~50MB.
  - Azure Key Vault provides a clean, well-documented REST API (`GET https://{vaultName}.vault.azure.net/secrets/{secretName}?api-version=7.4`).
  - Microsoft Entra ID OAuth2 token acquisition is a standard `client_credentials` grant via `POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token` with `scope=https://vault.azure.net/.default`.
  - Fits directly into Clapi's existing Ktor client architecture alongside `KtorHashiCorpVaultClient` and `KtorAwsSecretsManagerClient`.
- **Alternatives Considered**:
  - Official Azure SDK for Java (`com.azure:azure-security-keyvault-secrets`): Rejected due to massive jar footprint, reactive programming overhead, and lack of Kotlin Multiplatform support.
  - CLI wrapper (`az keyvault secret show`): Rejected because users may not have the Azure CLI installed, CLI invocation adds process-spawn latency, and it does not work in containerized or minimal desktop environments.

### 2. Configuration Storage Architecture: Global Settings vs. Environment Declarations
- **Research Question**: Where should Azure credentials and secret declarations reside to balance security, usability, and OpenCollection portability?
- **Decision**:
  - **Global Settings (Secrets Manager Tab)**: Vault Name / URL (`azureVaultName`), Tenant ID (`azureTenantId`), Client ID (`azureClientId`), Client Secret (`azureClientSecret`, encrypted via `PlatformStorage.putSecretString`), Authority Host (`azureAuthorityHost`, defaulting to `https://login.microsoftonline.com`), and API Version (`azureApiVersion`, defaulting to `7.4`).
  - **Environment Declarations (External Secrets Tab)**: Secret Variable Name (`name`), Secret Name / ID (`secretName`), Target Field / Property Key (`targetField`), Custom Filters (`customFilters`), Description (`description`), and Enabled status (`disabled`).
- **Rationale**:
  - Sensitive credentials (Client Secret) remain strictly machine-local and encrypted on the user's workstation.
  - Matches the established architecture for HashiCorp Vault, KeePassXC, and AWS Secrets Manager.
  - Prevents credential leakage when collection documents are committed to version control or exported.
- **Alternatives Considered**:
  - Storing Client Secret in Environment files: Rejected because it violates OpenCollection security principles and risks committing Azure credentials to Git.
  - Environment variable fallback in v1: The user explicitly specified: *"AzureKeyVaultConfig (has support for env variables, but ignore that for your implementation, it will be added later)"*. Direct UI inputs will be used first.

### 3. Secret Value Parsing & Multi-Value JSON Handling
- **Research Question**: Azure Key Vault secrets can store either JSON objects (bundles of key-value pairs) or arbitrary plain text strings. How should secret retrieval and target field extraction work?
- **Decision**:
  - When a secret is fetched via `GET /secrets/{secretName}`, Azure Key Vault returns a JSON document containing `value`, `id`, and optional `contentType`.
  - If `contentType` is `text/plain`, treat `value` as raw text (`mapOf("secret" to value)`).
  - Otherwise, attempt to parse `value` as a JSON Object. If valid JSON, decode into a `Map<String, String>`. If parsing fails, fall back to treating it as raw text (`mapOf("secret" to value)`).
  - If `targetField` is specified (e.g., `client_secret`), extract `map[targetField]`. If missing, throw a descriptive `IllegalStateException("Field '$targetField' not found in Azure secret '$secretName'...")`.
  - If `targetField` is blank, default to returning the raw secret value if single-valued or named `"secret"`, or extract the key matching the secret variable name.
  - Support custom field filters: If custom filters are provided, evaluate them against the parsed key-value attributes after interpolating any active environment variables.
- **Rationale**:
  - Matches the behavior of KeePassXC and AWS Secrets Manager, providing a unified developer experience.
  - Gracefully handles both JSON bundles and raw strings/tokens.
- **Alternatives Considered**:
  - Requiring all Azure secrets to be JSON: Rejected because many developers store single API keys or certificates as plain text in Azure Key Vault.

### 4. Local Testing & Lowkey Vault Integration
- **Research Question**: How can automated end-to-end integration tests run against Azure Key Vault without a live Azure subscription or internet connection?
- **Decision**:
  - Use `nagyesta/lowkey-vault:7.4.0-ubi10-minimal` in `qa-tests/docker/secret-managers-compose.yml`.
  - Configure Lowkey Vault on port `8443` with a seeder container that initializes a vault and seeds `payment-foo` (JSON containing `client_id` and `client_secret`) and `plain-token` (plaintext string).
  - Support `authorityHost = "bypass"` in `KtorAzureKeyVaultClient` to bypass Microsoft Entra ID token acquisition when testing against local emulators.
  - Ensure the test HTTP client trusts the Lowkey Vault self-signed TLS certificate.
- **Rationale**:
  - Lowkey Vault is the industry-standard, lightweight, open-source emulator for Azure Key Vault.
  - Enables 100% offline, deterministic CI testing with zero Azure cloud costs.
- **Alternatives Considered**:
  - Mock engine only: Rejected because real HTTP networking, JSON parsing, and TLS handshake verification are required to prevent regressions.

### 5. OpenCollection Schema & Model Mapping
- **Research Question**: How should `AzureKeyVaultExternalSecret` integrate with `core-model` and OpenCollection v1.0.0 schema?
- **Decision**:
  - Define `AzureKeyVaultExternalSecret` in `core-model` (`OpenCollectionEnvironment.kt`):
    ```kotlin
    @Serializable
    data class AzureKeyVaultExternalSecret(
        override val name: String,
        val secretName: String,
        val vaultName: String? = null,
        val targetField: String = "",
        val customFilters: Map<String, String> = emptyMap(),
        override val description: Description? = null,
        override val disabled: Boolean? = null,
    ) : ExternalSecretVariable
    ```
  - Update `ExternalSecretVariableSerializer` to polymorphic-deserialize `AzureKeyVaultExternalSecret` when `vaultName` is present or when provider is `azure-key-vault`.
  - Update `opencollection_v1.0.0.json` schema to include `secretName`, `targetField`, `customFilters`, and `vaultName` on `AzureKeyVaultExternalSecret`.
- **Rationale**:
  - Conforms to OpenCollection v1.0.0 while providing full support for secret naming and property key extraction.
- **Alternatives Considered**:
  - Reusing `HashicorpVaultExternalSecret`: Rejected because provider types and property models are distinct in OpenCollection.
