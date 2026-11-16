# Feature Specification: Azure Key Vault Integration

**Feature Branch**: `016-azure-key-vault`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "support Azure vault for secret manager similar to hashi corp vault - Use OpenCollection model/enums - see azure folder for example implementation with ktor http client: AzureKeyVaultConfig (has support for env variables, but ignore that for your implementation, it will be added later), KtorAzureKeyVaultClient for getting secrets, test folder with E2E test which can be used together with azure-compose.yml - ensure qa-tests contains E2E test like the one in azure via docker"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure Azure Key Vault Provider in Global Settings (Priority: P1)

Users managing API workflows that depend on Azure Key Vault need a centralized and secure configuration interface in application settings. Under the "Secrets Manager" tab in the application Settings dialog, users can select "Azure Key Vault" as their secret provider and configure:
1. Vault Name or URL (e.g., `my-vault` or `https://my-vault.vault.azure.net` or `https://localhost:8443` for local emulator)
2. Tenant ID (Azure Entra directory tenant ID, e.g., GUID `00000000-0000-0000-0000-000000000000`)
3. Client ID (Azure application / service principal client ID)
4. Client Secret (masked input field, securely encrypted on disk)
5. Authority Host (optional, defaults to `https://login.microsoftonline.com`, with support for custom Entra endpoints or `"bypass"` for local testing)
6. API Version (defaults to `7.4`)

Non-sensitive parameters (vault name, tenant ID, client ID, authority host, API version) are persisted in local preferences, while the client secret is encrypted using local platform storage before saving.

**Why this priority**: Global credentials and connection settings are required for the application to authenticate against Azure Entra ID and retrieve secrets at runtime. Centralizing credentials protects them from exposure in exported collections.

**Independent Test**: Open Settings -> Secrets Manager tab, select "Azure Key Vault", input Vault Name, Tenant ID, Client ID, Client Secret, and Authority Host `bypass`, close and reopen Settings. Verify that settings persist, sensitive fields remain masked, and the secret key is encrypted in platform storage.

**Acceptance Scenarios**:

1. **Given** the global Settings dialog is open, **When** the user navigates to the "Secrets Manager" tab, **Then** "Azure Key Vault" (`azure-key-vault`) is available in the secret provider dropdown.
2. **Given** "Azure Key Vault" is selected, **When** viewing the configuration fields, **Then** inputs are provided for Vault Name / URL, Tenant ID, Client ID, Client Secret (masked), Authority Host (defaulting to `https://login.microsoftonline.com`), and API Version (defaulting to `7.4`).
3. **Given** the user inputs valid Azure settings and saves, **Then** non-sensitive settings persist in local preferences, while Client Secret is stored in encrypted secure storage.
4. **Given** stored Azure settings, **When** the user reopens the Settings dialog, **Then** saved values are loaded and displayed, with Client Secret remaining masked.

---

### User Story 2 - Define Azure External Secrets in Environment Editor (Priority: P1)

Users defining request environments need to reference secrets stored in Azure Key Vault without hardcoding credentials in variables. In the Environment Editor's "External Secrets" tab, users select "Azure Key Vault" as their provider. For each secret, users configure:
1. Secret Variable Name (the local reference name used in requests as `{{_secret.<name>}}`)
2. Secret Name / ID (the secret name in Azure Key Vault, e.g., `payment-foo` or `plain-token`)
3. Target Field / Property Key (the JSON key to extract from multi-value secrets, e.g., `client_id` or `client_secret`, or blank for entire secret)
4. Custom Field Filters (optional key-value matching rules, supporting variable interpolation)
5. Description (optional explanatory note)
6. Enabled checkbox (toggle to activate or deactivate the secret)

The UI matches the tabular layout used for KeePassXC and AWS Secrets Manager external secrets, ensuring consistent UX across all supported providers.

**Why this priority**: Users must map Azure secret names and JSON property keys to local variable names so the system knows which secrets to fetch and how to extract individual attributes.

**Independent Test**: In Environment Editor -> External Secrets, select "Azure Key Vault", add a row with Name `api_token`, Secret Name `payment-foo`, and Target Field `client_secret`, then save. Verify the environment model persists the secret definition according to OpenCollection specifications.

**Acceptance Scenarios**:

1. **Given** the Environment Editor's "External Secrets" tab, **When** selecting the provider dropdown, **Then** "Azure Key Vault" is selectable.
2. **Given** Azure Key Vault is selected, **When** viewing the secrets table, **Then** columns are displayed for Name, Secret Name, Target Field, Filters, Description, and Enabled status.
3. **Given** a multi-value Azure secret containing JSON key-value pairs, **When** the user specifies a Target Field, **Then** the value of that specific key is designated as the resolved secret value.
4. **Given** an environment with Azure external secrets, **When** saved, **Then** the configuration serializes into the OpenCollection model with `ExternalSecretType.AZURE_KEY_VAULT`.

---

### User Story 3 - Resolve Azure Secrets at Runtime During Request Execution (Priority: P1)

Users executing requests that contain `{{_secret.<name>}}` placeholders or environment variables with `secret: true` expect the application to seamlessly fetch the required secret value from Azure Key Vault and inject it into the outgoing request (headers, URL, query parameters, body, or authentication).

At execution time, the application:
1. Identifies all secret references in the request.
2. Acquires an Azure Entra ID bearer token via OAuth2 client credentials flow (`grant_type=client_credentials`, `scope=https://vault.azure.net/.default`), or bypasses token acquisition when `authorityHost` is configured as `"bypass"`.
3. Groups secret references by Secret Name to query each unique Azure secret only once per request execution.
4. Queries Azure Key Vault using HTTP GET and parses the returned secret value (JSON object or plain text string).
5. Applies any configured custom field filters and extracts the specified Target Field.
6. Injects the resolved values into the request execution pipeline.
7. Aborts execution with a clear, descriptive error if authentication fails, the secret cannot be found (404), or the specified target field is missing.

**Why this priority**: Runtime secret resolution is the core capability that enables automated execution of authenticated requests against external systems without committing credentials into collection files.

**Independent Test**: Start Lowkey Vault with seeded secrets `payment-foo` (JSON containing `client_id="azure_client_999"`) and `plain-token` (plaintext `super-secret-plain-token-12345`). Configure Azure credentials in Settings pointing to the Lowkey Vault endpoint with authority host `bypass`. Execute a request using `{{_secret.client_id}}` and verify that the outgoing HTTP request transmits `azure_client_999`.

**Acceptance Scenarios**:

1. **Given** a request referencing `{{_secret.client_id}}` and an active environment with an enabled Azure external secret, **When** the request is dispatched, **Then** the application queries Azure Key Vault with bearer token authorization and injects the resolved secret value.
2. **Given** an Azure secret containing a JSON object `{"client_id": "azure_client_999", "client_secret": "sk_azure_secret_888"}`, **When** resolving a secret with Target Field `client_secret`, **Then** the application extracts `sk_azure_secret_888`.
3. **Given** multiple secret placeholders referencing different target fields from the same Secret Name (e.g., `{{_secret.client_id}}` and `{{_secret.client_secret}}`), **When** executed, **Then** the application queries the Azure Key Vault API only once for that Secret Name.
4. **Given** an Azure secret containing a plain text string (or `contentType: text/plain`), **When** no Target Field is specified (or defaults to `secret`), **Then** the entire string is returned.
5. **Given** a custom filter with variable interpolation (e.g., `{{stage}}`), **When** resolving the secret, **Then** variables in custom filters are interpolated from active environment variables before matching.
6. **Given** invalid Azure credentials, an unreachable endpoint, or a non-existent Secret Name, **When** a request is dispatched, **Then** execution halts with a descriptive error message without exposing client secrets or token values in logs or UI alerts.
7. **Given** an external secret marked as disabled, **When** referenced in a request, **Then** execution is halted with an error indicating the secret is disabled.

---

### User Story 4 - Discover and Insert Azure Secrets via Autocomplete (Priority: P2)

When drafting requests in URL fields, header tables, authentication fields, or request bodies, users need to discover and insert available Azure external secrets quickly. Triggering autocomplete (`Ctrl+Space`) shows available Azure external secrets formatted as `{{_secret.<name>}}` with a distinct visual secret badge. Selecting a suggestion inserts the placeholder at the cursor position.

**Why this priority**: Enhances user productivity and eliminates typographical errors when authoring requests that reference external secrets.

**Independent Test**: In a request editor URL bar with an active environment containing an Azure secret named `apiKey`, press `Ctrl+Space`, verify `{{_secret.apiKey}}` appears in the dropdown with a secret badge, and select it to insert into the editor.

**Acceptance Scenarios**:

1. **Given** an active environment containing enabled Azure external secrets, **When** triggering autocomplete (`Ctrl+Space`) in a request editor field, **Then** Azure external secrets appear in the suggestions list formatted as `{{_secret.<name>}}`.
2. **Given** the suggestions list, **When** viewing an Azure external secret item, **Then** it displays a distinct secret indicator differentiating it from standard variables.
3. **Given** an Azure external secret is marked disabled, **When** triggering autocomplete, **Then** the disabled secret is omitted from suggestions.

