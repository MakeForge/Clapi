# Feature Specification: Fix Desktop UI High-Severity Violations

**Feature Branch**: `020-fix-desktop-ui-violations`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "Fixing all the High Severity violations" (based on `scratch/composables_evaluation.md`)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Desktop Scrollbar Visibility & Direct Scrolling (Priority: P1)

As an API client desktop user working with long payloads, response bodies, headers, log feeds, and configuration dialogs, I want visible, mouse-draggable desktop scrollbars on all scrollable views so that I can immediately see my relative position within long content and quickly drag or jump to any section.

**Why this priority**: Without visual desktop scrollbars on scrollable containers, users cannot tell if content extends beyond the view boundary, cannot drag-scroll with a mouse, and lose their visual anchor within long lists (e.g. response bodies, console logs, environment variables, settings).

**Independent Test**: Open a long response body, console log feed, or settings dialog. Verify that a vertical scrollbar thumb is visible, highlights on hover, tracks scrolling, and can be clicked and dragged to reposition the view.

**Acceptance Scenarios**:

1. **Given** a response with content exceeding the visible viewer height, **When** the response viewer is displayed, **Then** a visible vertical desktop scrollbar appears on the right edge, matching the current scroll offset.
2. **Given** a scrollable panel or dialog (e.g., authorization editor, body editor, settings dialog, console logs, timing breakdown), **When** content overflows the container bounds, **Then** a desktop scrollbar is rendered and responds to mouse click-and-drag interactions.
3. **Given** a horizontal list of request tabs that overflows the top bar width, **When** the user views the tab strip, **Then** an intuitive horizontal scroll indicator and interaction affordance is provided.

---

### User Story 2 - Reliable Button Clicks & Contextual Tooltip Feedback (Priority: P1)

As a desktop user interacting with editor toolbars, search bars, and window headers, I want every icon button to reliably register click actions without dropped events or hover interference, while still displaying helpful tooltip hints when hovering.

**Why this priority**: When button hover handlers swallow pointer events, users experience unresponsive buttons or missed clicks, creating high frustration during rapid edit-and-test workflows.

**Independent Test**: Hover over toolbar and editor action buttons, observe the hover tooltip, and immediately click the button. Verify the action triggers on the first click without dropped events or delayed responsiveness.

**Acceptance Scenarios**:

1. **Given** an action button in the code editor toolbar (e.g., format, word wrap, search, replace), **When** the user hovers over it, **Then** a tooltip appears describing the action after the standard delay.
2. **Given** a hovered action button in the code editor or main toolbar, **When** the user clicks the button, **Then** the click event triggers reliably on the first attempt without getting swallowed by hover detection layers.
3. **Given** any interactive icon button with a tooltip across toolbars and editors, **When** the user moves the pointer away, **Then** the tooltip cleanly dismisses without lingering or obstructing underlying workspace content.

---

### User Story 3 - Flexible Workspace Layout Adaptability & Dialog Constraint Control (Priority: P2)

As a desktop user who resizes workspace panes, toggles sidebars, or docks tool panels, I want all primary workspace panels, editors, and dialogs to adapt cleanly to container size constraints and host layout modifiers without content clipping or rigid sizing.

**Why this priority**: Host containers and split panes must be able to pass sizing, padding, and layout constraints to nested panels. Missing layout pass-through makes components unconfigurable and leads to layout glitches upon window resize.

**Independent Test**: Resize split panes and window dimensions. Verify that all workspace panels (Request Editor, Response Viewer, Bottom Panel, Auth Editor, Collection Editor) smoothly fill available space and properly respect container paddings and constraints.

**Acceptance Scenarios**:

1. **Given** any top-level workspace panel or editor (e.g., Request Editor, Response Viewer, Bottom Panel, Auth Editor), **When** hosted within a parent layout container, **Then** it accepts external layout styling, padding, and sizing constraints from the caller and applies them to its outermost boundary.
2. **Given** any modal dialog (e.g., Delete Confirmation, Environment Edit, Settings, Git Sync), **When** presented on screen, **Then** it accepts container sizing constraints, centers appropriately, and scales within designated minimum and maximum boundary limits.
3. **Given** an automated UI test targeting any primary panel or dialog, **When** the test passes a layout identifier or test tag via container configuration, **Then** the container exposes that identifier cleanly on its root element.

