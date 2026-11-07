# Quickstart Validation Guide: OpenCollection Canonical Data Model

**Feature**: `001-opencollection-data-model`
**Date**: 2026-11-07
**Status**: Draft

This guide outlines end-to-end runnable validation scenarios to prove that the OpenCollection canonical data model works correctly, adheres to specification constraints, and causes zero regressions in Clapi.

---

## Prerequisites & Environment Setup

- **JDK**: Java 21 or higher.
- **Gradle**: Uses repository wrapper `./gradlew`.
- **Sample Server**: Local Ktor sample server running on an ephemeral port.
- **Official OpenCollection Schema**: `docs/Clapi/todos/opencollection_v1.0.0.json`.
- **Test Fixtures**:
  - Legacy Clapi fixture: `qa-tests/fixtures/clapi-test-collection.json`
  - Legacy environment fixture: `qa-tests/fixtures/clapi-test-environment.json`
  - Postman fixture: `qa-tests/fixtures/sap-byd-new-business.postman_collection.json`

---

## Validation Scenarios

### Scenario 1: OpenCollection Schema Validation & Loading (SC-001, SC-002)

**Objective**: Verify that valid OpenCollection documents load successfully and invalid documents produce actionable errors without modifying the file.

1. **Run Unit & Model Validation Tests**:
   ```bash
   rtk ./gradlew :core-model:desktopTest --tests "opencollection.*"
   ```
2. **Expected Outcome**:
   - `OpenCollectionDocument` parsed from valid JSON/YAML matches `docs/Clapi/todos/opencollection_v1.0.0.json` schema 100%.
   - Opening an invalid fixture (e.g., missing required fields, illegal `info.type`) throws `CollectionValidationException` with line/path coordinates.
   - The original file on disk remains completely untouched.

---

### Scenario 2: Lossless Round-Trip & Unmodeled Field Preservation (SC-002, FR-007, FR-008)

**Objective**: Verify that opening an OpenCollection document containing unknown fields, modifying one request, and saving preserves all unknown fields semantically.

1. **Run Round-Trip Verification Test**:
   ```bash
   rtk ./gradlew :core-storage:desktopTest --tests "*OpenCollectionRoundTripTest*"
   ```
2. **Validation Steps**:
   - Load fixture containing custom third-party fields (e.g., `"x-custom-vendor": { "foo": "bar" }`).
   - Edit the target URL of a nested request.
   - Serialize to JSON/YAML and deserialize back.
   - Verify that the modified URL is updated, while `"x-custom-vendor"` is 100% preserved in the output.

---

### Scenario 3: Request Execution from Canonical Model (SC-003, FR-035)

**Objective**: Verify that executing an HTTP request compiled from `HttpRequestItem` sends identical requests on the wire (headers, auth, params, body, scripts).

1. **Run E2E Regression Suite against Embedded Server**:
   ```bash
   rtk ./gradlew :qa-tests:test --tests "io.github.crustack.qa.SampleCollectionE2ETest"
   ```
2. **Expected Outcome**:
   - 100% of existing tests pass against the Ktor test server with identical status codes, headers, and response bodies.
   - Pre-request script and post-request test assertions execute identically via `ClapiScriptEngine`.

---

### Scenario 4: Legacy Collection Migration & Zero Data Loss (SC-004, SC-005, SC-006)

**Objective**: Verify that legacy `clapiWorkspace` and `clapiCollection` files are converted cleanly to OpenCollection without data loss.

1. **Run Migration Test Suite**:
   ```bash
   rtk ./gradlew :core-storage:desktopTest --tests "*LegacyCollectionMigratorTest*"
   ```
2. **Validation Steps**:
   - Ingest `qa-tests/fixtures/clapi-test-collection.json`.
   - Verify all folders, subfolders, and requests are preserved in identical order.
   - Verify auth, scripts, and parameters are mapped to their canonical equivalents.
   - Trigger a simulated failure during migration: verify original files remain byte-identical (SC-005).
   - Re-run migration on already-migrated data: verify idempotent no-op (SC-006).

---

### Scenario 5: MCP Configuration Isolation (SC-008, FR-021, FR-022)

**Objective**: Verify that MCP connections are stored under `extensions.clapi.mcp` and NEVER as HTTP requests.

1. **Run MCP Model Isolation Test**:
   ```bash
   rtk ./gradlew :core-model:desktopTest --tests "*McpOpenCollectionIsolationTest*"
   ```
2. **Expected Outcome**:
   - Items array in the serialized collection contains 0 HTTP requests representing MCP connections.
   - MCP configurations are serialized into `extensions.clapi.mcp`.
   - `McpSessionState` initializes and connects to MCP transports with unchanged behavior.

---

### Scenario 6: Secret Variable Sanitization (SC-007, FR-017)

**Objective**: Verify that secret variables are omitted from serialized output and masked in logs.

1. **Run Secret Sanitization Test**:
   ```bash
   rtk ./gradlew :core-model:desktopTest --tests "*SecretVariableSanitizationTest*"
   ```
2. **Expected Outcome**:
   - Exported OpenCollection JSON contains `"secret": true`, but no secret `value` property.
   - Console and debug logs contain masked values (`***`) instead of raw secrets.
