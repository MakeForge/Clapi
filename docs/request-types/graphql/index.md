---
title: GraphQL Requests
description: Complete guide to querying, mutating, and introspecting GraphQL APIs in Clapi.
---

# GraphQL Requests

<p class="badges">
  <span class="badge badge-protocol">Protocol: GraphQL</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/MainScreenPreviewsKt.MainScreenGraphQlPreviewLight.W1600dp_H800dp.png"
  dark="/generated/screenshots/MainScreenPreviewsKt.MainScreenGraphQlPreviewDark.W1600dp_H800dp.png"
  alt="Clapi GraphQL Request Editor"
/>

Clapi includes a dedicated, first-class GraphQL workbench (`RequestKind.GRAPHQL`). Instead of forcing GraphQL into a generic raw JSON HTTP POST editor, Clapi provides a specialized split editor with dedicated panels for your GraphQL query, JSON variables, schema introspection, and interactive documentation.

## Quick Start

1. Create a new request and set the protocol to **GraphQL** (`RequestKind.GRAPHQL`).
2. Enter the GraphQL endpoint URL (e.g. `https://countries.trevorblades.com/`).
3. In the **Query** tab, enter your query:
   ```graphql
   query GetCountry($code: ID!) {
     country(code: $code) {
       name
       native
       capital
       currency
       emoji
     }
   }
   ```
4. In the **Variables** tab, enter the JSON parameter:
   ```json
   {
     "code": "JP"
   }
   ```
5. Click **Send** (`⌘ + Enter` / `Ctrl + Enter`).
6. The formatted response data displays in the response viewer on the right.

## Detailed Feature Subpages

- [Query & Mutation Editor](./queries.md): Authoring queries, mutations, subscriptions, syntax highlighting, and auto-formatting.
- [Query Variables](./variables.md): Composing JSON variables and dynamic variable interpolation.
- [Schema Introspection & Docs Explorer](./introspection.md): Fetching `__schema` definitions and exploring types, queries, and mutations.
- [GraphQL Authentication & Headers](./auth.md): Setting authorization headers, Bearer tokens, and Apollo/Relay headers.
