---
title: V8 Scripting Engine Architecture & Sandbox Security
description: Understanding Clapi's embedded Javet V8 JavaScript scripting runtime and security sandbox.
---

# V8 Scripting Engine Architecture & Sandbox Security

<p class="badges">
  <span class="badge badge-protocol">Scripting</span>
  <span class="badge badge-version">Google V8</span>
</p>

## Overview

Clapi includes an embedded **Google V8 JavaScript engine** via [Javet](https://github.com/caoccao/Javet) (`com.caoccao.javet:javet`). Unlike lightweight JavaScript interpreters (like Nashorn or Rhino), Javet runs real Google V8 native binaries, providing modern ECMAScript (ES2023+) syntax support, high execution speed, and tight memory isolation.

---

## Script Execution Lifecycle

```
[Pre-Request Script (V8)]
         │
         ▼
[Variable Interpolation & Request Construction]
         │
         ▼
[Network Dispatch via Ktor] ──> Remote Server
         │
         ▼
[Response Arrival & Metrics Recording]
         │
         ▼
[Post-Response Test Script (V8)]
         │
         ▼
[Test Results Rendered in UI]
```

---

## Sandbox Security

To protect developers against malicious scripts embedded in downloaded collections:
- **No Node.js / OS Access**: The V8 runtime has no access to `child_process`, `fs`, or native system shells.
- **Controlled I/O**: Network and disk access are strictly constrained to the Clapi host process through audited bridge APIs (`clapi.*`).
- **Memory & Timeout Guards**: Scripts have strict execution timeouts (default: 5 seconds) to prevent infinite loops from hanging the desktop UI.
