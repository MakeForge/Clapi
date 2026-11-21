# Feature Specification: User-Defined Scriptable Dynamic Variables

**Feature Branch**: `027-dynamic-variables`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description:
- Currently there are built-in dynamic system variables like `$timestamp` or `$guid` to auto-generate some values per request
- it should now be possible for users to add dynamic variable which dont have static value but instead the user can define a script for it, which is run for every request execution and therefore returns a value per request
- in data model, maybe new `VariableEntry` `DynamicVariable` with new VariableType value `dynamic`?
- in the variable editor maybe display the `VariableType` and user can switch it to dynamic? And then instead of the value input field there is a button to open the dynamic script in a subdialog where user can edit the script and on the right have some sort of test execution window, to give input params and execute it and see output/errors
- should the script have any input params by default? E.g. Info about the request maybe (url, etc.)?
- allow the user to define additional primitive input params for the script? So that than it can be used e.g. like this:
  - new `secondsFromNow` dynamic variable defined, which will be called with: `{{secondsFromNow(10)}}`, and the script will then return e.g. isoTimestamp with 10 seconds from now
- ensure import/export works as expected
- ensure there are qa-tests for this
- ensure in the docs there is a separate subpage for dynamic variables that includes a section with the built in dynamic variables as well as a section for user-defined dynamic variables

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Defining & Executing User-Defined Dynamic Variables (Priority: P1)

As an API practitioner working with APIs that require freshly computed tokens, hashes, nonces, or dynamic timestamps per request, I want to define variables backed by custom executable scripts so that every request execution automatically generates fresh, dynamic values without requiring manual pre-request script boilerplate on every single request.

**Why this priority**: Core value of the feature. Transforms variables from static constants into reusable dynamic computed expressions across collections and environments.

**Independent Test**: Can be tested independently by creating a dynamic variable `currentEpochSec` with a script `return Math.floor(Date.now() / 1000);`, referencing `{{currentEpochSec}}` in an HTTP request URL or header, executing the request multiple times with delays, and verifying that each execution receives an updated, computed timestamp value.

**Acceptance Scenarios**:

1. **Given** a user is configuring variables in an environment or collection, **When** the user selects the variable type selector and changes a variable's type to `dynamic`, **Then** the static value text field is replaced by an indicator showing that the variable is dynamic along with an "Edit Script" action button.
2. **Given** a dynamic variable is defined with a script that returns a computed string or number, **When** any request containing `{{variableName}}` is executed, **Then** the variable resolver executes the script immediately prior to dispatch and injects the returned value into the request payload/URL/headers.
3. **Given** a dynamic variable is referenced across multiple sequential requests, **When** each request is dispatched, **Then** the script executes freshly for each request execution, returning distinct computed values if time- or randomness-dependent.
4. **Given** a dynamic variable is toggled to `disabled`, **When** a request referencing that variable is executed, **Then** the variable is treated as disabled/unresolved (consistent with standard variable scoping).

---

### User Story 2 - Dynamic Script Editor Dialog with Integrated Test Runner (Priority: P1)

As a developer authoring dynamic variable scripts, I want an interactive dedicated editor dialog featuring a code editor on the left and a test execution sandbox on the right so that I can immediately write, test, inspect return values, and debug script errors before using the variable in live requests.

**Why this priority**: Authoring scripts without feedback is error-prone. A side-by-side interactive test harness allows immediate validation, debugging, and rapid iteration.

**Independent Test**: Can be tested independently by clicking "Edit Script" on a dynamic variable, writing code in the script editor (e.g. `return "TEST_" + Math.random();`), clicking "Run Test", and verifying that the returned value, execution time, and any console outputs or errors appear in the test panel.

**Acceptance Scenarios**:

1. **Given** the user clicks "Edit Script" on a dynamic variable row, **When** the dialog opens, **Then** the user is presented with a code editor for the script on the left side and an interactive test execution pane on the right side.
2. **Given** code is written in the script editor, **When** the user clicks "Run Test", **Then** the script executes within the sandbox and the test panel displays the returned value, status (Success/Error), execution duration in milliseconds, and any captured console logs.
3. **Given** the script contains a syntax error or throws an uncaught exception, **When** "Run Test" is triggered, **Then** the test panel clearly highlights the error message and stack trace without crashing the application.
4. **Given** the user makes edits to the script, **When** the user clicks "Save", **Then** the updated script and its parameter definitions are persisted to the variable entry. If "Cancel" or escape is chosen, changes are discarded.

