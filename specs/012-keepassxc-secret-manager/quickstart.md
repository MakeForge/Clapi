# Quickstart: KeePassXC Secret Manager Validation Guide

This guide describes how to validate the KeePassXC secret manager integration end-to-end, covering configuration, UI validation feedback, runtime secret resolution, and lifecycle behavior.

## Prerequisites
1. KeePassXC (version 2.6.0+) installed and running locally.
2. In KeePassXC settings:
   - Navigate to **Tools → Settings → Browser Integration**.
   - Check **Enable browser integration**.
   - Ensure the database is unlocked.
3. Test Entry in KeePassXC:
   - Title: `Clapi Payment Test`
   - Entry URL: `https://test.api.payment.internal`
   - Username: `service-account-1`
   - Password: `super-secret-payment-token-99`
   - Custom String Field: `tier` = `production`

---

## Scenario 1: Configure Secret & Verify Live Match Feedback (No Leaks)

1. Launch Clapi and open the **Environment Editor**.
2. Create or select an environment (e.g., `Staging`).
3. Navigate to the **External Secrets** tab.
4. Set the Secret Provider to **KeePassXC** (`keepassxc`).
5. Click **Add Secret** and fill in:
   - **Name**: `payment_token`
   - **Entry URL**: `https://test.api.payment.internal`
   - **Target Field**: `password`
6. **Expected Outcome**:
   - KeePassXC prompts for browser/client association on first connect (accept the prompt).
   - The match status indicator updates to: `✔ 1 entry matched`.
   - **Security verification**: At no point is `super-secret-payment-token-99` displayed, printed in logs, or visible in the UI.

---

## Scenario 2: Refine Matches Using Custom Field Filters with Variables

1. In the `Staging` environment, add an environment variable:
   - `KEY`: `env_tier`
   - `VALUE`: `production`
2. In the `payment_token` external secret row, add a custom filter:
   - **Field**: `tier`
   - **Value**: `{{env_tier}}`
3. **Expected Outcome**:
   - Variable `{{env_tier}}` evaluates to `production`.
   - Filter matches the entry's custom attribute `tier`.
   - Indicator displays `✔ 1 entry matched`.
4. Change the filter value to `staging`:
   - Indicator updates to `✖ 0 entries found`.

---

## Scenario 3: Execute API Request Resolving Secret

1. Open a request tab in Clapi.
2. Set URL to a mock or echoing HTTP server (e.g. `http://localhost:8080/headers`).
3. Add a header:
   - `Authorization`: `Bearer {{_secret.payment_token}}`
4. Set the active environment to `Staging`.
5. Send the request.
6. **Expected Outcome**:
   - The secret placeholder is resolved to `super-secret-payment-token-99`.
   - The outgoing request header contains `Authorization: Bearer super-secret-payment-token-99`.
   - Clapi logs do not expose the token value.

---

## Scenario 4: Verify Zero Idle Proxy Footprint

1. Switch active environment from `Staging` to `Default` (which has no external secrets).
2. **Expected Outcome**:
   - Clapi dispatches disconnect to the KeePassXC client.
   - Socket connection to KeePassXC proxy closes.
   - No idle daemon, polling threads, or background processes remain active.
