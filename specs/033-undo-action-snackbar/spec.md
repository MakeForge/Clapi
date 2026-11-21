# Feature Specification: Undoable Action Snackbar

**Feature Branch**: `033-undo-action-snackbar`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "For some actions it would be good to show a snackbar in the bottom right corner, that briefly states what was done and have a undo button. It should be auto-dismissed after 5 seconds. If a snackbar should be shown while another snackbar is already shown, the old one is immediately dismissed, so that never 2 snackbars are shown at the same time. Keep the implementation easily maintainable in the future to extend it. Also in the General Global settings, the user should be able to change the auto-dismiss time. Actions for this to show snackbars:
    - requested/folder/items in general are deleted
    - any items were moved
    - Renames"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Undo Item Deletion via Bottom-Right Snackbar (Priority: P1)

When a user deletes a request, folder, environment, or other deletable workspace item, a transient snackbar appears in the bottom-right corner of the application window stating briefly what was deleted and providing an **Undo** button. If the user realizes the deletion was accidental, they can click **Undo** before the snackbar auto-dismisses (5 seconds by default) to immediately restore the deleted item—including all nested contents if a folder was deleted—to its original location.

**Why this priority**: Accidental deletion is the highest-impact destructive action in daily workflow; giving users an immediate, one-click recovery mechanism prevents data loss and delivers the core value of the feature.

**Independent Test**: Delete a request and a non-empty folder, verify the bottom-right snackbar appears with a concise description and an Undo button, click Undo before the timeout expires, and confirm the deleted item (and all nested items for a folder) is restored intact and the snackbar disappears immediately.

**Acceptance Scenarios**:

1. **Given** a collection contains a request named "Get Users", **When** the user deletes "Get Users", **Then** a snackbar appears in the bottom-right corner stating that "Get Users" was deleted and displaying an **Undo** button.
2. **Given** the deletion snackbar for "Get Users" is visible, **When** the user clicks **Undo** before the auto-dismiss timer expires, **Then** "Get Users" is restored to its original location with its previous contents and the snackbar is immediately dismissed.
3. **Given** a folder containing nested folders and requests is deleted, **When** the user clicks **Undo** on the snackbar, **Then** the folder and all of its nested folders and requests are restored in their original hierarchy and order.
4. **Given** an item is deleted and the snackbar is displayed, **When** the user takes no action for the configured auto-dismiss duration (default 5 seconds), **Then** the snackbar automatically disappears.

---

### User Story 2 - Undo Item Move and Rename Actions (Priority: P1)

When a user moves any item (such as dragging or moving a request or folder to a different folder or reordering it) or renames an item (such as a request, folder, environment, or other renameable item), a snackbar appears in the bottom-right corner summarizing what changed along with an **Undo** button. Clicking **Undo** reverts the move or rename cleanly and dismisses the snackbar.

**Why this priority**: Moves and renames are frequent organizational actions that are easy to trigger accidentally (especially via drag-and-drop or quick inline edits); pairing them with the same undoable snackbar completes the initial set of required user actions.

**Independent Test**: Move a request into another folder and click Undo to confirm it returns to its original parent and position; rename a request or folder and click Undo to confirm its previous name is restored.

**Acceptance Scenarios**:

1. **Given** a request "Create Order" is located in folder "Orders", **When** the user moves "Create Order" to folder "Archive", **Then** a bottom-right snackbar appears briefly stating the move and offering an **Undo** button.
2. **Given** the move snackbar is visible, **When** the user clicks **Undo**, **Then** "Create Order" returns to folder "Orders" in its original position and the snackbar is immediately dismissed.
3. **Given** an item named "User List" exists, **When** the user renames it to "Active Users", **Then** a bottom-right snackbar appears stating the rename and offering an **Undo** button.
4. **Given** the rename snackbar is visible, **When** the user clicks **Undo**, **Then** the item's name reverts to "User List" and the snackbar is immediately dismissed.
5. **Given** a user commits a rename without changing the name, or drops an item back in its exact original location, **When** the action completes, **Then** no snackbar is shown because no change occurred.

---

### User Story 3 - Single Active Snackbar Replacement (Priority: P2)

When a user performs multiple actions in quick succession, only one snackbar is ever visible at a time. Triggering a new snackbar while another is still visible immediately dismisses the old snackbar and displays the new one with a fresh auto-dismiss countdown.

**Why this priority**: Rapid edits (such as deleting or renaming several items in a row) are common; preventing stacked or overlapping snackbars keeps the workspace clean, predictable, and uncluttered.

**Independent Test**: Trigger two eligible actions within 2 seconds (e.g., rename an item, then delete another item), and verify that the first snackbar disappears immediately as the second appears, never showing two snackbars at once, and that the second snackbar remains visible for the full configured duration.

**Acceptance Scenarios**:

1. **Given** a snackbar for Action A is currently visible in the bottom-right corner, **When** the user performs Action B (which also triggers a snackbar), **Then** Action A's snackbar is immediately dismissed and replaced by Action B's snackbar.
2. **Given** Action A's snackbar had 1 second remaining when Action B replaced it, **When** Action B's snackbar appears, **Then** the auto-dismiss timer restarts for the full configured duration (default 5 seconds) rather than inheriting Action A's remaining time.
3. **Given** the user performs five eligible actions rapidly in succession, **When** observing the bottom-right corner throughout, **Then** at no point are two or more snackbars visible simultaneously.

---

### User Story 4 - Configure Auto-Dismiss Duration in General Global Settings (Priority: P2)

In the **General** section of **Global Settings**, the user can adjust how long action snackbars stay visible before auto-dismissing. This lets users who prefer more time to react (or quicker dismissal) tailor the experience to their workflow.

**Why this priority**: Different users have different reaction speeds and workspace preferences; making the timeout configurable in General Global Settings directly addresses user control without cluttering the snackbar itself.

**Independent Test**: Open Global Settings > General, change the snackbar auto-dismiss duration from 5 seconds to 10 seconds, perform a rename or delete action, verify the snackbar stays visible for ~10 seconds, restart the application, and confirm the 10-second setting is preserved.

**Acceptance Scenarios**:

1. **Given** a fresh installation with default preferences, **When** the user opens Global Settings and navigates to the General section, **Then** a snackbar auto-dismiss duration setting is displayed with a default value of 5 seconds.
2. **Given** the user changes the snackbar auto-dismiss duration to 8 seconds in General Global Settings, **When** the user performs a delete, move, or rename action, **Then** the resulting snackbar remains visible for 8 seconds before auto-dismissing (without requiring an app restart).
3. **Given** the user has changed the snackbar auto-dismiss duration to 8 seconds, **When** the user closes and reopens the application, **Then** the setting remains 8 seconds and subsequent snackbars use the 8-second duration.
4. **Given** the user attempts to enter an out-of-range or invalid value (such as 0, a negative number, or a value above the maximum bound), **When** saving or applying the setting, **Then** the system rejects or clamps the input to the valid range (1 to 60 seconds) and preserves a valid duration.

---

### User Story 5 - Extensible Action Notification Capability (Priority: P3)

The snackbar notification capability is organized as a single, centralized, extensible mechanism so future features and actions (both undoable and informational) can display bottom-right snackbars consistently without duplicating display, timing, or single-snackbar replacement behavior.

**Why this priority**: Ensuring maintainability and extensibility prevents fragmented notification behavior as new actions are added to the application over time.

**Independent Test**: Verify that adding a new snackbar-triggering action requires only supplying an action description and an optional undo operation to the centralized notification mechanism, automatically inheriting bottom-right placement, configurable auto-dismiss timing, manual dismissal, and single-snackbar replacement.

**Acceptance Scenarios**:

1. **Given** a new action is connected to the centralized snackbar mechanism with a message and an undo handler, **When** that action executes, **Then** it automatically appears in the bottom-right corner with an **Undo** button, respects the user's configured auto-dismiss time, and replaces any currently visible snackbar.
2. **Given** a future action is connected to the centralized snackbar mechanism without an undo handler, **When** that action executes, **Then** the snackbar displays the message without an **Undo** button while still adhering to the same placement, auto-dismiss, and replacement rules.

---

### Edge Cases