---

### User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

Teams sharing collection files need environment external secret references to be portable and version-controlled, while guaranteeing machine-specific Azure credentials (Client Secret, Tenant ID, Client ID, and local vault URLs) remain private to the local user.

When exporting a collection in OpenCollection JSON or YAML format, environment `externalSecrets` blocks with `type: "azure-key-vault"` and variable definitions (name, secretName, targetField, customFilters, description, disabled) are serialized. Global Azure connection credentials stored in application settings are never written to exported files.

**Why this priority**: Ensures zero credential leakage and adherence to the OpenCollection specification when exporting, sharing, or committing collections to source control.

**Independent Test**: Export a collection with an environment containing Azure external secrets to JSON and YAML. Inspect the exported content to confirm that secret variable definitions are present and that no Azure client secrets, tenant IDs, or local tokens exist in the file.

**Acceptance Scenarios**:

1. **Given** an environment with Azure external secrets, **When** exported to OpenCollection JSON or YAML, **Then** the `externalSecrets` block is serialized with `type: "azure-key-vault"` conforming to OpenCollection schema.
2. **Given** an exported collection document, **When** inspected for credentials, **Then** no Azure Client Secret, Tenant ID, Client ID, or platform storage keys appear anywhere in the output.
3. **Given** an OpenCollection file containing Azure external secret declarations, **When** imported into the application, **Then** all Azure external secret variables and their configurations are restored accurately in the Environment Editor.

---

### User Story 6 - Automated Lowkey Vault End-to-End Verification in QA Test Suite (Priority: P2)

Developers and continuous integration pipelines need automated, reproducible end-to-end tests validating Azure Key Vault functionality against a real API implementation without incurring Azure cloud costs or requiring live Azure subscriptions. The `qa-tests` suite includes Docker compose orchestration for Lowkey Vault, pre-seeded test secrets, and automated integration tests verifying client resolution and HTTP request dispatch.

**Why this priority**: Guarantees regressions in Azure Entra authentication, Ktor client communication, or secret parsing are caught before release.

**Independent Test**: Execute `./gradlew :qa-tests:jvmTest --tests "io.github.crustack.clapi.qa.AzureKeyVaultIntegrationTest"` against a running Lowkey Vault container and verify all assertions pass.

**Acceptance Scenarios**:

1. **Given** a Lowkey Vault container running with seeded secrets `payment-foo` (JSON) and `plain-token` (plaintext), **When** the E2E test runs, **Then** the Azure Key Vault client successfully fetches the secret value.
2. **Given** a complete request execution pipeline referencing an Azure external secret, **When** the integration test executes, **Then** the secret is resolved and the outgoing test request receives the correct payload.
3. **Given** a non-existent secret or missing target field in an automated test, **When** resolution is attempted, **Then** the test verifies that an appropriate exception is thrown.

---

### Edge Cases

