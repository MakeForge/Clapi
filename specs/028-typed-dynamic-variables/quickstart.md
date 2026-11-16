# Quickstart & Verification Guide: Typed Dynamic Variables

**Branch**: `028-typed-dynamic-variables` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

---

## 1. Prerequisites

- Zulu 21 / JDK 17+
- Gradle wrapper (`./gradlew`)

---

## 2. Validation Scenarios

### Scenario 1: Creating a Typed Number Dynamic Variable in UI
1. Open Clapi desktop (`./gradlew :ui-desktop:run`).
2. Open Environment Editor.
3. Click **Add Variable**.
4. In the **Kind** dropdown, select **Dynamic**.
5. In the **Type** dropdown, select **Number**.
6. Name the variable `orderCount`.
7. Click **Edit Script** and write:
   ```javascript
   return 42;
   ```
8. Verify the preview shows: `Preview: { "key": {{orderCount}} }`.
9. Save and close.

### Scenario 2: Unquoted JSON Request Body in Code Editor
1. Create a new HTTP Request.
2. Set Method to `POST`, URL to `https://httpbin.org/post`.
3. In the Body tab, select `JSON`.
4. Type:
   ```json
   {
     "count": {{orderCount}},
     "createdAt": {{$timestamp}}
   }
   ```
5. **Expected Outcome**:
   - Zero syntax errors reported by the editor.
   - No `"Expected property name at offset 7"` error badge.

### Scenario 3: String Variable Quoting Diagnostic
1. In the same JSON body, add:
   ```json
   {
     "invalidDate": {{nowDate}}
   }
   ```
   (where `nowDate` is typed as `String`).
2. **Expected Outcome**:
   - Editor shows diagnostic:
     `Variable 'nowDate' is typed as String; unquoted syntax generates invalid JSON. Wrap in quotes: "{{nowDate}}"`

### Scenario 4: Request Execution & Wire Format
1. Click **Send**.
2. Inspect outgoing request body in Network/History log:
   ```json
   {
     "count": 42,
     "createdAt": 1731776500123
   }
   ```
3. Verify `count` is parsed by backend as an integer primitive, not a string.

---

## 3. Automated Test Commands

Run the test suite for this feature:
```bash
rtk gradlew :core-model:desktopTest
rtk gradlew :editor-core:desktopTest
rtk gradlew :ui-shared:desktopTest
rtk gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.DynamicVariablesE2ETest"
```
