# Quickstart & Verification Guide: Multiple OpenCollection Collections

**Branch**: `008-multiple-collections` | **Date**: 2026-11-09

## 1. Overview

This document provides step-by-step end-to-end validation scenarios and automated test instructions to verify the multiple collections feature.

---

## 2. Automated Test Commands

Run the test suite across core model, storage, and UI desktop modules:

```bash
# Run core storage and serialization tests (OpenCollection multiple collections & export/import)
./gradlew :core-storage:test

# Run ui-shared persistence, mapping, and inheritance tests
./gradlew :ui-shared:test

# Run ui-desktop navigation and component tests
./gradlew :ui-desktop:jvmTest

# Run end-to-end QA verification suite
./gradlew :qa-tests:test
```

---

## 3. End-to-End Manual Verification Scenarios

### Scenario 1: Creating and Managing Multiple Collections in Sidebar

1. **Launch Clapi**: Start the application (`./gradlew :ui-desktop:run`).
2. **Observe Sidebar**: Notice the top-level section header is titled "Collections" and has an `+` icon.
3. **Add First Collection**: Click `+` on the "Collections" header.
   - Verify a new collection item appears with an Article icon and default name (e.g., `New Collection`).
4. **Inline Rename**: Double-click the collection name in the sidebar.
   - Type `Users API` and press `Enter`.
   - Verify the name changes to `Users API`.
5. **Add Second Collection**: Click `+` on "Collections" again.
   - Rename it to `Payments API`.
6. **Disambiguation Test**: Click `+` and attempt to rename the third collection to `Payments API`.
   - Verify it automatically renames to `Payments API (1)` without showing a blocking error dialog.
7. **Expand / Collapse**: Click the chevron next to `Payments API`.
   - Verify it collapses and expands independently of other collections.

### Scenario 2: Editing Collection Info & Collection Request Defaults

1. **Open Collection Editor**: Left-click on `Users API` in the sidebar.
   - Verify a tab titled `Users API` opens with the Article icon.
2. **Verify Tab Order**:
   - Verify the first tab on the left is **"Info"**, followed by **Variables**, **Headers**, **Auth**, **Pre-request**, **Tests**, and **Protobuf**.
3. **Configure Info**:
   - In the "Info" tab, enter Summary: `Core user management service`.
   - Enter Version: `2.1.0`.
4. **Configure Request Defaults**:
   - Switch to **Headers** tab: Add `X-Api-Source: Clapi-Workspace`.
   - Switch to **Auth** tab: Select `Bearer Token`, enter token `col-secret-token`.
   - Switch to **Variables** tab: Add `baseUrl: https://api.users.local`.
5. **Save**: Click the "Save" button in the collection editor header.
   - Verify dirty indicator disappears.

### Scenario 3: Request Defaults Inheritance Verification

1. **Create Folder inside Collection**: In the sidebar on `Users API`, click `+` (Add Folder).
   - Name the folder `Profile Endpoints`.
2. **Inspect Folder Auth**: Left-click on `Profile Endpoints` to open Folder Editor.
   - Switch to **Auth** tab.
   - Verify auth is set to `Inherit`, and an info banner shows: *"Inheriting Bearer Token from collection 'Users API'"*.
3. **Add Request in Folder**: Right-click `Profile Endpoints` -> "Add Request".
   - Name it `Get Profile`.
   - Open `Get Profile` editor tab.
   - Switch to **Headers** tab: Verify `X-Api-Source` is present, marked as `Inherited`.
   - Switch to **Auth** tab: Verify auth is set to `Inherit` from parent folder and collection.
4. **Override Header**: In `Get Profile` headers tab, add `X-Api-Source: Custom-Profile`.
   - Verify the custom request header overrides the collection-level inherited header during execution.

### Scenario 4: Selective Multi-Collection Global Export & Import

1. **Open Global Export**: In the sidebar header options menu, click "Export Collections...".
2. **Select Collections**:
   - In the `ExportCollectionsDialog`, select `Users API` and `Payments API`.
   - Leave any other collection unchecked.
   - Choose format: `OpenCollection JSON`.
3. **Choose Target Directory**: Click "Export (2)" and select a destination folder.
4. **Verify Files**:
   - Inspect the chosen folder: Verify exactly 2 files exist:
     - `Users API.opencollection.json`
     - `Payments API.opencollection.json`
   - Open `Users API.opencollection.json` and verify it contains `info.summary = "Core user management service"`, `info.version = "2.1.0"`, and the `request` defaults.
5. **Import Collection**: Click "Import" in the application and select `Users API.opencollection.json`.
   - Verify a new collection `Users API (1)` is added to the sidebar.

### Scenario 5: Legacy Single-Collection Migration

1. **Prepare Legacy Data**: Launch with a `workspace.json` containing a single global OpenCollection document.
2. **Verify Migration**:
   - Verify all existing items load under a primary collection in the new "Collections" sidebar.
   - Verify no requests, folders, scripts, or protobuf configurations were lost.
