# Contract: GCP Secret Manager Global Settings

## Overview

Defines the configuration parameters, defaults, UI presentation, and secure storage rules for GCP Secret Manager in Settings -> Secrets Manager.

---

## 1. Parameters Specification

| Parameter | Required | UI Field Name / Label | Default Value | Masked in UI | Storage Mechanism |
|:---|:---:|:---|:---|:---:|:---|
| `gcpProjectId` | Yes | **Project ID** | `""` | No | Plaintext preferences |
| `gcpAccessToken` | Yes | **Access Token** | `""` | Yes | Encrypted `PlatformStorage` |
| `gcpEndpointUrl` | No | **Endpoint URL Override** | `https://secretmanager.googleapis.com` | No | Plaintext preferences |

---

## 2. Storage Key Mapping

- `clapi.settings.gcpProjectId`
- `clapi.settings.gcpAccessToken` (stored securely via `PlatformStorage.putSecretString`)
- `clapi.settings.gcpEndpointUrl`

---

## 3. UI Invariants

1. **Provider Key**: When the user selects "GCP Secret Manager", the provider identifier is `"gcp-secrets-manager"`.
2. **Masking**: `gcpAccessToken` is rendered with password masking in the UI.
3. **Empty Field Validation**: If `gcpProjectId` or `gcpAccessToken` is left blank during secret resolution, request execution halts with a descriptive error prompting the user to configure GCP Secret Manager in Settings.
4. **Endpoint Normalization**: Trailing slashes are stripped before constructing API URLs (e.g. `http://localhost:8085/` -> `http://localhost:8085`).
