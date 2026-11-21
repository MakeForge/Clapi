# Feature Specification: Folder Settings and Request Defaults Inheritance

**Feature Branch**: `003-folder-request-defaults`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "In the UI currently what is shown as 'Collections' is under the hood a OpenCollection FolderItem, so it should be named 'Folder' in the ui as well. A FolderItem can have its own RequestDefaults, but currently I dont see a way to set them in the UI. So we need to implement that: if you left click on a Folder's name (Text) in the side bar it should open a Tab for that folder's settings to be able to view and edit the FolderItem.request RequestDefaults (re-use EditorTabBar and RequestEditorTabs (see RequestEditor composables); when a request in a folder is opened, the inherited request settings from its folder should be properly shown in the according tabs. For auth there should be option like 'Inherit' which then simply inherits from folder RequestDefaults or the other manual options. For e.g the headers the inherited header should be shown and marked as Inherited; If there are any major concerns when it comes to request scripts, clear that up or add TODO comments to address the issues later"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Renaming Collections to Folders in the User Interface (Priority: P1)

Users organizing their requests in the sidebar need terminology that accurately reflects folder hierarchies and matches the data model. When viewing and managing grouped items in the sidebar and navigation areas, users see the term "Folder" instead of "Collection".

**Why this priority**: Eliminates user confusion between collection containers and nested folders, establishing an intuitive mental model for folders containing request defaults.

**Independent Test**: Can be tested by launching the application, opening the sidebar navigation, and verifying that all labels, tooltips, dialogs, and section headers that previously said "Collection" or "Collections" for folder-level items now consistently display "Folder" or "Folders".

**Acceptance Scenarios**:

1. **Given** an open workspace with structured items, **When** the user views the sidebar tree and item headers, **Then** container nodes are labeled and presented as "Folders".
2. **Given** the user creates or renames a container node, **When** prompting for user input, **Then** dialogs and actions refer to "New Folder" and "Rename Folder".

---

### User Story 2 - Opening and Editing Folder Request Defaults (Priority: P1)

Users want to configure common request settings (such as base headers, query parameters, authentication, and scripts) once at the folder level so that all requests within that folder can share them. Clicking on a folder's name in the sidebar opens a dedicated Folder Settings tab in the main editor area with tabs for editing these defaults.

**Why this priority**: This is the core capability that enables users to view and edit folder-level defaults directly from the user interface.

**Independent Test**: Can be tested by clicking on a folder name in the sidebar, verifying that a folder settings tab opens in the tab bar, modifying headers or authentication defaults, saving/closing the tab, and reopening to verify changes persist.

**Acceptance Scenarios**:

1. **Given** a folder listed in the sidebar, **When** the user left-clicks on the folder's name text, **Then** a folder settings tab opens in the editor tab bar showing the folder name with an appropriate folder indicator.
2. **Given** an open folder settings tab, **When** the user switches between setting sub-tabs (Headers, Parameters, Auth, Scripts), **Then** the user can add, edit, toggle, or delete folder-level default values.
3. **Given** a folder settings tab already open, **When** the user left-clicks the same folder name in the sidebar again, **Then** the existing folder tab is focused rather than opening a duplicate tab.
4. **Given** multiple open tabs (requests and folders), **When** the user closes a folder tab, **Then** it closes gracefully without affecting other open tabs.

---

### User Story 3 - Visualizing and Using Inherited Settings in Requests (Priority: P2)

When working on an individual request inside a folder, users need to clearly see which settings come from the parent folder versus which are defined directly on the request. For authentication, users can choose "Inherit" to seamlessly use folder authentication, or select a specific auth method to override it. For headers and query parameters, inherited entries from the folder are clearly indicated alongside request-specific entries.

**Why this priority**: Delivers immediate transparency and predictability during request composition, preventing unintentional header duplication and making authentication inheritance effortless.