---

### User Story 3 - Parameterized Dynamic Variables with Arguments (Priority: P2)

As an API practitioner, I want to declare primitive input parameters for dynamic variables and invoke them with argument values (e.g., `{{secondsFromNow(10)}}` or `{{formatPrefix("ID", 42)}}`) so that a single dynamic script can be reused across different requests with customized inputs.

**Why this priority**: Reusability. Avoids having to create multiple distinct scripts for slight variations of the same logic (such as different offsets, lengths, or prefixes).

**Independent Test**: Can be tested independently by defining a dynamic variable `secondsFromNow` with a parameter `seconds`, invoking it in a request as `{{secondsFromNow(15)}}`, and verifying that the script receives `15` as an argument and returns an ISO timestamp exactly 15 seconds in the future.

**Acceptance Scenarios**:

1. **Given** the dynamic variable editor dialog, **When** the user configures parameters, **Then** the user can add, rename, reorder, or delete named primitive parameters (e.g., `seconds`, `prefix`).
2. **Given** a dynamic variable with defined parameters, **When** the test runner panel is displayed, **Then** input fields for each defined parameter are rendered, allowing the user to provide sample argument values for the test run.
3. **Given** a request template contains a parameterized variable token like `{{secondsFromNow(10)}}`, **When** the request is resolved, **Then** the arguments inside the parentheses are parsed as primitive literals (numbers, quoted strings, booleans) and passed into the script execution as defined parameters.
4. **Given** a parameterized dynamic variable is invoked with omitted or fewer arguments than defined, **When** the script executes, **Then** missing parameters receive a value of `undefined`/`null`, and default parameter values defined in the script logic apply.
5. **Given** an autocomplete or variable suggestion popup is triggered in request editors (e.g. via keyboard shortcut), **When** dynamic variables are listed, **Then** parameterized dynamic variables show their parameter signature (e.g. `secondsFromNow(seconds)`) and description.

---

### User Story 4 - Contextual Request Information in Dynamic Scripts (Priority: P2)

As an API tester generating HMAC signatures, request-bound checksums, or correlation IDs, I want the dynamic variable script to optionally read context about the currently executing request (such as URL, HTTP method, or headers) so that values can be mathematically or logically bound to the request being sent.

**Why this priority**: Advanced API automation (e.g., AWS/Hawk style request signing, idempotency keys derived from target endpoints) requires access to request details at execution time.

**Independent Test**: Can be tested independently by creating a dynamic variable `requestSignature` whose script inspects `context.request.method` and `context.request.url`, invoking it in a request header, and verifying that sending a `GET https://httpbin.org/get` resolves the header to the signature containing `GET:https://httpbin.org/get`.

**Acceptance Scenarios**:

1. **Given** a dynamic variable is resolved during an active request dispatch, **When** the script accesses the request context, **Then** the context provides read-only attributes of the pending request including its target URL, method, and active headers.
2. **Given** a dynamic variable is executed inside the test runner dialog outside of an active request, **When** the script references request context, **Then** safe fallback/mock request context values (customizable or standard defaults) are provided so the test execution succeeds without errors.
3. **Given** a script attempts to modify the request context properties directly, **When** the script runs, **Then** mutations to the read-only request context are ignored or prevented, ensuring deterministic variable resolution.

---

### User Story 5 - Persistence, Import/Export & OpenCollection Compatibility (Priority: P2)

As a user exporting and sharing API collections with teammates, I want dynamic variables (including their scripts and parameter signatures) to be saved and exported cleanly in OpenCollection format so that collections can be version-controlled, shared, and imported without loss of functionality.

**Why this priority**: Collaboration and interoperability. Dynamic variables must survive import/export cycles and be fully compatible with the OpenCollection specification.

**Independent Test**: Can be tested independently by creating environments and collections containing user-defined dynamic variables, exporting the collection/environment to JSON and YAML, importing the files into a clean workspace, and asserting that all dynamic variables retain their scripts, parameters, and execution capabilities.

**Acceptance Scenarios**:

