# Feature Specification: AWS Secrets Manager Integration

**Feature Branch**: `015-aws-secrets-manager`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "support AWS for secret manager similar to hashi corp vault - Use OpenCollection model/enums - see aws folder for example implementation with ktor http client: AwsSigV4Signer for special aws specific signing, AwsSecretsManagerConfig (has support for env variables, but ignore that for your implementation, it will be added later), KtorAwsSecretsManagerClient for getting secrets, test folder with E2E test which can be used together with aws-compose.yml - ensure qa-tests contains E2E test like the one in aws via docker localstack - for aws secret manager settings hints from other llm: Support direct user inputs in the UI / collection settings... (Secret ID/Name, Region default us-east-1, Access Key ID, Secret Access Key masked, Session Token optional, Endpoint URL Override optional, Version Stage default AWSCURRENT) - in AWS one secret id can contain multiple key-value pairs, so user should be able to set custom filter and targetfield, similar to keepassxc, so keep the UI the same for AWS external secrets"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure AWS Secrets Manager Provider in Global Settings (Priority: P1)

Users managing API workflows with AWS Secrets Manager need a centralized, secure location in application settings to configure AWS connection parameters. In the application Settings dialog under the "Secrets Manager" tab, users can select "AWS Secrets Manager" as their secret provider. The user configures:
1. AWS Region (defaults to `us-east-1`)
2. AWS Access Key ID (required for authentication)
3. AWS Secret Access Key (masked password input, stored securely)
4. AWS Session Token (optional masked input for temporary/STS credentials)
5. Endpoint URL Override (optional, e.g., `http://localhost:4566` for LocalStack testing or custom VPC endpoints)
6. Version Stage (optional, defaults to `AWSCURRENT`)

Non-sensitive configuration (region, endpoint URL, version stage) is persisted in local platform preferences, while sensitive credentials (secret access key, session token) are encrypted before writing to persistent platform storage.

**Why this priority**: Without credentials and connection details, the application cannot authenticate to AWS or retrieve external secrets at runtime. Centralizing credentials protects them from exposure in collection files.

**Independent Test**: Open Settings dialog, navigate to the "Secrets Manager" tab, select "AWS Secrets Manager", fill in Region, Access Key ID, Secret Access Key, and an Endpoint URL, save and reopen the dialog. Verify that the settings persist and sensitive fields are masked and encrypted.

**Acceptance Scenarios**:

1. **Given** the global Settings dialog is open, **When** the user navigates to the "Secrets Manager" tab, **Then** "AWS Secrets Manager" (`aws-secrets-manager`) is available in the secret provider dropdown.
2. **Given** "AWS Secrets Manager" is selected, **When** viewing the configuration fields, **Then** inputs are provided for AWS Region (defaulting to `us-east-1`), AWS Access Key ID, AWS Secret Access Key (masked), AWS Session Token (optional, masked), Endpoint URL Override (optional), and Version Stage (defaulting to `AWSCURRENT`).
3. **Given** the user inputs valid AWS settings and saves, **Then** Region, Access Key ID, Endpoint URL, and Version Stage persist in platform preferences, while Secret Access Key and Session Token are stored in encrypted secure storage.
4. **Given** stored AWS credentials, **When** the user reopens the Settings dialog, **Then** saved values are loaded and displayed, with secret keys remaining masked.

---

### User Story 2 - Define AWS External Secrets in Environment Editor (Priority: P1)

Users configuring environments need to reference secrets stored in AWS Secrets Manager rather than embedding credentials in plaintext variables. In the Environment Editor's "External Secrets" tab, the user selects "AWS Secrets Manager" as the provider. For each secret, the user configures:
1. Secret Variable Name (the local reference name used in requests as `{{_secret.<name>}}`)
2. Secret ID / Name (the AWS secret name or ARN, e.g., `local/payment/foo` or `prod/api/stripe`)
3. Target Field (the specific JSON key to extract from multi-value secrets, e.g., `client_id` or `client_secret`)
4. Custom Field Filters (optional key-value matching rules, supporting variable interpolation)
5. Description (optional explanatory note)
6. Enabled checkbox (toggle to activate or deactivate the secret)

The UI matches the tabular layout used for KeePassXC external secrets, providing consistent controls for target field extraction and custom filter configuration.

**Why this priority**: Users must declare which AWS secrets map to local variable names so the system knows what secrets to fetch and which JSON keys to extract.

**Independent Test**: In the Environment Editor, switch to "External Secrets", select "AWS Secrets Manager", add a row with Name `client_id`, Secret ID `local/payment/foo`, and Target Field `client_id`, then save. Verify that the secret definition persists in the environment according to OpenCollection models.

**Acceptance Scenarios**:

1. **Given** the Environment Editor's "External Secrets" tab, **When** selecting the provider dropdown, **Then** "AWS Secrets Manager" is selectable.
2. **Given** AWS Secrets Manager is selected, **When** viewing the secrets table, **Then** columns are displayed for Name, Secret ID / Name, Target Field, Filters, Description, and Enabled status, consistent with the KeePassXC secret layout.
3. **Given** a multi-value AWS secret containing key-value pairs, **When** the user specifies a Target Field, **Then** the value of that specific key is designated as the resolved secret value.
4. **Given** an external secret row, **When** the user opens the Custom Filters dialog, **Then** key-value filter pairs can be added, edited, or removed, and filter values support variable placeholders (e.g., `{{stage}}`).
5. **Given** an environment with AWS external secrets, **When** saved, **Then** the configuration serializes into the OpenCollection model with `ExternalSecretType.AWS_SECRETS_MANAGER`.

---

### User Story 3 - Resolve AWS Secrets at Runtime During Request Execution (Priority: P1)

Users executing requests that contain `{{_secret.<name>}}` placeholders or environment variables with `secret: true` expect the application to seamlessly fetch the required secret value from AWS Secrets Manager and inject it into the outgoing request (headers, URL, query parameters, body, or authentication).

At execution time, the application:
1. Identifies all secret references in the request.
2. Connects to AWS Secrets Manager using the configured credentials and endpoint (using AWS Signature Version 4 authentication).
3. Groups secret references by Secret ID to query each unique secret only once per request execution.
4. Parses the returned secret payload (JSON object or plain text string).
5. Applies any configured custom field filters and extracts the specified Target Field.
6. Injects the resolved values into the request execution pipeline.
7. Aborts execution with a clear, descriptive error if the secret cannot be found, authentication fails, or the specified target field is missing.

**Why this priority**: Runtime secret resolution is the core capability that enables automated execution of authenticated requests against external systems without committing credentials into collection files.

**Independent Test**: Start LocalStack with a seeded secret `local/payment/foo` containing `client_id="foo_client_123"`. Configure AWS credentials in Settings pointing to the LocalStack endpoint, configure an environment with an AWS external secret `client_id` pointing to `local/payment/foo` with target field `client_id`, execute a request using `{{_secret.client_id}}`, and verify that the outgoing HTTP request transmits `foo_client_123`.

**Acceptance Scenarios**:

1. **Given** a request referencing `{{_secret.client_id}}` and an active environment with an enabled AWS external secret, **When** the request is dispatched, **Then** the application queries AWS Secrets Manager with AWS SigV4 authorization and injects the resolved secret value.
2. **Given** an AWS secret containing a JSON object `{"client_id": "abc", "client_secret": "xyz"}`, **When** resolving a secret with Target Field `client_secret`, **Then** the application extracts `xyz`.
3. **Given** multiple secret placeholders referencing different target fields from the same Secret ID (e.g., `{{_secret.client_id}}` and `{{_secret.client_secret}}`), **When** executed, **Then** the application queries the AWS Secrets Manager API only once for that Secret ID.
4. **Given** an AWS secret containing a plain non-JSON string, **When** no Target Field is specified (or defaults to the secret value), **Then** the entire string is returned.
5. **Given** a custom filter with variable interpolation (e.g., `{{env_name}}`), **When** resolving the secret, **Then** variables in custom filters are interpolated from active environment variables before matching.
6. **Given** invalid AWS credentials, an unreachable endpoint, or a non-existent Secret ID, **When** a request is dispatched, **Then** execution halts with a descriptive error message without exposing access keys or secret values in logs or UI alerts.
7. **Given** an external secret marked as disabled, **When** referenced in a request, **Then** execution is halted with an error indicating the secret is disabled.

---

### User Story 4 - Discover and Insert AWS Secrets via Autocomplete (Priority: P2)

When drafting requests in URL fields, header tables, authentication fields, or request bodies, users need to discover and insert available AWS external secrets quickly. Triggering autocomplete (`Ctrl+Space`) shows available AWS external secrets formatted as `{{_secret.<name>}}` with a distinct visual secret badge, positioned below normal variables. Selecting a suggestion inserts the placeholder at the cursor position.

**Why this priority**: Enhances user productivity and eliminates typographical errors when authoring requests that reference external secrets.

**Independent Test**: In a request editor URL bar with an active environment containing an AWS secret named `apiKey`, press `Ctrl+Space`, verify `{{_secret.apiKey}}` appears in the dropdown with a secret badge, and select it to insert into the editor.

**Acceptance Scenarios**:

1. **Given** an active environment containing enabled AWS external secrets, **When** triggering autocomplete (`Ctrl+Space`) in a request editor field, **Then** AWS external secrets appear in the suggestions list formatted as `{{_secret.<name>}}`.
2. **Given** the suggestions list, **When** viewing an AWS external secret item, **Then** it displays a distinct secret indicator differentiating it from standard variables.
3. **Given** an AWS external secret is marked disabled, **When** triggering autocomplete, **Then** the disabled secret is omitted from suggestions.