**Independent Test**: Can be tested by defining headers and bearer auth in a folder, opening a child request within that folder, checking that the inherited headers appear marked as "Inherited" and that the auth dropdown defaults to or includes "Inherit" showing the inherited configuration.

**Acceptance Scenarios**:

1. **Given** a folder with default headers configured, **When** the user opens a request inside that folder and navigates to the Headers tab, **Then** the folder's default headers are displayed in the table with a visible "Inherited" badge/indicator.
2. **Given** inherited headers displayed in the request's Headers tab, **When** the user views inherited rows, **Then** they are clearly differentiated from request-owned headers (e.g., read-only indicator or non-editable indicator for the inherited row, with guidance to edit in folder settings).
3. **Given** an inherited header with key `X-Custom-Token`, **When** the user adds a request-level header with the same key `X-Custom-Token`, **Then** the request-level header takes precedence and overrides the inherited header value.
4. **Given** a request inside a folder that has authentication configured, **When** viewing the Auth tab for that request, **Then** the auth type selection offers an "Inherit" option (or "Inherit from parent").
5. **Given** the "Inherit" auth option is selected on a request, **When** the parent folder has an authentication method configured, **Then** the request preview/summary shows the resolved parent auth details and utilizes them when executing.
6. **Given** the user selects a specific auth method (e.g., "Bearer Token" or "No Auth") on the request, **When** the parent folder has auth configured, **Then** the request-level selection overrides the parent folder settings.

---

### User Story 4 - Handling Nested Folder Defaults and Script Semantics (Priority: P3)

In complex workspaces with nested folders (folders inside folders), request defaults cascade down through the hierarchy. Additionally, script behavior (pre-request and test scripts) follows clear sequential execution principles or displays appropriate notices regarding execution order.

**Why this priority**: Ensures consistent behavior across arbitrarily deep folder structures and provides clear transparency around script execution order.

**Independent Test**: Can be tested by creating a nested folder structure (Grandparent -> Parent -> Request), configuring defaults at each level, and verifying that the deepest request combines or resolves defaults from all ancestor folders in top-down order.

**Acceptance Scenarios**:

1. **Given** a request inside a nested folder structure, **When** viewing inherited headers, **Then** headers from all ancestor folders are accumulated, with deeper child folders overriding higher ancestor folders for identical keys.
2. **Given** a request inheriting auth in a nested folder, **When** the direct parent has auth set to "Inherit", **Then** auth resolution traverses up to the nearest ancestor folder that specifies concrete authentication.
3. **Given** pre-request or post-request scripts configured at the folder level, **When** the request runs, **Then** folder scripts execute in hierarchical order (ancestor pre-request scripts run before request pre-request scripts; request test scripts run before ancestor post-request scripts), or clear documentation and indicator notes inform the user of script execution flow.

---

### Edge Cases

