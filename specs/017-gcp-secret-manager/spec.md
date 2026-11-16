# Feature Specification: GCP Secret Manager Integration

**Feature Branch**: `017-gcp-secret-manager`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "support GCP Secret manager for secret manager similar to hashi corp vault - Use OpenCollection model/enums - see gcp folder for example implementation with ktor http client: GcpSecretManagerConfig (has support for env variables, but ignore that for your implementation, it will be added later), KtorGcpSecretManagerClient for getting secrets, test folder with E2E test which can be used together with gcp-compose.yml - ensure qa-tests contains E2E test like the one in gcp via docker"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure GCP Secret Manager Provider in Global Settings (Priority: P1)

Users managing API requests and environments that depend on Google Cloud Platform (GCP) Secret Manager need a centralized, secure location in application settings to configure GCP connection parameters. In the application Settings dialog under the "Secrets Manager" tab, users can select "GCP Secret Manager" as their secret provider and configure:
1. GCP Project ID (the Google Cloud project ID or project number, e.g., `my-cloud-project` or `my-test-project`)
2. GCP Access Token (masked credential input, stored encrypted in secure platform storage)
3. Endpoint URL Override (optional, e.g., `http://localhost:8085` for local emulator testing or custom proxy endpoints, defaulting to `https://secretmanager.googleapis.com`)

Non-sensitive configuration (Project ID, Endpoint URL) is persisted in local application preferences, while the access token is encrypted before being stored in persistent platform storage.

**Why this priority**: Global credentials and connection settings are required for the application to authenticate against the GCP Secret Manager API and retrieve secrets at runtime. Centralizing credentials prevents accidental exposure in collection files.

**Independent Test**: Open Settings -> "Secrets Manager" tab, select "GCP Secret Manager", input Project ID, Access Token, and a custom Endpoint URL, save and reopen Settings. Verify that settings persist, sensitive fields remain masked, and the access token is encrypted in platform storage.

**Acceptance Scenarios**:

1. **Given** the global Settings dialog is open, **When** the user navigates to the "Secrets Manager" tab, **Then** "GCP Secret Manager" (`gcp-secrets-manager`) is available in the secret provider dropdown.
2. **Given** "GCP Secret Manager" is selected, **When** viewing configuration fields, **Then** inputs are provided for GCP Project ID, GCP Access Token (masked), and Endpoint URL Override (optional).
3. **Given** the user enters valid GCP settings and saves, **Then** Project ID and Endpoint URL persist in application preferences, while Access Token is stored in encrypted secure storage.
4. **Given** stored GCP settings, **When** the user reopens the Settings dialog, **Then** saved values are loaded and displayed, with the Access Token remaining masked.

---

### User Story 2 - Define GCP External Secrets in Environment Editor (Priority: P1)

Users defining request environments need to reference secrets stored in GCP Secret Manager without hardcoding sensitive values in environment variables. In the Environment Editor's "External Secrets" tab, users select "GCP Secret Manager" as their provider. For each secret, users configure:
1. Secret Variable Name (the local reference name used in requests as `{{_secret.<name>}}`)
2. Secret ID / Name (the secret resource identifier in GCP, e.g., `payment-foo` or `plain-token`)
3. Version (optional target version snapshot or alias, e.g., `latest`, `1`, `staging`, defaulting to `latest`)
4. Target Field / Property Key (optional specific key to extract when the secret payload contains a JSON object, e.g., `client_id` or `client_secret`)
5. Project ID Override (optional per-secret GCP project override if different from global settings)
6. Custom Field Filters (optional key-value matching rules, supporting variable interpolation)
7. Description (optional explanatory note)
8. Enabled checkbox (toggle to activate or deactivate the secret)

The UI matches the tabular layout established for KeePassXC, AWS Secrets Manager, and Azure Key Vault external secrets, ensuring consistent user experience across secret providers.

**Why this priority**: Users must declare external secret mappings so the runtime knows which GCP secrets and keys to fetch and bind to variable placeholders.

