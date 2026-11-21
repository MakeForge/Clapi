# Feature Specification: Production VitePress Documentation Site

**Feature Branch**: `024-vitepress-documentation`

**Created**: 2026-11-14

**Status**: Draft

**Input**: User description: "the vitepress documentation described by scratch/documentation.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Developer & User Multi-Protocol API Guidance (Priority: P1)

As an API developer using Clapi, I want a comprehensive, searchable documentation site with visual previews and detailed subpages for all supported request protocols (HTTP, GraphQL, WebSocket, gRPC, and MCP), so that I can quickly learn how to configure, send, and troubleshoot requests without trial-and-error or digging through source code.

**Why this priority**: Discovering how to use Clapi's core request types is the primary reason users visit the documentation. Providing complete visual guides and subpages for each protocol directly drives user success and adoption.

**Independent Test**: Can be tested independently by navigating to each protocol section (e.g. `/request-types/http`, `/request-types/grpc`, `/request-types/mcp`), verifying that the parent page displays a high-resolution screenshot of the UI, and verifying that the subpages provide step-by-step instructions for parameters, authentication, body payloads, and response inspection.

**Acceptance Scenarios**:

1. **Given** a user viewing any request type parent page (HTTP, GraphQL, WebSocket, gRPC, MCP), **When** the page loads, **Then** they see a clear visual screenshot of the Clapi UI in that mode alongside a concise feature overview and quickstart guide.
2. **Given** a user looking for advanced protocol options (such as gRPC reflection, WebSocket live streaming logs, or MCP stdio command execution), **When** they navigate the protocol subpages, **Then** they find dedicated configuration tables, parameter rules, and operational examples.
3. **Given** a user needing to format request payloads, **When** they review the request body subpages, **Then** they see instructions for JSON/JSON5, multipart form-data, urlencoded data, and raw/binary payloads.

---

### User Story 2 - Security, Privacy & Enterprise Compliance Verification (Priority: P1)

As a security engineer or enterprise evaluator, I want clear, prominent documentation on Clapi's zero-telemetry guarantee, KSafe-backed local credential encryption, automated SBOM generation, OSV vulnerability scanning, and third-party open-source dependencies, so that I can formally verify that Clapi is safe to operate in air-gapped or sensitive corporate environments.

**Why this priority**: Trust and data privacy are core differentiators for Clapi compared to cloud-dependent API clients. Enterprise and security-conscious users require concrete verification of local-first storage, encryption mechanisms, and supply chain integrity before adoption.

**Independent Test**: Can be tested independently by opening the Security & Trust section, confirming the existence of the zero-telemetry policy, reviewing the KSafe AES-256-GCM architecture details, inspecting the CycloneDX SBOM download instructions and OSV scan workflows, and verifying that all third-party libraries have direct links to their public GitHub repositories.

**Acceptance Scenarios**:

1. **Given** an auditor reviewing privacy practices, **When** they access the Zero Telemetry documentation, **Then** they see an explicit guarantee confirming zero tracking pixels, zero analytics SDKs, and instructions for verifying network isolation via packet inspection.
2. **Given** a user configuring sensitive API credentials, **When** they read the Secret Storage documentation, **Then** they learn that credentials and external vault tokens are encrypted at rest locally via KSafe using AES-256-GCM and never leaked to workspace files.
3. **Given** a compliance team needing software supply chain validation, **When** they visit the SBOM & Vulnerabilities page, **Then** they find details on CycloneDX SBOM generation, automated OSV-Scanner checks, GitHub Security SARIF reporting, and release artifacts.
4. **Given** an open-source evaluator, **When** they visit the External Libraries page, **Then** they see an exhaustive table of all runtime dependencies with active links to their upstream GitHub repositories and license types.

---

### User Story 3 - Team Collaboration via Git Sync & OpenCollection (Priority: P2)

As a member of an engineering team, I want documentation explaining how Clapi uses standard Git repositories for collection synchronization and how the OpenCollection format works, so that my team can collaborate on API collections using existing Git pull requests, branches, and version control without paying for proprietary cloud workspaces.

