# Contract: OpenCollection Keeper Security Cloud Schema Extension

**File**: `core-storage/src/commonMain/resources/opencollection/opencollection_v1.0.0.json`

## 1. ExternalSecrets Provider Enum Extension

Under `"$defs/ExternalSecrets/properties/type/enum"`:
```json
"enum": [
  "hashicorp-vault-cloud",
  "hashicorp-vault-server",
  "aws-secrets-manager",
  "azure-key-vault",
  "gcp-secrets-manager",
  "one-password",
  "keepassxc",
  "keeper-security-cloud"
]
```

## 2. ExternalSecretVariable Polymorphic Definition

Under `"$defs/ExternalSecretVariable/oneOf"`:
```json
{
  "$ref": "#/$defs/KeeperSecurityExternalSecret"
}
```

## 3. KeeperSecurityExternalSecret Definition

```json
"KeeperSecurityExternalSecret": {
  "type": "object",
  "description": "An external secret resolved from Keeper Security Cloud",
  "properties": {
    "name": {
      "type": "string",
      "description": "The variable name"
    },
    "description": {
      "$ref": "#/$defs/Description"
    },
    "disabled": {
      "type": "boolean",
      "description": "Whether the external secret is disabled"
    },
    "recordTitle": {
      "type": "string",
      "description": "The title or UID of the record within Keeper Security Cloud"
    },
    "targetField": {
      "type": "string",
      "description": "The target field, property, or attribute to extract from the record",
      "default": "password"
    },
    "customFilters": {
      "type": "object",
      "additionalProperties": {
        "type": "string"
      },
      "description": "Key-value filters to match against record attributes and field values"
    }
  },
  "required": ["name"],
  "additionalProperties": false
}
```
