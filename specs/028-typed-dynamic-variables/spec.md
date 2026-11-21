# Feature Specification: Typed Dynamic Variables & Variable Editor UI Modernization

**Feature Branch**: `028-typed-dynamic-variables`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "Leverage VariableType for secret, dynamic and static variables to specify the data type (string, number, boolean, object), eliminate DYNAMIC from VariableType, rework variable editor with selection boxes for both variable kind and variable type, fix code editor live JSON validation for unquoted variables (preventing 'Expected property name' errors on valid templates), and update documentation."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define Variable Kind and Data Type via Selection Boxes (Priority: P1)

As an API developer configuring workspace, environment, collection, or folder variables, I want to explicitly select both the variable kind (Regular, Secret, Dynamic) and the variable data type (String, Number, Boolean, Object) using explicit selection controls so that I can configure how my variables behave and what data format they produce without ambiguous click-cycling.

**Why this priority**: Core usability and modeling foundation. Separating variable execution kind from data type eliminates conceptual confusion and allows the application to understand the intended data format of every variable across all scopes.

**Independent Test**: Open the Environment Editor, create a new variable, use the kind selection box to choose "Dynamic", use the type selection box to choose "Number", configure a script returning `42`, and verify the variable saves with kind=Dynamic and type=Number.

**Acceptance Scenarios**:

1. **Given** the variable editor table in an environment, collection, or folder dialog, **When** viewing a variable row, **Then** the variable kind is displayed and configurable via a selection dropdown offering options: "Regular", "Secret", and "Dynamic".
2. **Given** a variable row in the variable table, **When** the user opens the type selection control, **Then** a dropdown displays the available data types: "String", "Number", "Boolean", and "Object".
3. **Given** a variable configured as "Dynamic", **When** opening the Dynamic Variable Editor dialog, **Then** the dialog displays a data type selector showing its declared return type, allowing the user to change it to String, Number, Boolean, or Object.
4. **Given** an exported or saved collection or environment, **When** inspected in storage format, **Then** all variables persist their data type attribute cleanly without conflating kind with data type.

---

### User Story 2 - Template-Aware JSON Request Body Validation in Code Editor (Priority: P1)

As an API developer drafting a JSON request body containing dynamic or environment variable references, I want the code editor's live validator to accept unquoted template variable expressions that resolve to non-string JSON values (such as numbers, booleans, or objects) without false syntax errors so that I can write natural JSON templates with confidence.

**Why this priority**: Eliminates a severe false-positive syntax error that causes user friction and confusion when composing valid numeric or boolean template expressions (e.g. `{ "count": {{numberVar}} }` or `{ "timestamp": {{$timestamp}} }`).

**Independent Test**: Enter `{"x": {{numberVar}} }` in the JSON request body editor where `numberVar` is typed as Number (or built-in `{{$timestamp}}`). Verify that the editor reports 0 syntax errors instead of "Expected property name".

**Acceptance Scenarios**:

1. **Given** a JSON request body containing an unquoted variable token in a value position (e.g., `{"x": {{myNumber}} }`), **When** `myNumber` has a declared data type of Number, **Then** the code editor reports zero syntax errors and highlights the variable token as valid.
2. **Given** a JSON request body containing a built-in numeric dynamic variable (e.g., `{"createdAt": {{$timestamp}} }` or `{"id": {{$randomInt}} }`), **When** validated by the editor, **Then** the editor accepts the template syntax without errors.
3. **Given** a JSON request body containing an unquoted variable with data type Boolean or Object (e.g., `{"active": {{myBool}} }` or `{"profile": {{myObj}} }`), **When** validated by the editor, **Then** the editor accepts the expression without syntax errors.
4. **Given** a JSON request body containing an unquoted variable whose declared data type is String (e.g., `{"date": {{nowDate}} }`), **When** validated by the editor, **Then** the editor produces an informative diagnostic explaining that string variables must be enclosed in double quotes (`"{{nowDate}}"`).
5. **Given** actual syntax errors in a JSON template (such as missing colons, unclosed braces, or trailing commas when JSON5 is disabled), **When** validated, **Then** genuine syntax errors are still accurately detected and reported at the correct line and column.

---

### User Story 3 - Valid Wire Transmission of Typed Variable Bodies (Priority: P2)

As an API consumer sending requests with typed variables in JSON bodies, I want the request execution pipeline to serialize unquoted variables into valid RFC 8259 JSON numbers, booleans, or objects so that the target API server receives native JSON types rather than strings.

**Why this priority**: Validates that end-to-end execution correctly preserves types across the wire and fulfills the user's intent when designing request payloads.

**Independent Test**: Execute an HTTP request with body `{"count": {{numberVar}}, "active": {{boolVar}} }` against a test server and assert the server deserializes `count` as an integer number `42` and `active` as boolean `true`.

**Acceptance Scenarios**:

1. **Given** a request with an unquoted dynamic variable returning a number (e.g., `42`), **When** sent, **Then** the wire JSON contains the raw number literal and the server receives the field as a numeric type.
2. **Given** a request with an unquoted dynamic variable returning a boolean or object, **When** sent, **Then** the wire JSON contains the boolean literal (`true`/`false`) or JSON object, and the server successfully parses it.
3. **Given** a request with a quoted variable (e.g., `"{{numberVar}}"`), **When** sent, **Then** the value is transmitted as a JSON string `"42"`.

---

### User Story 4 - Complete Documentation & Authoring Guidance (Priority: P3)

As an API developer reading Clapi's documentation, I want clear guides explaining the difference between variable kinds and variable data types, how to author unquoted versus quoted JSON templates, and how the editor guides template composition so that I can effectively use dynamic and typed variables.

