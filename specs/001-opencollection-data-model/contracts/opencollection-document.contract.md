# Contract: OpenCollection Document & Serialization

**Feature**: `001-opencollection-data-model`
**Contract Name**: `OpenCollectionDocumentContract`
**Format Version**: OpenCollection `1.0.0`
**Schema URI**: `https://schema.opencollection.com/opencollection/v1.0.0.json`

## 1. Overview
Defines the canonical document contract for importing, exporting, and persisting collections in Clapi. Every collection document read or written by Clapi MUST adhere to this contract.

---

## 2. Document Invariants

1. **Schema Compliance (FR-005, SC-001)**:
   - Any document written by Clapi MUST validate against the official OpenCollection v1.0.0 JSON Schema.
   - Any document opened by Clapi MUST be validated against the schema before ingestion. If validation fails, an actionable error is returned identifying the path and failure reason (FR-006, FR-009).
2. **Version Pinning (FR-002)**:
   - The root field `"opencollection"` MUST be explicitly set to `"1.0.0"`.
3. **Bundled Layout (FR-034)**:
   - For single-file collections, the root field `"bundled"` MUST be `true`.
4. **Ordering Preservation (FR-004)**:
   - The ordering of entries in `"items"` (both root and folder level) MUST be strictly preserved during serialization and deserialization.
5. **Lossless Semantic Round-Trip (FR-007, FR-008, SC-002)**:
   - A `Deserialize → Modify → Serialize → Deserialize` cycle MUST preserve all supported fields and all unedited/unknown fields in `extensions` and item definitions.
6. **No Secret Leaks (FR-017, SC-007)**:
   - Secret variables in `config.environments` MUST be written as `SecretVariable` (`secret: true`) with no `value` property present in the serialized document.

---

## 3. Wire Document Format

### JSON Format (Default)
```json
{
  "opencollection": "1.0.0",
  "info": {
    "name": "My API Collection",
    "version": "1.0.0"
  },
  "bundled": true,
  "config": {
    "environments": [
      {
        "name": "Production",
        "variables": [
          {
            "name": "baseUrl",
            "value": "https://api.example.com"
          },
          {
            "name": "apiKey",
            "secret": true,
            "type": "string"
          }
        ]
      }
    ]
  },
  "items": [
    {
      "info": {
        "name": "Users",
        "type": "folder"
      },
      "items": [
        {
          "info": {
            "name": "Get Users",
            "type": "http"
          },
          "http": {
            "method": "GET",
            "url": "{{baseUrl}}/users",
            "headers": [
              {
                "name": "Accept",
                "value": "application/json"
              }
            ],
            "params": [
              {
                "name": "limit",
                "value": "20",
                "type": "query"
              }
            ]
          },
          "runtime": {
            "scripts": [
              {
                "type": "before-request",
                "code": "clapi.request.headers.upsert('X-Trace', '123');"
              }
            ]
          }
        }
      ]
    }
  ],
  "extensions": {
    "clapi": {
      "mcp": []
    }
  }
}
```

### YAML Format
The document may alternately be serialized/deserialized as equivalent YAML using identical semantic structures.