1. **Given** an environment or collection containing user-defined dynamic variables, **When** exported to OpenCollection format, **Then** each dynamic variable is serialized with its name, variable type `dynamic`, script content, parameter definitions, and metadata.
2. **Given** an OpenCollection JSON or YAML file containing dynamic variables, **When** imported into the application, **Then** the dynamic variables are recognized and populated into the respective environment or collection with their scripts intact.
3. **Given** an exported collection containing dynamic variables is opened in external tools or older versions that do not support dynamic variables, **When** the document is parsed, **Then** standard schema compliance is maintained without corrupting other variables.

---

### User Story 6 - Dedicated Documentation for Built-In & User-Defined Dynamic Variables (Priority: P3)

As a user exploring Clapi's documentation, I want a dedicated documentation page explaining dynamic variables, covering all built-in dynamic variables and step-by-step instructions for creating user-defined dynamic variables with scripts and parameters, so that I can easily discover and utilize dynamic capabilities.

**Why this priority**: Discoverability and developer education. Users need clear reference documentation and copyable examples to adopt dynamic variable scripting effectively.

**Independent Test**: Can be tested independently by navigating the documentation site, locating the "Dynamic Variables" page in the navigation sidebar, and verifying it includes both the built-in variables reference table and a user-defined dynamic variables tutorial with example scripts.

**Acceptance Scenarios**:

1. **Given** the documentation site, **When** a user browses the features/guide section, **Then** a dedicated "Dynamic Variables" page is present in the navigation hierarchy.
2. **Given** the Dynamic Variables documentation page, **When** the user reads the Built-in Dynamic Variables section, **Then** all system variables (`$timestamp`, `$isoTimestamp`, `$randomInt`, `$randomInt(min, max)`, `$guid`, `$uuid`) are documented with their syntax, output format, and examples.
3. **Given** the Dynamic Variables documentation page, **When** the user reads the User-Defined Dynamic Variables section, **Then** clear guides, screenshots/descriptions of the editor dialog, parameter usage (`{{variable(arg)}}`), request context access, and practical examples (e.g. `secondsFromNow`, hash generators) are provided.

---

### Edge Cases

- **Execution Timeout**: How does the system handle an infinite loop or long-running computation in a dynamic variable script (e.g. `while(true) {}`)? The script engine MUST enforce a strict per-variable execution timeout (e.g., 2000ms), terminate execution, and report a clear timeout error.
- **Syntax / Runtime Errors in Request**: When a dynamic script encounters an error (e.g., `ReferenceError: foo is not defined`) during request dispatch, the request execution MUST halt immediately with a descriptive error message indicating which dynamic variable failed and why, preventing silent corrupted payloads from being sent.
- **Argument Mismatches**: When a parameterized dynamic variable is invoked with unexpected argument counts or malformed syntax (e.g. unclosed parentheses `{{secondsFromNow(10}`), the system MUST gracefully fall back or flag an explicit resolution error rather than crashing.
- **Name Collisions**: If a user creates a dynamic variable with the same name as a built-in variable (e.g. `$timestamp` or `timestamp`) or an environment variable, well-defined precedence rules MUST apply (Request-scoped variables > Environment-scoped variables > Collection-scoped variables > Built-in dynamic system variables).
- **Circular Variable Invocation**: If a dynamic script evaluates variables that invoke the same dynamic variable recursively, the system MUST detect recursion depth and terminate with a circular reference error.
- **Return Type Coercion**: If a dynamic script returns a non-string type (e.g. integer `42`, boolean `true`, or a JavaScript object `{ "id": 1 }`), the return value MUST be coerced predictably (numbers and booleans converted to their literal string representations; objects converted to JSON string).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow users to set the variable type of an environment or collection variable entry to `dynamic`.
- **FR-002**: For dynamic variable entries, the system MUST store the executable script content, optional parameter list, description, and enabled/disabled state.
- **FR-003**: In variable editing views (environments and collections), when a variable's type is set to `dynamic`, the static value input field MUST be replaced by a visual indicator and an action button to open the Dynamic Script Editor subdialog.
- **FR-004**: The Dynamic Script Editor dialog MUST provide a code editor with syntax highlighting for writing and editing JavaScript scripts.
- **FR-005**: The Dynamic Script Editor dialog MUST include an interactive test runner panel displaying:
  - Input fields for defined parameters
  - A "Run Test" trigger
  - Execution outcome status (Success / Error)
  - Execution duration in milliseconds
  - Returned resolved string value
  - Captured console log outputs
  - Diagnostic error details (message, line number, stack trace) on failure.
