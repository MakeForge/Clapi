# Feature Specification: Environment Inheritance, Visual Customization, and Import/Export Enhancements

**Feature Branch**: `026-environment-enhancements`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description:
- If user adds new Environment it should immediately after open the edit environment dialog for it. Also in the sidebar the Environment should be expanded
- Use OpenCollection.Environment.extends to be able to extend from another environment. In an edit environment dialog there should be a selection box with which the user can select which environment to extend from (completely optional). If there are no other environments the selection box should be disabled. An Environment that extends another, inherits all variables etc. from that environment but can define its own variables too. If a environment defines a variable which already exists in the inherited environment, the variable will be overwritten. This should be indicated to the user in the environment edit dialog.
- OpenCollection.environment.color should be usable to give an environment a color (hex string). In the edit dialog a user can choose the color freely, but give some predefined colors
- In the Edit Environment dialog in variables and external secrets table there should always be on top a row where the user can immediately add a new variable/secret, instead of having to click Add Variable/ Add Secret on the bottom
- Import Environment button should have same icon as collection Import
- For Import dragging and dropping a yaml file does not work
- For Import/Export: ensure environment secrets are also exported/imported (not their secret values just their config)
- ensure all of this has proper tests, in best case qa-tests

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Environment Inheritance & Variable Overrides (Priority: P1)

As an API practitioner working across multiple staging, pre-production, and production configurations, I want an environment to extend a base environment so that I can reuse shared configuration variables and only specify environment-specific overrides without copying and maintaining redundant values.

**Why this priority**: Inheritance eliminates error-prone duplication across environments, enabling scalable multi-tier configurations (e.g., base URLs, tenant keys, and shared tokens).

**Independent Test**: Can be tested independently by creating an environment `Production` that extends `Staging`, defining a unique variable in `Staging` and an overridden variable in both, and verifying in the editor and variable resolution that `Production` inherits the staging variable and retains its own overridden variable with clear visual indicators in the edit dialog.

**Acceptance Scenarios**:

1. **Given** two environments exist (e.g., `Base` with variable `API_URL=https://api.example.com` and `TIMEOUT=30`), **When** the user edits environment `Dev` and selects `Base` as the parent environment in the "Extends" selector, **Then** `Dev` inherits `API_URL` and `TIMEOUT`.
2. **Given** an environment extends a parent environment, **When** the child environment defines a variable with the same key name as an inherited variable (e.g., `API_URL=https://dev.example.com`), **Then** the child variable value takes precedence and the environment edit dialog explicitly indicates that this variable overrides an inherited value.
3. **Given** an environment has no other environments available to extend, **When** the user views the environment edit dialog, **Then** the "Extends" selector is disabled with clear guidance that no eligible environments exist.
4. **Given** multiple environments exist, **When** a user configures inheritance, **Then** the selector only offers valid non-circular options (an environment cannot extend itself, nor can it extend an environment that would create a circular dependency).
5. **Given** an environment extends another environment, **When** the user clears the "Extends" selection, **Then** the inheritance link is removed and the child environment retains only its own explicitly defined variables.

---

### User Story 2 - Instant Environment Creation & Expanded Navigation (Priority: P1)

As a developer creating a new environment, I want the sidebar Environments section to expand automatically and the environment editor dialog to open immediately upon creation so that I can immediately configure variables and settings without extra navigation clicks.

**Why this priority**: Streamlining the environment creation flow removes friction and guides the user directly into configuring their newly created environment.

**Independent Test**: Can be tested independently by clicking the "New Environment" button while the sidebar environments list is collapsed and confirming that the section expands and the Edit Environment dialog is displayed focused on the newly created environment.

**Acceptance Scenarios**:

1. **Given** the sidebar Environments section is collapsed, **When** the user adds a new environment, **Then** the Environments section automatically expands in the sidebar.
2. **Given** a user inputs a name for a new environment and confirms creation, **When** the environment is added, **Then** the Edit Environment dialog immediately opens for that newly created environment.
3. **Given** the Edit Environment dialog opens following creation, **When** the user inspects the dialog, **Then** the newly created environment is selected as the active editing target and its fields are ready for input.

---

### User Story 3 - Quick-Add Top Row for Variables & External Secrets (Priority: P2)

As a user editing environment variables and external secrets, I want an inline entry row pinned to the top of both tables so that I can immediately enter and add new variables or external secrets without scrolling down to find an "Add" button.

**Why this priority**: When environments contain dozens of variables, having to scroll to the bottom of the table to add a new variable creates unnecessary friction. A persistent top row speeds up repeated data entry.

