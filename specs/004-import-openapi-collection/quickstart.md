# Quickstart & Validation Guide: OpenAPI & OpenCollection Import Dialog

**Feature**: OpenAPI & OpenCollection Import Dialog
**Feature Directory**: `specs/004-import-openapi-collection`
**Date**: 2026-11-07

## Overview
This document describes runnable end-to-end scenarios to validate the import dialog workflow, OpenAPI parsing, OpenCollection importing, selective import, renaming, and conflict overwrite handling.

---

## Prerequisites & Setup

Ensure the project compiles and dependencies resolve:
```bash
./gradlew test
```

Fixtures used for testing can be found or placed in `core-storage/src/commonTest/resources/fixtures/`:
- `fixtures/petstore-openapi-v3.yaml` (Valid OpenAPI 3.0 specification)
- `fixtures/sample-opencollection.yaml` (Valid OpenCollection YAML specification)
- `fixtures/swagger-v2.yaml` (Unsupported Swagger 2.0 specification)

---

## Validation Scenarios

### Scenario 1: Launch Import Dialog from Sidebar
1. Launch Clapi desktop application:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. In the left sidebar, locate the Folders section header.
3. Click the Import Folder icon button (`testTag("collection-import-button")`).
4. **Expected Result**:
   - The `ImportSpecificationDialog` modal appears.
   - The "File" tab is selected by default.
   - The dropzone is visible showing "Drag and Drop or Choose Files to import".
   - The "Scan" button is disabled.

---

### Scenario 2: File Import with OpenAPI Specification
1. In the Import Dialog ("File" tab), click "Choose Files".
2. Select an OpenAPI 3.x file (`petstore-openapi-v3.yaml`).
3. Observe that the file name and size appear in the dialog and the "Scan" button is now enabled.
4. Click "Scan".
5. **Expected Result**:
   - The dialog progresses to the "Select Items to Import" screen.
   - A root parent folder named after the schema (e.g. "Swagger Petstore") is listed with its request count.
   - Detected environments (e.g. "Swagger Petstore Environment") with `baseUrl` variable are listed.
   - Checkboxes are selected by default.
6. Click "Import".
7. **Expected Result**:
   - The dialog closes.
   - The new folder appears in the sidebar.
   - Expanding the folder reveals tag subfolders (e.g. "pets", "user") containing their respective HTTP requests.
   - The imported environment is available in the environment dropdown.

---

### Scenario 3: Clipboard Import
1. Copy the raw text of a valid OpenCollection YAML file to your system clipboard.
2. In Clapi, open the Import Dialog from the sidebar.
3. Switch to the "Clipboard" tab.
4. Observe that the dialog detects text on the clipboard and enables the "Scan" button.
5. Click "Scan".
6. **Expected Result**:
   - Dialog transitions to the item selection view showing detected folders and environments.
7. Click "Import".
8. **Expected Result**:
   - Items are imported into the workspace.

---

### Scenario 4: Selective Import, Inline Renaming, and Conflict Overwrite Warning
1. Ensure the workspace contains an existing folder named "Petstore API".
2. Open the Import Dialog and scan an OpenAPI file whose schema title is "Petstore API".
3. In the item selection view:
   - Observe that the folder "Petstore API" displays an **Overwrite Warning** badge, notifying that proceeding will overwrite the existing folder.
4. Click the rename button/field next to "Petstore API" and rename it to "Petstore API v2".
5. **Expected Result**:
   - The Overwrite Warning badge disappears immediately upon renaming.
6. Uncheck one of the environments in the list to exclude it from import.
7. Click "Import".
8. **Expected Result**:
   - The folder is imported under the new name "Petstore API v2".
   - The existing "Petstore API" folder remains untouched.
   - The deselected environment is not imported into the workspace.

---

### Scenario 5: Error Handling for Unsupported / Invalid Formats
1. Select or paste a Swagger 2.0 specification (`swagger-v2.yaml`).
2. Click "Scan".
3. **Expected Result**:
   - The dialog remains on Step 1.
   - A visible error message states that Swagger 2.0 is not supported and an OpenAPI v3+ specification is required.
   - No modifications are made to the workspace.

---

## Automated Test Suites

Run the UI and parser unit/integration tests:
```bash
./gradlew :core-storage:desktopTest
./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.ImportSpecificationUiTest"
```
