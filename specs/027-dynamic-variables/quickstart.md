# Quickstart & Verification Guide: User-Defined Dynamic Variables

**Branch**: `027-dynamic-variables` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

## Overview
This guide provides step-by-step runnable verification scenarios to validate user-defined dynamic variables from unit tests to UI interactions and end-to-end request execution.

---

## Prerequisites
- JDK 17+ (JDK 21 recommended)
- Gradle wrapper (`./gradlew`)
- Repository root directory: `/home/user/IdeaProjects/Clapi`

---

## Scenario 1: Model Serialization & Deserialization
Verify that `DynamicVariable` serializes and deserializes cleanly in JSON and YAML without schema errors.

### Command
```bash
./gradlew :core-model:test --tests "io.github.makeforge.clapi.core.model.opencollection.DynamicVariableSerializationTest"
```

### Expected Outcome
- Encoded JSON includes `"type": "dynamic"` and `"script"`.
- Roundtrip deserialization preserves parameter declarations and script content.
- Polymorphic deserializer in `OpenCollectionEnvironment` recognizes dynamic variables.

---

## Scenario 2: Dynamic Variable Script Execution & Sandboxing
Verify that `ClapiScriptEngine` / `DynamicVariableEngine` executes scripts, binds parameters, accesses request context, and enforces timeout limits.

### Command
```bash
./gradlew :core-scripting:test --tests "io.github.makeforge.clapi.core.scripting.DynamicVariableEngineTest"
```

### Expected Outcome
- Basic script `return "HELLO_" + (args.num * 2)` with argument `num = 21` produces `"HELLO_42"`.
- Accessing `context.request.url` in script correctly reflects the injected request URL.
- An intentional infinite loop (`while(true) {}`) triggers a timeout error after 2,000ms without freezing the test runner.

---

## Scenario 3: Variable Resolution with Parameterized Tokens
Verify that `VariableResolver` parses `{{func(arg1, arg2)}}` tokens and calls dynamic variable handlers.

### Command
```bash
./gradlew :core-network:test --tests "io.github.makeforge.clapi.core.network.DynamicVariableResolverTest"
```

### Expected Outcome
- Input: `https://api.example.com/items?ts={{secondsFromNow(10)}}`
- Output: `https://api.example.com/items?ts=2026-11-15T22%3A10%3A00Z` (computed on demand).
- Sequential resolutions generate independent fresh values.

---

## Scenario 4: Desktop UI Subdialog Interaction
Verify that the `DynamicVariableEditorDialog` UI renders, accepts edits, and runs interactive tests.

### Command
```bash
./gradlew :ui-desktop:desktopTest --tests "io.github.makeforge.clapi.ui.desktop.DynamicVariableEditorDialogTest"
```

### Expected Outcome
- Setting variable type to `dynamic` shows `[Edit Script]` pill button.
- Clicking `[Edit Script]` opens the two-pane editor subdialog.
- Typing script and clicking "Run Test" renders the returned string, duration, and success badge.

---

## Scenario 5: End-to-End QA Request Execution
Verify that dynamic variables execute automatically during full HTTP request dispatch and survive collection export/import.

### Command
```bash
./gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.DynamicVariablesE2ETest"
```

### Expected Outcome
- E2E request with dynamic variable header reaches test API with freshly computed value.
- Exporting the collection to YAML and re-importing retains all dynamic variables and scripts intact.

---

## Scenario 6: Documentation Site Build
Verify that the VitePress documentation builds cleanly with the new Dynamic Variables page and sidebar links.

### Command
```bash
npm run docs:build
```

### Expected Outcome
- Documentation builds successfully without broken links.
- `/features/dynamic-variables` page includes built-in reference and user-defined scripting guides.