- **Root-level Requests (No Folder)**: Requests located directly at the collection root or outside any folder must not display inherited headers or the "Inherit" auth option (or selecting "Inherit" acts as "No Auth" with an informative message).
- **Empty or Incomplete Folder Defaults**: If a folder has an empty request defaults block, child requests display no inherited headers and resolve auth to "No Auth" when "Inherit" is selected.
- **Header Case Sensitivity**: Header names are matched case-insensitively (e.g., `authorization` matches `Authorization`). A request-level header overrides a folder-level header regardless of character casing.
- **Folder Renaming / Deletion While Tab is Open**: If a folder is renamed, open folder settings tabs update their title immediately. If a folder is deleted, any open folder settings tab for that folder is automatically closed or marked invalid.
- **Disabled Inherited Headers**: If an inherited header is disabled at the folder level, it must not be sent with the child request. If a user wishes to suppress an inherited folder header on an individual request without deleting it at the folder level, defining a blank or overridden header or an override mechanism takes effect.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application UI MUST display the term "Folder" (or "Folders") for folder item components in the sidebar, menus, and item navigation, replacing any legacy "Collection" references for folder items.
- **FR-002**: Left-clicking a folder's title/text in the sidebar MUST open a dedicated Folder Settings tab in the main editor area.
- **FR-003**: The editor tab bar MUST support folder settings tabs alongside request tabs, including distinct visual labeling (such as a folder icon and the folder's name) and standard close actions.
- **FR-004**: Clicking an already-open folder in the sidebar MUST bring its existing Folder Settings tab into focus rather than opening a duplicate tab.
- **FR-005**: The Folder Settings view MUST provide editable sections for request defaults, including Headers, Query Parameters, Authentication, and Pre-request / Post-request Scripts.
- **FR-006**: When viewing a request that belongs to a folder, the request's Headers tab MUST display both request-owned headers and inherited headers from ancestor folders.
- **FR-007**: Inherited headers and query parameters displayed inside a request MUST be visually identified with an "Inherited" marker or tag to distinguish them from request-owned entries.
- **FR-008**: Inherited headers displayed in a request view MUST be read-only in that view, with clear visual cues directing the user to edit them in the parent folder's settings tab.
- **FR-009**: When a request defines a header key matching an inherited folder header (case-insensitive), the request-level header value MUST override the inherited value.
- **FR-010**: The request Authentication settings MUST provide an "Inherit" (or "Inherit from parent") option whenever the request is inside a folder.
- **FR-011**: When "Inherit" authentication is selected on a request, the request MUST execute using the effective authentication credentials and configuration resolved from the parent folder (or closest ancestor with configured auth).
- **FR-012**: If a request selects an explicit authentication method (e.g., Bearer, Basic, API Key, No Auth), that explicit configuration MUST completely override folder authentication defaults.
- **FR-013**: In nested folder hierarchies, default settings MUST cascade from root to leaf, where child folder settings override or supplement ancestor folder settings.
- **FR-014**: Folder pre-request and test/post-request scripts MUST either execute in hierarchical cascade order (parent pre-script before child pre-script; child test-script before parent test-script) or provide clear UI indicators and documented behavior for script execution scoping.

### Key Entities

- **Folder**: An organizational container in the sidebar hierarchy that can contain requests and child folders, and holds folder-level configuration including request defaults.
- **Folder Request Defaults**: Configuration options defined at the folder level (headers, query parameters, authentication settings, and scripts) designed to be shared and inherited by all descendant requests within that folder.
- **Request Tab / Folder Tab**: Editor tabs hosted in the main editor tab bar representing either an individual HTTP request or a folder's settings view.
- **Inherited Setting**: A configuration value (e.g., a header or auth credential) originating from an ancestor folder that applies automatically to a child request unless explicitly overridden.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can open a folder's settings tab by clicking the folder name in the sidebar in a single click.
- **SC-002**: 100% of headers and query parameters configured in folder defaults are visible and designated as "Inherited" when inspecting any child request under that folder.
- **SC-003**: Switching a child request's authentication mode to "Inherit" successfully applies the folder's authentication configuration on 100% of test executions without manual credential re-entry.
- **SC-004**: Overriding an inherited header at the request level successfully substitutes the child value in the executed HTTP request without altering the parent folder's stored default.
- **SC-005**: In a workspace with at least 50 folders and requests, opening folder tabs and resolving inherited request defaults occurs smoothly with sub-second response times.

## Assumptions

- **Header Matching Rule**: Header key collisions between parent folders and child requests are resolved using case-insensitive matching; the child request's value replaces the folder's value for that request execution.
- **Script Cascade Semantics**: For pre-request scripts, parent folder scripts run before child request scripts. For post-request (test) scripts, child request scripts run before parent folder scripts. Any script runtime limitations will be marked with explicit TODO comments in the codebase.
- **Inheritance Scope**: Only requests located inside a folder can inherit folder request defaults. Standalone root requests do not inherit folder defaults.
- **Tab Persistence**: Active folder settings tabs maintain their open state and unsaved edits consistently with standard editor tab behavior across navigation and view switching.
