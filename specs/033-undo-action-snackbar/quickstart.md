# Quickstart & Validation Guide: Undoable Action Snackbar

**Feature**: `033-undo-action-snackbar` | **Date**: 2026-11-21

## Prerequisites

- JDK 17+ configured for Gradle
- Repository root: `/home/user/IdeaProjects/Clapi`
- Always run Gradle commands via `rtk gradlew ...` per project rules.

## Automated Validation Commands

### 1. Unit Tests (`ui-shared`)
Verifies `ActionSnackbarController` (single-snackbar replacement, once-only undo, stale-dismiss safety, failure fallback), tree snapshot/restore helpers (delete/move/rename undo and no-op detection), and `SettingsRepository` persistence/clamping for `snackbarAutoDismissSec`:

```bash
rtk gradlew :ui-shared:jvmTest
```

### 2. Desktop UI & Integration Tests (`ui-desktop`)
Verifies bottom-right snackbar rendering, auto-dismiss timing, immediate replacement when two actions fire back-to-back, Undo button restoration for delete/move/rename, and the General Global Settings auto-dismiss input:

```bash
rtk gradlew :ui-desktop:desktopTest
```

### 3. Full Verification Build
```bash
rtk gradlew :ui-shared:jvmTest :ui-desktop:desktopTest
```

## Manual End-to-End Validation Scenarios

1. **Delete & Undo (Request, Folder with Children, Environment, History)**
   - Delete a request from the sidebar → confirm snackbar appears in the bottom-right corner with `"Deleted \"<name>\""` and an **Undo** button.
   - Click **Undo** within 5 seconds → confirm request reappears in its original position and snackbar disappears immediately.
   - Delete a non-empty folder → click **Undo** → confirm folder and all nested subfolders/requests are restored intact.
   - Delete an environment → click **Undo** → confirm environment and its variables are restored.

2. **Move & Undo (Drag-and-Drop & Move Up/Down)**
   - Move a request up/down or drag it into another folder → confirm bottom-right snackbar appears with **Undo**.
   - Click **Undo** → confirm request returns to its original folder and index.
   - Drag a folder to reorder it → click **Undo** → confirm original order is restored.
   - Drop an item back in its exact original position → confirm **no** snackbar appears.

3. **Rename & Undo (Request, Folder, Collection, Environment)**
   - Rename a request (inline in sidebar or from tab bar) → confirm snackbar shows old and new names with **Undo**.
   - Click **Undo** → confirm old name is restored in both sidebar and open tabs.
   - Commit a rename with the exact same name → confirm **no** snackbar appears.

4. **Single-Snackbar Replacement**
   - Rename an item, then immediately (within 1–2s) delete another item → confirm the first snackbar disappears immediately and only the second snackbar is visible, staying on screen for the full configured duration.

5. **General Global Settings Configuration & Persistence**
   - Open **Global Settings → General** (`Ctrl+,` / `⌘+,`).
   - Verify **Snackbar Auto-Dismiss Duration** defaults to `5` seconds.
   - Enter an invalid value (`0` or `999`) → confirm red validation border and value is not applied.
   - Change to `10` seconds, close Settings, rename an item → confirm snackbar stays visible for ~10 seconds.
   - Restart Clapi → open Global Settings → General → confirm `10` seconds persisted.

6. **Documentation Check**
   - Open `docs/settings/settings.md` and confirm the new **Snackbar Auto-Dismiss Duration** setting (default 5s, 1–60s) is documented under General settings.
