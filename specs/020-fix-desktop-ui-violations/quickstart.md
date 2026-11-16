# Quickstart Validation Guide: Fix Desktop UI High-Severity Violations

**Feature**: `020-fix-desktop-ui-violations`  
**Date**: 2026-11-13  
**Status**: Ready for Validation

---

## 1. Prerequisites & Environment Setup

- **Java JDK**: Version 17 or 21 installed.
- **Gradle Wrapper**: `./gradlew` configured in project root.
- **Display Server**: Linux X11/Wayland or equivalent desktop GUI display environment.

```bash
# Verify environment and build desktop artifacts
./gradlew compileKotlinDesktop
```

---

## 2. Validation Scenarios

### Scenario 1: Desktop Scrollbar Functionality in Long Content Views
**Objective**: Prove vertical scrollbar presence and direct mouse drag navigation.

1. **Launch Desktop App**:
   ```bash
   ./gradlew :ui-desktop:run
   ```
2. **Execute Long Request & View Response**:
   - Send any HTTP request that yields a multi-line body (>50 lines).
   - In `ResponseViewer`, inspect the right edge of the response body, headers, and cookies tabs.
   - **Expected Outcome**: A vertical scrollbar thumb is visible; clicking and dragging the thumb scrolls the content smoothly.
3. **Open Dialog with Overflows**:
   - Open Settings Dialog or Environment Edit Dialog with multiple variables.
   - **Expected Outcome**: The dialog content shows an interactive vertical desktop scrollbar on the right.

---

### Scenario 2: Rapid Clicking on Toolbars & Tooltip Responsiveness
**Objective**: Prove that `IconButton` click events are never swallowed by hover layers.

1. **Test Toolbar Buttons**:
   - Hover over action buttons in `TopToolbar` (e.g. Save, Send, Git Sync, Settings).
   - Verify hover tooltip appears after ~200ms.
   - Rapidly click the button 3 times in quick succession.
   - **Expected Outcome**: Every click triggers the intended action immediately; tooltip does not intercept the pointer.
2. **Test Code Editor Search & Action Buttons**:
   - Open search in `CodeEditor` (`Ctrl/Cmd + F`).
   - Hover over search toggle buttons (Case Sensitive, Regex, Whole Word) and click them rapidly.
   - **Expected Outcome**: Toggles respond instantly without missed clicks.

---

### Scenario 3: Container Sizing & Layout Modifier Propagation
**Objective**: Verify that host layouts can pass modifiers, constraints, and test tags to primary panels.

1. **Run UI Component Checks**:
   ```bash
   ./gradlew :ui-shared:check :ui-desktop:check
   ```
2. **Interactive Split Pane Resizing**:
   - Drag the horizontal split between Request Editor and Response Viewer.
   - Drag the vertical split between Request Tabs and Bottom Console.
   - **Expected Outcome**: Child panels adapt dynamically without clipping or overflowing window boundaries.

---

## 3. Automated Regression Verification

```bash
# Run unit tests across all shared UI modules
./gradlew :ui-shared:allTests

# Run desktop integration tests
./gradlew :ui-desktop:desktopTest
```
