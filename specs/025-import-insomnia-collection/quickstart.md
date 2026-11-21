# Quickstart & Verification Guide: Insomnia v5 Import & Migration

This guide outlines runnable scenarios to validate Insomnia v5 collection and environment import in Clapi, as well as instructions for migrating existing workspaces from Postman and Insomnia into Clapi.

---

## 1. Migrating to Clapi from Postman & Insomnia

Clapi makes it simple to migrate your existing collections, folders, environments, and automated test scripts without manual rework.

### From Insomnia
1. In Insomnia, select your document or collection.
2. Open the workspace dropdown menu and choose **Export**.
3. Select **Insomnia v5 (YAML or JSON)** as the export format and save the file.
4. In Clapi, click the **Import** button in the sidebar (or press `Ctrl+I` / `Cmd+I`).
5. Drag and drop your `.yaml` or `.json` file into the import area (or paste from clipboard).
6. Review the folders, requests, and environments in the preview, then click **Import**.

### From Postman
1. In Postman, open your collection's menu (`...`) and click **Export**.
2. Choose **Collection v2.1 (recommended)** and export as `.json`.
3. (Optional) Export your active environment from the Environments tab.
4. In Clapi, open the **Import Dialog** and upload the exported JSON file.
5. All requests, headers, variables, and `pm.*` test assertions will be automatically converted to Clapi.

---

## 2. Validation Scenarios

### Scenario 1: Insomnia v5 Collection Import (End-to-End)

**Goal**: Verify importing a multi-folder Insomnia v5 YAML export creates collections, nested folders, HTTP requests, auth, and environment variables.

1. **Prerequisites**: A running Clapi desktop instance or test harness.
2. **Setup**: Provide a fixture file conforming to `insomnia.schema.5.1.json` with nested folders, a GET request, and a POST request with basic auth and pre-request scripts.
3. **Execution**:
   ```bash
   ./gradlew :core-storage:test --tests "io.github.makeforge.clapi.core.storage.importers.InsomniaImporterTest"
   ```
4. **Expected Outcome**:
   - `canHandle()` returns `true`.
   - `scanContent()` yields `ImportScanResult.Success` with format `SpecFormat.INSOMNIA`.
   - All folders, requests, and environments are present with 100% field fidelity.

---

### Scenario 2: Insomnia Script Translation Verification

**Goal**: Verify `insomnia.*` scripting calls are correctly translated into `clapi.*` calls.

1. **Setup**: Provide an Insomnia request with:
   - `scripts.preRequest`: `insomnia.environment.set("token", "secret123");`
   - `scripts.afterResponse`: `insomnia.test("Status code", () => { insomnia.expect(insomnia.response.status).to.eql(200); });`
2. **Execution**:
   ```bash
   ./gradlew :core-storage:test --tests "io.github.makeforge.clapi.core.storage.importers.InsomniaScriptTranslatorTest"
   ```
3. **Expected Outcome**:
   - `scripts.preRequest` translated to `clapi.environment.set("token", "secret123");`.
   - `scripts.afterResponse` translated to `clapi.test("Status code", () => { clapi.expect(clapi.response.status).to.eql(200); });`.

---

### Scenario 3: Import Dialog UI Format Badges & Workflow

**Goal**: Verify the Import Dialog displays all 4 format badges and processes Insomnia files in the UI.

1. **Execution**:
   ```bash
   ./gradlew :ui-desktop:test --tests "io.github.makeforge.clapi.ui.desktop.ImportSpecificationUiTest"
   ```
2. **Expected Outcome**:
   - Badges for `OpenAPI 3.0+`, `OpenCollection`, `Postman`, and `Insomnia` are rendered in Step 1.
   - Dropping or scanning an Insomnia file advances the dialog to Step 2 with format badge "Insomnia v5".
   - Confirming import persists the collection into `AppState.collections`.

---

### Scenario 4: Automated QA End-to-End Test Suite

**Goal**: Execute comprehensive QA tests covering full workspace migration.

1. **Execution**:
   ```bash
   ./gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.InsomniaImportE2ETest"
   ```
2. **Expected Outcome**: All QA tests pass with zero regressions.

---

## 3. Related Artifacts & Specifications
- Specification: [spec.md](spec.md)
- Data Model: [data-model.md](data-model.md)
- Importer Engine Contract: [contracts/insomnia-importer-contract.md](contracts/insomnia-importer-contract.md)
- UI Badges Contract: [contracts/import-dialog-badges-contract.md](contracts/import-dialog-badges-contract.md)
- Unsupported Fields Catalog: [scratch/insomnia-import-unsupported.md](../../scratch/insomnia-import-unsupported.md)
