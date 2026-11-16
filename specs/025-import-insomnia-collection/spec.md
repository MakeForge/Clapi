# Feature Specification: Insomnia v5 Collection & Environment Import

**Feature Branch**: `025-import-insomnia-collection`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "Add Insomnia import from Insomnia v5 export (schema 5.1, example spec, unsupported fields listed in scratch/insomnia-import-unsupported.md, script conversion evaluation); Ensure Import Dialog clearly states Postman/Insomnia exports are supported with format badges; ensure qa-tests; ensure quickstart docs mention migration from Postman/Insomnia."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import Insomnia v5 Request Collections (Priority: P1)

As an API developer migrating from Insomnia to Clapi, I want to import my Insomnia v5 collection files (in either YAML or JSON format) so that my folder hierarchy, HTTP requests, parameters, headers, bodies, authentication settings, and environment variables are immediately available in Clapi without manual re-creation.

**Why this priority**: Importing existing collections is the fundamental requirement for users to transition away from Insomnia to Clapi without losing their workspace organization and request history.

**Independent Test**: Can be fully tested by selecting an Insomnia v5 collection export (e.g. `collection.insomnia.rest/5.0`) in the Import Dialog and verifying that a complete collection hierarchy with nested folders, requests, headers, and authentication is created in Clapi.

**Acceptance Scenarios**:

1. **Given** a user has an Insomnia v5 collection export file in YAML or JSON format containing folders and HTTP requests, **When** they select and import the file via the Import Dialog, **Then** a new collection is created matching the Insomnia workspace name with the complete folder hierarchy and request definitions preserved.
2. **Given** an Insomnia v5 export contains request authentication (such as Basic, Bearer, API Key, AWS IAM, or OAuth2), **When** imported into Clapi, **Then** the requests retain their respective authentication credentials and configurations.
3. **Given** an Insomnia export containing a base environment and sub-environments, **When** imported, **Then** the corresponding environments and variable key-value pairs are imported into Clapi's environment manager.

---

### User Story 2 - Discover Supported Formats in the Import Dialog (Priority: P2)

As a developer opening the Import Dialog, I want to clearly see all supported import formats (including OpenAPI, OpenCollection, Postman, and Insomnia) prominently displayed below the file selection area as visual badges or indicators so that I immediately know which file types I can drag and drop or upload.

**Why this priority**: Clear format discovery directly increases migration success rates and eliminates uncertainty about whether Postman and Insomnia export files are accepted.

**Independent Test**: Can be tested independently by opening the Import Dialog and asserting that badges for all supported formats ("OpenAPI 3.0+", "OpenCollection", "Postman v2/v2.1", "Insomnia v5") are rendered visibly beneath the file selection dropzone.

**Acceptance Scenarios**:

1. **Given** the user opens the Import Dialog, **When** the file selection tab is active, **Then** format badges for OpenAPI 3.0+, OpenCollection, Postman, and Insomnia are displayed with clear visual styling beneath the file drop area.
2. **Given** the user selects or pastes an Insomnia v5 export, **When** the content is scanned, **Then** the dialog identifies the format as Insomnia v5 and presents the item selection review before finalizing import.

---

### User Story 3 - Automatic Translation of Insomnia Pre-Request & Test Scripts (Priority: P3)

As an API engineer with automated test workflows in Insomnia, I want my pre-request scripts and after-response test assertions to be automatically imported and translated into Clapi-compatible scripting so that my tests run seamlessly in Clapi.

**Why this priority**: Automated test assertions and pre-request variable scripts represent significant engineering investment; migrating them automatically removes the largest friction point for advanced teams.

**Independent Test**: Can be tested independently by importing an Insomnia request with `scripts.preRequest` and `scripts.afterResponse` containing `insomnia.environment.set()`, `insomnia.test()`, and `insomnia.expect()`, and verifying that the resulting Clapi request contains before-request and after-response scripts using `clapi.*` syntax.

**Acceptance Scenarios**:

1. **Given** an Insomnia request has a pre-request script using `insomnia.environment.set("token", "val")`, **When** imported, **Then** the request contains a before-request script translated to `clapi.environment.set("token", "val")`.
2. **Given** an Insomnia request has an after-response test assertion script using `insomnia.test(...)` and `insomnia.expect(...)`, **When** imported, **Then** the request contains an after-response script translated to `clapi.test(...)` and `clapi.expect(...)` that executes successfully in Clapi.

---

### User Story 4 - Seamless Migration Guidance in Documentation (Priority: P4)

As a new user exploring Clapi, I want the quickstart documentation to clearly explain how to export from Postman and Insomnia and import directly into Clapi so that I have a frictionless onboarding experience.

**Why this priority**: Documentation ensures that potential switchers find clear, actionable migration steps during their first evaluation of the product.

**Independent Test**: Can be verified by reviewing the quickstart and import guide documentation to confirm comprehensive step-by-step migration instructions from Postman and Insomnia to Clapi.

**Acceptance Scenarios**:

1. **Given** a user navigates to the Quickstart documentation, **When** reviewing the migration and import section, **Then** they find step-by-step instructions for exporting collections from Insomnia (v5) and Postman (v2/v2.1) and importing them into Clapi.

