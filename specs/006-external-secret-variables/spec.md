# Feature Specification: External Secret Variables

**Feature Branch**: `006-external-secret-variables`

**Created**: 2026-11-08

**Status**: Draft

**Input**: User description: "Implement Secret Variables with the OpenCollection given schema (environments can have externalSecrets, see @opencollection_v1.0.0.json) - For now only support type HashiCorpVault (server) - External Secret Providers should be configurable in Clapi global Settings -> new Tab Secrets Manager - In this new setting tab you should be able to define the credentials needed for any Secret Manager (for now only hashicorp-vault-server) - For HashiCorp vault settings we need: Server Url, mount path (optional, defaults to \"secret\"), namespace (optional), vault token (password like input) - The not security relevant settings like server url, mount path, namespace can simply be stored in plain PlatformStorage - For vault token extend PlatformStorage to have `putSecretString`/`getSecretString` and use `ksafe` library to store it encrypted - In Environments editor there should now be new tab `External Secrets` where user can choose the secret manager type (currently only `hashicorp-vault-server`) and add/remove secrets (see OpenCollection Hashi Corp Vault external secret type) by setting its name and path, etc. - External Secrets should be usable just like normal variables, e.g. in Request editor it should then be possible to use the external secrets in a similar way , with syntax like `{{_secret.secretName}}`, also CTRL+Space should show below variables also any suitable secret for easier usage - When a request that uses a secret is then executed it should fetch the external secret from its secret manager - To fetch a secret use ktor client for HashiCorp Vault API, could look something like: suspend fun fetchSecret(...) - Clapi will only allow to read secrets from secret-managers, not to add secrets to the external service like HashiCorp Vault - for qa-tests: I already added @qa-tests/secret-managers-compose.yml that spins up hashicorp vault with predefined secret: secret/payment/foo client_id=\"foo_client_123\" client_secret=\"sk_live_secret_456\" - can be fetched then via e.g. curl -H \"X-Vault-Token: root-dev-token\" http://127.0.0.1:8200/v1/secret/data/payment/foo - In OpenCollection export the HashiCorp Vault credentials will not be included, only the environment's externalSecrets with path, name etc."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure Secret Provider Credentials in Global Settings (Priority: P1)

Users managing API workflows with third-party secret managers need a secure central location in application settings to store connection details for secret providers. In the application settings dialog, a new "Secrets Manager" tab allows users to configure providers (starting with HashiCorp Vault Server). The user enters the Server URL (e.g., `http://127.0.0.1:8200`), optional mount path (defaulting to `secret`), optional namespace, and a Vault access token. The token input is masked like a password to prevent accidental exposure on screen. Non-sensitive configuration is stored in local platform storage, while the token is securely encrypted before being written to persistent device storage.

**Why this priority**: Without credentials for the external secret manager, no external secret resolution can take place. Storing credentials centrally and securely protects sensitive credentials from being committed to collection files.

**Independent Test**: Open Settings dialog, navigate to the "Secrets Manager" tab, fill out HashiCorp Vault connection parameters with a masked token, close and re-open the dialog, and verify that the settings persist and the token is stored encrypted.

**Acceptance Scenarios**:

1. **Given** the global Settings dialog is open, **When** the user views the navigation sections, **Then** a "Secrets Manager" tab is present.
2. **Given** the Secrets Manager settings view, **When** HashiCorp Vault is selected, **Then** inputs are provided for Server URL, Mount Path (with placeholder or default `secret`), Namespace (optional), and Vault Token (masked password input with show/hide toggle).
3. **Given** the user inputs valid Vault settings and saves or closes the dialog, **Then** Server URL, Mount Path, and Namespace persist in local preferences, and the Vault Token is stored encrypted in platform secure storage.
4. **Given** stored Vault credentials, **When** the user navigates back to the Secrets Manager tab, **Then** previously saved values are loaded and displayed accurately.

---

### User Story 2 - Define External Secrets in Environment Editor (Priority: P1)

Users configuring environments need to reference secrets stored in an external secret manager rather than hardcoding credentials into plain environment variables. In the Environment Editor, a new "External Secrets" tab allows users to select a secret provider (`hashicorp-vault-server`) and manage a list of external secrets. For each secret, the user specifies the variable name (e.g., `client_id`), the secret path in the vault (e.g., `payment/foo`), an optional description, and an enabled/disabled toggle.

**Why this priority**: Users must declare which external secrets belong to an environment so the application knows what secrets exist and where to fetch them.

**Independent Test**: Open an environment in the Environment Editor, switch to the "External Secrets" tab, select "HashiCorp Vault (Server)", add a secret with name `client_id` and path `payment/foo`, save the environment, and verify that the external secret definition is saved within the environment model according to the OpenCollection schema.

**Acceptance Scenarios**:

1. **Given** the Environment Editor dialog, **When** switching tabs, **Then** both a "Variables" tab and an "External Secrets" tab are available.
2. **Given** the "External Secrets" tab, **When** viewing the provider selection, **Then** the user can select "HashiCorp Vault (Server)" (`hashicorp-vault-server`).
3. **Given** the "External Secrets" table, **When** the user clicks "Add Secret", **Then** a new editable row is created with fields for Name, Path, Description, and an Enabled checkbox.
4. **Given** an existing row in the External Secrets table, **When** the user toggles enabled/disabled or clicks delete, **Then** the state updates immediately and persists when saving the environment.
5. **Given** an environment with defined external secrets, **When** saving the environment, **Then** the external secrets structure conforms to OpenCollection v1.0.0 specification.

---

### User Story 3 - Discover and Insert Secrets via Code Completion (Priority: P2)

Users drafting requests need quick discoverability and autocompletion of external secrets alongside regular variables. When typing in request fields (URL, headers, query parameters, request body, authentication fields), pressing `Ctrl+Space` (or `Cmd+Space`) presents a suggestion dropdown. The dropdown displays standard environment and collection variables, followed by available external secrets formatted as `{{_secret.<name>}}` with clear visual distinction (such as a Secret badge or indicator). Selecting an external secret inserts `{{_secret.<name>}}` at the cursor position.

**Why this priority**: Improves developer experience and prevents syntax typos when referencing vault secrets in request templates.

**Independent Test**: In a request editor URL or body field with an active environment containing external secrets, press `Ctrl+Space`, verify that external secrets appear in the suggestions list with the `{{_secret.<name>}}` syntax, select one, and verify the placeholder is inserted into the text.

**Acceptance Scenarios**:

1. **Given** an active environment containing external secrets, **When** the user triggers autocomplete (`Ctrl+Space`) in an editable request field, **Then** the dropdown displays available external secrets below regular variables.
2. **Given** the autocomplete dropdown, **When** viewing an external secret entry, **Then** it clearly indicates its secret nature and displays syntax `{{_secret.<name>}}`.
3. **Given** the user selects a secret from the autocomplete list, **When** confirmed via Enter or click, **Then** `{{_secret.<name>}}` is inserted at the cursor position without adding trailing or spurious whitespace.
4. **Given** an external secret is marked disabled in the active environment, **When** displaying suggestions, **Then** the disabled secret is omitted from active suggestions.

---

### User Story 4 - Resolve External Secrets at Runtime During Request Execution (Priority: P1)

Users executing requests that reference external secrets expect the application to seamlessly fetch the required secrets from the configured secret manager and substitute them into the outgoing request. When the user sends a request containing `{{_secret.<name>}}` tokens, the application connects to the HashiCorp Vault server using the stored credentials, reads the secret at the specified path, extracts the matching value, replaces the token with the secret value, and dispatches the request. Secret retrieval is read-only; the application never writes or modifies secrets in the external vault.

**Why this priority**: Resolving secrets at execution time is the core business capability of external secrets management, enabling automated authenticated calls against services.

**Independent Test**: Start the HashiCorp Vault test container with secret `payment/foo` containing `client_id="foo_client_123"`. Configure Vault credentials in Settings, configure an external secret `client_id` pointing to `payment/foo` in the active environment, execute a request using `{{_secret.client_id}}`, and verify the outgoing request transmits the resolved secret value.

**Acceptance Scenarios**:

1. **Given** a request with `{{_secret.<name>}}` in the URL, headers, or body, **When** the request is sent, **Then** the application retrieves the corresponding secret from the HashiCorp Vault KV v2 API.
2. **Given** a vault secret at path `payment/foo` returning key-value pairs (e.g. `client_id`), **When** resolving `{{_secret.client_id}}`, **Then** the application extracts `foo_client_123` and injects it into the request.
3. **Given** multiple secret references sharing the same vault path within a single request execution, **When** executed, **Then** the vault path is queried efficiently without redundant duplicate round-trips.
4. **Given** an unreachable Vault server, invalid Vault token, or non-existent secret path, **When** the request is sent, **Then** execution aborts with a descriptive error message indicating the secret resolution failure, without leaking secret tokens in logs.
5. **Given** an external secret marked as disabled, **When** referenced in a request, **Then** the system reports an error indicating the required secret is disabled.

---

### User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

Teams sharing collection files need environment external secret references to be shared while ensuring machine-local secret manager credentials (such as Vault tokens and internal server URLs) remain private to the local machine. When exporting or importing collections in OpenCollection format, environment `externalSecrets` blocks (specifying provider type `hashicorp-vault-server`, secret names, paths, and disabled states) are preserved. Global secret manager credentials stored in application settings are never written to collection files.

**Why this priority**: Guarantees zero credential leakage when sharing or committing API collections to source control repositories.

**Independent Test**: Export a collection containing an environment with configured external secrets to OpenCollection JSON/YAML. Inspect the export file to verify that the environment's `externalSecrets` are present, but no Vault server token or local server credentials appear anywhere in the exported content.

**Acceptance Scenarios**:

1. **Given** an environment with configured external secrets, **When** the collection is exported to OpenCollection JSON or YAML, **Then** the environment contains the `externalSecrets` structure conforming to the OpenCollection v1.0.0 specification.
2. **Given** an exported OpenCollection file, **When** inspected, **Then** no Vault access tokens, passwords, or local provider credentials appear in the file.
3. **Given** an OpenCollection file containing `externalSecrets`, **When** imported into Clapi, **Then** the external secrets and their metadata (name, path, provider type, enabled status) are restored in the Environment Editor.

