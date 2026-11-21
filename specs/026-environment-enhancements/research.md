# Research & Architectural Decisions: Environment Enhancements

## Decision 1: Environment Inheritance Resolution & Cycle Prevention

### Context
Users need environments to be able to inherit variables from parent environments via `OpenCollection.Environment.extends`, with child variables overriding parent variables of the same key. The UI must provide a selector for `extends`, disable it when no eligible environments exist, prevent self-extension and cyclic dependency loops, and clearly indicate inherited vs overridden variables in the edit dialog.

### Decision
- **Model Representation**: `OpenCollection.Environment.extends` (`String?`) stores the name of the parent environment. `EnvState` exposes `var extends by mutableStateOf(extends)`.
- **Variable Resolution Hierarchy**:
  - Traversal: Nearest-child priority. If environment `Dev` extends `Staging` which extends `Base`, the resolution chain evaluates `[Dev, Staging, Base]`.
  - Resolution: For each variable key, the first active (enabled) variable found along the chain wins.
  - Cycle Detection: When resolving variables or building the options list in the UI, maintain a `visited: Set<String>`. If an environment is already in `visited`, terminate traversal and log/warn rather than recursing infinitely.
  - Missing Parent: If `extends` refers to a nonexistent or deleted environment, treat inherited variables as empty without failing or crashing.
- **UI Extends Selector in Edit Dialog**:
  - Placed in the environment edit dialog header near the name/color controls.
  - Populated with eligible environments: all other environments except `self` and any environment that directly or transitively extends `self` (to prevent cycles).
  - If no eligible environments exist, the dropdown is disabled with tooltip/placeholder `"No eligible environments to extend"`.
  - Includes a `"None"` (or clear) option to detach inheritance.
- **Visual Indication in Variables Table**:
  - When an environment extends a parent, the table displays inherited variables (or an indicator badge) alongside local variables.
  - If a local variable has the same name as an inherited variable, a badge or visual label `"Overridden"` (with tooltip `"Overrides value from [ParentEnv]"`) is prominently displayed.

### Alternatives Considered
- *Implicit merge during save*: Flattening inherited variables directly into the child environment on save. Rejected because changes in the parent environment would not automatically propagate to child environments, defeating the purpose of inheritance.
- *Strict ID-based linking*: Linking by UUID instead of environment name. Rejected because OpenCollection specification specifies `extends` as a string referencing the parent environment name.

---

## Decision 2: Environment Color Customization (`color`)

### Context
`OpenCollection.Environment.color` allows assigning a color hex string to an environment. Users need both quick predefined color presets and free-form hex code entry in the Edit Environment dialog. The color must be visibly reflected in the sidebar, header environment selector, and dialogs.

### Decision
- **Model Representation**: `OpenCollection.Environment.color` (`String?`) stores a 6- or 8-character hex string (e.g., `"#3B82F6"`). `EnvState` exposes `var color by mutableStateOf(color)`.
- **Predefined Palette Presets**:
  - A balanced set of 8 distinct, accessible theme-friendly colors:
    - Red (`#EF4444`)
    - Orange (`#F97316`)
    - Amber (`#F59E0B`)
    - Green (`#10B981`)
    - Cyan (`#06B6D4`)
    - Blue (`#3B82F6`)
    - Purple (`#8B5CF6`)
    - Gray (`#6B7280`)
- **Custom Color Entry**:
  - Text input for hex color string with automatic `#` prefix handling and validation.
  - Live color preview swatch.
- **UI Surfaces**:
  - `SidebarEnvironmentRow`: The 8dp dot next to the environment name renders `env.color` (if defined), otherwise falling back to default active/inactive colors.
  - `TopToolbar`: The environment dropdown button and menu items display the environment color swatch next to the environment name.
  - `EnvironmentEditDialog`: Title bar or name row displays the color picker.

### Alternatives Considered
- *Full system color picker dialog*: Launching an OS native color chooser. Rejected because inline swatch selection + hex text field is significantly faster, cross-platform consistent, and matches desktop developer tools (Postman, Insomnia, VS Code).

---

## Decision 3: Quick-Add Inline Top Row for Variables & External Secrets

### Context
Currently, adding a variable or external secret requires clicking an "Add Variable" / "Add Secret" button at the bottom of the table, requiring tedious scrolling when an environment has many variables. Users requested a persistent top row for immediate additions.

