# Quickstart Validation Guide: Folder Settings and Request Defaults

**Feature Branch**: `003-folder-request-defaults`
**Date**: 2026-11-07
**Status**: Ready

This guide outlines runnable scenarios to validate that folder renaming, folder settings editing, and request defaults inheritance work end-to-end.

---

## 1. Prerequisites

- Linux desktop environment with Java 17+ and Gradle.
- Workspace repository cloned and built:
  ```bash
  ./gradlew :ui-desktop:build -x test
  ```

---

## 2. Automated Test Execution

Run the automated UI and unit test suites verifying folder tab state, inheritance resolution, and sidebar interaction:

```bash
# Run unit & state tests for tabs, folders, and models
./gradlew :ui-shared:desktopTest --tests "io.github.makeforge.clapi.ui.shared.*"
./gradlew :core-model:desktopTest

# Run desktop integration tests
./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.TabManagementUiTest"
./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.components.SidebarCollectionHelpersTest"
```

---

## 3. Manual End-to-End Validation Scenarios

### Scenario 1: Verify UI Terminology (Folders vs Collections)
1. Launch Clapi desktop application:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. Inspect the left sidebar.
3. **Expected**:
   - The primary sidebar section header displays **"Folders"**.
   - Context menus and buttons refer to **"New Folder"**, **"Rename Folder"**, and **"Delete Folder"**.

### Scenario 2: Open and Edit Folder Request Defaults
1. In the sidebar, create or find an existing folder named `"API v1"`.
2. Left-click directly on the text `"API v1"`.
3. **Expected**:
   - A new tab opens in the main editor bar with a Folder icon and the title `"API v1"`.
   - The editor area displays the **Folder Settings** view with sub-tabs for **Params**, **Headers**, **Auth**, **Pre-request**, and **Tests**.
4. In the **Headers** sub-tab, add a header:
   - Key: `X-Api-Version`
   - Value: `1.0`
5. In the **Auth** sub-tab, configure:
   - Auth Type: `Bearer`
   - Token: `folder-secret-token`
6. Click **Save** (or press Ctrl+S / Cmd+S).
7. Re-click `"API v1"` in the sidebar.
8. **Expected**: The existing tab is brought into focus without opening a duplicate tab.

### Scenario 3: Verify Inheritance in a Child Request
1. Right-click the `"API v1"` folder in the sidebar and choose **"New Request"** (e.g. `"Get Users"`).
2. Click on `"Get Users"` to open the request tab.
3. Switch to the **Headers** tab.
4. **Expected**:
   - The header `X-Api-Version: 1.0` is visible in the table.
   - It is marked with an **"Inherited"** badge and its fields are read-only.
5. Switch to the **Auth** tab.
6. **Expected**:
   - The **"Inherit"** option is selected by default (or available as a chip).
   - An indicator shows `"Inherited from API v1: Bearer Token"`.
7. Override the inherited header:
   - Add a request header with Key: `X-Api-Version` and Value: `2.0`.
8. Send the request to a mock or sample endpoint.
9. **Expected**:
   - The wire request sends `X-Api-Version: 2.0` and `Authorization: Bearer folder-secret-token`.

### Scenario 4: Nested Folder Cascading
1. Inside `"API v1"`, create a subfolder `"Admin"`.
2. Click on `"Admin"` to open its folder settings tab.
3. Configure an additional header `X-Role: Admin`.
4. Open a request inside `"Admin"`.
5. **Expected**:
   - Both `X-Api-Version: 1.0` (from `"API v1"`) and `X-Role: Admin` (from `"Admin"`) appear marked as **"Inherited"**.
