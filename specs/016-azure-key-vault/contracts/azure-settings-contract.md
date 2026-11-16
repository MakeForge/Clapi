# Contract: Azure Key Vault Global Settings

## Overview

Defines the fields, defaults, UI representation, and storage rules for Azure Key Vault in Settings -> Secrets Manager.

---

## 1. Parameters Specification

| Parameter | Required | UI Field Name / Label | Default Value | Masked in UI | Storage Mechanism |
|:---|:---:|:---|:---|:---:|:---|
| `azureVaultName` | Yes | **Vault Name or URL** | `""` | No | Plaintext preferences |
| `azureTenantId` | Yes | **Tenant ID** | `""` | No | Plaintext preferences |
| `azureClientId` | Yes | **Client ID** | `""` | No | Plaintext preferences |
| `azureClientSecret` | Yes | **Client Secret** | `""` | Yes | Encrypted `PlatformStorage` |
| `azureAuthorityHost` | No | **Authority Host** | `https://login.microsoftonline.com` | No | Plaintext preferences |
| `azureApiVersion` | No | **API Version** | `7.4` | No | Plaintext preferences |

---

## 2. Storage Key Mapping

- `clapi.settings.azureVaultName`
- `clapi.settings.azureTenantId`
- `clapi.settings.azureClientId`
- `clapi.settings.azureClientSecret` (stored securely via `PlatformStorage.putSecretString`)
- `clapi.settings.azureAuthorityHost`
- `clapi.settings.azureApiVersion`

---

## 3. UI Invariants

1. **Provider Key**: When the user selects "Azure Key Vault", the provider identifier is `"azure-key-vault"`.
2. **Masking**: `azureClientSecret` is displayed using password masking (dots/asterisks).
3. **Empty Field Validation**: If `azureVaultName`, `azureTenantId`, `azureClientId`, or `azureClientSecret` is left blank during secret resolution, request execution halts with an instructional message prompting the user to configure Azure Key Vault in Settings.