### Decision
- **Variables Quick-Add Row**:
  - Positioned as a pinned header row immediately above the scrollable variable rows (or as item 0 in the list).
  - Contains inline input fields: Key text field, Value text field, Type/Secret toggle, and an "Add" action button.
  - Committing (via Enter key or clicking Add) validates non-blank key, appends the new `MutableKeyValue` to the working variables, and resets the quick-add inputs to blank.
- **External Secrets Quick-Add Row**:
  - Positioned at the top of the External Secrets list.
  - Contains quick inputs appropriate for the current provider (e.g. Variable Name, Secret Identifier/Path, Target Field).
  - Committing validates inputs, appends the new `MutableExternalSecret`, and resets the quick-add fields.

### Alternatives Considered
- *Floating action button (FAB)*: Floating button in the corner. Rejected because an inline input row eliminates the extra click and dialog step entirely, allowing direct typing.

---

## Decision 4: Drag-and-Drop YAML File Import on Desktop

### Context
Dragging and dropping a `.yaml` or `.yml` file into the import dropzone currently fails. The app requires robust file drag-and-drop handling for both YAML and JSON files.

### Decision
- **AWT DropTarget Integration**:
  - In Compose Multiplatform Desktop, connect an AWT `DropTarget` listener (or reusable Compose modifier `onFileDrop`) to the window / dropzone component.
  - On `drop`, inspect `Transferable` for `DataFlavor.javaFileListFlavor`.
  - Extract files, filter by supported extensions: `.json`, `.yaml`, `.yml` (case-insensitive).
  - Read content safely on IO dispatcher and invoke the callback `(fileName, content) -> Unit`.
  - Show drag-over visual highlight state on the dropzone when a valid file is hovered.
- **Testability**:
  - Ensure the file ingestion logic is exposed through a clean contract so unit and UI tests can trigger file drops programmatically without requiring native OS window event injection.

### Alternatives Considered
- *Clipboard-only fallback*: Requiring users to copy and paste YAML. Rejected because drag-and-drop is an explicit user requirement and core desktop workflow expectation.

---

## Decision 5: External Secrets Import/Export Security & Completeness

### Context
When exporting an environment, external secret configurations (provider, secret path, target field, custom filters) must be exported so other team members can configure their access, but sensitive resolved secret values must NEVER be exported in plaintext. When importing, external secrets must be restored.

### Decision
- **Export Sanitization**:
  - `ImportExportRepository.exportEnvironmentToString(env)`:
    - Exports `type = "clapiEnvironment"`, `name`, `color`, `extends`, `variables`, and `externalSecrets`.
    - For `variables`: variables marked `secret = true` have their values omitted (or exported as blank/masked secret variable definitions) so secret tokens are never written to disk in export files.
    - For `externalSecrets`: exports complete configuration (provider type, name, path/secretName/secretId/recordTitle, url/vaultName/projectId, targetField, customFilters, description, disabled). No runtime secret tokens or cached credentials are included.
- **Import Restoration**:
  - `ImportExportRepository.importEnvironmentFromString`:
    - Correctly parses `color`, `extends`, `variables`, and `externalSecrets` from Clapi environment JSON, OpenCollection YAML/JSON, and Postman/Insomnia formats.
    - External secret configurations are instantiated as `MutableExternalSecret` entries attached to the imported `EnvState`.

---

## Decision 6: Environment Creation & Sidebar Navigation Flow

### Context
When a user adds a new environment, they should not have to manually expand the sidebar and click edit. The Environments sidebar section should expand immediately, and the Edit Environment dialog should open for the newly created environment.

### Decision
- In `Sidebar.kt` (and any other environment creation entry points):
  - On confirm of `Create environment`:
    1. Create and append the new `EnvState`.
    2. Set `state.selectedEnvIndex = state.environments.lastIndex`.
    3. Ensure `state.environmentsExpanded = true` and `state.settings.environmentsExpanded = true`.
    4. Call `state.openEnvEdit(state.selectedEnvIndex)` to immediately launch the dialog.
- Change the environment import icon in `SidebarEnvironmentsSection.kt` from `Res.drawable.ic_input` to `Res.drawable.ic_download` to match collection import.