- **Folder deletion with nested content**: Undoing deletion of a folder containing multiple levels of subfolders and requests restores the entire subtree, preserving item contents and ordering.
- **Repeated clicks on Undo**: Clicking the **Undo** button multiple times in rapid succession executes the undo operation only once and does not create duplicate items or corrupt state.
- **Undo failure due to external or conflicting changes**: If an undo cannot be completed (for example, the original parent folder was removed externally or another item with the same name/path already exists), the system does not crash or corrupt existing data and informs the user that the undo could not be completed.
- **Long item names**: When a deleted, moved, or renamed item has a very long name, the snackbar message truncates gracefully so the snackbar remains compact and the **Undo** button is never pushed off-screen or obscured.
- **Manual dismissal**: Users can dismiss an active snackbar early (before the timeout elapses) without triggering Undo.
- **Timer expiration race with Undo click**: If the auto-dismiss timer expires at the same instant the user clicks Undo, the operation either completes cleanly once or is ignored cleanly—never partially applied or executed twice.
- **No-op edits**: Renaming an item to its current name or moving an item to its current position does not trigger a snackbar.
- **Window resizing**: Resizing the application window keeps the active snackbar anchored to the bottom-right corner with consistent padding and full visibility.
- **Deleting an active or open item**: If a deleted item was open or selected in the workspace, undoing the deletion restores the item cleanly without leaving broken references or stale state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a transient snackbar anchored in the bottom-right corner of the main application window when a supported action completes.
- **FR-002**: Each snackbar MUST show a brief, clear summary of the completed action (identifying what was done and the affected item) and an **Undo** button whenever the action is undoable.
- **FR-003**: System MUST automatically dismiss an active snackbar after the configured auto-dismiss duration elapses, with a default duration of 5 seconds.
- **FR-004**: System MUST enforce a strict single-snackbar policy: when a new snackbar is requested while another snackbar is already visible, the previous snackbar MUST be immediately dismissed and replaced by the new snackbar with a reset auto-dismiss timer, so two snackbars are never shown at the same time.
- **FR-005**: System MUST immediately dismiss the active snackbar when the user clicks its **Undo** button and execute the associated undo operation at most once.
- **FR-006**: System MUST allow the user to manually dismiss an active snackbar before the auto-dismiss timer expires without triggering the undo action.
- **FR-007**: System MUST display an undoable snackbar when requests, folders, environments, or other deletable workspace/collection items are deleted; undoing MUST restore the deleted item (including all nested items for deleted folders) to its original location and state.
- **FR-008**: System MUST display an undoable snackbar when any item is moved (across containers/folders or reordered within a container); undoing MUST restore the item to its original parent and position.
- **FR-009**: System MUST display an undoable snackbar when any item (request, folder, environment, or other renameable item) is renamed; undoing MUST restore the item's previous name.
- **FR-010**: System MUST NOT display a snackbar for no-op actions (such as renaming an item to its identical current name or moving an item to its identical current location).
- **FR-011**: System MUST provide a configurable **Snackbar Auto-Dismiss Duration** setting (measured in seconds, defaulting to 5 seconds) in the **General** section of **Global Settings**.
- **FR-012**: System MUST constrain the auto-dismiss duration setting to a valid range of 1 to 60 seconds, persist the user's preference across application restarts, and apply updates immediately to subsequent snackbars without requiring a restart.
- **FR-013**: System MUST handle undo failures gracefully (e.g., naming conflicts or externally altered state) without crashing or corrupting data, and inform the user when an undo cannot be completed.
- **FR-014**: System MUST truncate excessively long item names within the snackbar message so the snackbar maintains a compact footprint and keeps the **Undo** button accessible.
- **FR-015**: System MUST centralize snackbar presentation, timing, dismissal, and replacement behind a single extensible notification mechanism so future actions (undoable or informational) can be added cleanly without duplicating snackbar logic.

### Key Entities

- **Action Notification**: A transient feedback entry representing a completed user action. Attributes include a concise summary message, an optional undo action label and reversible operation, and an active/dismissed lifecycle.
- **Undoable Action Record**: The captured inverse information required to revert a specific action—such as a deleted item snapshot (including nested children and original position), original container and index for a moved item, or previous name for a renamed item.
- **Snackbar Auto-Dismiss Preference**: A persistent global user setting stored under General Global Settings representing the auto-dismiss timeout in seconds (default: 5, valid range: 1–60).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users see the bottom-right snackbar within 200 milliseconds of completing any delete, move, or rename action.
- **SC-002**: Users can undo an accidental delete, move, or rename with a single click, restoring 100% of the affected item's prior state (including nested folder contents, original location, or previous name).
- **SC-003**: Across rapid or back-to-back action sequences, 0% of interactions result in two or more snackbars appearing simultaneously.
- **SC-004**: When not interacted with, snackbars auto-dismiss within ±0.25 seconds of the configured duration (default 5 seconds).
- **SC-005**: Users can locate and update the snackbar auto-dismiss duration in General Global Settings in under 15 seconds, and the updated preference persists across 100% of application restarts.
- **SC-006**: Extending snackbar feedback to a new user action requires only supplying the message and optional undo handler to the centralized notification mechanism, with zero duplicated timing, positioning, or replacement logic.

## Assumptions

- **Single-level snackbar undo**: Only the currently visible snackbar's action can be undone via the snackbar UI. Once a snackbar is dismissed (by timeout, manual close, or replacement by a newer snackbar), its undo button is no longer available. A full multi-step application-wide undo/redo history stack (e.g., Ctrl+Z history across sessions) is out of scope for this feature.
- **Scope of "items in general"**: Covers deletable, movable, and renameable items managed within the workspace (such as requests of all supported protocols, folders, environments, and similar collection items). Permanently deleting an entire external collection directory from disk is excluded from snackbar undo unless handled as a workspace reference removal.
- **Valid auto-dismiss range**: A range of 1 to 60 seconds (integer seconds) is a reasonable default that accommodates both quick dismissal and accessibility needs while preventing unusable values like 0 or infinite timers.
- **Non-blocking overlay**: The bottom-right snackbar is non-modal and does not block keyboard input or mouse interactions with the rest of the workspace.
- **Desktop primary target**: Positioning and layout assume the desktop application window, maintaining comfortable margin from the bottom and right edges so it does not obscure primary navigation or status controls.
