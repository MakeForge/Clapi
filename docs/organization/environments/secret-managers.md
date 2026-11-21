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

::: tip Inserting Secrets with Shortcuts
You can quickly insert external secrets and variables anywhere in your requests (URL bar, headers, parameters, auth fields, body editor) using the keyboard shortcut **`Ctrl + Space`**. This opens the autocomplete dropdown listing all configured vault secrets and active environment variables. See the [Keyboard Shortcuts](/settings/shortcuts) page for details.
:::

All master passwords and tokens used to connect to these secret vaults are encrypted locally via **KSafe** (`AES-256-GCM`).


## Supported Secret Providers

### <img src="/icons/secret-manager/hashicorp.svg" class="secret-manager-icon" alt="HashiCorp" /> HashiCorp Vault
- Connect to self-hosted or HCP HashiCorp Vault servers (`v1` KV secrets engine).
- Authenticate via Token, AppRole, or Userpass.
- **Documentation**:
  - [HashiCorp Vault Overview & Docs](https://developer.hashicorp.com/vault)
  - [Create Secret Tutorial](https://developer.hashicorp.com/vault/tutorials/get-started-hcp-vault-dedicated/create-secret)

### <img src="/icons/secret-manager/keepassxc.svg" class="secret-manager-icon" alt="KeePassXC" /> KeePassXC
- Integrates via local native messaging IPC (KeepassXC proxy).
- Fetches secrets from your local running KeePassXC database without internet access.
- Cryptographically associates with your KeePassXC database key.
- **Documentation**:
  - [KeePassXC Getting Started Guide](https://keepassxc.org/docs/KeePassXC_GettingStarted)

### <img src="/icons/secret-manager/awssecrets.svg" class="secret-manager-icon" alt="AWS Secrets Manager" /> AWS Secrets Manager
- Resolves secrets stored in Amazon Web Services.
- Configurable via AWS Access Key ID, Secret Access Key, Region (`us-east-1`, `eu-west-1`), and Secret ARN.
- **Documentation**:
  - [AWS Secrets Manager Product Page](https://aws.amazon.com/secrets-manager/)
  - [AWS User Guide: Creating Secrets](https://docs.aws.amazon.com/secretsmanager/latest/userguide/create_secret.html)

### <img src="/icons/secret-manager/azurevault.svg" class="secret-manager-icon" alt="Azure Key Vault" /> Azure Key Vault
- Resolves secrets from Microsoft Azure Key Vault.
- Configurable via Vault URL, Azure Tenant ID, Client ID, and Client Secret.
- **Documentation**:
  - [Azure Key Vault Overview](https://learn.microsoft.com/en-us/azure/key-vault/general/quick-create-portal)
  - [Create Secret in Azure Key Vault](https://learn.microsoft.com/en-us/azure/key-vault/secrets/quick-create-portal)

### <img src="/icons/secret-manager/gcpsecretmanager.svg" class="secret-manager-icon" alt="GCP Secret Manager" /> Google Cloud Secret Manager
- Resolves secrets from Google Cloud Platform.
- Configurable via GCP Project ID and Service Account JSON credentials.
- **Documentation**:
  - [GCP Secret Manager Product Page](https://cloud.google.com/security/products/secret-manager)
  - [GCP Create Secret Quickstart](https://docs.cloud.google.com/secret-manager/docs/create-secret-quickstart)

### <img src="/icons/secret-manager/keepersecurity.svg" class="secret-manager-icon" alt="Keeper Security" /> Keeper Security
- Integrates with Keeper Secrets Manager.
- Configurable via one-time access token and configuration payload.
- **Documentation**:
  - [Keeper Secrets Manager Quick Start Guide](https://docs.keeper.io/keeperpam/secrets-manager/quick-start-guide#setup-secrets-manager)
