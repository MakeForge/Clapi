# Feature Specification: OpenCollection Canonical Data Model

**Feature Branch**: `001-opencollection-data-model`
**Created**: 2026-05-22
**Status**: Draft
**Input**: User description: "Migrate the data storage model to opencollection, see docs/Clapi/todos/02_opencollection-adoption.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open and save an existing collection in OpenCollection format (Priority: P1)

A Clapi user opens a collection that is stored in OpenCollection format (a valid OpenCollection document), sees its folders and HTTP requests, edits a request, saves it, and reopens the collection to find the change persisted with nothing else altered.

**Why this priority**: This is the core of the initiative. Without reliable load and save of OpenCollection documents, no other story delivers value.

**Independent Test**: Load a representative OpenCollection fixture, change one request's URL and one header, save, reopen, and compare the result to the original with only the intended fields changed.

**Acceptance Scenarios**:

1. **Given** a valid OpenCollection document with nested folders and HTTP requests, **When** the user opens it in Clapi, **Then** all folders and requests appear in the same nesting and order as in the document.
2. **Given** an opened collection, **When** the user edits a request and saves, **Then** the saved document is a valid OpenCollection document with the same OpenCollection version as before.
3. **Given** a saved collection, **When** the user reopens it, **Then** the edited request shows the saved values and all unedited items are unchanged in meaning.
4. **Given** an OpenCollection document containing fields Clapi does not edit, **When** the user opens and saves it, **Then** those fields are still present and unchanged.

---

### User Story 2 - Execute HTTP requests from the new model with unchanged behavior (Priority: P1)

A user runs an HTTP request from a collection. Authentication, environment selection, variable interpolation, pre-request and post-request scripts, and assertions behave as they did before the migration.

**Why this priority**: Existing HTTP execution is the primary user-facing function. It must not regress.

**Independent Test**: Run a fixed set of requests (with auth, variables, scripts, and assertions) against a test server before and after the change and compare the outgoing requests and results.

**Acceptance Scenarios**:

1. **Given** a request whose URL and headers contain `{{variable}}` references, **When** the user executes it with an environment selected, **Then** the variables resolve with the same precedence as before the migration.
2. **Given** a request with bearer, basic, API key, or JWT auth, **When** the user executes it, **Then** the outgoing request carries the same authentication as before.
3. **Given** a request with a pre-request script and a post-request script containing assertions, **When** the user executes it, **Then** the scripts run in the same order with the same `clapi.*` API and the assertion results match prior behavior.
4. **Given** a request with a JSON, raw, form, multipart, urlencoded, binary, or GraphQL-over-HTTP body, **When** the user executes it, **Then** the body is sent the same way as before.

---

### User Story 3 - Keep existing user collections through migration (Priority: P1)

A user upgrading Clapi has collections saved in the legacy proprietary Clapi format. On first launch, the collections are converted to OpenCollection with no silent data loss, and the original files are kept until the conversion is verified.

**Why this priority**: Losing user data during upgrade is unacceptable and blocks adoption.

**Independent Test**: Run the migration on a set of legacy fixture collections and verify that every folder, request, ordering position, auth setting, environment, variable, and script is present in the result, and that the original files are still intact.

**Acceptance Scenarios**:

1. **Given** legacy collections with nested folders, **When** migrated, **Then** the folder structure and request ordering are preserved.
2. **Given** a legacy collection with auth, environments, variables, and scripts, **When** migrated, **Then** each of these is present in the result, or is recorded in a migration report as not representable.
3. **Given** a migration that fails partway, **When** the failure occurs, **Then** the original legacy files are unchanged and the user is told which collection failed and why.
4. **Given** a successfully migrated collection, **When** migration runs again, **Then** no duplicate or altered data is created.
5. **Given** legacy data with a field that has no OpenCollection equivalent, **When** migrated, **Then** the field is kept in a Clapi-specific preserved location and is not discarded.

---

### User Story 4 - Keep MCP functionality working outside the HTTP model (Priority: P2)

A user who uses the MCP client continues to configure and use MCP connections as before. MCP data is not stored as HTTP requests in collections.

**Why this priority**: MCP must keep working, but it is not part of the OpenCollection standard, so it needs its own representation.

**Independent Test**: Run the existing MCP workflows (connect, list tools, invoke a tool) before and after the change and confirm identical behavior and stored data.

**Acceptance Scenarios**:

1. **Given** an existing MCP configuration, **When** Clapi starts after the change, **Then** the MCP configuration loads and the connection works as before.
2. **Given** an MCP item, **When** it is saved, **Then** it is stored in a Clapi-specific representation and is not written as an OpenCollection HTTP request.
3. **Given** a collection containing both HTTP requests and any MCP-related data, **When** saved and reopened, **Then** the HTTP items are unchanged and MCP data is intact.

---

### User Story 5 - Persist only collection data, not runtime state (Priority: P2)

A user's open tabs, selected request, last responses, request history, and UI layout are kept separate from the saved collection file. Saving a collection never writes runtime or UI state into it.

**Why this priority**: Clean separation keeps collection files shareable and stable.