---

### Edge Cases

- **Missing Vault Credentials**: What happens when a request references `{{_secret.<name>}}` but no HashiCorp Vault settings or token have been configured? The system halts execution and informs the user to configure Vault credentials in Settings -> Secrets Manager.
- **Vault Token Expired or Unauthorized (HTTP 401/403)**: How does the system handle expired credentials? The execution halts with an error indicating an authentication or authorization failure with the secret provider.
- **Secret Key Not Found in Vault Payload (HTTP 404 or Missing Key)**: What happens if the path exists in Vault but does not contain the requested variable name, or the path does not exist? The execution halts with a clear error identifying the missing secret name and path.
- **Special Characters and Escaping**: How are leading/trailing slashes in server URL, mount path, and secret path handled? URLs and paths are normalized (e.g. trimming redundant trailing slashes from the server URL and leading slashes from secret paths).
- **Offline / Network Timeout**: How does the system handle an unreachable Vault server? A graceful timeout occurs with a network failure message; the application UI remains responsive.
- **OpenCollection Secret Variables (`secret: true`)**: When an environment variable has `secret: true`, resolution checks process environment variables, then matching environment external secrets, and reports an error if neither provides a value.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a "Secrets Manager" tab within the global Settings dialog to configure external secret providers.
- **FR-002**: System MUST support the "HashiCorp Vault (Server)" (`hashicorp-vault-server`) provider type in the Secrets Manager settings.
- **FR-003**: System MUST provide inputs for HashiCorp Vault Server URL, Mount Path (defaulting to `secret`), optional Namespace, and Vault Token.
- **FR-004**: System MUST mask the Vault Token input field and securely store the token using encrypted device storage via platform storage encryption extensions.
- **FR-005**: System MUST store non-sensitive provider settings (Server URL, Mount Path, Namespace) in standard persistent platform storage.
- **FR-006**: System MUST provide an "External Secrets" tab in the Environment Editor dialog.
- **FR-007**: System MUST allow users to select the secret provider type (starting with `hashicorp-vault-server`) and manage external secret entries (add, edit, delete, toggle enabled/disabled) with `name`, `path`, and optional `description`.
- **FR-008**: System MUST support variable referencing using `{{_secret.<name>}}` syntax across request URLs, headers, query parameters, request bodies, and authentication fields.
- **FR-009**: System MUST show available external secrets in the autocomplete dropdown below regular variables when the user triggers the variable shortcut (`Ctrl+Space` / `Cmd+Space`).
- **FR-010**: System MUST fetch secrets from the configured secret manager via HTTP API when executing requests referencing external secrets.
- **FR-011**: System MUST restrict external secret manager interactions strictly to read-only retrieval; the application MUST NOT modify, write, or delete secrets in the external secret manager.
- **FR-012**: System MUST halt request execution and display an error message if an external secret cannot be retrieved due to missing configuration, network unreachable, authentication failure, or missing secret key.
- **FR-013**: System MUST serialize environment external secrets into the `externalSecrets` property according to the OpenCollection v1.0.0 schema during collection export.
- **FR-014**: System MUST NEVER export Vault access tokens or secret manager credentials into OpenCollection files or workspace collection exports.
- **FR-015**: System MUST deserialize the `externalSecrets` block when importing OpenCollection documents and populate the Environment Editor accordingly.

### Key Entities

- **SecretProviderConfig**: Represents application-level connection credentials for a secret provider (provider type, server URL, mount path, namespace, and encrypted token reference).
- **ExternalSecretsBlock**: Represents an environment's binding to an external secret provider (provider type and list of external secret declarations).
- **ExternalSecretVariable**: Represents a single declared external secret within an environment, including its variable name, vault path, optional description, and disabled state.
- **VaultSecretResponse**: Represents the structured key-value payload returned by the HashiCorp Vault KV v2 secret engine (`data.data`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure HashiCorp Vault connection settings and verify persistence in under 1 minute.
- **SC-002**: 100% of external secret tokens stored on disk are encrypted using platform-native cryptography and never stored in plain text.
- **SC-003**: Requests referencing external secrets resolve the expected secret values and successfully complete execution against the test service.
- **SC-004**: 0% of exported OpenCollection files contain secret manager tokens or local provider credentials.
- **SC-005**: Autocomplete suggestions display external secrets within 50 milliseconds of invoking the shortcut.
- **SC-006**: When multiple secrets from the same vault path are used in a single request, the vault path is queried only once per execution run.

## Assumptions

- HashiCorp Vault Server uses the standard Key-Value Version 2 (KV v2) engine endpoint structure (`/v1/{mount}/data/{path}`).
- The variable name (`name`) in an external secret corresponds to the key within the Vault KV v2 data map; if the secret at the path contains a single value, that value is used as a fallback if the key name does not strictly match.
- Secret resolution occurs just prior to request execution during the variable interpolation phase.
- Secret values fetched for request execution are held in volatile memory only for the duration of the request execution and are never persisted to disk or collection files.