**Independent Test**: In Environment Editor -> External Secrets, select "GCP Secret Manager", add a row with Variable Name `stripe_key`, Secret ID `payment-foo`, Target Field `client_secret`, Version `latest`, and save. Verify the environment model persists the secret definition according to OpenCollection specifications.

**Acceptance Scenarios**:

1. **Given** the Environment Editor's "External Secrets" tab, **When** selecting the provider dropdown, **Then** "GCP Secret Manager" is selectable.
2. **Given** "GCP Secret Manager" is selected, **When** viewing the secrets table, **Then** columns are displayed for Variable Name, Secret ID, Target Field, Version, Filters, Description, and Enabled status.
3. **Given** a multi-value GCP secret containing JSON key-value pairs, **When** the user specifies a Target Field, **Then** the value of that specific key is designated as the resolved secret value.
4. **Given** a plaintext GCP secret without a Target Field, **When** resolved, **Then** the entire decoded payload is returned.
5. **Given** an environment with GCP external secrets, **When** saved, **Then** the configuration serializes into the OpenCollection model with `ExternalSecretType.GCP_SECRETS_MANAGER`.

---

### User Story 3 - Resolve GCP Secrets at Runtime During Request Execution (Priority: P1)

When a user executes a request referencing `{{_secret.<name>}}` or an environment variable marked `secret: true`, the runtime engine must resolve the secret value from GCP Secret Manager and inject it into the request before transmission. Secret resolution queries GCP's `:access` endpoint, decodes Base64 payloads, extracts requested fields, and handles errors gracefully without leaking credentials.

To maximize performance, multiple variables mapped to the same Secret ID and Version are resolved with a single batched network query.

**Why this priority**: Seamless secret substitution during request execution is the core operational value of the external secret manager feature.

**Independent Test**: Configure a test GCP Secret Manager endpoint with seeded secrets (`payment-foo` with JSON keys and `plain-token` with plaintext). Execute an HTTP request whose header references `{{_secret.client_id}}`. Verify the request is dispatched with the secret value substituted and the execution succeeds.

**Acceptance Scenarios**:

1. **Given** a request with header `Authorization: Bearer {{_secret.my_token}}` referencing an enabled GCP external secret, **When** the request executes, **Then** the secret is retrieved from GCP Secret Manager, substituted, and sent with the request.
2. **Given** multiple variables referencing different keys within the same GCP Secret ID (`payment-foo`), **When** the request executes, **Then** the secret payload is fetched once and each variable receives its corresponding target field value.
3. **Given** an invalid Access Token or unreachable GCP endpoint, **When** the request executes, **Then** the execution fails cleanly with an informative error message explaining the connection or authentication failure, without exposing token details.
4. **Given** a referenced GCP secret that does not exist or has a missing target field, **When** the request executes, **Then** the user is notified with a clear error indicating the missing secret or property key.

---

### User Story 4 - Discover and Insert GCP Secrets via Autocomplete (Priority: P2)

When authoring requests in the URL bar, headers table, query parameters, auth configuration, or request body editors, users need inline autocomplete suggestions for declared GCP external secrets.

**Why this priority**: Autocomplete speeds up authoring and prevents typos in variable names without requiring users to switch back and forth between the request editor and environment settings.

**Independent Test**: In the URL bar or header value input of a request tab, type `{{_secret.` and observe autocomplete suggestions. Verify all enabled GCP external secrets appear with distinctive badge indicators and descriptions.

**Acceptance Scenarios**:

1. **Given** active GCP external secrets in the current environment, **When** typing `{{_secret.` in any request text field, **Then** suggestions for all enabled GCP secrets appear in the autocomplete popup.
2. **Given** a selected autocomplete item, **When** confirmed, **Then** the full placeholder syntax `{{_secret.<name>}}` is inserted into the editor.
3. **Given** a disabled external secret in the environment, **When** browsing autocomplete suggestions, **Then** disabled secrets are omitted from the list.

---

### User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

Collections and environments containing GCP Secret Manager secret definitions must export cleanly into standard OpenCollection JSON/YAML formats. Sensitive credentials (access tokens) and custom emulator endpoints must never be exported into collection files, ensuring team members can share collections safely via version control without leaking credentials.

