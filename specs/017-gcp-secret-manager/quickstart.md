# Quickstart: GCP Secret Manager Validation Guide

This guide describes how to validate the GCP Secret Manager integration end-to-end, covering local emulator container setup, global settings configuration, environment secret definitions, runtime request execution, and OpenCollection export verification.

## Prerequisites

1. Docker and Docker Compose installed.
2. Spin up the GCP Secret Manager emulator container:
   ```bash
   docker compose -f scratch/gcp/gcp-compose.yml up -d
   ```
   (or `docker compose -f qa-tests/docker/secret-managers-compose.yml up -d`).
3. Verify the emulator is healthy and seeded:
   ```bash
   curl -s http://localhost:8085/v1/projects
   ```
   Seeded secrets in project `my-test-project`:
   - Secret ID: `payment-foo` (version 1)
     - Payload (JSON): `{"client_id":"gcp_client_789","client_secret":"sk_gcp_secret_999"}`
   - Secret ID: `plain-token` (version 1)
     - Payload (Plaintext): `gcp-plain-token-value-12345`

---

## Scenario 1: Configure GCP Secret Manager in Global Settings

1. Open Clapi **Settings** (`Ctrl+,` or Settings gear icon).
2. Navigate to the **Secrets Manager** tab.
3. Select **GCP Secret Manager** as the Secret Provider.
4. Fill in connection parameters:
   - **Project ID**: `my-test-project`
   - **Access Token**: `emulator-dummy-token` (masked)
   - **Endpoint URL Override**: `http://localhost:8085`
5. Close and reopen the Settings dialog.
6. **Expected Outcome**:
   - Settings persist accurately.
   - `Access Token` remains masked and stored encrypted in secure platform storage.

---

## Scenario 2: Define GCP External Secrets in Environment Editor

1. Open the **Environment Editor**.
2. Select or create an environment (e.g. `GCP Emulator Dev`).
3. Switch to the **External Secrets** tab.
4. Select Provider: `GCP Secret Manager`.
5. Add three secret rows:
   - Row 1:
     - **Name**: `payment_client_id`
     - **Secret ID**: `payment-foo`
     - **Target Field**: `client_id`
     - **Version**: `latest`
     - **Enabled**: `true`
   - Row 2:
     - **Name**: `payment_client_secret`
     - **Secret ID**: `payment-foo`
     - **Target Field**: `client_secret`
     - **Version**: `latest`
     - **Enabled**: `true`
   - Row 3:
     - **Name**: `plain_api_token`
     - **Secret ID**: `plain-token`
     - **Target Field**: `""` (blank)
     - **Version**: `latest`
     - **Enabled**: `true`
6. Save the environment.
7. **Expected Outcome**:
   - All rows persist with configured target fields and versions.

---

## Scenario 3: Execute API Request Resolving GCP Secrets

1. Open a request tab in Clapi.
2. In the Headers table, enter:
   - `X-Client-Id: {{_secret.payment_client_id}}`
   - `X-Client-Secret: {{_secret.payment_client_secret}}`
   - `Authorization: Bearer {{_secret.plain_api_token}}`
3. Select `GCP Emulator Dev` as the active environment.
4. Click **Send**.
5. **Expected Outcome**:
   - Exactly one HTTP call is made to the emulator to retrieve `payment-foo:access` and one to `plain-token:access`.
   - The outgoing HTTP request receives:
     - `X-Client-Id: gcp_client_789`
     - `X-Client-Secret: sk_gcp_secret_999`
     - `Authorization: Bearer gcp-plain-token-value-12345`
   - Clapi logs never expose `sk_gcp_secret_999` or the access token.

---

## Scenario 4: Autocomplete Verification

1. In any request editor URL, Header, or Body field, type `{{_secret.` and press `Ctrl+Space`.
2. **Expected Outcome**:
   - Autocomplete dropdown includes `{{_secret.payment_client_id}}`, `{{_secret.payment_client_secret}}`, and `{{_secret.plain_api_token}}`.
   - Items display a distinct Secret badge.
   - Selecting an item inserts the placeholder at the cursor.

---

## Scenario 5: Portable OpenCollection Export & Leak Check

1. Export the collection to OpenCollection JSON or YAML.
2. Inspect the exported file.
3. **Expected Outcome**:
   - The `externalSecrets` block has `type: "gcp-secrets-manager"`.
   - No GCP access tokens or local emulator endpoints appear in the file.

---

## Scenario 6: Automated End-to-End QA Integration Tests

1. Run the Gradle test task for the QA test suite:
   ```bash
   ./gradlew :qa-tests:test --tests io.github.crustack.clapi.qa.GcpSecretManagerIntegrationTest
   ```
2. **Expected Outcome**:
   - All integration tests pass against the seeded GCP Secret Manager container.
