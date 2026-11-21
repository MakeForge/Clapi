# Technical Research & Architecture Decisions: Insomnia v5 Import

**Feature**: Insomnia v5 Collection & Environment Import
**Date**: 2026-11-15
**Status**: Completed (Phase 0)

---

## 1. Insomnia v5 Document Detection & Parsing Strategy

### Decision
Parse Insomnia v5 exports directly using `kaml` (for YAML) and `kotlinx.serialization.json` (for JSON). Document types are identified by inspecting the root `type` property:
- `collection.insomnia.rest/5.0` (Request collection)
- `spec.insomnia.rest/5.0` (Design document containing collection and/or OpenAPI `spec.contents`)
- `environment.insomnia.rest/5.0` (Environment export)

### Rationale
- Insomnia v5 export files are formatted as either YAML or JSON.
- Clapi already includes `kaml` (`com.charleskorn.kaml:kaml`) in `core-storage` and `kotlinx.serialization` across all multiplatform modules.
- Reusing existing serialization libraries guarantees zero additional third-party binary weight, keeping the distribution size optimized.
- YAML parsing in `kaml` handles JSON as a strict subset of YAML, allowing a unified or content-type-based parsing pipeline.

### Alternatives Considered
- **SnakeYAML or Jackson**: Rejected because they add unnecessary JVM-only dependencies and increase release artifact size.
- **Regex-based extraction**: Rejected because recursive folder hierarchies and complex nested request structures require proper document tree parsing.

---

## 2. Importer Architecture & Module Placement

### Decision
Place the core parsing and data mapping engine in `core-storage`:
`core-storage/src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/importers/InsomniaImporter.kt`

Integrate `InsomniaImporter` into:
1. `CompositeSpecificationImporter`: Enables interactive scanning and preview in `ImportSpecificationDialog`.
2. `ImportExportRepository.importCollectionFromString`: Enables direct string/file import into `AppState`.

Add `SpecFormat.INSOMNIA` and `SpecFormat.POSTMAN` to `SpecFormat` in `core-model/src/commonMain/kotlin/io/github/makeforge/clapi/core/model/importers/ImportModels.kt`.

### Rationale
- Adheres to the repository's **Library-First & Modularity** constitution principle.
- Decouples parsing and conversion logic from Compose UI, enabling thorough unit and integration testing without a UI harness.
- Ensures consistency between interactive dialog imports and automated or CLI-driven collection imports.

### Alternatives Considered
- **UI-only parser in `ui-shared`**: Rejected because business logic and data transformations should not live in the UI presentation layer.

---

## 3. Script Translation Feasibility & Implementation

### Decision
Support automatic conversion of Insomnia `scripts.preRequest` and `scripts.afterResponse` into Clapi's before-request (`ScriptType.BEFORE_REQUEST`) and after-response / test (`ScriptType.AFTER_RESPONSE`) scripts.

Translation transformations:
- `insomnia.environment.get/set/unset` $\rightarrow$ `clapi.environment.get/set/unset`
- `insomnia.baseEnvironment.get/set` $\rightarrow$ `clapi.collectionVariables.get/set`
- `insomnia.collectionVariables.get/set/unset` $\rightarrow$ `clapi.collectionVariables.get/set/unset`
- `insomnia.variables.get/set` $\rightarrow$ `clapi.variables.get/set`
- `insomnia.test(title, fn)` $\rightarrow$ `clapi.test(title, fn)`
- `insomnia.expect(val)` $\rightarrow$ `clapi.expect(val)`
- `insomnia.sendRequest(req, cb)` $\rightarrow$ `clapi.sendRequest(req, cb)`
- `insomnia.response.status / code / json() / text() / headers / cookies` $\rightarrow$ `clapi.response.status / code / json() / text() / headers / cookies`
- `insomnia.request.*` $\rightarrow$ `clapi.request.*`

### Rationale
- Insomnia v5 deliberately patterned its scripting API after Postman's `pm.*` API.
- Clapi already has an effective, proven regex/token replacement translator in `PostmanImporter.convertScript`.
- Translating `insomnia.*` to `clapi.*` provides immediate script compatibility for the vast majority of user test assertions and variable extraction scripts.

### Alternatives Considered
- **Full JavaScript AST parser/transpiler (e.g. Babel or Nashorn)**: Unnecessary complexity and runtime overhead; the API mappings are straightforward 1-to-1 namespace and method renames.
- **Skipping scripts**: Severely damages user migration experience, as assertions and auth token extraction scripts are critical for API test suites.

---

## 4. Import Dialog Format Badges UI Design

### Decision
In `ImportSpecificationDialog.kt`, render a stylized list of format badges below the "Choose Files" button in the dropzone area:
- Badges: `OpenAPI 3.0+`, `OpenCollection`, `Postman`, `Insomnia`.
- Badges use `ClapiColors.SurfaceContainerHigh` background, subtle border, primary tint, rounded pill shape (4.dp to 6.dp), and 11.sp font.
- Maintain test tags: `testTag("import-format-badge-openapi")`, `testTag("import-format-badge-opencollection")`, `testTag("import-format-badge-postman")`, `testTag("import-format-badge-insomnia")`.

### Rationale
- Gives immediate visual confirmation to users who might otherwise wonder if Postman or Insomnia files are supported.
- Replaces or supplements the single generic line of text with polished, recognizable format tags.

### Alternatives Considered
- **File dropdown filter**: Adds unnecessary clicks; Clapi auto-detects formats upon scan.

---

## 5. Unsupported Fields & Graceful Degradation

### Decision
Safely ignore unsupported metadata (client TLS certs, mock server routes, Kong Konnect cloud keys, unit test runner suites) without halting import, and preserve all relevant fields in the Clapi data model. A detailed mapping specification is documented in `scratch/insomnia-import-unsupported.md`.

### Rationale
- Prevents import crashes when users export full workspaces from Insomnia.
- Maintains data integrity and clean workspace state.
