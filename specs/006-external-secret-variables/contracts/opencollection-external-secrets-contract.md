# Contract: OpenCollection ExternalSecrets Schema Contract

## Specification

Conforms to OpenCollection v1.0.0 (`$defs/ExternalSecrets`).

### Schema Snippet:
```json
{
  "$defs": {
    "ExternalSecrets": {
      "type": "object",
      "description": "A block of external secrets resolved from a secret provider for an environment",
      "properties": {
        "type": {
          "type": "string",
          "enum": [
            "hashicorp-vault-cloud",
            "hashicorp-vault-server",
            "aws-secrets-manager",
            "azure-key-vault",
            "gcp-secrets-manager",
            "one-password"
          ],
          "description": "The secret provider that resolves these external secrets"
        },
        "variables": {
          "type": "array",
          "description": "Array of external secret variables resolved from the provider",
          "items": {
            "$ref": "#/$defs/ExternalSecretVariable"
          }
        }
      },
      "required": ["type"],
      "additionalProperties": false
    },
    "HashicorpVaultExternalSecret": {
      "type": "object",
      "description": "An external secret resolved from HashiCorp Vault",
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
        "path": {
          "type": "string",
          "description": "The path of the secret within HashiCorp Vault"
        }
      },
      "required": ["name", "path"],
      "additionalProperties": false
    }
  }
}
```

### Serialized OpenCollection Environment Example:
```json
{
  "name": "Production",
  "variables": [
    {
      "name": "baseUrl",
      "value": "https://api.example.com"
    }
  ],
  "externalSecrets": {
    "type": "hashicorp-vault-server",
    "variables": [
      {
        "name": "client_id",
        "path": "payment/foo"
      },
      {
        "name": "client_secret",
        "path": "payment/foo",
        "description": "Payment gateway secret key",
        "disabled": false
      }
    ]
  }
}
```

### Zero-Leakage Guarantee:
- The exported collection contains **only** the secret declarations (`type`, `name`, `path`, `description`, `disabled`).
- Sensitive Vault tokens and local server addresses are **never** present in the exported document.
