# Contract: OpenCollection Azure Secrets Schema

## Overview

Defines the serialized representation of Azure Key Vault external secrets in OpenCollection v1.0.0 JSON and YAML documents.

---

## 1. JSON Schema Definition

In `opencollection_v1.0.0.json`, `ExternalSecrets` has `type: "azure-key-vault"`, and its variables conform to `AzureKeyVaultExternalSecret`:

```json
{
  "$defs": {
    "AzureKeyVaultExternalSecret": {
      "type": "object",
      "description": "An external secret resolved from Azure Key Vault",
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
        "secretName": {
          "type": "string",
          "description": "The name of the secret within Azure Key Vault"
        },
        "vaultName": {
          "type": "string",
          "description": "Optional Azure Key Vault name or URL override"
        },
        "targetField": {
          "type": "string",
          "description": "The target key or attribute within the secret value"
        },
        "customFilters": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Key-value filters to match against attributes"
        }
      },
      "required": ["name", "secretName"],
      "additionalProperties": false
    }
  }
}
```

---

## 2. Example OpenCollection JSON Serialization

```json
{
  "opencollection": "1.0.0",
  "info": {
    "name": "Payments API",
    "version": "1.0.0"
  },
  "config": {
    "environments": [
      {
        "name": "Azure Production",
        "externalSecrets": {
          "type": "azure-key-vault",
          "variables": [
            {
              "name": "gateway_client_id",
              "secretName": "payment-foo",
              "targetField": "client_id",
              "customFilters": {
                "stage": "prod"
              },
              "description": "Gateway Client ID",
              "disabled": false
            },
            {
              "name": "gateway_client_secret",
              "secretName": "payment-foo",
              "targetField": "client_secret",
              "disabled": false
            }
          ]
        }
      }
    ]
  }
}
```

---

## 3. Credential Leakage Prevention Contract

- **Forbidden Fields**: The following keys MUST NEVER appear in any exported JSON or YAML collection file:
  - `azureClientSecret`
  - `clientSecret`
  - `azureTenantId`
  - `tenantId`
  - `azureClientId`
  - `clientId`
  - `access_token`
  - `Authorization`
