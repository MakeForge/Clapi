# Contract: Environment Import/Export & Drag-and-Drop

## Overview
Defines the serialization structure, external secret preservation, secret sanitization, and drag-and-drop ingestion contracts.

## 1. Export Sanitization Contract

```kotlin
interface EnvironmentExporterContract {
    /**
     * Serializes [environment] into a portable JSON string.
     * MUST include:
     * - "type": "clapiEnvironment"
     * - "name": environment name
     * - "color": hex color (if set)
     * - "extends": parent environment name (if set)
     * - "variables": key-value map of non-secret variables
     * - "externalSecrets": full provider and reference config
     *
     * MUST NOT include:
     * - Plaintext secret values for variables where secret == true
     * - Runtime tokens or resolved credentials from external secret managers
     */
    fun exportEnvironmentToString(environment: EnvState): String
}
```

### JSON Schema Verification
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["type", "name", "variables"],
  "properties": {
    "type": { "const": "clapiEnvironment" },
    "name": { "type": "string" },
    "color": { "type": ["string", "null"] },
    "extends": { "type": ["string", "null"] },
    "variables": {
      "type": "object",
      "additionalProperties": { "type": "string" }
    },
    "externalSecrets": {
      "type": "object",
      "required": ["type", "variables"],
      "properties": {
        "type": { "type": "string" },
        "variables": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name"],
            "properties": {
              "name": { "type": "string" },
              "path": { "type": "string" },
              "secretName": { "type": "string" },
              "secretId": { "type": "string" },
              "recordTitle": { "type": "string" },
              "url": { "type": "string" },
              "vaultName": { "type": "string" },
              "projectId": { "type": "string" },
              "targetField": { "type": "string" },
              "customFilters": { "type": "object" },
              "description": { "type": "string" },
              "disabled": { "type": "boolean" }
            }
          }
        }
      }
    }
  }
}
```

---

## 2. File Drag-and-Drop Ingestion Contract

```kotlin
interface FileDropHandlerContract {
    /**
     * Determines whether [fileName] has a supported extension for import.
     * Accepted extensions: .json, .yaml, .yml (case-insensitive).
     */
    fun isSupportedImportFile(fileName: String): Boolean

    /**
     * Handles file content dropped onto the dropzone.
     * Invokes [onFileLoaded] with file name and text content.
     */
    fun onFileDropped(
        fileName: String,
        content: String,
        onFileLoaded: (String, String) -> Unit,
    )
}
```

### Invariants & Guarantees
1. **Extension Acceptance**: `.yaml`, `.yml`, `.json` and uppercase variants (`.YAML`, `.YML`, `.JSON`) are strictly accepted.
2. **Error Isolation**: Dropping an unreadable or binary file produces a user-visible error banner without throwing uncaught exceptions or crashing the UI.
3. **Desktop Native Interop**: Integrates with AWT `DropTarget` and `DataFlavor.javaFileListFlavor` without interfering with intra-application drag-and-drop operations (e.g. sidebar reordering).
