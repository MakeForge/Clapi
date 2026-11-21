# Quickstart: External Secret Variables Validation Guide

This guide details steps to validate HashiCorp Vault Server secret management, settings configuration, environment secret declaration, and request variable resolution end-to-end.

---

## Prerequisites

1. **Docker / Docker Compose**: Ensure Docker is installed and running.
2. **Local Vault Test Service**:
   Spin up the pre-seeded HashiCorp Vault instance:
   ```bash
   docker compose -f qa-tests/secret-managers-compose.yml up -d
   ```
3. **Verify Seeded Vault Secret**:
   Query the test secret using `curl`:
   ```bash
   curl -s -H "X-Vault-Token: root-dev-token" http://127.0.0.1:8200/v1/secret/data/payment/foo
   ```
   Expected response contains:
   ```json
   "data": {
     "client_id": "foo_client_123",
     "client_secret": "sk_live_secret_456"
   }
   ```

---

## Scenario 1: Configure Secret Provider in Global Settings

1. Launch Clapi.
2. Open Settings dialog (`Ctrl+,` or click Settings gear in toolbar).
3. Select the **Secrets Manager** tab.
4. Fill in connection details:
   - **Provider**: `HashiCorp Vault (Server)`
   - **Server URL**: `http://127.0.0.1:8200`
   - **Mount Path**: `secret`
   - **Namespace**: *(leave blank)*
   - **Vault Token**: `root-dev-token`
5. Close the Settings dialog.
6. Re-open the Settings dialog and verify the fields are still populated and the token is preserved.

---

## Scenario 2: Declare External Secrets in Environment Editor

1. Open Environment Editor (click active environment pill in top toolbar or sidebar).
2. Create or select an environment (e.g., `Local Vault Dev`).
3. Switch to the **External Secrets** tab.
4. Select Provider: `HashiCorp Vault (Server)`.
5. Click **Add Secret** and add:
   - **Name**: `client_id`
   - **Path**: `payment/foo`
6. Click **Add Secret** and add:
   - **Name**: `client_secret`
   - **Path**: `payment/foo`
7. Save the environment.

---

## Scenario 3: Autocomplete & Request Execution

1. Create a new HTTP request:
   - **Method**: `POST`
   - **URL**: `https://httpbin.org/anything` (or local test endpoint)
2. In the Headers tab, add a header:
   - **Key**: `X-Client-Id`
   - **Value**: Type `{{_secret.` and press `Ctrl+Space`.
   - Verify `_secret.client_id` appears in the dropdown. Select it.
3. In the Body tab (JSON):
   ```json
   {
     "client_id": "{{_secret.client_id}}",
     "client_secret": "{{_secret.client_secret}}"
   }
   ```
4. Click **Send**.
5. Inspect the response payload:
   - Verify `X-Client-Id` header received `foo_client_123`.
   - Verify JSON payload contains `client_id="foo_client_123"` and `client_secret="sk_live_secret_456"`.

---

## Scenario 4: Export Collection & Verify Zero Credential Leakage

1. Export the collection containing the environment (`Export` button or File menu).
2. Open the exported JSON/YAML file in a text editor.
3. Verify that:
   - `externalSecrets` block exists in the environment with `client_id` and `client_secret` paths.
   - The string `root-dev-token` does NOT appear anywhere in the file.
   - The string `http://127.0.0.1:8200` does NOT appear in the file.

---

## Teardown

Stop the local Vault test container:
```bash
docker compose -f qa-tests/secret-managers-compose.yml down
```
