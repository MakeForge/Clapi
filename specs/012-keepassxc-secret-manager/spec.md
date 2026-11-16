# Feature Specification: KeePassXC Secret Manager Integration

**Feature Branch**: `012-keepassxc-secret-manager`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "support keeper for secret manager similar to hashi corp vault - add `keepassxc` as ExternalSecrets.type (new enum value, extends OpenCollection available ones) - see @keepassxc.md for library to access locally running keepassxc via proxy - for user to add keepassxc external secret to environment: - user must enter url for desired entry - optionally user can define custom filter for any field (he can also use variables here) - user must enter desired target field for the secret's value - use `getLogins` of keepasxc-proxy-access to query all entries for given url, then filter for the optional custom field filters - indicate to the user if and how many fitting entries/values were found (but do not leak them since they are secrets!) while editing the exteral secret's settings - ensure that if the user in his current environment does not use keepassxc for external secrets that there is no proxy or whatever running unecessarily"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure KeePassXC External Secrets in Environment (Priority: P1)

Users managing API workflows need to reference credentials stored in their local KeePassXC password manager within environment configurations. In the Environment Editor's External Secrets view, the user selects KeePassXC as the secret provider type. For each secret, the user configures:
1. Secret variable name (used in requests as `{{_secret.<name>}}`)
2. Entry URL (the URL associated with the target entry in KeePassXC)
3. Target Field (the specific entry attribute to extract as the secret value, such as password, username, totp, or a custom attribute name)
4. Optional Custom Field Filters (key-value rules to match specific attributes like title or username, supporting environment variable interpolation)

While editing or viewing these settings, the editor connects locally to KeePassXC and displays a non-sensitive match indicator showing whether and how many matching entries were found (e.g., "1 entry matched", "3 entries matched", or "0 entries found"). The indicator never reveals passwords or secret values, ensuring security while giving confidence that the configuration is correct.

**Why this priority**: Configuring external secret definitions and validating that they successfully resolve against KeePassXC entries without leaking secrets is the primary interaction required for KeePassXC integration.

**Independent Test**: Open Environment Editor, select KeePassXC as external secret provider, define a secret pointing to a known test entry URL with target field "password", observe the match indicator reporting "1 entry matched" without revealing the secret value, and save the environment.

**Acceptance Scenarios**:

1. **Given** an environment configuration, **When** selecting the external secrets provider, **Then** `keepassxc` is available as a provider option.
2. **Given** KeePassXC is selected as the provider, **When** adding an external secret, **Then** inputs are provided for Secret Name, Entry URL, Target Field, Description, Enabled toggle, and optional Custom Field Filters.
3. **Given** an Entry URL and optional custom filters, **When** editing the secret in the environment dialog, **Then** the UI queries the local KeePassXC database and displays an entry match indicator showing the count of matching entries.
4. **Given** the entry match indicator, **When** results are displayed, **Then** no password, secret text, or credential payload is exposed in the UI or written to logs.
5. **Given** a custom filter containing a variable expression (e.g. `{{stage}}`), **When** evaluating matching entries, **Then** the variable expression is resolved using current environment variables before matching entry attributes.

---

### User Story 2 - Resolve KeePassXC Secrets at Request Execution (Priority: P1)

Users executing API requests containing `{{_secret.<name>}}` placeholders expect the application to seamlessly fetch the corresponding secret value from their locally running KeePassXC application and inject it into the request context (headers, URL, body, or authentication). The application queries the local KeePassXC instance for entries matching the secret's configured Entry URL, narrows results using any configured custom field filters, extracts the value from the specified target field, and injects it into the outgoing request.

**Why this priority**: Resolving external secrets at runtime is the core functionality enabling automated execution of authenticated API requests without committing secrets into collection files.

**Independent Test**: Send an HTTP request whose header contains `Authorization: Bearer {{_secret.my_token}}` where `my_token` is mapped to a KeePassXC entry with target field "password". Verify the outgoing request transmits the resolved password value.

**Acceptance Scenarios**:

1. **Given** an active environment with KeePassXC external secrets, **When** a request referencing `{{_secret.<name>}}` is dispatched, **Then** the application queries KeePassXC, extracts the target field, and substitutes the value into the request.
2. **Given** a KeePassXC secret configured with custom filters, **When** resolving the secret, **Then** only entries matching all custom filter criteria are considered.
3. **Given** multiple requests in a session referencing the same KeePassXC secret, **When** executed, **Then** values are retrieved accurately without requiring repeated user prompts if already associated.
4. **Given** a disabled KeePassXC external secret referenced in a request, **When** the request is sent, **Then** execution is halted with an error indicating the secret is disabled.
5. **Given** KeePassXC is closed or locked when a request is dispatched, **When** resolution fails, **Then** the request execution aborts with a clear, descriptive error indicating KeePassXC is unavailable or locked.

---

### User Story 3 - On-Demand Connection and Zero Idle Proxy Footprint (Priority: P2)

Users who do not use KeePassXC in their active environment, or who switch between projects and environments, must not experience background resource usage, persistent open proxy sockets, or unsolicited association popups from KeePassXC. The application establishes local communication with KeePassXC on demand only when the currently selected environment has KeePassXC configured as its external secrets provider. When switching to an environment that does not use KeePassXC, any active connection or proxy communication is terminated or kept inactive.

**Why this priority**: Protects system performance, avoids phantom connection prompts in KeePassXC, and ensures clean isolation between environments.

**Independent Test**: Switch to an environment with no external secrets (or HashiCorp Vault). Verify that no KeePassXC proxy connection is initialized or running. Switch to an environment using KeePassXC, execute a secret check/request, and verify the connection activates only then.

**Acceptance Scenarios**:

1. **Given** an environment that does not use KeePassXC external secrets, **When** the user works in the application, **Then** no KeePassXC proxy connection or association request is initiated.
2. **Given** an active KeePassXC connection, **When** the user switches to an environment without KeePassXC external secrets, **Then** the proxy connection is released and no idle background proxy process remains active.
3. **Given** an environment where all KeePassXC external secrets are toggled off (disabled), **When** requests are executed, **Then** the application does not initiate KeePassXC connection calls.

---

### User Story 4 - Discover KeePassXC Secrets via Autocomplete (Priority: P3)

When drafting requests in URL bars, header editors, or request bodies, users need to discover available KeePassXC external secrets alongside normal variables. Typing and triggering auto-completion (`Ctrl+Space`) lists KeePassXC secrets with the `{{_secret.<name>}}` syntax and a distinct visual indicator denoting external secrets.

**Why this priority**: Enhances usability and prevents typing errors when referencing secret variables across requests.

**Independent Test**: In a request editor with an active KeePassXC-enabled environment, trigger `Ctrl+Space` and verify the secret variable name appears in the suggestion list with the `_secret` prefix.

**Acceptance Scenarios**:

1. **Given** an active environment containing enabled KeePassXC secrets, **When** triggering autocompletion (`Ctrl+Space`) in a request field, **Then** KeePassXC secrets appear in the suggestions list formatted as `{{_secret.<name>}}`.
2. **Given** a suggestion for a KeePassXC secret, **When** selected, **Then** the placeholder is inserted into the editor at the cursor position.

---

### Edge Cases