**Independent Test**: Open a collection, open tabs, send requests, change the selected item, then save. Confirm the collection file contains only collection data.

**Acceptance Scenarios**:

1. **Given** a collection with open tabs and a response, **When** the user saves, **Then** the collection file contains no tab, response, selection, or history data.
2. **Given** runtime state changes, **When** the collection is reopened, **Then** the collection content is unchanged by those runtime changes.

---

### User Story 6 - Validate documents and report problems clearly (Priority: P3)

When a user opens a document that is not a valid OpenCollection document, Clapi reports the error with the file and item that caused it, instead of failing silently or corrupting data.

**Why this priority**: Clear validation makes interoperability with other compatible tools reliable.

**Independent Test**: Open invalid fixtures (missing required fields, wrong item type, unsupported version) and verify each produces an actionable error and leaves the file unchanged.

**Acceptance Scenarios**:

1. **Given** a document that fails schema validation, **When** opened, **Then** Clapi shows an error naming the document and the affected item or field, and does not modify the file.
2. **Given** a document with an item type Clapi does not support yet, **When** opened, **Then** the item is shown as unsupported and is preserved on save, and the rest of the collection works.

---

### Edge Cases

- A folder that contains no requests, or a request at the root level, must load and save correctly.
- Requests with the same name in the same folder must keep their distinct positions and identities after round trip.
- Environment variables with secret values must not appear in serialized output intended for sharing, in logs, or in debugging output.
- A variable defined at more than one scope (global, collection, request) must resolve with the same precedence as before.
- An OpenCollection document from a newer compatible version than Clapi targets must be opened without losing unknown fields.
- A legacy collection file that is already partially migrated (for example, from an interrupted run) must not produce duplicates.
- Empty or missing optional fields must be treated as their documented defaults, not as errors.
- Request bodies with binary content must not be altered in encoding during round trip.

## Requirements *(mandatory)*

### Functional Requirements

**Canonical format**

- **FR-001**: The system MUST use OpenCollection as the only persisted format for collections, folders, HTTP requests, environments, variables, and authentication settings.
- **FR-002**: The system MUST record the OpenCollection specification version used in every collection it writes.
- **FR-003**: The system MUST NOT write the legacy proprietary Clapi collection format after migration is complete.

**Reading and writing**

- **FR-004**: The system MUST read valid OpenCollection documents and reconstruct the collection, its nested folders, and its ordering.
- **FR-005**: The system MUST write OpenCollection documents that validate against the official OpenCollection JSON schema for the version recorded in the document.
- **FR-006**: The system MUST validate documents against the official schema when they are opened and before they are written.
- **FR-007**: The system MUST preserve fields that it does not edit, including unknown fields, when opening and saving a document.
- **FR-008**: The system MUST preserve the meaning of supported data through a deserialize, modify, serialize, deserialize cycle. Textual formatting and comments are not required to be preserved.
- **FR-009**: Validation errors MUST identify the document and, where possible, the item or field at fault.

**Items and protocols**

- **FR-010**: The collection model MUST represent items as a choice between folders and protocol-specific requests, so that a folder is never an HTTP request.
- **FR-011**: The system MUST support HTTP requests with method, URL, query parameters, headers, and bodies of type JSON, raw, form/multipart, urlencoded, binary, and GraphQL-over-HTTP.
- **FR-012**: The system MUST support authentication types: none, basic, bearer, API key, and JWT.
- **FR-013**: New protocol types MUST be addable without changing the storage, folder, environment, or variable behavior.
- **FR-014**: The system MUST NOT implement gRPC, GraphQL as a separate protocol, or WebSocket as part of this initiative.

**Environments, variables, scripts**

- **FR-015**: The system MUST represent environments and variables at the global, collection, and request scopes, and MUST keep these scopes separate.
- **FR-016**: Variable interpolation using `{{variable}}` MUST resolve with the same precedence as before the change.
- **FR-017**: Secret variable values MUST be excluded from any serialization intended for sharing, from logs, and from debugging output.
- **FR-018**: The system MUST store pre-request and post-request scripts and their source code, and MUST preserve their execution order.
- **FR-019**: The system MUST store assertions and tests in the form supported by the OpenCollection specification where compatible, and MUST keep any Clapi-specific assertion behavior that has no equivalent in a separate, documented representation.
- **FR-020**: The JavaScript scripting API (including the `clapi.*` namespace) MUST NOT change as part of this initiative.

**MCP and extensions**

- **FR-021**: MCP functionality MUST keep working as before.
- **FR-022**: MCP data MUST NOT be stored as an OpenCollection HTTP request.
- **FR-023**: MCP data and any other Clapi-specific data that OpenCollection does not define MUST be stored in a separate Clapi-specific representation that is documented.

**Runtime and UI separation**

- **FR-024**: The collection model MUST NOT contain runtime state (responses, selection, open tabs, history, or UI state).
- **FR-025**: The collection repository MUST expose operations in terms of the canonical model and MUST NOT expose the legacy storage representation.
- **FR-026**: The UI MUST NOT convert between the legacy model and OpenCollection.

