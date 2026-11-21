# Contract: Dynamic Variable Serialization & OpenCollection Schema

**Branch**: `027-dynamic-variables` | **Date**: 2026-11-15 | **Spec**: [spec.md](../spec.md)

## Purpose
Specifies the canonical JSON and YAML representations of `DynamicVariable` entries in OpenCollection document exports, imports, and workspace storage.

---

## 1. JSON Representation

```json
{
  "name": "secondsFromNow",
  "type": "dynamic",
  "script": "const offset = Number(args.seconds || 0);\nconst now = new Date(Date.now() + offset * 1000);\nreturn now.toISOString();",
  "parameters": [
    {
      "name": "seconds",
      "description": "Offset in seconds added to current time",
      "defaultValue": "0"
    }
  ],
  "description": "Generates an ISO-8601 timestamp with an optional seconds offset",
  "disabled": false
}
```

---

## 2. YAML Representation

```yaml
name: secondsFromNow
type: dynamic
script: |
  const offset = Number(args.seconds || 0);
  const now = new Date(Date.now() + offset * 1000);
  return now.toISOString();
parameters:
  - name: seconds
    description: Offset in seconds added to current time
    defaultValue: "0"
description: Generates an ISO-8601 timestamp with an optional seconds offset
disabled: false
```

---

## 3. Polymorphic Deserialization Rules

When reading a `VariableEntry` object:
1. If `secret == true`, deserialize as `SecretVariable`.
2. Else if `type == "dynamic"` OR `script` field is present, deserialize as `DynamicVariable`.
3. Otherwise, deserialize as `RegularVariable`.

Export files MUST preserve all fields (`name`, `type`, `script`, `parameters`, `description`, `disabled`).
Import parsers MUST accept documents with or without optional fields (`description`, `disabled`, `parameters`).
