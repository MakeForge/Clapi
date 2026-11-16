# Contract: OpenCollection GCP Secrets Schema

## Overview

Defines the serialized representation of GCP Secret Manager external secrets in OpenCollection v1.0.0 JSON and YAML documents.

---

## 1. JSON Schema Definition

In `opencollection_v1.0.0.json`, `ExternalSecrets` supports `type: "gcp-secrets-manager"`, and its variables conform to `GcpSecretsManagerExternalSecret`:

```json
{
  "$defs": {
    "GcpSecretsManagerExternalSecret": {
      "type": "object",
      "description": "An external secret resolved from GCP Secret Manager",
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
        "secretId": {
          "type": "string",
          "description": "The resource ID of the secret within GCP Secret Manager"
        },
        "version": {
          "type": "string",
          "description": "The target secret version snapshot or alias (defaults to 'latest')"
        },
        "targetField": {
          "type": "string",
          "description": "The target key or attribute within the secret JSON payload"
        },
        "projectId": {
          "type": "string",
          "description": "Optional GCP project ID override"
        },
        "customFilters": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Key-value filters to match against attributes"
        }
      },
      "required": ["name", "secretId"],
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
    "name": "Cloud Backend Collection",
    "version": "1.0.0"
  },
  "config": {
    "environments": [
      {
        "name": "GCP Staging",
        "externalSecrets": {
          "type": "gcp-secrets-manager",
          "variables": [
            {
              "name": "payment_client_id",
              "secretId": "payment-foo",
              "version": "latest",
              "targetField": "client_id",
              "customFilters": {
                "env": "staging"
              },
              "description": "Payment Gateway Client ID",
              "disabled": false
            },
            {
              "name": "payment_client_secret",
              "secretId": "payment-foo",
              "version": "latest",
              "targetField": "client_secret",
              "disabled": false
            },
            {
              "name": "api_token",
              "secretId": "plain-token",
              "version": "1",
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
  - `gcpAccessToken`
  - `accessToken`
  - `token`
  - `Authorization`
  - `Bearer`
