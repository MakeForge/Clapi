# Technical Research: OpenAPI & OpenCollection Import Dialog

**Feature**: OpenAPI & OpenCollection Import Dialog
**Feature Directory**: `specs/004-import-openapi-collection`
**Date**: 2026-11-07

## 1. OpenAPI Parser & Specification Validation

### Decision
Use `io.swagger.parser.v3:swagger-parser:2.1.25` added to `core-storage` (under `desktopMain` / `commonMain`). Invoke `OpenAPIParser().readContents(rawContent, null, null)` for parsing strings and `OpenAPIParser().readLocation(filePath, null, null)` for file paths.

### Rationale
- `swagger-parser` v3 is the official, industry-standard OpenAPI 3.x parser for JVM applications.
- Supports both JSON and YAML transparently without requiring external format conversion.
- Returns `SwaggerParseResult`, exposing both the parsed `OpenAPI` POJO and validation warnings/errors via `result.messages`.
- Readily verifies OpenAPI specification version (requires `openAPI.openapi` version >= 3.0.0; Swagger 2.0 documents return `swagger: "2.0"` and are flagged as unsupported with a clear user-facing message).

### Alternatives Considered
- *Custom parser / JSON schema validation*: Rejected due to high complexity and maintenance burden of parsing full OpenAPI 3.0 and 3.1 specifications.
- *Atlassian swagger-request-validator*: Specialized for request/response validation rather than full schema-to-object parsing.

---

## 2. OpenAPI Specification to OpenCollection Mapping

### Decision
Implement an `OpenApiImporter` in `core-storage` that converts an `io.swagger.v3.oas.models.OpenAPI` instance into candidate `CollectionNode` folders and `EnvState` (or OpenCollection `Environment`) instances.

Mapping rules:
1. **Root Folder**: Created using `openAPI.info?.title?.ifBlank { "OpenAPI Import" } ?: "OpenAPI Import"`.
2. **Subfolders by Tag**:
   - Inspect operations on all `openAPI.paths`.
   - If an operation defines `tags` (e.g. `tag = "Pets"`), the operation is placed inside a subfolder named after the tag inside the root folder.
   - Operations without tags are placed directly under the root folder.
3. **HTTP Requests**:
   - Each path item contains operations (`get`, `post`, `put`, `delete`, `patch`, `head`, `options`, `trace`).
   - Operation name: `operation.summary ?: operation.operationId ?: "$method $path"`.
   - Method: HTTP verb in uppercase.
   - URL: Base URL from `openAPI.servers.firstOrNull()?.url` combined with path template (e.g. `{{baseUrl}}/pets/{petId}`).
   - Parameters: Map query parameters to `MutableKeyValue` URL parameters, headers to header parameters.
   - Body: Extract request body example or schema defaults (e.g. `application/json`).
4. **Environments**:
   - If `openAPI.servers` are defined, create an environment named `"${openAPI.info.title} Environment"` containing a `baseUrl` variable pointing to the primary server URL, plus any server variables defined in the spec.

### Rationale
- Aligns with standard API client behavior (Insomnia, Postman) where API specs are organized by tag groupings under a parent folder.
- Retains server base URLs without hardcoding them into every individual request, allowing environment switching.

### Alternatives Considered
- *Flat request list*: Discards OpenAPI tag organization, making large specs difficult to navigate.
- *Strict path-segment folder hierarchy*: Can lead to excessive nesting (e.g. `/api/v1/users/{id}/orders` -> 5 levels deep) which degrades UX. Tag-based grouping with path fallback is industry standard.

---

## 3. OpenCollection YAML Parsing & Validation

### Decision
Reuse existing `OpenCollectionSchemaValidator` and `OpenCollectionSerializer` in `core-storage`.
- Test if content is OpenCollection: check if YAML contains `opencollection:` or validates against the OpenCollection schema.
- Convert document into `ImportCandidateFolder` (from `toCollectionFolder()`) and `ImportCandidateEnvironment` (from `doc.config.environments`).

### Rationale
- Completely reuses existing thoroughly-tested OpenCollection deserialization and validation logic.
- Guarantees 100% compatibility with Clapi's canonical data model.

---

## 4. Import Dialog UI & UX Design

### Decision
Create `ImportSpecificationDialog.kt` in `ui-shared/src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/components/` modeled after the Insomnia import dialog:
1. **Step 1: Source Selection**
   - Header: Title `Import` and close button (`X`).
   - Source Type Tabs: `File` and `Clipboard`.
   - File Tab:
     - Drag-and-drop dropzone with upload icon, "Drag and Drop or Choose Files to import".
     - Clicking "Choose Files" opens file picker filtered to `.json`, `.yaml`, `.yml`.
     - When file is chosen/dropped, display file name, size, and clear button.
   - Clipboard Tab:
     - Displays description "Paste content from system clipboard directly into the workspace".
     - Live detection: reads system clipboard via `PlatformApi.readFromClipboard()`.
   - Footer:
     - Trust warning note: "Make sure that you trust the import source before continuing."
     - Supported formats badges: `OpenCollection`, `OpenAPI (>=v3)`.
     - Primary button: "Scan", enabled only if file is staged or clipboard contains text.
2. **Step 2: Preview & Selection**
   - Summary of detected items: Folders and Environments.
   - Item list with checkboxes for individual selection/deselection.
   - Inline rename button/field for each item.
   - Conflict warning badge (`Overwrite Warning`) for items whose name matches an existing workspace folder or environment.
   - Updating the name immediately re-evaluates the conflict check.
   - Footer actions: "Cancel" (dismisses with no changes) and "Import" (commits selected items, enabled if >=1 item selected).

### Rationale
- Mirrors the user-provided Insomnia dialog layout (`insomnia_import_dialog.png`).
- Gives the user complete control and visibility before any data is written to the workspace.

---

## 5. Overwrite Semantics & State Persistence

### Decision
When the user clicks "Import" in Step 2:
- **Folders**:
  - For each selected folder: if an existing folder in `state.collections` has the same name, the existing folder is replaced by the imported folder.
  - If no collision, the folder is added to `state.collections`.
- **Environments**:
  - For each selected environment: if an existing environment in `state.environments` has the same name, its variables are replaced/merged with the imported environment.
  - If no collision, the environment is added to `state.environments`.
- Auto-save workspace via `WorkspaceRepository.save(state)` and notify via `state.log(..., LogLevel.SUCCESS)`.

### Rationale
- Satisfies the explicit requirement: "Show the user if name conflicts between existing Environments/Folders exist and that if he chooses to import them, the existing ones will be overwritten".
- Guarantees predictable, transparent overwrite behavior.
