---
title: cURL Integration
description: Importing and exporting cURL commands in Clapi.
---

# cURL Integration

<p class="badges">
  <span class="badge badge-protocol">HTTP</span>
</p>

## Overview

cURL is the universal command-line lingua franca for HTTP requests. Clapi features bidirectional cURL compatibility: you can paste any valid cURL command into Clapi to create a request, or copy any request from Clapi as an exact, reproducible cURL command line.

---

## Copy as cURL

To export your active request as a cURL command:
1. Open the request tab.
2. In the top request bar, click the **cURL** button (or open the tab context menu and select **Copy as cURL**).
3. Clapi copies a multiline shell command to your clipboard:

```bash
curl --location --request POST 'https://api.example.com/v1/users' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer my-secret-token' \
--data-raw '{
  "username": "alex",
  "email": "alex@example.com"
}'
```

::: tip Secret Sanitization
If your request references variables like <code v-pre>{{apiKey}}</code>, Clapi automatically interpolates the active environment value into the exported cURL command so it can be executed immediately in your terminal.
:::

---

## Import from cURL

To import an existing cURL command:
1. Copy a cURL command from your terminal, browser DevTools, or documentation.
2. In Clapi, press `⌘ + V` / `Ctrl + V` while the sidebar or tab bar is focused, or click **Import → From Clipboard**.
3. Clapi parses:
   - Target URL and query parameters
   - HTTP method (`-X POST`, `-X DELETE`, etc.)
   - Headers (`-H`, `--header`)
   - Payload (`-d`, `--data-raw`, `--form`)
   - Authentication flags (`-u`, `--user`, `--oauth2-bearer`)
4. A new request tab is created instantly with all fields populated.
