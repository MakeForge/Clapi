# Feature Specification: OpenAPI and OpenCollection Import Dialog

**Feature Branch**: `004-import-openapi-collection`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "In the sidebar when clicking the import folder iconbutton a dialog should appear - For Import OpenAPI a dialog will appear similar to Insomnia (see @[insomnia_import_dialog.png]), with import type selection and Scan button to proceed - There should be a selection for different types of Imports: File (drag and drop/choose file, singular File, JSON or YAML), Clipboard (no explicit further content) - Scan button enabled if file added or clipboard not empty - Scan validates input (OpenCollection YAML or OpenAPI >=v3 JSON/YAML), maps to OpenCollection, displays Folders and Environments with selection and rename options - Show name conflicts between existing Environments/Folders and warn about overwrite - Import button commits selected items, Cancel aborts - For OpenAPI Schema create parent folder with schema's name and subfolders as applicable - Use io.swagger.parser.v3:swagger-parser for OpenAPI parsing"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import from File via Drag-and-Drop or File Selection (Priority: P1)

A user wants to import an API specification or existing collection file into their workspace. They click the import folder button in the sidebar, which opens an import dialog. They select "File", choose or drag-and-drop an OpenAPI v3 (JSON/YAML) or OpenCollection (YAML) file, and click "Scan". The application validates the file, shows an overview of the detected folders and environments, and allows the user to select which items to import and click "Import" to complete the import into the active workspace.

**Why this priority**: Importing existing API definitions and collections from files is the primary entry point for populating a workspace without manual entry.

**Independent Test**: Provide a valid OpenAPI v3 YAML file containing paths and operations. Open the import dialog, select the file, click Scan, select all folders, click Import, and verify that the corresponding folders and requests appear in the sidebar under a parent folder named after the schema.

**Acceptance Scenarios**:

1. **Given** the user is viewing the main application workspace, **When** they click the import folder button in the sidebar, **Then** an import dialog appears with input source options ("File" and "Clipboard") and the "File" option selected by default.
2. **Given** the "File" source option is selected, **When** no file has been provided yet, **Then** the dialog shows a drag-and-drop dropzone with a "Choose Files" action, and the "Scan" button is disabled.
3. **Given** the user drops or selects a valid file (JSON or YAML), **When** the file is loaded into the dialog, **Then** the "Scan" button becomes enabled.
4. **Given** the user clicks "Scan" on a valid file, **When** scanning succeeds, **Then** the dialog transitions to the item selection view displaying the list of detected folders and environments.
5. **Given** the item selection view is shown, **When** the user confirms their selection and clicks "Import", **Then** the selected folders and environments are imported into the workspace, the dialog closes, and the new items are visible in the sidebar.

---

### User Story 2 - Import from Clipboard (Priority: P2)

A user has copied an OpenAPI schema (JSON/YAML) or OpenCollection YAML content to their system clipboard from an external tool, browser, or documentation. They open the import dialog, select the "Clipboard" tab, and click "Scan". The system reads the clipboard contents, parses the specification, and presents the detected folders and environments for selection and import.

**Why this priority**: Importing directly from clipboard provides a fast workflow for quick API testing without needing to save intermediate files to disk.

**Independent Test**: Copy a valid OpenAPI JSON payload to the clipboard, switch to the "Clipboard" tab in the dialog, click Scan, verify the detected items appear in the selection view, and complete the import.

**Acceptance Scenarios**:

1. **Given** the import dialog is open, **When** the user selects the "Clipboard" import type, **Then** the dialog displays a minimal clipboard import view without a file dropzone.
2. **Given** the system clipboard contains text content, **When** the "Clipboard" tab is active, **Then** the "Scan" button is enabled.
3. **Given** the system clipboard is completely empty, **When** the "Clipboard" tab is active, **Then** the "Scan" button is disabled.
4. **Given** the user clicks "Scan" with valid clipboard content, **When** scanning completes, **Then** the detected folders and environments are presented for selection.

---

### User Story 3 - Selective Import, Renaming, and Conflict Resolution (Priority: P1)