- **KeePassXC Not Running**: When KeePassXC is not open on the machine, the editor match indicator and runtime execution report a friendly error (e.g. "Cannot connect to KeePassXC. Please ensure KeePassXC is running.") rather than crashing or freezing.
- **KeePassXC Database Locked**: When KeePassXC is open but the database is locked, the system detects the locked state and displays an informative prompt instructing the user to unlock their database.
- **Association Denied or Cancelled**: If the user denies or cancels the association prompt in KeePassXC, the application presents a message explaining that KeePassXC access was not authorized, allowing retry.
- **No Matching Entries Found**: If the specified URL or custom filters do not match any entry in the database, the match indicator states "0 entries found", and request execution fails gracefully with an unresolved secret error.
- **Multiple Entries Matching**: If more than one entry matches the URL and filters, the match indicator shows the exact count (e.g., "3 entries matched"), and runtime resolution consistently selects the first matching entry while notifying the user to refine custom filters if ambiguity is undesirable.
- **Missing Target Field**: If a matching entry exists but does not contain the specified target field (e.g. target field is "totp" but the entry has no TOTP configured), resolution fails with a clear message indicating the missing field on the matched entry.
- **Filter Variable Unresolved**: If a custom filter uses a variable (e.g. `{{stage}}`) that does not exist in the active environment, the filter evaluation treats the unresolved variable safely and reports that no matching entry was found.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST add `keepassxc` as a supported `ExternalSecrets.type` enum value extending available OpenCollection external secret types.
- **FR-002**: System MUST allow users to select `keepassxc` as the external secret provider for any collection environment.
- **FR-003**: System MUST provide input fields for each KeePassXC external secret: Variable Name (mandatory), Entry URL (mandatory), Target Field (mandatory), Description (optional), and Enabled state (mandatory, defaulting to true).
- **FR-004**: System MUST allow users to configure optional Custom Field Filters consisting of field name and filter value criteria for any KeePassXC secret.
- **FR-005**: System MUST interpolate environment and collection variables used within Custom Field Filter values before querying or matching KeePassXC entries.
- **FR-006**: System MUST query KeePassXC for entries matching the specified URL and apply any defined custom field filters.
- **FR-007**: System MUST provide an entry match count indicator in the secret editor UI showing the number of matching entries found in KeePassXC without revealing any sensitive secret values.
- **FR-008**: System MUST extract the configured Target Field (such as password, username, totp, or named custom attributes) from the matched KeePassXC entry when resolving the secret value.
- **FR-009**: System MUST resolve KeePassXC external secrets dynamically when executing requests that reference `{{_secret.<name>}}` in an active environment.
- **FR-010**: System MUST NOT start or maintain active KeePassXC proxy communication if the current environment does not use KeePassXC external secrets.
- **FR-011**: System MUST release and disconnect active KeePassXC proxy connections when switching away from a KeePassXC-enabled environment or when all KeePassXC secrets are disabled.
- **FR-012**: System MUST persist KeePassXC client association identity locally on the workstation so subsequent queries do not trigger repeated association prompts in KeePassXC.
- **FR-013**: System MUST NOT export local KeePassXC association keys or credentials into shared collection files or OpenCollection export documents.
- **FR-014**: System MUST report clear, non-leaking error messages when KeePassXC is closed, locked, denied association, or when entries/fields cannot be resolved.

### Key Entities

- **KeePassXC External Secret**: An external secret definition within an environment holding a reference name, provider type (`keepassxc`), target Entry URL, target attribute field, optional custom field filters, description, and enabled flag.
- **Custom Field Filter**: A matching condition specifying an entry attribute key and expected value (with support for variable substitution) used to filter KeePassXC entries.
- **Secret Match Feedback**: An ephemeral validation indicator displayed in the editor conveying connection status and the number of matching entries without holding or exposing any sensitive secret data.
- **KeePassXC Association Record**: Local workstation credentials (Associate ID and public key pair) enabling authorized encrypted communication with the local KeePassXC proxy.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users receive entry match feedback within 3 seconds of entering an Entry URL in the environment secret editor when KeePassXC is running and unlocked.
- **SC-002**: Zero secret values (passwords, tokens, totp) are displayed, rendered in tooltips, or written to application logs during entry validation and match count display.
- **SC-003**: 100% of API requests referencing valid KeePassXC external secrets execute successfully without requiring manual credential copy-pasting.
- **SC-004**: Zero background proxy connections or processes remain active when working in an environment that does not use KeePassXC external secrets.
- **SC-005**: Error states (KeePassXC closed, database locked, association rejected, zero matches) are detected and presented to the user with actionable diagnostic feedback within 2 seconds.

## Assumptions

- The user has KeePassXC (version 2.6.0 or newer) installed locally with browser integration / proxy communication enabled in KeePassXC settings.
- Clapi operates in read-only mode regarding KeePassXC; it will never create, update, or delete entries or groups in the KeePassXC database.
- Target fields supported include standard KeePassXC fields (`password`, `username`, `url`, `totp`) and any custom attribute strings defined on the entry.
- When multiple entries match both the Entry URL and custom field filters, the first matching entry is used for runtime secret extraction, and the UI displays the total count of matches so the user can add or refine filters if needed.
- Association data (Associate ID and ID Key Pair) is workstation-specific public association data and is stored locally on the device, never bundled into shared collection files.
