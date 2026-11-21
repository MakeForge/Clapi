---
title: Schema Introspection & Docs Explorer
description: Fetching GraphQL schema introspection and exploring documentation in Clapi.
---

# Schema Introspection & Docs Explorer

<p class="badges">
  <span class="badge badge-protocol">GraphQL</span>
</p>

## Overview

When connected to an introspection-enabled GraphQL server, Clapi can automatically query the `__schema` endpoint and generate an interactive documentation browser directly inside your workspace.

---

## Introspecting a Schema

1. Enter your GraphQL endpoint URL.
2. If the endpoint requires authentication, configure your Bearer token or API key in the **Auth** or **Headers** tab first.
3. Click the **Docs** or **Introspect Schema** button.
4. Clapi sends an introspection query:
   ```graphql
   query IntrospectionQuery {
     __schema {
       queryType { name }
       mutationType { name }
       subscriptionType { name }
       types {
         ...FullType
       }
     }
   }
   ```
5. Once fetched, the schema is cached locally for fast offline browsing and autocompletion.

---

## Exploring the Schema

The **Docs Explorer** panel allows you to:
- **Search Types**: Quickly filter queries, mutations, object types, interfaces, unions, enums, and scalar definitions.
- **Inspect Fields & Deprecations**: View field descriptions, argument types, required flags (`!`), and `@deprecated` reasons.
- **Copy Type Names**: One-click copy for type names directly into your query editor.