**Why this priority**: Security and compliance require that sensitive tokens stay isolated in local platform secure storage, while structural secret variable mappings remain versionable and shareable.

**Independent Test**: Export an environment with GCP external secrets to OpenCollection JSON and YAML. Inspect the exported output and verify `type: "gcp-secrets-manager"` is present along with variable declarations, but the GCP access token is strictly absent. Import the file on another instance and verify secret definitions load correctly.

**Acceptance Scenarios**:

1. **Given** an environment with GCP external secrets, **When** exported to OpenCollection JSON or YAML, **Then** `type` is serialized as `"gcp-secrets-manager"` and variable mappings are preserved.
2. **Given** an exported collection or environment file, **When** inspected, **Then** no GCP access tokens or local emulator overrides appear in the file.
3. **Given** a valid OpenCollection JSON or YAML file containing `gcp-secrets-manager` external secrets, **When** imported, **Then** the secrets are correctly parsed into `GcpSecretsManagerExternalSecret` instances.

---

### User Story 6 - Automated GCP Secret Manager End-to-End Verification in QA Test Suite (Priority: P2)

To prevent regressions and verify integration across components, the QA test suite (`qa-tests`) must include automated end-to-end integration tests that run against a containerized GCP Secret Manager emulator. The tests must validate authenticating, retrieving JSON secrets with target field extraction, retrieving plaintext secrets, handling error states, and verifying request variable substitution.

**Why this priority**: Automated integration tests running in standard CI/CD environments provide continuous confidence that GCP secret resolution remains operational across releases.

**Independent Test**: Execute the QA test suite with the GCP Secret Manager emulator running via Docker Compose (`ghcr.io/blackwell-systems/gcp-secret-manager-emulator-rest:1.9`). Verify all GCP Secret Manager integration tests pass.

**Acceptance Scenarios**:

1. **Given** a running GCP Secret Manager emulator container seeded with test secrets, **When** running the QA integration test suite, **Then** secret retrieval tests successfully fetch and parse both JSON and plaintext secrets.
2. **Given** a simulated request referencing GCP external secrets, **When** executed by the test harness, **Then** variable substitution succeeds and matches expected seeded values.
3. **Given** negative test scenarios (non-existent secret, missing property key), **When** executed, **Then** expected exception types and error handling behaviors are validated.

---

### Edge Cases

