---
title: Secret Managers
description: Integrating with KeePassXC, AWS Secrets Manager, Azure Key Vault, GCP, and Keeper via KSafe.
---

# Secret Managers

<p class="badges">
  <span class="badge badge-protocol">Security & Secrets</span>
</p>

## Overview

Storing production API keys or database passwords directly in shared configuration files is unsafe. Clapi integrates natively with enterprise secret vaults. You store only the secret reference (e.g. <code v-pre>{{my-api-key}}</code>), and Clapi resolves the actual secret dynamically at runtime.

All master passwords and tokens used to connect to these secret vaults are encrypted locally via **KSafe** (`AES-256-GCM`).

---

## Supported Secret Providers

### 1. KeePassXC
- Integrates via local native messaging IPC (`org.purejava:keepassxc-proxy-access`).
- Fetches secrets from your local running KeePassXC database without internet access.
- Cryptographically associates with your KeePassXC database key.

### 2. AWS Secrets Manager
- Resolves secrets stored in Amazon Web Services.
- Configurable via AWS Access Key ID, Secret Access Key, Region (`us-east-1`, `eu-west-1`), and Secret ARN.

### 3. Azure Key Vault
- Resolves secrets from Microsoft Azure Key Vault.
- Configurable via Vault URL, Azure Tenant ID, Client ID, and Client Secret.

### 4. Google Cloud Secret Manager
- Resolves secrets from Google Cloud Platform.
- Configurable via GCP Project ID and Service Account JSON credentials.

### 5. Keeper Security
- Integrates with Keeper Secrets Manager (`com.keepersecurity.secrets-manager:core`).
- Configurable via one-time access token and configuration payload.