**Migration**

- **FR-027**: The system MUST detect legacy Clapi collections and convert them to the canonical model on first use.
- **FR-028**: Migration MUST preserve nested folders, request ordering, authentication, environments, variables, and scripts, or record in a migration report any item that cannot be represented.
- **FR-029**: Migration MUST NOT overwrite or delete a legacy file until the converted result has been validated and written successfully.
- **FR-030**: Migration MUST be safely repeatable; a second run MUST NOT duplicate or alter already-converted data.
- **FR-031**: Data with no OpenCollection equivalent MUST be preserved in a Clapi-specific location and MUST NOT be silently discarded.
- **FR-032**: Existing Postman import MUST continue to work, with its output produced in the canonical model.

**Storage**

- **FR-033**: The storage layer MUST NOT expose filesystem-specific paths or assumptions to the editor or execution engine.
- **FR-034**: The first delivery MUST support one OpenCollection storage layout, and the storage design MUST allow the other supported layout to be added later without changing callers.

**Compatibility**

- **FR-035**: Existing HTTP request execution, request editing, nested folders, authentication, environment selection, variable resolution, scripts, assertions, request tabs, request history, SSE and NDJSON handling, and existing UI interactions MUST behave as before, except where a requirement in this specification explicitly changes behavior.

### Key Entities

- **Collection**: The root document. Has metadata, the OpenCollection version, an ordered list of items, and optionally environments, variables, auth, and scripts.
- **Item**: An entry in a collection or folder. Is either a Folder or a protocol-specific Request. Folders contain items. Ordering is preserved.
- **Folder**: A named container of items, with optional auth, variables, and scripts.
- **HTTP Request**: A request item with method, URL, query parameters, headers, body, auth, scripts, and assertions.
- **Body**: One of JSON, raw, form/multipart, urlencoded, binary, or GraphQL-over-HTTP.
- **Authentication**: One of none, basic, bearer, API key, or JWT, defined at collection, folder, or request level.
- **Environment**: A named set of variables that can be selected at execution time.
- **Variable**: A name and value at a given scope (global, collection, request), with a flag indicating whether it is secret.
- **Script**: Pre-request or post-request source code with its execution order.
- **Clapi Extension Data**: Clapi-specific data that OpenCollection does not define, including MCP items and any preserved unknown legacy fields. Stored separately from standard items.
- **Migration Report**: A record of what was converted, what was preserved as extension data, and what could not be represented.
- **Runtime State**: Open tabs, selection, responses, history, and UI state. Not persisted in collection files.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of collections written by Clapi validate against the official OpenCollection schema for the recorded version in automated tests.
- **SC-002**: For a fixed set of representative OpenCollection fixtures, a load and save cycle with no edits produces a document with the same meaning in 100% of cases, and no supported field is lost.
- **SC-003**: All existing HTTP execution tests pass without changes to their expected results, except tests that explicitly cover behavior changed by this specification.
- **SC-004**: 100% of legacy fixture collections migrate with every folder, request, ordering position, auth setting, environment, variable, and script either present in the result or listed in the migration report.
- **SC-005**: In a migration failure test, the original legacy files remain byte-identical in 100% of cases.
- **SC-006**: Running migration twice on the same input produces identical output in 100% of cases.
- **SC-007**: Secret variable values appear in zero shared-export outputs, log entries, or debug output in automated checks.
- **SC-008**: Existing MCP workflows pass their acceptance checks with no change in observable behavior.
- **SC-009**: A user can open an existing collection, edit a request, save, reopen, and send the request, with every step completing in the same user-visible way as before the change.
- **SC-010**: Adding a new protocol item type requires no changes to the storage, folder, environment, or variable code paths, as verified by a test that adds a stub type.

## Assumptions

- The OpenCollection specification and its official JSON schema (version 1.0.0 at time of writing, published at https://spec.opencollection.com/) are the validation authority. The exact version to target is confirmed in the planning phase.
- Only HTTP requests are in scope as a protocol. Folders are not HTTP requests.
- MCP is not part of the OpenCollection standard and is preserved through a Clapi-specific representation rather than an OpenCollection protocol type.
- Migration converts legacy Clapi collections on first use and keeps the original files until the converted result is verified. The user is not asked to convert manually.
- Textual YAML formatting and comments are not required to round trip. Semantic preservation is the requirement.
- The existing JavaScript scripting engine and `clapi.*` API are kept unchanged; only how scripts are stored changes.
- Postman import keeps working; its internal output target becomes the canonical model.
- Adding cloud sync, collaboration, new authentication schemes, a redesigned editor, or redesigned history is out of scope.
- Choice of storage layout (bundled document or directory) and the exact module boundaries are decided in the planning phase, not here.
- The project constitution is still an unfilled template, so no additional governance constraints apply yet.

## Dependencies

- Availability of the official OpenCollection specification and JSON schema for the chosen version.
- Existing legacy collection fixtures and QA fixtures in the repository, used for migration tests.
- Existing automated tests for HTTP execution, scripting, and MCP, used as regression baselines.