**Why this priority**: User education ensures developers understand when to use quotes versus unquoted templates in JSON and how to take full advantage of the updated variable editor.

**Independent Test**: Navigate to the Dynamic Variables and Environment documentation pages and verify the updated Kind and Type selection box documentation, JSON body template examples with both quoted strings and unquoted numeric/boolean values, and explanations of editor diagnostics.

**Acceptance Scenarios**:

1. **Given** the documentation page for Dynamic Variables, **When** reviewed, **Then** it accurately documents the new Kind selection box (Regular, Secret, Dynamic) and Type selection box (String, Number, Boolean, Object).
2. **Given** the documentation page, **When** explaining JSON request body authoring, **Then** it clearly explains that numeric, boolean, and object variables can be unquoted (`{"count": {{count}}}`), while string variables must be quoted (`{"name": "{{name}}"}`).
3. **Given** the Environment Variables documentation, **When** reviewed, **Then** screenshots, tables, and instructions reflect the selection boxes in the variable editor table.

---

### Edge Cases

- **Unknown or Untyped Variables in Unquoted Positions**: If an unquoted variable token is referenced in JSON whose type cannot be determined (e.g., an ad-hoc token without an active environment variable definition), the editor linter assumes it can be a valid template placeholder rather than crashing or claiming "Expected property name", while warning that the variable is unresolved.
- **Dynamic Script Returning Mismatched Type**: If a dynamic variable's type is declared as `Number` but the script evaluation returns a non-numeric string (e.g., `"abc"`), request execution halts before sending with a descriptive evaluation error explaining that the script result cannot satisfy the declared type.
- **Nested Braces in Complex Templates**: Object dynamic variables returning stringified JSON objects (e.g., `{"a": 1}`) inserted into an unquoted position must produce valid overall JSON when substituted into a parent object.
- **Whitespace inside Variable Tokens**: Unquoted tokens with internal whitespace (e.g., `{ "x": {{ numberVar }} }`) must be recognized and validated identically to `{ "x": {{numberVar}} }`.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST define variable data types as a distinct classification consisting of String, Number, Boolean, and Object.
- **FR-002**: The system MUST separate variable kind (Regular, Secret, Dynamic) from variable data type across all variable models and editors.
- **FR-003**: The environment and collection variable tables MUST provide an explicit selection control (dropdown) for selecting the variable kind (Regular, Secret, Dynamic) instead of a cycling badge.
- **FR-004**: The environment and collection variable tables MUST provide an explicit selection control (dropdown) for selecting the variable data type (String, Number, Boolean, Object).
- **FR-005**: The Dynamic Variable Editor dialog MUST allow the user to select the variable data type, representing the expected return type of the script.
- **FR-006**: The code editor live validator MUST recognize template variable tokens (`{{...}}`) located in unquoted JSON value positions.
- **FR-007**: The code editor validator MUST NOT report syntax errors (such as "Expected property name") when an unquoted variable token has a declared data type of Number, Boolean, or Object, or when it is a built-in numeric/boolean dynamic variable.
- **FR-008**: The code editor validator MUST report a diagnostic warning when a variable with data type String is used in an unquoted JSON value position, recommending that the user wrap it in double quotes.
- **FR-009**: The request execution pipeline MUST serialize resolved dynamic and static variables according to their unquoted or quoted syntax in the template, emitting valid RFC 8259 JSON to the wire.
- **FR-010**: All relevant documentation pages (including Dynamic Variables and Environments) MUST be updated to describe the Kind and Type selection controls, JSON body authoring rules, and editor validation behavior.

---

### Key Entities

- **VariableKind**: Represents the execution nature of a variable (`REGULAR` for static values, `SECRET` for masked/external secrets, `DYNAMIC` for JavaScript generators).
- **VariableType**: Represents the primitive JSON data type (`STRING`, `NUMBER`, `BOOLEAN`, `OBJECT`).
- **DynamicVariable**: Represents a scripted generator variable containing a script, declared parameters, and an associated `VariableType` representing its return type.
- **RegularVariable / SecretVariable**: Represents static or secret variables, each associated with a `VariableType` representing the data type of its value.
- **TemplateDiagnostic**: Editor diagnostic emitted during live JSON validation, capable of recognizing template variables and offering contextual syntax recommendations.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of valid JSON templates containing unquoted numeric, boolean, or object variables (e.g. `{"x": {{numberVar}} }` and `{"ts": {{$timestamp}} }`) produce zero false-positive syntax errors in the code editor.
- **SC-002**: 100% of unquoted String variable usages in JSON value positions receive an actionable recommendation in the editor to enclose the variable in quotes.
- **SC-003**: Users can configure both variable kind and variable data type via explicit 2-click dropdown selection controls without cycling through unintended options.
- **SC-004**: End-to-end request execution of JSON payloads with unquoted numeric variables sends valid RFC 8259 JSON where the recipient server parses the value as a numeric primitive 100% of the time.
- **SC-005**: All updated documentation guides pass validation and render accurate UI control descriptions and JSON authoring examples.

---

## Assumptions

- There are no legacy external Clapi versions requiring migration support; the data model for variables can be cleanly restructured.
- Default variable data type for newly created variables is `String`.
- Built-in dynamic variables (such as `{{$timestamp}}`, `{{$timestampMs}}`, and `{{$randomInt}}`) have implicit `Number` data types, while `{{$isoTimestamp}}`, `{{$guid}}`, and `{{$randomAlphaNumeric}}` have implicit `String` data types.
- The desktop code editor's JSON and JSON5 parsing modes will both benefit from template-aware token validation.
