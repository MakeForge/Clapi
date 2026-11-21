# Quickstart Validation Guide: Desktop UI Polish & Modularization

**Feature**: `021-desktop-ui-polish`  
**Date**: 2026-11-14  
**Status**: Ready for Validation

---

## 1. Prerequisites & Environment Setup

- **Java JDK**: 17 or 21 installed.
- **Gradle Wrapper**: `./gradlew` configured in repo root.
- **Display Environment**: Linux X11/Wayland or equivalent desktop GUI display session.

```bash
# Verify build environment
./gradlew compileKotlinDesktop
```

---

## 2. Validation Scenarios

### Scenario 1: Modal Dialog Escape Key Dismissal
**Objective**: Verify that every desktop modal dialog closes immediately when pressing `Escape`.

1. **Launch Desktop App**:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. **Test Dialogs**:
   - Open **Settings Dialog** (`Ctrl/Cmd + ,` or toolbar icon) -> Press `Escape`. Verify dialog closes.
   - Open **Environment Edit Dialog** (click environment chip -> Manage) -> Press `Escape`. Verify dialog closes.
   - Open **Git Sync Dialog** -> Press `Escape`. Verify dialog closes.
   - Trigger a **Delete confirmation** prompt -> Press `Escape`. Verify prompt cancels without deleting.
   - Open **Help / About Dialog** -> Press `Escape`. Verify dialog closes.
3. **Test Dirty State Safety**:
   - In Environment Edit Dialog, modify a variable value -> Press `Escape`.
   - **Expected Outcome**: Discard confirmation prompt appears asking whether to discard unsaved changes.

---

### Scenario 2: Desktop Cursor Affordances & Pointer Feedback
**Objective**: Verify that custom interactive surfaces switch to pointing hand cursor on hover.

1. **Hover over Custom Rows**:
   - Hover mouse over rows in `KeyValueEditor` (Params, Headers).
   - Hover mouse over log lines in `BottomPanel` (Console).
   - Hover mouse over proto files in `CollectionEditor`.
   - **Expected Outcome**: Cursor shape immediately changes from standard arrow to `PointerIcon.Hand`.
2. **Hover over Custom Chips & Buttons**:
   - Hover over `MethodDropdown` in the request bar.
   - Hover over `EnvironmentChip` in the top toolbar.
   - Hover over tab buttons in `ResponseViewer` and `SettingsDialog`.
   - **Expected Outcome**: Cursor displays `PointerIcon.Hand`.
3. **Verify Disabled State**:
   - When Send button is disabled or action is unavailable, hover over it.
   - **Expected Outcome**: Cursor remains standard arrow (`PointerIcon.Default`).

---

### Scenario 3: Virtualized List Scroll Stability & Key Reconciliation
**Objective**: Ensure streaming logs or updating list items does not cause scroll jumps or state loss.

1. **Test Console Stream**:
   - Send requests repeatedly or trigger script execution generating multiple log entries.
   - Scroll up in the Console tab to view earlier entries while new logs arrive.
   - **Expected Outcome**: Scroll position is maintained stably; no visual jitter or item blinking.
2. **Test Header / Cookie Navigation**:
   - View response headers and cookies for a request with 20+ headers.
   - Select or filter items.
   - **Expected Outcome**: Items render smoothly with zero reconciliation warnings.

---

### Scenario 4: Dark / Light Theme Contrast & Centralized Strings
**Objective**: Ensure all UI elements adapt cleanly to theme switching without unreadable white text or hardcoded hex colors.

1. **Toggle Theme**:
   - Switch between Light Theme and Dark Theme in Settings.
   - Inspect GraphQL Panel, WebSocket Request Editor, Auth Editor, and timing breakdown in Response Viewer.
   - **Expected Outcome**: All text, status badges, and borders resolve semantic tokens from `ClapiColors` with crisp contrast.
2. **Inspect Strings**:
   - Verify all dialog headers, buttons, placeholders, and tooltips are readable and have corresponding entries in `Strings.kt`.

---

### Scenario 5: File Line Limits & Decomposition Verification
**Objective**: Verify that no UI source file in `ui-shared` exceeds 1,000 lines.

```bash
# Verify no component file exceeds 1,000 lines
find ui-shared/src/commonMain/kotlin/io/github/crustack/clapi/ui/shared/components -name "*.kt" -exec wc -l {} + | awk '$1 > 1000 { print }'
```
- **Expected Outcome**: Output is completely empty (zero files > 1,000 lines).

---

## 3. Automated Regression Verification

```bash
# Run unit test suites across shared UI and desktop runner
./gradlew :ui-shared:desktopTest
./gradlew :ui-desktop:desktopTest

# Verify full compilation
./gradlew compileKotlinDesktop
```
