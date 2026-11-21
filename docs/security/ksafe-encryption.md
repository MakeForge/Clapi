---
title: Local Credential Encryption
description: How Clapi protects passwords, bearer tokens, and cloud vault credentials using KSafe AES-256-GCM hardware encryption.
---

# Local Credential Encryption

<p class="badges">
  <span class="badge badge-protocol">Security Architecture</span>
  <span class="badge badge-version">AES-256-GCM</span>
</p>

## Overview

A major vulnerability in API clients is saving plain text secrets (Bearer tokens, basic auth passwords, client secrets, API keys) into JSON or YAML workspace files that developers inadvertently commit to public Git repositories.

Clapi completely decouples **workspace definitions** from **sensitive secrets**:
1. Collection and request files (`.json` / `.yaml`) store non-sensitive metadata and variable placeholder references (e.g. <code v-pre>{{vaultSecret}}</code>).
2. Actual sensitive credentials and vault tokens are persisted in an isolated, encrypted local keystore.

---

## Cryptographic Architecture

Clapi uses the multiplatform cryptographic library **[KSafe](https://github.com/ioannisa/KSafe)** for local keystore management:

```
[Plaintext Secret] 
       │
       ▼
[PBKDF2 Key Derivation] ──> Master Salt + Device Hardware Entropy
       │
       ▼
[AES-256-GCM Encryption] ──> Hardware-accelerated authenticated cipher
       │
       ▼
[Encrypted Keystore on Local Disk] (`clapi_secrets`)
```

### Key Security Features
- **Cipher**: **AES-256-GCM** (Galois/Counter Mode), providing both strong confidentiality and cryptographic integrity verification (AEAD).
- **Key Derivation**: **PBKDF2** with a high iteration count and unique local salts to protect against offline brute-force attacks.
- **Hardware Acceleration**: Utilizes CPU AES-NI instructions for near-zero latency cryptographic throughput during read/write cycles.

---

## What data is store encrypted?

| Category | Encrypted Fields | Storage Location |
|---|---|---|
| **Secret Manager Credentials** | Like KeePassXC key, AWS Secret Access Keys, Azure Client Secret, GCP Service Account keys, Keeper tokens | Encrypted local keystore |
| **OAuth 2.0 Credentials** | Client secrets, Refresh tokens, Access tokens | Encrypted local keystore |
| **Authentication Passwords** | Basic auth passwords, mTLS private keys | Encrypted local keystore |
| **Sensitive Environment Vars** | Any variable flagged as `Secret` | Encrypted local keystore |

---

## Git Safety

When you export a collection or use Clapi's **Git Sync** feature to commit API collections to Git:
- **Secrets are automatically stripped** or replaced with variable tokens.
- No encrypted master passwords or sensitive credentials are ever pushed to remote Git repositories.
