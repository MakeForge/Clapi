# Contract: OpenCollection KeePassXC External Secrets Schema

## Overview
This contract defines the OpenCollection document serialization format for environments utilizing KeePassXC as an external secret provider.

## 1. JSON Schema Representation

```json
{
  "name": "Production",
  "externalSecrets": {
    "type": "keepassxc",
    "variables": [
      {
        "name": "api_token",
        "url": "https://api.example.com",
        "targetField": "password",
        "customFilters": {
          "login": "service-worker",
          "env": "{{stage}}"
        },
        "description": "API production bearer token"
      },
      {
        "name": "db_user",
        "url": "https://db.internal.example.com",
        "targetField": "login"
      }
    ]
  }
}
```

## 2. YAML Representation

```yaml
name: Production
externalSecrets:
  type: keepassxc
  variables:
    - name: api_token
      url: https://api.example.com
      targetField: password
      customFilters:
        login: service-worker
        env: "{{stage}}"
      description: API production bearer token
    - name: db_user
      url: https://db.internal.example.com
      targetField: login
```

## 3. Schema Constraints
1. **No Sensitive Data**: Passwords, TOTPs, secret values, and workstation association keys MUST NEVER be persisted in this document.
2. **Provider Type**: The `type` field must be exactly `"keepassxc"`.
3. **Target Field Defaults**: If omitted during serialization/deserialization, `targetField` defaults to `"password"`.
4. **Custom Filters Defaults**: If omitted, `customFilters` defaults to an empty object `{}`.