**Why this priority**: Collaboration is vital for engineering teams. Demonstrating that Clapi natively supports Git-based team collaboration without third-party cloud lock-in empowers teams to integrate Clapi into existing code-review workflows.

**Independent Test**: Can be tested independently by navigating to the Collaboration section, following the step-by-step Git Sync guide to configure a local Git collection, review status indicators, stage/commit changes, and synchronize collection files and `.proto` schemas across branches.

**Acceptance Scenarios**:

1. **Given** a user with a Git-backed collection, **When** they read the Git Sync documentation, **Then** they understand how to use the Git Sync dialog, commit changes, push to remotes, and pull upstream updates.
2. **Given** a team sharing collections via Git, **When** they review the OpenCollection specification guide, **Then** they understand how collection and folder files are structured for human-readable diffs and clean merging.
3. **Given** a gRPC development team, **When** they check protobuf sync documentation, **Then** they learn how shared `.proto` definitions are versioned and synchronized alongside collection requests.

---

### User Story 4 - Advanced Automation, Environments & Scripting Reference (Priority: P2)

As a QA engineer or automated tester, I want complete documentation on Clapi's environment variable scoping, external secret vault integrations, and the embedded JavaScript scripting engine with full assertion API reference, so that I can script complex API test flows and pre-request token generation.

**Why this priority**: Automated testing, dynamic variable chaining, and environment management are critical workflows for intermediate and power users.

**Independent Test**: Can be tested independently by reviewing the Scripting API reference and verifying that all `clapi.*` assertions (`clapi.test`, `clapi.expect`, assertion chains) and variable scopes are comprehensively documented with copyable code snippets.

**Acceptance Scenarios**:

1. **Given** a developer writing post-response test assertions, **When** they consult the Scripting API reference, **Then** they find exhaustive examples for status code checks, JSON payload parsing, header inspection, and Chai-compatible assertion chaining.
2. **Given** a developer managing multi-tiered environments (Dev, Staging, Prod), **When** they check the Variables documentation, **Then** they understand the resolution precedence (Global, Environment, Collection, Folder, Request) and dynamic variable syntax (`{{$guid}}`, `{{$timestamp}}`).
3. **Given** a user integrating with cloud secret vaults (AWS Secrets Manager, Azure Key Vault, Google Cloud Secret Manager, Keeper, KeePassXC), **When** they read the Secret Managers guide, **Then** they find step-by-step credential setup and reference syntax.

---

### User Story 5 - Continuous Deployment & Single Source of Truth (Priority: P3)

As a project maintainer or contributor, I want the documentation site to build automatically and deploy to GitHub Pages whenever documentation files are updated on the main branch, while completely superseding the legacy `/docs` directory, so that the published documentation is always current, authoritative, and free of obsolete artifacts.

**Why this priority**: Eliminating fragmented legacy documentation and automating publishing prevents documentation drift and ensures users always access the single source of truth.

**Independent Test**: Can be tested independently by triggering the GitHub Actions workflow, verifying successful static site generation with zero broken internal links, and verifying that the output deploys directly to the repository's GitHub Pages domain.

**Acceptance Scenarios**:

1. **Given** a pull request or push modifying documentation sources under `docs/`, **When** the GitHub Actions workflow executes, **Then** it builds the static site, validates internal links, and publishes to GitHub Pages on pushes to `main`.
2. **Given** a reader accessing documentation previously housed in disparate `/docs/*.md` files, **When** they visit the new documentation site, **Then** all historical architectural, testing, and operational information is organized logically under modern routes with no legacy duplicate pages.

---

### Edge Cases