**Independent Test**: Can be tested independently by opening the Edit Environment dialog with an environment containing multiple variables and asserting that an empty inline input row is always displayed at the top of the variables and external secrets tables. Entering values into the row immediately registers the new entry.

**Acceptance Scenarios**:

1. **Given** the user is on the Variables tab of the Edit Environment dialog, **When** looking at the variables table, **Then** a dedicated entry row is always visible at the top above existing variable entries.
2. **Given** the user inputs a key and value into the top quick-add row, **When** the user commits the row (via Enter key or Add action), **Then** the new variable is appended to the environment and the top entry row resets for the next input.
3. **Given** the user is on the External Secrets tab of the Edit Environment dialog, **When** looking at the secrets table, **Then** a dedicated entry row is always visible at the top allowing immediate configuration of a new secret reference.
4. **Given** the quick-add row is displayed, **When** invalid or empty inputs are provided, **Then** no invalid empty entries are committed.

---

### User Story 4 - Environment Color Customization & Visual Palette (Priority: P2)

As a developer switching between different environments, I want to assign a distinctive color (via predefined palette or custom hex code) to each environment so that I can instantly recognize which environment is active across the interface.

**Why this priority**: Color-coding prevents accidental destructive operations in critical environments (such as Production) by providing prominent, glanceable visual differentiation.

**Independent Test**: Can be tested independently by selecting a predefined color swatch or entering a hex string in the Edit Environment dialog, saving, and verifying that the color indicator renders correctly alongside the environment name in the sidebar and environment selectors.

**Acceptance Scenarios**:

1. **Given** a user is editing an environment, **When** they view the environment color settings, **Then** a set of predefined color swatches (e.g., Red, Green, Blue, Amber, Purple, Gray) and a custom hex code input field are presented.
2. **Given** a user clicks a predefined color swatch, **When** selected, **Then** the environment's color value updates to the corresponding hex value and reflects in the preview.
3. **Given** a user enters a valid 6-character hex code (with or without `#`), **When** saved, **Then** the environment stores the hex color string.
4. **Given** an environment has a configured color, **When** viewed in the sidebar, header environment selector, or editor dialogs, **Then** a colored badge or dot is visibly rendered next to the environment name.

---

### User Story 5 - Reliable YAML Drag-and-Drop Import & Consistent Action Icons (Priority: P3)

As a user importing specifications and environments into Clapi, I want to drag and drop YAML files directly into the import dialog and have the environment import button match the collection import icon, so that file ingestion is intuitive and visually consistent.

**Why this priority**: YAML is the predominant format for OpenAPI and OpenCollection files. A failing drag-and-drop workflow on YAML files breaks user expectations. Consistent iconography strengthens UI polish.

**Independent Test**: Can be tested independently by dragging a `.yaml` or `.yml` file into the file drop area of the Import Dialog and verifying file content is read and processed correctly, and checking that the sidebar Environment Import button uses the same download/import icon as Collection Import.

**Acceptance Scenarios**:

1. **Given** the user opens the Import Dialog, **When** dragging a file with `.yaml` or `.yml` extension into the dropzone, **Then** the file is accepted, its name and size are displayed, and its content is scanned for import.
2. **Given** the sidebar contains import actions, **When** observing the Environment Import button and Collection Import button, **Then** both actions display the same standard import icon for visual consistency.

---

### User Story 6 - External Secrets Configuration Import & Export (Priority: P3)

As a security-conscious team lead, I want to export and import environments containing external secrets configurations without exporting sensitive secret values, so that teammates can share environment definitions safely.

**Why this priority**: Sharing environment templates across teams requires exporting external secret provider configurations (e.g., Vault path, AWS secret name, GCP secret ID) while ensuring underlying secret values are never leaked in export files.

**Independent Test**: Can be tested independently by exporting an environment with external secret configurations, inspecting the exported file to verify configuration fields are present and actual secret values are absent, and importing the file to verify the external secrets configuration is completely restored.

**Acceptance Scenarios**:

1. **Given** an environment has configured external secret variables (e.g., HashiCorp Vault, AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, KeePassXC, or Keeper), **When** exported to a file, **Then** the external secrets configuration (provider type, key names, secret identifiers, paths) is included in the export.
2. **Given** an exported environment file containing external secrets, **When** inspected, **Then** no resolved sensitive secret values or tokens are present in the export output.
3. **Given** an exported environment file containing external secrets configuration, **When** imported into Clapi, **Then** the environment is created with its external secrets definitions intact and ready for secret resolution.

---

### Edge Cases

