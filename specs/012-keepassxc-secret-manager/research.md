# Research: KeePassXC Secret Manager Integration

## Unknowns & Research Inquiries

### 1. Library Selection & Native Messaging Protocol
- **Research Question**: How to connect to a locally running KeePassXC instance from a JVM/Compose Multiplatform desktop application?
- **Decision**: Integrate `org.purejava:keepassxc-proxy-access:1.3.2` as a desktop runtime dependency.
- **Rationale**: KeePassXC exposes a secure native messaging proxy socket / named pipe using the KeePassXC-Browser protocol (JSON payloads encrypted with TweetNaCl `crypto_box`). `keepassxc-proxy-access` is a tested, cross-platform Java library specifically implementing this protocol (supporting Linux, macOS, and Windows) with support for `get-logins`, `get-totp`, `associate`, and status checks.
- **Alternatives Considered**:
  - Direct raw socket/named pipe implementation from scratch: High complexity and maintenance burden for cryptographic key negotiation and OS-specific IPC pipes.
  - CLI-based invocation (`keepassxc-cli`): Requires user to provide master password/keyfile directly on the command line or store it, defeating the purpose of seamless local session integration and risking credential exposure in process lists.

### 2. KeePassXC Association Credentials Lifecycle
- **Research Question**: How should KeePassXC association credentials (Associate ID and ID Key Pair public key) be managed and stored?
- **Decision**: Persist the `associateId` and `idKeyPairPublicKey` locally in `PlatformStorage` (`clapi.keepassxc.associateId`, `clapi.keepassxc.idKeyPairPublicKey`).
- **Rationale**: According to KeePassXC protocol specification and library documentation, the Associate ID and ID Key Pair public key are non-sensitive public handshake identities. Storing them locally on the workstation avoids repeated association prompts in KeePassXC while ensuring they are NEVER committed or exported to shared collection files.
- **Alternatives Considered**:
  - In-memory only: Forces the user to re-approve the association dialog in KeePassXC every time Clapi is restarted.
  - Storing inside collection/environment files: Violates security and environment portability because association is machine- and database-specific.

### 3. Querying and Filtering Logins via `getLogins`
- **Research Question**: How does `getLogins` work and how should custom field filters be evaluated?
- **Decision**: Invoke `kpa.getLogins(entryUrl, "", true, idKeyMap)`, which returns a map containing `entries: List<Map<String, Object>>`. Each entry contains standard attributes (`name`, `login`, `password`, `uuid`, `group`) and custom attributes in `stringFields: Map<String, String>`. Custom field filters are evaluated in-memory against these fields.
- **Rationale**: KeePassXC's native messaging API indexes entries by URL. Custom filters allow matching specific entry titles, usernames, or custom attributes (e.g., `login == "admin"` or `env == "{{stage}}"`). Resolving template variables in filter values prior to comparison ensures dynamic multi-environment support.
- **Alternatives Considered**:
  - Requesting all entries across the whole database: Inefficient, privacy-invasive, and not supported by the KeePassXC browser protocol without a URL scope.

### 4. Non-Leaking Match Indicator in Environment UI
- **Research Question**: How to indicate entry availability to users editing secret settings without leaking credentials?
- **Decision**: The UI issues an asynchronous validation call to query matching entries for the current URL and filters, but only captures and exposes the integer count of matches (or connectivity error states such as locked/closed). Secret values (`password`, `totp`, custom values) are never returned to the UI component, never logged, and never rendered in tooltips.
- **Rationale**: Fulfills the user requirement "indicate to the user if and how many fitting entries/values were found (but do not leak them since they are secrets!) while editing the external secret's settings".
- **Alternatives Considered**:
  - Displaying masked placeholder asterisks with length: Leaks password length and entry metadata.
  - Only validating at request execution time: Degrades UX; users would not know if their URL or filters were incorrect until an API call fails.

### 5. Zero Idle Proxy / Lifecycle Management
- **Research Question**: How to guarantee no proxy or background socket runs unnecessarily when KeePassXC is not used?
- **Decision**: Introduce a `KeePassXcClientManager` with lazy connection semantics and active environment tracking.
- **Rationale**:
  - When the active environment has `externalSecretsProvider != "keepassxc"` or contains no enabled KeePassXC secrets, the client is disconnected and no socket or proxy connection is established.
  - When switching environments, the manager disconnects immediately.
  - During secret editing in the Environment Editor, a temporary connection is used for verification and closed upon dialog dismissal if the active environment does not use KeePassXC.
- **Alternatives Considered**:
  - Persistent background daemon process: Wastes system resources and triggers unwanted KeePassXC wakeups/prompts when the user is working on unrelated collections.