---

### User Story 5 - Portable OpenCollection Export and Import without Credential Leakage (Priority: P2)

Teams sharing collection files need environment external secret references to be portable and version-controlled, while guaranteeing machine-specific AWS credentials (Access Key ID, Secret Access Key, Session Token, and custom endpoint URLs) remain private to the local user.

When exporting a collection in OpenCollection JSON or YAML format, environment `externalSecrets` blocks with `type: "aws-secrets-manager"` and variable definitions (name, secretId/secretName, targetField, customFilters, description, disabled) are serialized. Global AWS connection credentials stored in application settings are never written to exported files.

**Why this priority**: Ensures zero credential leakage and adherence to the OpenCollection specification when exporting, sharing, or committing collections to source control.

**Independent Test**: Export a collection with an environment containing AWS external secrets to JSON and YAML. Inspect the exported content to confirm that secret variable definitions are present and that no AWS access keys, secret keys, or local tokens exist in the file.

**Acceptance Scenarios**:

1. **Given** an environment with AWS external secrets, **When** exported to OpenCollection JSON or YAML, **Then** the `externalSecrets` block is serialized with `type: "aws-secrets-manager"` conforming to OpenCollection schema.
2. **Given** an exported collection document, **When** inspected for credentials, **Then** no AWS Access Key ID, Secret Access Key, Session Token, or platform storage keys appear anywhere in the output.
3. **Given** an OpenCollection file containing AWS external secret declarations, **When** imported into the application, **Then** all AWS external secret variables and their configurations are restored accurately in the Environment Editor.

---

### User Story 6 - Automated LocalStack End-to-End Verification in QA Test Suite (Priority: P2)

Developers and continuous integration pipelines need automated, reproducible end-to-end tests validating AWS Secrets Manager functionality against a real API implementation without incurring AWS cloud costs or requiring live AWS credentials. The `qa-tests` suite includes Docker compose orchestration for LocalStack Secrets Manager, pre-seeded test secrets, and automated integration tests verifying client resolution and HTTP request dispatch.

**Why this priority**: Guarantees regressions in AWS SigV4 signing, Ktor client communication, or secret parsing are caught before release.

**Independent Test**: Execute the QA integration test against a LocalStack container, verifying that pre-seeded secrets are successfully fetched and substituted into an executing HTTP request.

**Acceptance Scenarios**:

1. **Given** a LocalStack container running the `secretsmanager` service with a seeded secret, **When** the E2E test runs, **Then** the AWS Secrets Manager client successfully signs the request with AWS SigV4 and fetches the secret value.
2. **Given** a complete request execution pipeline referencing an AWS external secret, **When** the integration test executes, **Then** the secret is resolved and the outgoing test request receives the correct payload.
3. **Given** a non-existent secret or invalid configuration in an automated test, **When** resolution is attempted, **Then** the test verifies that an appropriate exception is thrown.

---

### Edge Cases