- **Self-Extension & Circular Extension**: An environment attempting to extend itself or forming a loop (A extends B, B extends A) must be prevented by the UI selector and handled gracefully by variable resolution (cycle detection returning safe fallback).
- **Deleted Parent Environment**: If an environment extends a parent that is subsequently deleted, the child environment must gracefully fallback to resolving only its local variables without crashing.
- **Deep Inheritance Chains**: If environments are chained (A extends B, B extends C), variable resolution must resolve hierarchically from deepest ancestor to immediate child, with child variables taking highest precedence.
- **Invalid Color Hex Values**: If a user inputs an invalid or malformed hex string (e.g. invalid characters, wrong length), the input should either be sanitized, ignored, or revert to default without causing UI crashes.
- **Empty / Blank Quick-Add Row Submission**: Pressing enter or clicking add on a quick-add row when key name is blank must not create empty or corrupted variable entries.
- **Importing YAML with Extension Variations**: Drag-and-drop file ingestion must handle `.yaml`, `.yml`, uppercase extensions (`.YAML`, `.YML`), and files dropped onto various regions of the drop target.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST automatically expand the Environments section in the sidebar whenever a user creates a new environment.
- **FR-002**: System MUST immediately open the Edit Environment dialog focused on the newly created environment upon creation confirmation.
- **FR-003**: System MUST provide an optional "Extends" environment selection control in the Edit Environment dialog.
- **FR-004**: System MUST disable the "Extends" selection control when no other environments exist in the workspace.
- **FR-005**: System MUST exclude the current environment and any environments that would cause circular dependencies from the "Extends" options list.
- **FR-006**: System MUST resolve variables hierarchically such that child environment variables override inherited parent environment variables with matching names.
- **FR-007**: System MUST visually indicate inherited variables and highlighted overridden variables within the Edit Environment dialog.
- **FR-008**: System MUST support assigning a color hex string to an environment via predefined color swatches and custom hex code input.
- **FR-009**: System MUST display an environment's assigned color indicator alongside its name in the sidebar and environment selector components.
- **FR-010**: System MUST provide an inline quick-add input row pinned to the top of the environment variables table.
- **FR-011**: System MUST provide an inline quick-add input row pinned to the top of the environment external secrets table.
- **FR-012**: System MUST use the same import icon (`ic_download`) for the Environment Import action as used for the Collection Import action.
- **FR-013**: System MUST accept `.yaml` and `.yml` files via drag-and-drop into the file import dropzone.
- **FR-014**: System MUST include external secret configuration definitions (provider type, target identifiers, and variable mappings) when exporting an environment.
- **FR-015**: System MUST strictly omit resolved secret values and credential tokens when exporting environments.
- **FR-016**: System MUST restore external secret configuration definitions when importing environment files.
- **FR-017**: All functionality MUST be accompanied by comprehensive automated test coverage, including unit tests and end-to-end / desktop UI QA tests.

### Key Entities

- **Environment**: Represents a named runtime configuration context containing variables, external secret bindings, an optional parent environment reference (`extends`), and an optional visual color code (`color`).
- **Variable Entry**: Represents a key-value pair within an environment; can be locally defined, inherited from an ancestor environment, or explicitly overriding an inherited variable.
- **External Secret Configuration**: Metadata describing a secret variable linked to a remote secret provider (e.g., Vault path, AWS secret name, GCP secret ID), excluding the sensitive secret payload.
- **Color Palette Preset**: A predefined set of distinct theme-compatible hex colors offered to users for rapid environment styling.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: New environment creation requires 0 additional clicks to reach the editing interface (dialog opens immediately upon creation).
- **SC-002**: 100% of inherited variables from a parent environment are available for resolution during request execution unless explicitly overridden.
- **SC-003**: 100% of overridden variables take precedence over inherited variables with zero data ambiguity in test execution.
- **SC-004**: Users can add a new variable or secret immediately from the top row without scrolling or navigating away from the current viewport.
- **SC-005**: 100% of dragged `.yaml` and `.yml` files are successfully received and parsed by the import dropzone without rejecting the file format.
- **SC-006**: 0% of secret values or sensitive tokens leak into exported environment files; only configuration schema is exported.
- **SC-007**: 100% of new capabilities pass automated unit and desktop UI verification tests.

## Assumptions

- Predefined color options will include a clean, accessible palette of 6 to 8 recognizable colors suitable for light and dark themes.
- If a parent environment referenced by `extends` is deleted, the child environment will gracefully retain its own variables and treat the inherited set as empty.
- Multi-level inheritance (e.g., Dev extends Staging, Staging extends Prod) is supported, resolving variables with nearest-child priority.
- Drag-and-drop support targets the desktop JVM application environment.