- **Offline Reading**: How does the documentation site handle users working in air-gapped or offline environments? The site uses local client-side search (`minisearch`) with zero external CDN dependencies or external analytics scripts, allowing the built static bundle to be browsed locally or hosted on an internal intranet.
- **Missing or Outdated Screenshots**: How does the documentation handle newly added features or UI theme variations? The documentation structure defines standardized asset paths with fallback previews and light/dark theme toggle support.
- **Broken Internal Links during Migration**: How does the build ensure that old document links do not result in 404 errors? The VitePress build process fails on broken internal links, and URL redirects/aliases map former `/docs/*.md` references to the new page hierarchy.
- **Large API Code Snippets on Mobile Viewports**: How does the documentation site present wide tables, JSON schemas, and code blocks on smaller screens? Responsive CSS containers provide horizontal scrolling with fixed header row readability.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Site Infrastructure & Design
- **FR-001**: The documentation site MUST be implemented using a modern markdown-based static site generator (VitePress) structured under `docs/`, replacing the legacy documentation files.
- **FR-002**: The site MUST feature responsive dark and light themes matching the application's Material 3 visual styling.
- **FR-003**: The site MUST provide a fast, client-side, privacy-preserving local search engine that operates without transmitting queries to third-party servers.
- **FR-004**: The site MUST support syntax highlighting for all relevant languages: Kotlin, JavaScript, TypeScript, JSON, JSON5, YAML, GraphQL, Protocol Buffers (`proto3`), HTTP, and Shell.
- **FR-005**: The site MUST support native Mermaid diagrams for visual architectural, inheritance, and workflow diagrams.

#### Trust, Security & Open Source Governance
- **FR-006**: The site MUST feature a dedicated **Zero-Telemetry & Privacy Guarantee** page explicitly stating that Clapi contains no analytics, no user tracking, and zero background telemetry.
- **FR-007**: The site MUST feature a dedicated **Local Credential Encryption via KSafe** page detailing the AES-256-GCM hardware-accelerated encryption mechanism and PBKDF2 key derivation for local secrets.
- **FR-008**: The site MUST feature a dedicated **SBOM & Vulnerability Scanning** page documenting CycloneDX SBOM generation, OSV-Scanner checks, GitHub Security SARIF uploads, and download links for release SBOMs.
- **FR-009**: The site MUST provide a comprehensive, transparent **External Libraries Directory** listing all runtime dependencies with their version, license type, and direct clickable links to their official GitHub repositories.

#### Multi-Protocol Request Type Documentation
- **FR-010**: The site MUST provide a dedicated parent page for **HTTP / REST Requests** displaying a high-resolution UI screenshot and subpages for parameters, headers/inheritance, body formats, authentication methods, response viewer metrics, and cURL integration.
- **FR-011**: The site MUST provide a dedicated parent page for **GraphQL Requests** displaying a UI screenshot and subpages for queries/mutations, variables, schema introspection/documentation explorer, and authentication.
- **FR-012**: The site MUST provide a dedicated parent page for **WebSocket Requests** displaying a UI screenshot and subpages for connection handshakes, message composer, live bidirectional message logs, and connection lifecycle.
- **FR-013**: The site MUST provide a dedicated parent page for **gRPC Requests** displaying a UI screenshot and subpages for Protobuf schema management, server reflection, method selection, dynamic JSON-to-protobuf editing, and TLS/mTLS configuration.
- **FR-014**: The site MUST provide a dedicated parent page for the **Model Context Protocol (MCP) Client** displaying a UI screenshot and subpages for transports (Streamable HTTP 2025-06-18, Legacy SSE 2024-11-05, stdio with shell PATH resolution), tools discovery/execution, resources, prompts/sampling, OAuth 2.1, and protocol activity logs.
- **FR-015**: The site MUST document **Realtime Streams** including Server-Sent Events (SSE), Socket.IO, and MQTT over WebSockets.

#### Workspaces, Inheritance & Team Collaboration
- **FR-016**: The site MUST document **Multiple Collections & OpenCollection Architecture**, including collection lifecycle, settings tabs, and OpenCollection schema portability.
- **FR-017**: The site MUST detail **Folder Request Defaults & Inheritance**, documenting the cascading rules (`Collection -> Folder -> Request`), visual `[Inherited]` badges, and parameter override mechanics.
- **FR-018**: The site MUST feature a dedicated **Git Sync & Team Collaboration** guide explaining local repository mapping, the in-app Git Sync dialog, commit/push/pull workflows, branch switching, and Protobuf schema versioning.

