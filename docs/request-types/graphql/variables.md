---
title: GraphQL Query Variables
description: Managing JSON variables and dynamic parameter substitution in Clapi GraphQL requests.
---

# GraphQL Query Variables

<p class="badges">
  <span class="badge badge-protocol">GraphQL</span>
</p>

## Overview

In GraphQL, arguments should be passed dynamically using variables rather than hardcoded directly into query strings. Clapi provides a dedicated **Variables** pane right below or beside the query editor.

---

## Defining Variables

In your query editor, define the variable types in the operation signature:

```graphql
query SearchUsers($query: String!, $limit: Int, $includeArchived: Boolean) {
  searchUsers(query: $query, limit: $limit, includeArchived: $includeArchived) {
    id
    name
    email
  }
}
```

In the **Variables** tab, supply the corresponding JSON document:

```json
{
  "query": "engineering",
  "limit": 20,
  "includeArchived": false
}
```

---

## Dynamic Variables in GraphQL Variables

You can use Clapi's dynamic variables directly inside the Variables JSON:

```json
{
  "orderId": "{{$guid}}",
  "timestamp": "{{$timestamp}}",
  "apiKey": "{{mySecretKey}}"
}
```

Before dispatch, Clapi parses and resolves all <code v-pre>{{variables}}</code> before serializing the payload to the wire.
