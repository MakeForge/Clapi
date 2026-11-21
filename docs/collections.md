# Multiple Collections in Clapi

Clapi supports maintaining and managing multiple distinct [OpenCollection](https://opencollection.com) collections side by side in a unified workspace.

---

## 1. Sidebar Hierarchy & Management

In the sidebar, the top-level **Collections** section allows managing multiple API collections:
- **Add Collection**: Click the `+` button in the "Collections" header to create a new collection document. If a collection with the same name already exists, an incremental index is automatically appended (e.g., `New Collection (1)`).
- **Expand / Collapse**: Click the chevron on the "Collections" header or on individual collection rows to toggle their visibility.
- **Inline Renaming**: Double-click any collection's name in the sidebar to rename it inline. Press Enter to commit or Escape to cancel. Duplicate names automatically disambiguate without blocking validation dialogs.
- **Context Menu**: Right-click (or click the three dots) on a collection row to:
  - Add Folder
  - Add Request
  - Duplicate Collection
  - Export Collection
  - Delete Collection (closes open descendant tabs and prompts confirmation if enabled)

---

## 2. Collection Editor

Clicking a collection row in the sidebar opens its dedicated `CollectionEditor` tab:

### 2.1 Tab Layout
The tab bar is organized left-to-right as:
1. **Info** (first tab on the left):
   - **Name**: Single-line text input for the collection's title.
   - **Summary**: Multi-line description of the collection's purpose.
   - **Version**: Semantic version string (e.g. `1.0.0`).
2. **Variables**: Key-value table for collection-level request default variables.
3. **Headers**: Key-value table for collection-level request default headers.
4. **Auth**: Authentication configuration (None, Bearer, Basic, API Key, etc.) applied as the default for all requests and folders in the collection.
5. **Pre-request**: JavaScript pre-request script executed before requests within the collection.
6. **Tests**: JavaScript post-response / test script executed after requests within the collection.
7. **Protobuf**: Shared `.proto` files and import path search directories for gRPC services.

---

## 3. Request Defaults Inheritance

Clapi implements a consistent cascade hierarchy:
`Collection` -> `Parent Folder` -> `Child Folder` -> `Request`

- **Headers & Variables**:
  - Descendant folders and requests inherit all collection-level and folder-level defaults.
  - Inherited rows are clearly marked with an "Inherited" badge and key-locked in the editor.
  - Defining a row in a child folder or request with the same key overrides the inherited value.
- **Authentication**:
  - Folders and requests support `AuthType.INHERIT`.
  - When set to "Inherit", an info banner displays the resolved auth method and source (e.g. `Inheriting from collection 'Payments API' (BEARER)`).
- **Script Cascade**:
  - Pre-request scripts run root-to-leaf: `Collection -> Folder -> Request`.
  - Post-response test scripts run leaf-to-root: `Request -> Folder -> Collection`.

---

## 4. Multi-Collection Import & Export

- **Global Selective Export**:
  - Open the options menu on the Collections header and choose **Export Collections...**.
  - Check the collections to export, choose OpenCollection JSON or YAML format, and pick a destination directory.
  - Exactly 1 file is generated per selected collection (`<name>.opencollection.json`).
- **Single Collection Export**:
  - Right-click any collection and choose **Export** to export just that collection to a user-chosen file.
- **Importing Collections**:
  - Choose **Import...** from the toolbar or drop an OpenCollection file.
  - Clapi validates the file against OpenCollection v1.0.0 schema and creates a distinct collection in the workspace, automatically disambiguating duplicate names.
