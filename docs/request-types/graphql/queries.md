---
title: Query & Mutation Editor
description: Writing, formatting, and executing GraphQL queries and mutations in Clapi.
---

# Query & Mutation Editor

<p class="badges">
  <span class="badge badge-protocol">GraphQL</span>
</p>

## Overview

The GraphQL query editor is tailored specifically for the GraphQL specification (RFC 2021). It features syntax highlighting for keywords, types, fields, arguments, and directives, alongside auto-indentation and bracket closing.

---

## Writing Queries & Mutations

### Basic Query
```graphql
query ListProducts {
  products(limit: 5) {
    id
    title
    price
    inStock
  }
}
```

### Mutation with Input Types
```graphql
mutation CreateNewProduct($input: ProductInput!) {
  createProduct(input: $input) {
    product {
      id
      title
      createdAt
    }
    userErrors {
      field
      message
    }
  }
}
```

---

## Auto-Formatting & Prettify

Click the **Prettify** button (or press `Shift + Alt + F` / `Shift + Option + F`) to format your GraphQL query into standard indentation and consistent spacing.