---

### User Story 4 - Predictable Control Configuration & Parameter Ordering (Priority: P3)

As a UI component developer and maintainer, I want split panes and reusable visual badges to follow standardized parameter ordering where mandatory inputs precede optional styling modifiers, so that components integrate smoothly across all workspace views.

**Why this priority**: Inconsistent parameter conventions increase caller errors, make optional modifier overrides awkward, and violate established UI component guidelines.

**Independent Test**: Use split pane and badge components across various host screens, verifying that mandatory configuration parameters are required first and layout modifiers default cleanly without boilerplate.

**Acceptance Scenarios**:

1. **Given** a split pane layout component, **When** instantiated by a screen, **Then** required configuration (split position and change listener) is provided first, followed by optional layout modifiers.
2. **Given** a method badge component, **When** customized by a caller, **Then** the layout modifier can be supplied immediately as the first optional parameter.

---

### Edge Cases

- **Zero or Minimal Content Overflow**: When panel content fits entirely within the visible container without scrolling, scrollbars should either remain hidden or idle unobtrusively without occupying interactive screen space.
- **Rapid Clicking While Hovering**: Rapidly clicking an icon button during tooltip reveal animation must trigger the action every time without delay or event suppression.
- **Extreme Window Resizing**: When the application window is shrunk to minimal dimensions, panels and dialogs must not crash or trigger layout infinite loops; scrollbars should expand their thumb travel accordingly.
- **Nested Scroll Containers**: In editors where both the outer panel and inner code block have scrollable content, scrolling gestures must route to the intended target container without conflicts.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide visible, mouse-interactive desktop scrollbars on all scrollable workspace editors, panels, and dialog content areas whenever content exceeds visible bounds.
- **FR-002**: All scrollable lists and text views (including response bodies, response headers, cookies, console logs, test results, timing breakdowns, and settings sections) MUST display vertical scrollbar indicators aligned with content scroll state.
- **FR-003**: The request tabs bar MUST provide horizontal scrollability with clear visual scroll indicators when open tabs exceed the horizontal bar width.
- **FR-004**: Icon buttons with hover tooltips in all toolbars and editors MUST dispatch click events immediately and reliably without interference or event interception from hover detection mechanisms.
- **FR-005**: All tooltip implementations across toolbars and editors MUST follow a unified, centralized tooltip behavior and presentation model.
- **FR-006**: Every top-level workspace panel, editor view, toolbar, and modal dialog MUST accept an optional root layout configuration parameter as its first optional argument.
- **FR-007**: When an optional root layout parameter is passed to any workspace panel or dialog, the component MUST apply that parameter directly to its top-level layout element.
- **FR-008**: Modal dialog components MUST accept outer layout configuration constraints to allow predictable width, height, and accessibility bounds.
- **FR-009**: Split pane components MUST position all mandatory configuration parameters before optional layout modifier parameters.
- **FR-010**: Reusable badge and leaf components MUST accept layout modifiers as their first optional parameter.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of scrollable panels and dialogs identified in the high-severity evaluation display a functional desktop scrollbar when content overflows container bounds.
- **SC-002**: 0% click drop rate on icon buttons across all toolbars and editors during continuous rapid clicking and hover interactions.
- **SC-003**: 100% of top-level workspace panels, editors, and dialogs accept and apply root layout configuration without hardcoded sizing defaults.
- **SC-004**: Users can navigate, visually inspect scroll positions, and drag scrollbars across long responses, logs, and settings without relying solely on mouse wheel input.
- **SC-005**: All high-severity violations documented in the desktop composable audit are resolved without introducing regressions to existing workspace workflows.

## Assumptions

- Desktop JVM is the primary execution platform, requiring standard mouse and physical keyboard desktop ergonomics.
- Scrollbar appearance and behavior should match the existing dark/light theme styling defined in the application design system.
- Component callers will continue to manage domain state (such as tab state, settings state, and dialog state), while layout modifiers control purely visual and spatial properties.
- Medium and low-severity improvements (such as file decomposition and non-critical localization extractions) will be handled in separate dedicated phases to maintain focused, verifiable changes.