- **Non-JSON secret payload with Target Field requested**: If a secret contains a plain text string but the user configured a `targetField`, the resolution engine must fail with a descriptive error indicating that the secret is not a JSON object.
- **Missing or blank Version parameter**: Defaults automatically to `"latest"` so users do not have to manage version strings unless pinned to a specific snapshot.
- **Nested JSON secret values**: If a secret contains nested JSON structures, primitive leaf values are extracted as strings, while complex sub-objects are serialized as formatted JSON strings.
- **Empty or whitespace-only access token**: When no valid access token is provided in settings, resolution fails immediately before making an HTTP network call, advising the user to configure credentials in Settings.
- **Custom endpoint URL formatting**: Custom endpoint URLs are normalized to handle trailing slashes consistently (e.g., `http://localhost:8085/` vs `http://localhost:8085`).
- **Secret payload encoding**: Decodes Base64 data received in the GCP `:access` API payload according to standard Base64 encoding specifications.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a secret provider option for "GCP Secret Manager" (`gcp-secrets-manager`) in the application global Settings dialog.
- **FR-002**: System MUST allow users to configure GCP Secret Manager settings including Project ID, masked Access Token, and optional custom Endpoint URL Override (defaulting to `https://secretmanager.googleapis.com`).
- **FR-003**: System MUST store sensitive GCP credentials (Access Token) in encrypted platform secure storage and mask their representation in the user interface.
- **FR-004**: System MUST allow users to define external secrets in the Environment Editor when `gcp-secrets-manager` is selected, supporting Variable Name, Secret ID, Target Field / Property Key, Version (defaulting to `latest`), Project ID Override (optional), Custom Filters, Description, and Enabled status.
- **FR-005**: System MUST provide a table-based UI for GCP external secrets consistent with the layout used for KeePassXC, AWS Secrets Manager, and Azure Key Vault external secrets.
- **FR-006**: System MUST represent GCP external secrets in the OpenCollection data model using a `GcpSecretsManagerExternalSecret` entity containing `name`, `secretId` / `secretName`, `version`, `targetField`, `projectId`, `customFilters`, `description`, and `disabled`.
- **FR-007**: System MUST support `ExternalSecretType.GCP_SECRETS_MANAGER` with serialized identifier `"gcp-secrets-manager"`.
- **FR-008**: System MUST retrieve GCP secrets via HTTP REST API call to `/v1/projects/{project}/secrets/{secretId}/versions/{version}:access` with an `Authorization: Bearer {accessToken}` header.
- **FR-009**: System MUST decode Base64 secret payload data returned by the GCP Secret Manager `:access` API endpoint.
- **FR-010**: System MUST parse decoded secret payloads, supporting both JSON key-value extraction via Target Field and raw plaintext secret values.
- **FR-011**: System MUST interpolate environment variables within custom filter values before evaluating matching rules.
- **FR-012**: System MUST batch secret resolution requests by Secret ID and Version during request execution so that multiple variables mapped to the same GCP secret execute only a single network query.
- **FR-013**: System MUST substitute resolved GCP secrets into request URLs, query parameters, headers, bodies, and authentication fields referencing `{{_secret.<name>}}` and environment variables marked `secret: true`.
- **FR-014**: System MUST provide code autocomplete suggestions for active, enabled GCP external secrets in request editor text fields with `{{_secret.<name>}}` syntax and a distinct secret badge.
- **FR-015**: System MUST prevent GCP credentials (access tokens, custom local emulator URLs) from being included in exported OpenCollection JSON and YAML files.
- **FR-016**: System MUST provide automated end-to-end integration tests in `qa-tests` validating secret retrieval and request execution against a containerized GCP Secret Manager emulator.

### Key Entities

- **GcpSecretManagerConfig**: Connection configuration for GCP Secret Manager, encompassing `projectId`, `accessToken`, and optional `endpointUrl`.
- **GcpSecretsManagerExternalSecret**: An OpenCollection environment variable mapping an external secret reference to a GCP Secret Manager secret, including `name` (local variable name), `secretId` (GCP secret resource name), `version` (version string or alias), `targetField` (attribute key within secret JSON), `projectId` (optional project override), `customFilters` (optional key-value matching criteria), `description`, and `disabled`.
- **SecretDeclaration**: Internal domain representation of an external secret used by the runtime secret resolution engine across secret providers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure GCP Secret Manager settings and declare an external secret in under 1 minute.
- **SC-002**: Runtime resolution of a GCP external secret from a local emulator or low-latency endpoint completes in under 200 milliseconds during request execution.
- **SC-003**: 100% of declared GCP external secrets referenced in a single request sharing the same Secret ID and Version are resolved with exactly one network round-trip to the GCP Secret Manager endpoint.
- **SC-004**: 0% credential leakage: Zero GCP access tokens or local emulator endpoints appear in exported OpenCollection collection files or application logs.
- **SC-005**: 100% pass rate for automated end-to-end QA tests running against the containerized GCP Secret Manager emulator.

## Assumptions

- Users have valid GCP credentials (or a running local GCP Secret Manager emulator) when attempting to resolve GCP secrets.
- GCP Secret Manager secrets containing multiple values are formatted as standard JSON objects with string keys.
- If a secret contains a plain non-JSON string, omitting the target field returns the full secret string under the default key.
- Automatic fallback to process environment variables (`GCP_PROJECT_ID`, `GCP_ACCESS_TOKEN`, `GOOGLE_CLOUD_PROJECT`, etc.) is deferred to a future iteration, as explicitly requested; credentials are configured directly via UI settings / config for this version.
- Network connectivity to GCP API endpoints or custom emulator URLs is available during request execution.
