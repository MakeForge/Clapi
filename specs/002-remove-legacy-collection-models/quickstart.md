# Quickstart: Validate Remove Legacy Collection Models

Run from the repository root. Use `rtk gradlew` (no `./`), per repo convention.

## 1. Confirm the deprecated types are gone (SC-001)

```bash
rtk grep -rn "WorkspaceDefinition\|CollectionDefinition\|FolderDefinition" --include=*.kt .
```

Expected: no matches.

## 2. Confirm no legacy writes or reads remain (SC-002, FR-003, FR-004)

```bash
rtk grep -rn "clapiCollection\|clapiWorkspace" --include=*.kt .
```

Expected: matches only in the rejection code path in `ImportExportRepository.kt` and in the rejection tests. No production write of `clapiCollection` or `clapiWorkspace`.

## 3. Build and run the affected suites (SC-003)

```bash
rtk gradlew :core-model:allTests
rtk gradlew :core-storage:allTests
rtk gradlew :ui-shared:desktopTest
rtk gradlew :ui-desktop:desktopTest
rtk gradlew :qa-tests:jvmTest
```

Expected: all pass.

## 4. Check the OpenCollection round trip (SC-005)

```bash
rtk gradlew :qa-tests:jvmTest --tests io.github.makeforge.qa.OpenCollectionRoundTripE2ETest
```

Expected: export then import returns the same folders, requests, and order.

## 5. Check the qa fixture (SC-006)

```bash
rtk gradlew :qa-tests:jvmTest --tests io.github.makeforge.qa.SampleCollectionE2ETest
rtk gradlew :qa-tests:jvmTest --tests io.github.makeforge.qa.OpenCollectionValidationE2ETest
node qa-tests/collection-validator.mjs
```

Expected: the fixture parses as an OpenCollection document. The validator runs against it and reports the same request results as before the conversion, or the difference is explained in the change.

## 6. Check legacy rejection (SC-004)

Import a file with `"type": "clapiCollection"` through the import tests in `ui-desktop` (`ImportExportRepositoryTest`).

Expected: `ImportExportException` with the "no longer supported" message. The collection count does not change.

## 7. Check the global document persistence (FR-014 to FR-019)

Run the workspace persistence tests (`PersistenceErrorReportingTest` and the desktop persistence tests).

Expected:
- Every Clapi collection is a top-level folder of one global document.
- Environments reload from the global document and are not tied to any collection. They persist with zero collections.
- Importing a collection with an existing environment name adds only missing variables and fields. Nothing existing is removed.
- A collection's id, and its expanded state, survive reload.
- Global variables, history, and expanded state reload.
- A stored `clapi.workspace` value is not read.