- **Secret Not Found in Key Vault (404)**: When Azure Key Vault returns HTTP 404 (SecretNotFound), request execution halts with a descriptive error: `External secret '<name>' not found in Azure Key Vault (Secret Name: '<secretName>')`.
- **Invalid Credentials or Unauthorized Access (401/403)**: When Azure Entra ID or Key Vault returns an authentication error (HTTP 401/403), request execution halts with an error explaining the authentication failure without logging raw client secrets.
- **Missing Target Field in Secret JSON**: If the Azure secret is retrieved as a JSON object but does not contain the specified Target Field (e.g., target field `token` requested, but only `client_id` exists), execution halts with an informative error stating that the field was not found in the secret.
- **Plain Text / Non-JSON Secret String**: If the Azure secret is plain text (or `contentType: text/plain`) and the target field is empty or defaulted to `secret`, the application treats the entire raw string as the secret value.
- **Unreachable Endpoint or TLS Error**: If the specified Vault URL is unreachable or untrusted, execution halts with a clear network timeout or SSL error rather than crashing or freezing the UI.
- **Authority Host Bypass Mode**: When `authorityHost` is configured as `"bypass"`, OAuth2 token acquisition from Microsoft Entra ID is bypassed and a dummy token is supplied, allowing local container testing against Lowkey Vault without internet access.
- **Empty or Blank Credentials in UI**: If a user attempts to resolve an Azure secret without configuring a Vault Name, Tenant ID, Client ID, or Client Secret in Settings, execution immediately halts with an instructional message prompting the user to configure Azure Key Vault in Settings -> Secrets Manager.
- **Filter Variable Not Found**: If a custom filter contains a variable placeholder (e.g., `{{stage}}`) that is not defined in the active environment, the filter evaluation safely treats the value as unmatched and reports that no matching entry was found.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support `azure-key-vault` as an external secret provider type in global Settings and Environment configurations, matching `ExternalSecretType.AZURE_KEY_VAULT`.
- **FR-002**: System MUST allow users to configure global Azure Key Vault connection settings including Vault Name / URL, Tenant ID, Client ID, masked Client Secret, optional Authority Host (default: `https://login.microsoftonline.com`), and API Version (default: `7.4`).
- **FR-003**: System MUST securely store sensitive Azure credentials (`clientSecret`) using encrypted local platform storage, keeping them masked in the UI.
- **FR-004**: System MUST allow users to define external secrets in the Environment Editor for the `azure-key-vault` provider, including Secret Variable Name, Secret Name / ID, Target Field (property key), Custom Filters, Description, and Enabled status.
- **FR-005**: System MUST authenticate against Azure Entra ID using the OAuth2 Client Credentials flow (`/oauth2/v2.0/token`) to obtain a bearer token with scope `https://vault.azure.net/.default`.
- **FR-006**: System MUST support bypassing Entra ID authentication when `authorityHost` is set to `"bypass"` to allow offline testing against Lowkey Vault.
- **FR-007**: System MUST query Azure Key Vault via HTTP GET (`/secrets/{secretName}?api-version={apiVersion}`) using the acquired bearer token.
- **FR-008**: System MUST group external secret references by Secret Name to query each unique Azure Key Vault secret only once per request execution.
- **FR-009**: System MUST parse secret values as JSON objects when applicable, extracting the specified Target Field, and return raw strings when secret values are plain text or `contentType: text/plain`.
- **FR-010**: System MUST support custom key-value field filters with environment variable interpolation (e.g., `{{stage}}`) prior to matching secret attributes.
- **FR-011**: System MUST inject resolved secrets into request URLs, query parameters, headers, bodies, and authentication fields using `{{_secret.<name>}}` placeholders.
- **FR-012**: System MUST resolve `secret: true` environment variables referenced as `{{<name>}}` against matching Azure external secrets if not provided by the process environment.
- **FR-013**: System MUST display enabled Azure external secrets in request editor autocomplete (`Ctrl+Space`) with an external-secret badge, and omit disabled secrets.
- **FR-014**: System MUST serialize Azure external secret declarations to OpenCollection JSON and YAML without including Azure client secrets, tenant IDs, client IDs, or platform credentials.
- **FR-015**: System MUST include containerized Lowkey Vault automated end-to-end integration tests in the `qa-tests` module verifying secret retrieval, JSON target field extraction, and error handling.

## Key Entities

- **AzureKeyVaultConfig**: Configuration data model containing `vaultName`, `secretName`, `secretVersion`, `propertyKey`, `tenantId`, `clientId`, `clientSecret`, `authorityHost`, and `apiVersion`.
- **AzureKeyVaultExternalSecret**: OpenCollection data model for an Azure external secret variable containing `name`, `secretName` (or `vaultName`), `targetField`, `customFilters`, `description`, and `disabled`.
- **AppSettings**: Application settings state holding global Azure parameters (`azureVaultName`, `azureTenantId`, `azureClientId`, `azureClientSecret`, `azureAuthorityHost`, `azureApiVersion`).
- **SecretDeclaration**: Internal resolution descriptor capturing secret metadata (`name`, `path`/`secretName`, `targetField`, `customFilters`, `enabled`, `providerType`).

## Success Criteria *(mandatory)*

- **SC-001**: Users can configure Azure Key Vault connection parameters in global settings in under 60 seconds with sensitive secrets stored securely encrypted.
- **SC-002**: Users can define and map multi-value JSON secrets from Azure Key Vault in the Environment Editor using the same familiar interface as KeePassXC and AWS Secrets Manager.
- **SC-003**: 100% of HTTP requests referencing enabled Azure external secrets execute with substituted secret values, batching queries so that multiple placeholders for the same secret issue exactly one network request to Azure Key Vault.
- **SC-004**: 0% credential leakage: exported OpenCollection documents contain zero Azure client secrets, tenant IDs, client IDs, or local tokens.
- **SC-005**: Autocomplete displays available Azure external secrets in less than 50 milliseconds upon `Ctrl+Space` trigger.
- **SC-006**: Automated QA test suite executes end-to-end against Lowkey Vault in continuous integration with 100% pass rate.