---

### Edge Cases

- **Design Document Exports (`spec.insomnia.rest/5.0`)**: If an Insomnia export contains embedded OpenAPI definitions (`spec.contents`) alongside or instead of a collection array, the importer must extract both the OpenAPI specification items and any associated collection requests.
- **Standalone Environment Exports (`environment.insomnia.rest/5.0`)**: If an export file contains only an environment hierarchy without requests, the importer must import the environments without failing on missing collections.
- **Unsupported Document Types**: When attempting to import unsupported Insomnia document types (such as `mock.insomnia.rest/5.0` or `mcpClient.insomnia/5.0`), the system must display an informative, user-friendly message explaining that mock servers or MCP definitions cannot be imported as request collections.
- **Unsupported Attribute Preservation**: Attributes not mapped to Clapi's execution model (such as client TLS certificates or cloud-specific Kong Konnect routing keys) must be safely bypassed without causing import failures or corrupted collection state, and documented in `scratch/insomnia-import-unsupported.md`.
- **Duplicate Names**: When an imported collection or environment has a name matching an existing one in the workspace, the system must apply standard unique name de-duplication (e.g., appending an incremental index).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST detect and parse Insomnia v5 export files in both YAML and JSON format, identifying document types `collection.insomnia.rest/5.0`, `spec.insomnia.rest/5.0`, and `environment.insomnia.rest/5.0`.
- **FR-002**: The system MUST map Insomnia folder hierarchies recursively into Clapi collections and folders, preserving folder names, descriptions, and nested structures.
- **FR-003**: The system MUST map Insomnia HTTP requests into Clapi HTTP requests, preserving HTTP methods, target URLs, query parameters, headers, and body payloads (including JSON, XML, raw text, form-data, and x-www-form-urlencoded).
- **FR-004**: The system MUST map Insomnia request authentication definitions, supporting Basic Auth, Bearer Token, API Key, AWS IAM (Signature V4), OAuth2, and Digest authentication.
- **FR-005**: The system MUST map Insomnia gRPC requests into Clapi gRPC requests, capturing endpoint URLs, method names, metadata headers, and request message payloads.
- **FR-006**: The system MUST map Insomnia WebSocket and SSE requests into Clapi WebSocket requests, capturing endpoint URLs, connection headers, and query parameters.
- **FR-007**: The system MUST import Insomnia base environments and sub-environments into Clapi Environments, preserving variable names and values.
- **FR-008**: The system MUST automatically translate Insomnia pre-request scripts (`scripts.preRequest`) and after-response scripts (`scripts.afterResponse`) by converting `insomnia.*` API namespaces to corresponding `clapi.*` runtime namespaces.
- **FR-009**: The Import Dialog MUST display prominent format badges below the file selection dropzone indicating support for OpenAPI 3.0+, OpenCollection, Postman, and Insomnia.
- **FR-010**: The Import Dialog MUST support both File selection and Clipboard paste input for Insomnia exports.
- **FR-011**: The system MUST safely ignore unsupported Insomnia metadata (such as Kong Konnect routing attributes and client TLS certificates) without halting import, matching the specification in `scratch/insomnia-import-unsupported.md`.
- **FR-012**: The application MUST include automated end-to-end QA tests validating Insomnia v5 collection and environment import workflows.
- **FR-013**: The project quickstart documentation MUST include a clear migration section guiding users on how to export from Postman and Insomnia and import into Clapi.

### Key Entities *(include if feature involves data)*

- **Insomnia Document**: Represents an exported Insomnia entity (collection, design specification, or environment) adhering to schema version 5.x.
- **Collection Tree**: The hierarchical representation in Clapi consisting of root collections, subfolders, and individual requests.
- **Import Candidate**: An item scanned from the input source that can be individually selected or unselected before finalizing the import.
- **Script Translation Engine**: A conversion utility that transforms Insomnia scripting methods (`insomnia.environment.*`, `insomnia.test()`, `insomnia.expect()`) into native Clapi execution scripts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can import an Insomnia v5 export file containing up to 100 requests in under 2 seconds.
- **SC-002**: 100% of standard HTTP methods, headers, query parameters, and supported authentication types from Insomnia v5 fixtures import without data loss or corruption.
- **SC-003**: 100% of standard pre-request and test assertion scripts utilizing `insomnia.environment` and `insomnia.test` translate into executable Clapi scripts without syntax errors.
- **SC-004**: The Import Dialog visual format badges are visible immediately upon opening the dialog without requiring additional user clicks.
- **SC-005**: Automated QA test coverage validates Insomnia collection import, environment import, and script translation across positive and boundary cases.

## Assumptions

- Users provide valid Insomnia v5 YAML or JSON exports matching schema version 5.0 or 5.1.
- Client TLS certificates and mock server routes exported from Insomnia are out of scope for collection import, as documented in `scratch/insomnia-import-unsupported.md`.
- JavaScript code inside Insomnia scripts follows ES6 standard JavaScript and Insomnia's documented `insomnia.*` scripting API.
- Unique naming resolution handles any naming collisions with existing collections or environments in the active workspace.
