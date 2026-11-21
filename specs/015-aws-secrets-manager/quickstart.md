# Quickstart: AWS Secrets Manager Validation Guide

This guide describes how to validate the AWS Secrets Manager integration end-to-end, covering LocalStack container setup, global settings configuration, environment secret definitions, runtime request execution, and OpenCollection export verification.

## Prerequisites

1. Docker and Docker Compose installed.
2. Spin up the LocalStack Secrets Manager container:
   ```bash
   docker compose -f scratch/aws/aws-compose.yml up -d
   ```
   (or the corresponding `qa-tests/docker/secret-managers-compose.yml`).
3. Verify LocalStack is healthy and seeded:
   ```bash
   curl -s http://localhost:4566/_localstack/health | grep '"secretsmanager": "running"'
   ```
   Seeded secret:
   - Secret Name: `local/payment/foo`
   - Content: `{"client_id":"foo_client_123","client_secret":"sk_live_secret_456"}`

---

## Scenario 1: Configure AWS Secrets Manager in Global Settings

1. Launch Clapi and open **Settings** (`Ctrl+,` or Settings icon).
2. Navigate to the **Secrets Manager** tab.
3. Set **Secret Provider** to `AWS Secrets Manager`.
4. Fill in connection parameters:
   - **AWS Region**: `us-east-1`
   - **AWS Access Key ID**: `test`
   - **AWS Secret Access Key**: `test` (input is masked)
   - **AWS Session Token**: *(leave blank)*
   - **Endpoint URL Override**: `http://localhost:4566`
   - **Version Stage**: `AWSCURRENT`
5. Close the Settings dialog and reopen it.
6. **Expected Outcome**:
   - All settings persist.
   - `AWS Secret Access Key` remains masked.
   - Local platform storage stores `test` securely via encrypted storage.

---

## Scenario 2: Define AWS External Secrets in Environment Editor

1. Open the **Environment Editor**.
2. Select or create an environment (e.g. `LocalStack Dev`).
3. Switch to the **External Secrets** tab.
4. Set Provider to `AWS Secrets Manager`.
5. Click **Add Secret** to add two rows:
   - Row 1:
     - **Name**: `gateway_client_id`
     - **Secret ID / Name**: `local/payment/foo`
     - **Target Field**: `client_id`
     - **Enabled**: `true`
   - Row 2:
     - **Name**: `gateway_client_secret`
     - **Secret ID / Name**: `local/payment/foo`
     - **Target Field**: `client_secret`
     - **Enabled**: `true`
6. Save the environment.
7. **Expected Outcome**:
   - Secrets are saved in the environment's `externalSecrets` block.
   - The UI displays both entries with their configured target fields.

---

## Scenario 3: Execute API Request Resolving AWS Secrets

1. Open a request tab in Clapi.
2. Set URL to `http://localhost:8080/auth/token` (or a local echo endpoint).
3. In the Request Body (JSON) or Headers, enter:
   ```json
   {
     "clientId": "{{_secret.gateway_client_id}}",
     "clientSecret": "{{_secret.gateway_client_secret}}"
   }
   ```
4. Select `LocalStack Dev` as the active environment.
5. Click **Send**.
6. **Expected Outcome**:
   - Exactly one network call is made to LocalStack `http://localhost:4566` using AWS SigV4 authorization.
   - The outgoing HTTP request body contains:
     ```json
     {
       "clientId": "foo_client_123",
       "clientSecret": "sk_live_secret_456"
     }
     ```
   - Clapi logs do not expose `sk_live_secret_456` or AWS secret keys.

---

## Scenario 4: Autocomplete Verification

1. In any request editor URL, Header, or Body field, type `{{_secret.` and press `Ctrl+Space`.
2. **Expected Outcome**:
   - Autocomplete list includes `{{_secret.gateway_client_id}}` and `{{_secret.gateway_client_secret}}`.
   - Items display an `external-secret` badge.
   - Selecting an item inserts the placeholder without trailing whitespace.

---

## Scenario 5: Portable OpenCollection Export (Zero Credential Leakage)

1. Export the collection containing `LocalStack Dev` to OpenCollection JSON and YAML.
2. Inspect the exported file.
3. **Expected Outcome**:
   - The file contains:
     ```json
     "externalSecrets": {
       "type": "aws-secrets-manager",
       "variables": [
         {
           "name": "gateway_client_id",
           "secretName": "local/payment/foo",
           "targetField": "client_id"
         }
       ]
     }
     ```
   - Zero occurrences of `test`, `AWS_SECRET_ACCESS_KEY`, `awsSecretAccessKey`, `foo_client_123`, or `localhost:4566`.

---

## Scenario 6: Automated QA Integration Test Run

Run the automated integration test suite:
```bash
./gradlew :qa-tests:test --tests "io.github.makeforge.clapi.qa.AwsSecretsManagerIntegrationTest"
```
**Expected Outcome**: Tests execute against LocalStack Secrets Manager and pass with 0 failures.
