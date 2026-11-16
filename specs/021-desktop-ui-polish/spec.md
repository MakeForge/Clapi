# Feature Specification: Desktop UI Polish & Modularization

**Feature Branch**: `021-desktop-ui-polish`

**Created**: 2026-11-14

**Status**: Ready for Planning

**Input**: User description: "all scratch/composables_evaluation.md medium and low severity suggestions"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Effortless Dialog Dismissal via Keyboard Navigation (Priority: P1)

As a keyboard-centric desktop user, I want all modal dialogs to close immediately when I press the `Escape` key, so that I can quickly dismiss prompts and return to my primary workflow without reaching for the mouse or hunting for a close button.

**Why this priority**: Fast, predictable dialog dismissal via the `Escape` key is a fundamental desktop ergonomics standard. Forcing users to click small cancel buttons or outer scrims degrades keyboard navigation speed.

**Independent Test**: Can be fully tested by opening any desktop modal dialog (Settings, Environment Edit, Confirm Delete, Git Sync, Import/Export, Error, Help/About, Progress) and pressing `Escape`; the dialog dismisses cleanly without executing destructive actions.

**Acceptance Scenarios**:

1. **Given** any modal dialog is currently visible, **When** the user presses `Escape`, **Then** the dialog dismisses and returns focus to the underlying workspace.
2. **Given** a modal dialog with an open internal dropdown or autocomplete popup is visible, **When** the user presses `Escape`, **Then** only the popup/dropdown collapses on the first press, and a second `Escape` press dismisses the dialog.
3. **Given** an operation in progress (e.g. Git Sync or Import) has a non-cancellable blocking state, **When** the user presses `Escape`, **Then** the dismiss action is ignored or prompts confirmation according to the dialog's dismissibility policy.

---

### User Story 2 - Clear Cursor Affordances & Pointer Feedback (Priority: P2)

As a mouse/trackpad user, I want interactive surfaces, table rows, dropdown chips, and copy buttons to display a pointing hand cursor on hover, so that I immediately recognize which elements are clickable without trial and error.

**Why this priority**: Desktop interfaces rely on cursor shape to convey affordances. When clickable table rows, cards, or custom dropdowns retain a standard arrow or text caret cursor, users cannot distinguish interactive controls from static content.

**Independent Test**: Hover the pointer over key-value rows, console log entries, environment selector chips, method badges, token copy buttons, setting rows, and tabs; verify the system cursor instantly changes to a pointing hand.

**Acceptance Scenarios**:

1. **Given** interactive table rows or list cards (e.g., key-value rows, console rows, protobuf file rows, global variable rows), **When** the mouse hovers over them, **Then** the cursor changes to a pointing hand.
2. **Given** custom clickable chips, toggles, and dropdown triggers (e.g., method selector, environment chip, settings toggles, response viewer tab bar), **When** hovered, **Then** the cursor displays the hand icon.
3. **Given** copyable token fields and file picker cells, **When** hovered over their clickable regions, **Then** the cursor displays the pointing hand icon.

---

### User Story 3 - Smooth List Scrolling & State Stability via Unique Keys (Priority: P3)

As an active user streaming logs, inspecting headers, or configuring dozens of environment variables, I want virtualized lists to retain their scroll position, focus, and state during dynamic updates, so that content updates smoothly without flickering or jumping.

**Why this priority**: Virtualized lists without stable unique keys rebuild items on every change or addition, losing intra-item state, triggering redundant layout passes, and causing visual stuttering during high-frequency log streams or real-time message feeds.

**Independent Test**: Populate console logs, response headers and cookies, or environment secrets with items; trigger updates or additions; verify items animate and update stably without scroll hitching or state recreation.

**Acceptance Scenarios**:

1. **Given** a high-frequency console log stream or test results view in the bottom panel, **When** new entries arrive, **Then** existing entries retain their layout state and item keys prevent re-rendering unchanged items.
2. **Given** a list of response headers or cookies in the response viewer, **When** viewing the list, **Then** each item has a unique, deterministic key derived from its header/cookie identity.
3. **Given** working environment variables or secrets in the environment edit dialog, **When** items are reordered, added, or deleted, **Then** the input fields and toggles retain their respective row states without cross-contamination.

---

### User Story 4 - Visual Polish through Theme Tokens & Centralized Strings (Priority: P4)

As a user working across light and dark display modes or with internationalization, I want all UI text to be properly localized and all interface elements to use semantic theme colors, so that there are no unreadable hardcoded white texts or jarring off-palette colors in either mode.

**Why this priority**: Hardcoded hex colors and static white text break theme adaptability, causing poor contrast in light theme and inconsistent highlights. Hardcoded string literals prevent localization and maintainability.

**Independent Test**: Switch between dark and light themes across auth editor, GraphQL panel, WebSocket editor, gRPC editor, and response viewer; verify all text, borders, and status indicators adapt to theme tokens with sufficient contrast and all user-facing strings are resolved from the central string catalog.

**Acceptance Scenarios**:

1. **Given** light or dark theme active, **When** viewing status badges, timing phases, connection indicators, and error banners, **Then** colors adapt dynamically using semantic theme palette tokens rather than hardcoded hex codes.
2. **Given** button labels, dialog titles, placeholders, and tooltips across all dialogs and editors, **When** rendered, **Then** all user-visible text is resolved from the centralized string catalog.

---

### User Story 5 - Modular Component Architecture & File Sizing Decomposition (Priority: P5)

As an engineer maintaining and extending Clapi, I want oversized monolithic files (>1,000 lines) and monolithic components (>100 lines) to be decomposed into dedicated subpackages and focused sub-components, so that the codebase remains clean, readable, easily testable, and maintainable.

**Why this priority**: Extreme file sizes (such as the environment edit dialog at 3,153 lines, sidebar at 1,349 lines, settings dialog at 1,599 lines, and MCP panel at 1,658 lines) create cognitive overload, cause frequent merge conflicts, and hinder automated UI testing and preview tooling.

**Independent Test**: Inspect the component file tree and verify that monolithic components are divided into focused modules under dedicated subpackages, with no single component file exceeding 1,000 lines, while preserving all existing functionalities and public APIs.

**Acceptance Scenarios**:

1. **Given** monolithic dialogs (environment edit dialog, settings dialog), **When** refactored, **Then** each settings tab, secret provider row, and dialog section lives in a dedicated file within a clean subpackage.
2. **Given** monolithic panels (sidebar, MCP panel, WebSocket editor, code editor, collection editor), **When** refactored, **Then** their sub-panes and toolbars are extracted into dedicated sub-components.
3. **Given** duplicate tooltip button logic in the top toolbar and code editor, **When** refactored, **Then** both reuse the unified tooltip action button component.

---

### Edge Cases

- **Nested Key Events**: When a text field or search bar inside a modal dialog has focus, typing characters or navigation keys must not inadvertently trigger dialog dismissal; only an explicit `Escape` press bubbles up to dismiss.
- **Dirty State on Escape**: When a dialog has unsaved dirty changes (e.g. edited environment variables or unsaved request tab), pressing `Escape` must trigger the dirty-close confirmation prompt rather than silently discarding changes.
- **Duplicate Keys in Virtualized Lists**: In response headers or logs where identical key names or messages may appear, the key selector must combine name, value, index, or timestamp to guarantee absolute uniqueness and prevent runtime crashes.
- **Cursor State during Disabled Actions**: When an action button, chip, or row is disabled, the pointer hover icon must fall back to the default arrow cursor rather than showing the pointing hand.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST intercept the `Escape` key on all modal dialogs to dismiss the dialog safely.
- **FR-002**: System MUST confirm with the user before dismissing on `Escape` if the dialog contains unsaved dirty state.
- **FR-003**: System MUST provide pointing hand cursor feedback on all clickable table rows, cards, custom buttons, tabs, chips, and dropdown toggles.
- **FR-004**: System MUST supply stable, unique keys to all items and indexed items in virtualized lists and table views.
- **FR-005**: System MUST replace raw hardcoded user-visible string literals in evaluated components with entries in the centralized string localization catalog.
- **FR-006**: System MUST replace raw hardcoded colors with semantic tokens from the application theme palette.
- **FR-007**: System MUST refactor monolithic UI files exceeding 1,000 lines into structured subpackages with focused single-responsibility files.
- **FR-008**: System MUST decompose oversized user interface components exceeding 100 lines into cohesive, independently previewable child components.
- **FR-009**: System MUST deduplicate custom tooltip button wrappers across the workspace by standardizing on a unified tooltip action button component.

---

### Key Entities

- **Modal Dialog Scope**: The root container of any desktop dialog that handles keyboard focus, back/escape interception, and dismissal lifecycle.
- **Interactive Cursor Affordance**: Hover modifier specifying pointing hand cursor behavior for desktop pointing devices.
- **Lazy Item Identity**: A deterministic string or integer key uniquely identifying a virtualized list row across dataset mutations.
- **Semantic Theme Palette**: The centralized color token set dynamically resolving colors for light, dark, and high-contrast environments.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of modal dialogs in the application close immediately or show a discard confirmation upon pressing the `Escape` key.
- **SC-002**: 100% of clickable rows, chips, and custom controls across evaluated components exhibit pointing hand cursor feedback on hover.
- **SC-003**: 0 instances of un-keyed virtualized list item rows remain across the shared UI module.
- **SC-004**: 0 raw hex color literals or static color constants remain in component presentation code; all colors resolve via the theme system.
- **SC-005**: 100% of user-visible text in evaluated dialogs and editors is localized through the string resource catalog.
- **SC-006**: 0 UI component source files in the shared UI module exceed 1,000 lines of code after decomposition.
- **SC-007**: Full test suite passes and project compiles cleanly with zero desktop UI regressions.

---

## Assumptions

- Keyboard dismissal via `Escape` adheres to desktop key event propagation standards.
- Localization strings added to the catalog will use English defaults while supporting multi-language bundle lookups.
- File splitting and decomposition preserve existing public API signatures, imports, and state bindings without behavioral changes.
- The unified tooltip action button component meets all latency and layout requirements for editor and toolbar action buttons.
