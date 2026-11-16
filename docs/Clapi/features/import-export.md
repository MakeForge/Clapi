# Specification Import: OpenAPI & OpenCollection

This document describes the design, user workflows, and technical details of the OpenAPI and OpenCollection specification import functionality in Clapi.

---

## 1. Overview & Supported Formats

Clapi provides a streamlined import dialog (inspired by modern API clients like Insomnia) that enables users to import API specifications and existing collections into their workspace.

### Supported Formats
- **OpenAPI v3.0 / v3.1** (JSON or YAML): Full support for endpoints, request bodies, query/header parameters, tag-based folder grouping, and server configurations.
- **OpenCollection** (YAML): Canonical format for Clapi and open collection tools, preserving nested folders, requests, scripts, and environment variable sets.

### Format Validation & Version Requirements
- **OpenAPI v3+ Enforcement**: Swagger 2.0 specifications are detected and rejected with a clear diagnostic message indicating that OpenAPI v3 or higher is required.
- **Syntax Validation**: Malformed JSON or YAML displays an actionable error banner without closing the dialog or corrupting workspace state.

---

## 2. Dialog Workflow & Architecture

The import workflow is split into two sequential steps to provide validation and review before any changes are committed to the active workspace.

### Step 1: Source Selection & Scanning

Users launch the dialog by clicking the **Import Folder** icon button in the sidebar (test tag: `collection-import-button`).

The modal dialog offers two tabs:
1. **File Import (`import-tab-file`)**:
   - Displays a drag-and-drop dropzone (`import-dropzone`).
   - Includes a **Choose Files** action filtering for `.json`, `.yaml`, and `.yml` files.
   - Shows selected filename and file size.
   - The **Scan** button (`import-scan-button`) is enabled once a file is selected.
2. **Clipboard Import (`import-tab-clipboard`)**:
   - Directly checks the system clipboard for text content.
   - Previews line count or character snippet.
   - Shows an empty-state guidance message if the clipboard contains no text.
   - The **Scan** button is enabled whenever readable clipboard content is present.

Clicking **Scan** asynchronously parses and validates the content using `SpecificationImporter.default()`.

---

### Step 2: Item Selection, Inline Renaming & Conflict Resolution

Upon successful scanning, the dialog transitions to Step 2:
- **Format Badge (`import-format-badge`)**: Displays the detected format (e.g., `OpenAPI 3.0` or `OpenCollection`).
- **Selective Checkboxes**:
  - Folders and environments are listed with individual checkboxes (`import-folder-check-<id>`, `import-env-check-<id>`).
  - All items are checked by default.
  - The **Import** button (`import-confirm-button`) is disabled if no items are selected.
- **Inline Renaming**:
  - Each candidate folder and environment includes an inline text field (`import-folder-name-<id>`, `import-env-name-<id>`).
  - Users can edit names prior to import.
- **Conflict & Overwrite Warnings**:
  - If a candidate folder or environment matches the name of an existing item in the workspace, an **Overwrite Warning** badge (`import-folder-conflict-<id>`, `import-env-conflict-<id>`) is displayed.
  - Renaming the item to a non-colliding name immediately clears the warning.
  - Proceeding with a conflicting name safely updates the existing item in place without creating duplicate entries.
- **Safe Abort**:
  - Clicking **Cancel** (`import-cancel-button`) or the close icon at any point closes the dialog and makes zero modifications to the workspace.

---

## 3. OpenAPI Hierarchy & Environment Mapping Rules

When importing an OpenAPI v3 specification, `OpenApiImporter` transforms the schema into structured workspace items:

### 3.1 Folder Hierarchy
1. **Root Folder**: Named after `info.title` (or "Imported API" if unspecified).
2. **Tag-to-Subfolder Grouping**:
   - Operations that specify OpenAPI `tags` are organized into subfolders corresponding to each tag.
   - Multi-tagged operations are grouped into their primary tag subfolder.
3. **Untagged Operations**: Endpoints without tags are placed directly in the root parent folder alongside subfolders.

### 3.2 Endpoint & Request Mapping
- **URL Path**: Formatted as `{{baseUrl}}/path` with OpenAPI path templating preserved.
- **HTTP Methods**: Mapped to canonical HTTP verbs (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, etc.).
- **Parameters**:
  - `query` parameters are converted to URL query parameters.
  - `header` parameters are converted to request headers.
- **Request Bodies**: JSON request body schemas and example values are extracted and populated into the request body editor.

### 3.3 Environment Generation
- If the specification contains `servers`:
  - A candidate environment is generated (named `[API Title] Environment`).
  - The first server URL is mapped to the `baseUrl` variable.
  - Default server variables are substituted or added to the environment variables list.

---

## 4. Verification & Testing

Import functionality is verified via both automated test suites and manual validation:

- **Storage Unit Tests**:
  - `core-storage/src/commonTest/kotlin/.../SpecificationImporterTest.kt`: OpenCollection, OpenAPI v3, and Swagger 2.0 rejection tests.
  - `core-storage/src/commonTest/kotlin/.../OpenApiHierarchyMappingTest.kt`: Hierarchy, parameters, bodies, and server mapping tests.
- **Compose UI Tests**:
  - `ui-desktop/src/desktopTest/kotlin/.../ImportSpecificationUiTest.kt`: Full end-to-end dialog lifecycle, tab switching, scan, item selection, confirm, and abort.
  - `ui-desktop/src/desktopTest/kotlin/.../importers/ImportConflictResolutionTest.kt`: Conflict warning badges, inline rename clearing, selective import, and overwrite replacement.
  - `ui-desktop/src/desktopTest/kotlin/.../importers/ImportClipboardUiTest.kt`: Clipboard empty state, valid scan, and error banner display.