After scanning an import source, the user reviews the detected folders and environments. The user can selectively include or exclude individual folders or environments. If any detected folder or environment shares a name with an existing item in the workspace, the dialog displays a visible conflict warning alerting the user that proceeding will overwrite the existing item. Additionally, the user can rename any folder or environment directly in the dialog before completing the import to resolve conflicts or organize items as desired.

**Why this priority**: Preventing unintended data overwrites and allowing selective imports ensures data safety and control over the workspace contents.

**Independent Test**: Create an existing environment named "Development" in the workspace. Scan a file containing an environment named "Development" and a folder "Users". Verify that a conflict indicator is displayed for "Development", rename it in the dialog to "Development-Imported", and verify that upon clicking Import, both the existing "Development" and new "Development-Imported" environments exist without data loss.

**Acceptance Scenarios**:

1. **Given** the scan results are displayed, **When** the user views the folders and environments, **Then** all detected folders and environments are checked by default with checkboxes allowing individual selection/deselection.
2. **Given** an imported item name matches an existing workspace folder or environment name, **When** the item is displayed in the list, **Then** a prominent warning badge or indicator alerts the user that importing it will overwrite the existing item.
3. **Given** any detected folder or environment in the list, **When** the user clicks an edit or rename action next to its name, **Then** the user can edit the name directly inline.
4. **Given** the user changes a conflicting item's name to a non-conflicting name, **When** the new name is saved, **Then** the overwrite conflict warning is immediately cleared.
5. **Given** the user deselects all items or keeps at least one selected, **When** no items are selected, **Then** the "Import" button is disabled; when at least one item is selected, the "Import" button is enabled.
6. **Given** the user clicks "Cancel" at any point during the workflow, **When** the dialog closes, **Then** no modifications are made to the workspace.

---

### User Story 4 - OpenAPI Specification Hierarchy Mapping (Priority: P2)

When an OpenAPI specification (version 3.0 or higher) is imported, the system structures the imported requests into a top-level parent folder named after the OpenAPI document title/info, and organizes endpoints into subfolders based on OpenAPI tags or operation grouping.

**Why this priority**: Retaining the semantic structure of OpenAPI definitions ensures that imported collections remain readable, organized, and aligned with the API provider's intended structure.

**Independent Test**: Scan an OpenAPI v3 spec with title "Petstore API" and operations tagged with "pets" and "store". Verify that the resulting folder structure contains a root folder "Petstore API" with subfolders "pets" and "store" containing their respective requests.

**Acceptance Scenarios**:

1. **Given** an OpenAPI specification with a defined `info.title`, **When** mapped to collection items, **Then** a root parent folder is created using the specification title.
2. **Given** operations in the OpenAPI specification that contain tags, **When** mapped to collection items, **Then** subfolders matching the tags are created within the parent folder, and operations are placed inside their respective tag subfolders.
3. **Given** operations in the OpenAPI specification without tags, **When** mapped to collection items, **Then** they are placed directly inside the schema parent folder.

---

### Edge Cases

