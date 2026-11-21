# Quickstart Validation Guide: Environment Enhancements

## Overview
This document guides developers and QA testers through manual and automated validation of the environment enhancements in Clapi.

---

## Scenario 1: New Environment Immediate Edit Flow & Sidebar Expansion

### Purpose
Verify that creating a new environment automatically expands the Environments section in the sidebar and immediately opens the Edit Environment dialog for that new environment.

### Steps
1. Launch Clapi. Ensure the sidebar Environments section is collapsed (click the header to collapse if needed).
2. Click the `+` ("New environment") icon in the Environments section header (or click the empty state button if no environments exist).
3. In the "Create environment" dialog, enter `Staging` and click "Create".
4. **Expected Results**:
   - The Environments section in the sidebar automatically expands.
   - `Staging` is visible and highlighted as the active/selected environment.
   - The Edit Environment dialog opens immediately for `Staging` with the name field focused.

---

## Scenario 2: Environment Inheritance & Variable Overrides (`extends`)

### Purpose
Verify that an environment can extend a parent environment, inherit its variables, and override individual variables with clear visual feedback.

### Steps
1. Create environment `Base` with variables:
   - `BASE_URL` = `https://api.example.com`
   - `TIMEOUT` = `30`
2. Create environment `Dev`.
3. In the `Dev` Edit Environment dialog:
   - In the "Extends" selector, select `Base`.
   - Verify that inherited variables from `Base` are indicated.
   - In the top quick-add row, enter `BASE_URL` = `https://dev.api.example.com` and add it.
4. **Expected Results**:
   - `BASE_URL` displays an "Overridden" badge indicating it overrides the value from `Base`.
   - During request variable substitution when `Dev` is active, `{{BASE_URL}}` resolves to `https://dev.api.example.com` and `{{TIMEOUT}}` resolves to `30`.

---

## Scenario 3: Predefined & Custom Color Selection

### Purpose
Verify assigning predefined palette colors and custom hex strings to environments.

### Steps
1. Open the Edit Environment dialog for `Production`.
2. Click the red color preset swatch (`#EF4444`). Save the environment.
3. Observe the sidebar and header top toolbar.
4. **Expected Results**:
   - The environment dot/badge in `SidebarEnvironmentRow` turns red (`#EF4444`).
   - The active environment indicator in `TopToolbar` displays the red color.
5. Edit again, input custom hex `#8A2BE2` (BlueViolet) into the custom hex field and save.
   - Color indicator updates to `#8A2BE2`.

---

## Scenario 4: Inline Quick-Add Top Row

### Purpose
Verify that users can enter and add variables and external secrets directly from the top row without scrolling.

### Steps
1. Open an environment containing 20+ variables so the list scrolls.
2. Observe the top of the table: a quick-add input row is pinned above the list entries.
3. Type `NEW_KEY` in the key field and `NEW_VAL` in the value field, then press `Enter`.
4. **Expected Results**:
   - The new variable is committed to the environment.
   - The quick-add row fields are cleared and ready for the next entry.
   - The same behavior works on the External Secrets tab with provider-specific fields.

---

## Scenario 5: YAML File Drag-and-Drop Import

### Purpose
Verify dragging and dropping `.yaml` and `.yml` files into the import dialog.

### Steps
1. Open the Import Dialog (`collection-import-button` or Ctrl+I).
2. Drag a `.yaml` or `.yml` file (e.g. an OpenAPI or OpenCollection spec) from the OS file manager into the dropzone (`import-dropzone`).
3. Release the mouse button.
4. **Expected Results**:
   - The dropzone acknowledges the file, shows the file name and size, and transitions to preview/selection.
   - No crash or format rejection error occurs.

---

## Scenario 6: Environment External Secrets Export & Import Sanitization

### Purpose
Verify that exporting an environment preserves external secrets configurations without leaking secret values.

### Steps
1. Configure an environment `CloudEnv` with an AWS Secrets Manager secret (`secretName = "prod/app/db"`, `targetField = "password"`).
2. Export the environment to a JSON file.
3. Open the exported file in a text editor.
4. **Expected Results**:
   - The configuration (`aws-secrets-manager`, `secretName`, `targetField`) is present in the `externalSecrets` block.
   - No plaintext passwords or tokens are present.
5. In Clapi, delete `CloudEnv` and import the exported file.
   - `CloudEnv` is restored with its external secrets configuration intact.

---

## Automated Verification Commands

```bash
# Run multiplatform model and mapping tests
./gradlew :core-model:test :core-storage:test

# Run shared UI logic and persistence tests
./gradlew :ui-shared:desktopTest

# Run desktop Compose UI verification tests
./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.EnvironmentEditorUiTest"
./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.ImportSpecificationUiTest"

# Run end-to-end QA tests
./gradlew :qa-tests:desktopTest
```
