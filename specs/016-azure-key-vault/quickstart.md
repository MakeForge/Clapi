# Quickstart: Azure Key Vault Validation Guide

This guide describes how to validate the Azure Key Vault integration end-to-end, covering Lowkey Vault container setup, global settings configuration, environment secret definitions, runtime request execution, and OpenCollection export verification.

## Prerequisites

1. Docker and Docker Compose installed.
2. Spin up the Lowkey Vault container:
   ```bash
   docker compose -f scratch/azure/azure-compose.yml up -d
   ```
   (or `qa-tests/docker/secret-managers-compose.yml`).
3. Verify Lowkey Vault is healthy and seeded:
   ```bash
   curl -k -f https://localhost:8443/ping
   ```
   Seeded secrets:
   - Secret Name: `payment-foo`
     - Content: `{"client_id":"azure_client_999","client_secret":"sk_azure_secret_888"}`
     - Content Type: `application/json`
   - Secret Name: `plain-token`
     - Content: `super-secret-plain-token-12345`
     - Content Type: `text/plain`

---

## Scenario 1: Configure Azure Key Vault in Global Settings

1. Open Clapi **Settings** (`Ctrl+,` or Settings gear icon).
2. Navigate to the **Secrets Manager** tab.
3. Select **Azure Key Vault** as the Secret Provider.
4. Fill in connection parameters:
   - **Vault Name or URL**: `https://localhost:8443`
   - **Tenant ID**: `00000000-0000-0000-0000-000000000000`
   - **Client ID**: `test-client-id`
   - **Client Secret**: `test-secret` (masked)
   - **Authority Host**: `bypass`
   - **API Version**: `7.4`
5. Close and reopen the Settings dialog.
6. **Expected Outcome**:
   - Settings persist accurately.
   - `Client Secret` remains masked and stored encrypted.

---

## Scenario 2: Define Azure External Secrets in Environment Editor

1. Open the **Environment Editor**.
2. Select or create an environment (e.g. `Lowkey Vault Dev`).
3. Switch to the **External Secrets** tab.
4. Select Provider: `Azure Key Vault`.
5. Add two secret rows:
   - Row 1:
     - **Name**: `gateway_client_id`
     - **Secret Name**: `payment-foo`
     - **Target Field**: `client_id`
     - **Enabled**: `true`
   - Row 2:
     - **Name**: `gateway_client_secret`
     - **Secret Name**: `payment-foo`
     - **Target Field**: `client_secret`
     - **Enabled**: `true`
6. Save the environment.
7. **Expected Outcome**:
   - Both rows persist with configured target fields.

---

## Scenario 3: Execute API Request Resolving Azure Secrets

1. Open a request tab in Clapi.
2. In the URL or Headers, enter:
   - `X-Client-Id: {{_secret.gateway_client_id}}`
   - `X-Client-Secret: {{_secret.gateway_client_secret}}`
3. Select `Lowkey Vault Dev` as the active environment.
4. Click **Send**.
5. **Expected Outcome**:
   - Exactly one HTTP call is made to Lowkey Vault to retrieve `payment-foo`.
   - The outgoing HTTP request receives `X-Client-Id: azure_client_999` and `X-Client-Secret: sk_azure_secret_888`.
   - Clapi logs never expose `sk_azure_secret_888` or client secrets.

---

## Scenario 4: Autocomplete Verification

1. In any request editor URL, Header, or Body field, type `{{_secret.` and press `Ctrl+Space`.
2. **Expected Outcome**:
   - Autocomplete dropdown includes `{{_secret.gateway_client_id}}` and `{{_secret.gateway_client_secret}}`.
   - Items display a distinct Secret badge.
   - Selecting an item inserts the placeholder at the cursor.

---

## Scenario 5: Portable OpenCollection Export & Leak Check

1. Export the collection to OpenCollection JSON or YAML.
2. Inspect the exported file.
3. **Expected Outcome**:
   - The `externalSecrets` block has `type: "azure-key-vault"`.
   - No Azure client secrets, tenant IDs, client IDs, or local tokens appear in the file.