- **Invalid or Corrupted Data**: If a user uploads or pastes content that is neither valid OpenCollection YAML nor valid OpenAPI (>= v3) JSON/YAML, scanning fails and an informative error message is displayed without closing the dialog or crashing the application.
- **Unsupported OpenAPI Version**: If an older OpenAPI or Swagger 2.0 file is provided, the validation scan indicates that OpenAPI v3 or higher is required.
- **Empty File or Blank Clipboard**: If a selected file is 0 bytes or clipboard contains only whitespace, the Scan action remains disabled or displays an appropriate validation notice.
- **Network or System Clipboard Access Permissions**: If clipboard access fails or is denied by system security settings, the UI reports that the clipboard could not be accessed.
- **Duplicate Names within Imported File**: If the imported specification itself contains multiple folders or environments with identical names, they are clearly distinguished in the selection list.
- **Deeply Nested Subfolders**: If the specification contains multiple levels of nesting, the selection view renders the hierarchy clearly.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an import button/icon in the sidebar that opens an import dialog when clicked.
- **FR-002**: The import dialog MUST allow the user to select between two import source types: "File" and "Clipboard".
- **FR-003**: When "File" is selected, the dialog MUST display a dropzone supporting drag-and-drop and a file chooser action for a single file.
- **FR-004**: The file picker MUST restrict acceptable file extensions to JSON (`.json`) and YAML (`.yaml`, `.yml`).
- **FR-005**: When "Clipboard" is selected, the dialog MUST indicate that clipboard content will be read without requiring an explicit text input area.
- **FR-006**: The "Scan" button MUST be enabled only when a file has been selected (in "File" mode) or when the system clipboard contains non-empty text (in "Clipboard" mode).
- **FR-007**: When the user clicks "Scan", the system MUST validate that the input conforms to either OpenCollection YAML or OpenAPI specification version 3.0 or higher (JSON or YAML).
- **FR-008**: If validation fails during scanning, the dialog MUST display user-friendly error messages detailing why the input could not be parsed.
- **FR-009**: When scanning succeeds, the dialog MUST map the parsed content to an internal collection structure and present a preview list of all detected Folders and Environments.
- **FR-010**: The preview list MUST allow the user to toggle inclusion for each individual folder and environment using selection checkboxes.
- **FR-011**: The preview list MUST allow the user to rename any detected folder or environment directly before confirming the import.
- **FR-012**: The dialog MUST detect name collisions between incoming items and existing workspace folders or environments, and display a prominent warning indicating that existing items with the same name will be overwritten.
- **FR-013**: The dialog MUST provide an "Import" button to execute the import of selected items into the active workspace, and a "Cancel" button to abort the import at any time without changes.
- **FR-014**: The "Import" button MUST be enabled only when at least one item (folder or environment) is selected for import.
- **FR-015**: When importing an OpenAPI specification, the system MUST create a root parent folder named after the schema's title, and subfolders corresponding to OpenAPI tags or sub-groupings.

### Key Entities

- **Import Source**: Represents the selected mechanism for acquiring specification data (`FILE` or `CLIPBOARD`), along with the raw payload and file metadata (if applicable).
- **Import Candidate Folder**: Represents a folder parsed from the import source, including its proposed name, hierarchy/parent-child relationships, contained requests, and whether it conflicts with an existing workspace folder.
- **Import Candidate Environment**: Represents an environment configuration parsed from the import source, including its proposed name, variables, and whether it conflicts with an existing workspace environment.
- **Import Session State**: Encapsulates the multi-step dialog state, tracking active source tab, staged payload, validation errors, detected candidate items, user selections, rename overrides, and conflict statuses.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can initiate an import, scan a valid file or clipboard payload, and reach the preview selection screen in under 3 seconds for standard specifications (under 5 MB).
- **SC-002**: 100% of detected name collisions with existing workspace folders or environments display an explicit overwrite warning before import execution.
- **SC-003**: Users can successfully rename any detected folder or environment in the preview list within 1 click of the rename target.
- **SC-004**: Canceling the dialog at any stage guarantees 0 unintended changes or partial writes to the workspace.
- **SC-005**: 100% of valid OpenAPI v3+ and OpenCollection files pass validation and are mapped into their corresponding hierarchical folders and requests.

## Assumptions

- **OpenAPI Version**: Only OpenAPI specifications of version 3.0.0 and above are supported. Legacy Swagger 2.0 or earlier formats will report a validation notice prompting the user for an OpenAPI v3+ document.
- **File Input Mode**: Only single file selection is supported in this initial iteration.
- **Default Selection State**: All valid detected folders and environments are initially selected when the preview screen opens.
- **Overwrite Semantics**: If a user confirms import of an item that has a name collision with an existing item, the imported item replaces the existing item with the same name in the workspace.
- **OpenAPI Parsing Library**: The OpenAPI parsing and validation is powered by standard OpenAPI v3 parsing utilities (`io.swagger.parser.v3:swagger-parser`).
- **Sidebar Integration**: The sidebar already contains or will expose an import folder action button that triggers the launch of this modal dialog.