- **Secret ID Not Found**: When AWS Secrets Manager returns a `ResourceNotFoundException` (HTTP 400), request execution halts with a descriptive error: `External secret '<name>' not found in AWS Secrets Manager (Secret ID: '<secretId>')`.
- **Invalid Credentials or Unauthorized Access**: When AWS returns an authentication or signature error (HTTP 400/403 with `UnrecognizedClientException` or `SignatureDoesNotMatch`), request execution halts with an error explaining the authentication failure without logging raw secret keys.
- **Missing Target Field in Secret JSON**: If the AWS secret is retrieved as a JSON object but does not contain the specified Target Field (e.g., target field `token` requested, but only `client_id` exists), execution halts with an informative error stating that the field was not found in the secret.
- **Plain Text / Non-JSON Secret String**: If the AWS secret string is not valid JSON (e.g., a raw certificate, API key, or PEM block) and the target field is empty or defaults to the secret value, the application treats the entire raw string as the secret value.
- **Binary Secret Payload**: If AWS Secrets Manager returns a `SecretBinary` instead of `SecretString`, the application provides the binary payload or decoded string representation.
- **Unreachable Endpoint**: If the specified Endpoint URL is unreachable or times out, execution halts with a clear network timeout/connection error rather than crashing or freezing the UI.
- **Session Token Handling**: When a temporary AWS Session Token is provided, it is included in the AWS SigV4 canonical headers (`x-amz-security-token`); when omitted, the header is not sent.
- **Empty or Blank Credentials in UI**: If a user attempts to resolve an AWS secret without configuring an Access Key ID or Secret Access Key in Settings, execution immediately halts with an instructional message prompting the user to configure AWS credentials in Settings -> Secrets Manager.
- **Filter Variable Not Found**: If a custom filter contains a variable placeholder (e.g., `{{stage}}`) that is not defined in the active environment, the filter evaluation safely treats the value as unmatched and reports that no matching entry was found.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support `aws-secrets-manager` as an external secret provider type in global Settings and Environment configurations, matching `ExternalSecretType.AWS_SECRETS_MANAGER`.
- **FR-002**: System MUST allow users to configure global AWS Secrets Manager connection settings including AWS Region (default: `us-east-1`), AWS Access Key ID, AWS Secret Access Key, optional AWS Session Token, optional Endpoint URL Override, and Version Stage (default: `AWSCURRENT`).
- **FR-003**: System MUST store sensitive AWS credentials (Secret Access Key, Session Token) in platform encrypted secure storage and mask their values in the user interface.
- **FR-004**: System MUST allow users to define external secrets in the Environment Editor when `aws-secrets-manager` is selected, with fields for Variable Name, Secret ID / Name, Target Field, Custom Filters, Description, and Enabled status.
- **FR-005**: System MUST provide a table-based UI for AWS external secrets matching the layout and interaction pattern established for KeePassXC external secrets.
- **FR-006**: System MUST represent AWS external secrets in the OpenCollection data model using an `AwsSecretsManagerExternalSecret` entity containing `name`, `secretName` (or `secretId`), `targetField`, `customFilters`, `description`, and `disabled`.
- **FR-007**: System MUST sign all outgoing AWS Secrets Manager HTTP requests using AWS Signature Version 4 (AWS SigV4) with service `secretsmanager` and the configured region, access key, secret key, and optional session token.
- **FR-008**: System MUST support custom endpoint URLs for AWS Secrets Manager to enable local testing with LocalStack or routing through private API gateways.
- **FR-009**: System MUST parse AWS `GetSecretValue` responses, supporting both JSON key-value extraction via Target Field and raw secret strings.
- **FR-010**: System MUST interpolate environment variables within custom filter values before evaluating secret matching rules.
- **FR-011**: System MUST batch secret resolution requests by Secret ID during request execution so that multiple variables mapped to the same AWS secret ID execute only a single AWS API query.
- **FR-012**: System MUST substitute resolved AWS secrets into request URLs, query parameters, headers, bodies, and authentication fields referencing `{{_secret.<name>}}` and environment variables marked `secret: true`.
- **FR-013**: System MUST provide code autocomplete suggestions for active, enabled AWS external secrets in request editor text fields with `{{_secret.<name>}}` syntax and a distinct secret badge.
- **FR-014**: System MUST prevent AWS credentials (access keys, secret keys, session tokens, local endpoint URLs) from being included in exported OpenCollection JSON and YAML files.
- **FR-015**: System MUST provide automated end-to-end integration tests in `qa-tests` validating secret retrieval and request execution against a LocalStack Secrets Manager container.

### Key Entities *(include if feature involves data)*

- **AwsSecretsManagerConfig**: Global connection parameters for AWS Secrets Manager, encompassing `region`, `accessKeyId`, `secretAccessKey`, `sessionToken`, `endpointUrl`, and `versionStage`.
- **AwsSecretsManagerExternalSecret**: An OpenCollection environment variable mapping an external secret reference to an AWS Secrets Manager secret, including `name` (local variable name), `secretName` / `secretId` (AWS secret identifier or ARN), `targetField` (attribute key within secret JSON), `customFilters` (optional key-value matching criteria), `description`, and `disabled`.
- **SecretDeclaration**: Internal domain representation of an external secret used by the runtime secret resolution engine across secret providers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure AWS Secrets Manager settings and declare an external secret in under 1 minute.
- **SC-002**: Runtime resolution of an AWS external secret from a local or low-latency endpoint completes in under 200 milliseconds during request execution.
- **SC-003**: 100% of declared AWS external secrets referenced in a request sharing the same Secret ID are resolved with exactly one network round-trip to the AWS Secrets Manager endpoint.
- **SC-004**: 0% credential leakage: Zero AWS access keys, secret keys, session tokens, or local endpoints appear in exported OpenCollection collection files or application logs.
- **SC-005**: 100% pass rate for automated end-to-end QA tests running against LocalStack Secrets Manager.

## Assumptions

- Users have valid AWS credentials or a running LocalStack instance when attempting to resolve AWS secrets.
- AWS Secrets Manager secrets containing multiple values are formatted as standard JSON objects with string keys.
- If a secret contains a plain non-JSON string, omitting the target field or specifying the default returns the full secret string.
- Automatic fallback to process environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, etc.) is deferred to a future iteration, as specified by the user prompt; credentials are configured directly via UI settings for this implementation.
- Network connectivity to AWS endpoints or custom LocalStack URLs is available during request execution.
