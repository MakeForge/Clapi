---
title: Environments & Fast Switching
description: Creating environment sets and fast switching from the top toolbar in Clapi.
---

# Environments & Fast Switching

<p class="badges">
  <span class="badge badge-protocol">Automation</span>
</p>

## Overview

Environments allow maintaining different configurations (such as base URLs, API keys, and client secrets) for different deployment targets (e.g. `Localhost`, `Development`, `Staging`, `Production`) without duplicating requests.

## Creating Environments

<ThemedScreenshot
  light="/generated/screenshots/EnvironmentEditDialogPreviewsKt.EnvironmentEditDialogPreviewLight.W1200dp_H800dp.png"
  dark="/generated/screenshots/EnvironmentEditDialogPreviewsKt.EnvironmentEditDialogPreviewDark.W1200dp_H800dp.png"
  alt="Clapi Environment Editor Dialog"
  :crop="false"
  max-width="960px"
/>

1. In the sidebar, expand the **Environments** section.
2. Click `+` to create a new environment (e.g. `Staging`).
3. Add key-value variables:
   - `baseUrl` = `https://staging-api.example.com`
   - `apiKey` = `sk_test_1234567890`
   - `port` = `8443`
4. Toggle the **Secret** checkbox for sensitive variables so their values are masked in the UI and encrypted locally via KSafe.

::: tip Shortcuts for Inserting Variables & Secrets
While editing requests, press **`Ctrl + Space`** inside any variable-aware input field (URL bar, headers, parameters, auth credentials, or request body) to open the autocomplete popup and quickly insert variables and secrets from your active environment. See the [Keyboard Shortcuts](/settings/shortcuts) page for details.
:::

## Fast Switching

In the top toolbar:
- Click the environment dropdown.
- Select your target environment.
- All requests in your open tabs immediately re-bind to the selected environment's values.
