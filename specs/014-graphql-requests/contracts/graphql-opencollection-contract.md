# Contract: GraphQL OpenCollection v1.0.0 Serialization

**Feature**: `014-graphql-requests`  
**Date**: 2026-11-10  

## 1. OpenCollection v1.0.0 Schema Target

Every exported GraphQL request MUST strictly validate against `opencollection_v1.0.0.json` under `$defs/GraphQLRequest`.

### Example Canonical Single-Body Document:
```json
{
  "info": {
    "name": "Get User Details",
    "type": "graphql"
  },
  "graphql": {
    "method": "POST",
    "url": "{{baseUrl}}/graphql",
    "headers": [
      {
        "name": "Accept",
        "value": "application/json"
      }
    ],
    "params": [],
    "body": {
      "query": "query GetUser($id: ID!) {\n  user(id: $id) {\n    id\n    name\n  }\n}",
      "variables": "{\n  \"id\": \"123\"\n}"
    },
    "auth": {
      "type": "bearer",
      "bearer": {
        "token": "{{authToken}}"
      }
    }
  },
  "runtime": {
    "variables": [],
    "scripts": []
  },
  "settings": {
    "timeout": 5000,
    "followRedirects": true
  },
  "docs": "# User Details Query\nFetches user details by ID."
}
```

### Example Canonical Variants-Body Document:
```json
{
  "info": {
    "name": "Users Operations",
    "type": "graphql"
  },
  "graphql": {
    "method": "POST",
    "url": "{{baseUrl}}/graphql",
    "body": [
      {
        "title": "All Users",
        "selected": true,
        "body": {
          "query": "query { users { id name } }"
        }
      },
      {
        "title": "User by ID",
        "selected": false,
        "body": {
          "query": "query GetUser($id: ID!) { user(id: $id) { id name } }",
          "variables": "{\"id\": \"1\"}"
        }
      }
    ]
  }
}
```

---

## 2. Serialization Rules
1. `info.type` MUST be set to `"graphql"`.
2. The request details object key MUST be `"graphql"`.
3. `GraphQLRequest` schema enforces `additionalProperties: false`. Unknown fields are stored in `extensions` and stripped or isolated to prevent schema validation failure on external validators.
4. Polymorphic body deserializer MUST transparently accept either a single object (`GraphQLBody`) or an array of variant objects (`List<GraphQLBodyVariant>`).
5. When saving from Clapi to OpenCollection:
   - If only one variant exists and its title is `"Default"`, serialize as single `GraphQLBody` object.
   - If multiple variants exist or custom titles are used, serialize as `List<GraphQLBodyVariant>`.