- **FR-006**: Users MUST be able to define zero or more named primitive parameters (e.g. strings, numbers, booleans) for a dynamic variable.
- **FR-007**: When executing a dynamic variable script during request dispatch, the execution environment MUST provide a read-only `context.request` object containing the request's `url`, `method`, `headers`, and `name`. When executing inside the dynamic script test runner panel, the environment MUST supply a configurable mock request context fallback.
- **FR-008**: The system MUST support parameterized variable token syntax in request templates using the format `{{variableName(arg1, arg2, ...)}}`.
- **FR-009**: The argument parser MUST correctly parse primitive literals (integers, floats, quoted single/double strings, booleans, null) passed inside parameterized variable invocations.
- **FR-010**: Non-parameterized dynamic variables MUST be invokable via standard variable syntax `{{variableName}}`.
- **FR-011**: Dynamic variable scripts MUST be executed on every request dispatch where the variable token is referenced, producing fresh dynamic values per request.
- **FR-012**: The execution of dynamic variable scripts MUST enforce a strict sandbox execution timeout (default: 2,000 milliseconds) to prevent UI freezes and infinite loops.
- **FR-013**: If a dynamic variable script throws an exception or times out during request resolution, request dispatch MUST be aborted with a clear diagnostic message identifying the failed variable.
- **FR-014**: Return values from dynamic scripts MUST be converted to string format (numbers and booleans stringified; objects and arrays serialized to JSON).
- **FR-015**: The system MUST serialize and deserialize dynamic variables to and from OpenCollection format without data loss, maintaining compatibility with the OpenCollection schema.
- **FR-016**: Variable autocomplete / quick-suggestion popups MUST include user-defined dynamic variables, displaying their name, parameter signature, and description.
- **FR-017**: Comprehensive automated tests (unit tests and end-to-end QA tests) MUST verify script evaluation, parameter passing, error handling, timeouts, request context injection, and import/export persistence.
- **FR-018**: The project documentation MUST include a dedicated subpage for Dynamic Variables detailing all built-in dynamic variables and complete user documentation for user-defined dynamic variables.

### Key Entities *(include if feature involves data)*

- **DynamicVariableEntry**: Represents a user-defined dynamic variable item within an environment or collection. Contains `name`, `type` (`dynamic`), `script` (source code string), `parameters` (list of declared parameter definitions), optional `description`, and `disabled` boolean flag.
- **DynamicVariableParameter**: Represents a declared parameter for a dynamic variable. Contains `name` (string identifier), optional `type` (primitive type hint), and optional `defaultValue`.
- **DynamicScriptContext**: Represents the sandbox context injected into the script execution environment. Contains argument values mapped to parameter names, standard utility objects, and read-only execution context (such as request URL, HTTP method, and active headers).
- **DynamicScriptExecutionResult**: Encapsulates the output of a test or runtime execution. Contains `returnValue` (string or null), `success` (boolean), `durationMs` (long), `consoleLogs` (list of strings), and `errorMessage` (string or null).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure, write a script for, test, and save a new dynamic variable in under 60 seconds using the editor subdialog.
- **SC-002**: Dynamic variable execution adds less than 10 milliseconds of resolution overhead per dynamic variable during normal request execution.
- **SC-003**: 100% of defined dynamic variables survive an export-import roundtrip to and from OpenCollection format with zero loss of script content or parameter configuration.
- **SC-004**: Script errors, syntax bugs, or execution timeouts in dynamic variables halt request dispatch with an actionable diagnostic error 100% of the time, never producing silent corrupted requests or application crashes.
- **SC-005**: All built-in dynamic variables and user-defined dynamic variable features achieve 100% pass rate in automated QA and unit regression suites.
- **SC-006**: Documentation covering built-in and user-defined dynamic variables is published with runnable script examples for at least 3 common use cases (timestamps, hashes/nonces, and parameterized transforms).

## Assumptions

- Dynamic variable scripts will execute in the existing lightweight JavaScript engine infrastructure (`ClapiScriptEngine`) utilized across Clapi for pre-request and test scripts.
- Scripts return a value either via an explicit `return` statement or by evaluating a final expression.
- Dynamic variables can be defined at both the Environment level (available when that environment is active) and Collection level (available to all requests in that collection), adhering to Clapi's standard hierarchical variable resolution order.
- Parameters for dynamic variables accept primitive values (numbers, strings, booleans, null); complex nested objects as invocation arguments are out of scope for initial release.
