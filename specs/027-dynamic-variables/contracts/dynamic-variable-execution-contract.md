# Contract: Dynamic Variable Execution Engine

**Branch**: `027-dynamic-variables` | **Date**: 2026-11-15 | **Spec**: [spec.md](../spec.md)

## Purpose
Defines the execution interface, runtime sandbox guarantees, input/output protocols, and error contracts for evaluating user-defined dynamic variables.

---

## 1. Engine Interface

```kotlin
package io.github.makeforge.clapi.core.scripting

interface DynamicVariableEngine {
    /**
     * Executes a dynamic variable script with the provided parameters and request context.
     * Enforces execution timeout and captures return value, duration, logs, and errors.
     */
    suspend fun executeDynamicVariable(
        script: String,
        context: DynamicScriptContext,
        timeoutMs: Long = 2000L,
    ): DynamicScriptExecutionResult
}
```

---

## 2. JavaScript Sandbox Protocol

### 2.1 IIFE Envelope
The execution engine wraps the user's script in an immediately invoked function expression (IIFE):

```javascript
(function() {
    "use strict";
    const context = __CLAPI_DYNAMIC_CONTEXT__;
    const args = __CLAPI_DYNAMIC_ARGS__;
    
    // Bind declared parameters as local variables or access via args
    __CLAPI_PARAM_BINDINGS__

    let __result = undefined;
    try {
        __result = (function() {
            // User Script Body
            __USER_SCRIPT_BODY__
        })();
    } catch (e) {
        return JSON.stringify({
            success: false,
            error: e && (e.stack || e.message) ? String(e.stack || e.message) : "Unknown script error",
            logs: __captured_logs__
        });
    }

    return JSON.stringify({
        success: true,
        returnValue: typeof __result === "object" && __result !== null ? JSON.stringify(__result) : (__result !== undefined ? String(__result) : ""),
        logs: __captured_logs__
    });
})();
```

### 2.2 Global APIs Available
- Standard JavaScript built-ins: `Math`, `Date`, `JSON`, `RegExp`, `Array`, `String`, `Number`, `Boolean`, `parseInt`, `parseFloat`, `encodeURIComponent`, `decodeURIComponent`, `btoa`, `atob`.
- Logging: `console.log(...)`, `console.info(...)`, `console.warn(...)`, `console.error(...)` (captured into `consoleLogs`).
- Context:
  - `context.request.url`: String
  - `context.request.method`: String ("GET", "POST", etc.)
  - `context.request.headers`: Record<String, String>
  - `context.request.name`: String
  - `args`: Record<String, Any> (mapping parameter names to evaluated primitive arguments)

---

## 3. Error & Timeout Handling

| Error Scenario | Engine Behavior | Request Resolution Behavior |
|---|---|---|
| **Syntax Error** (e.g. `foo(`) | Returns `DynamicScriptExecutionResult(success = false, errorMessage = "SyntaxError: ...")` | Request halts immediately with resolution error identifying variable name and syntax error. |
| **Runtime Exception** (e.g. `null.prop`) | Returns `DynamicScriptExecutionResult(success = false, errorMessage = "TypeError: ...")` | Request halts with diagnostic error. |
| **Infinite Loop / Timeout** (`while(true)`) | Javet `V8Guard` terminates execution at `timeoutMs` (2000ms), returns timeout error. | Request halts immediately; no UI freezing. |
| **Non-String Return Value** (`return 123` or `return { a: 1 }`) | Automatically stringified (`"123"`, `'{"a":1}'`). | Injected smoothly into token location. |
| **No Return Statement** (`const a = 1;`) | `returnValue` is `""`. | Replaces token with empty string or warning. |