#### Environments, Secrets & Scripting
- **FR-019**: The site MUST document **Variable Scopes** (Global, Environment, Collection, Folder, Request), variable syntax (`{{variable}}`), and dynamic variables (`{{$guid}}`, `{{$timestamp}}`).
- **FR-020**: The site MUST document external secret vault integrations (KeePassXC, AWS Secrets Manager, Azure Key Vault, Google Cloud Secret Manager, Keeper) and explain how vault secrets remain encrypted on disk via KSafe.
- **FR-021**: The site MUST document the **Javet V8 Scripting Engine**, pre-request scripting lifecycle, post-response assertions, and provide a complete API reference table for `clapi.*` methods.

#### Utilities & Deployment
- **FR-022**: The site MUST document specification **Import & Export** capabilities for OpenAPI v3+, OpenCollection, Postman, and Insomnia formats.
- **FR-023**: The site MUST provide a complete **Keyboard Shortcuts** reference sheet for macOS, Windows, and Linux.
- **FR-024**: The site MUST include an automated **GitHub Actions Workflow** that triggers on pushes to `main` modifying documentation files to build and deploy the static site to GitHub Pages.
- **FR-025**: The site MUST serve as the sole authoritative documentation, rendering the legacy contents of `/docs` obsolete upon migration.

---

### Key Entities

- **Documentation Site**: The static VitePress project deployed to GitHub Pages containing all user-facing and technical manuals.
- **Protocol Guide**: A structured documentation module consisting of a visual parent page with a UI screenshot and specialized subpages detailing a specific network protocol.
- **Security Attestation**: Explicit documentation describing security boundaries, zero-telemetry policy, KSafe local cryptographic storage, CycloneDX SBOM assets, and OSV vulnerability scanning.
- **External Dependency Record**: An entry in the open-source library catalog documenting library name, groupId/artifactId, license, description, and direct link to the upstream GitHub repository.
- **Collaboration Workflow**: Documentation explaining the synchronization of OpenCollection workspaces via native Git operations and remote repositories.
- **Deployment Pipeline**: A GitHub Actions workflow automating the build, link-check, and GitHub Pages deployment of the documentation site.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of core Clapi features (HTTP, GraphQL, WebSocket, gRPC, MCP, Git Sync, KSafe Secret Management, V8 Scripting, Import/Export) are documented with zero reliance on legacy `/docs` markdown files.
- **SC-002**: Users can locate any configuration topic, protocol option, or scripting method using the integrated search within 3 seconds without making external network queries.
- **SC-003**: 100% of supported request types (HTTP, GraphQL, WebSocket, gRPC, MCP) have a dedicated parent page featuring a full UI screenshot and at least 3 deep-dive subpages.
- **SC-004**: 100% of runtime external libraries defined in the project version catalog are cataloged in the documentation with direct links to their official GitHub repositories and license terms.
- **SC-005**: Any commit to documentation sources on the primary branch triggers an automated build and publishes to GitHub Pages within 3 minutes with zero broken internal links.
- **SC-006**: Prospective and security-conscious users can verify the zero-telemetry guarantee, local KSafe encryption architecture, and latest CycloneDX SBOM within 2 clicks from the documentation home page.
- **SC-007**: 100% of code examples across the documentation (scripting, cURL, JSON payloads, Protobuf definitions) pass syntax formatting checks and provide one-click copy capability.

---

## Assumptions

- **Target Audience**: Software engineers, QA testers, API developers, AI/LLM developers integrating with MCP servers, and security auditors.
- **Hosting & Infrastructure**: Documentation will be hosted on GitHub Pages associated with the repository using standard GitHub Actions deployment permissions.
- **Documentation Engine**: VitePress (Vue 3 + Vite) is selected as the documentation engine due to its performance, native markdown extensions, built-in offline search, and GitHub Pages compatibility.
- **Air-Gapped Compatibility**: The documentation site itself will avoid external tracking pixels or third-party fonts that would fail in restricted network environments.
- **Migration Scope**: All relevant architectural and testing notes currently in the `/docs` folder will be absorbed into appropriate sections in the new documentation structure before the legacy files are decommissioned.
