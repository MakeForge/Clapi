---
title: Authentication Methods
description: Configuring Basic, Bearer, API Key, OAuth 2.0 PKCE, and AWS SigV4 authentication in Clapi.
---

# Authentication Methods

<p class="badges">
  <span class="badge badge-protocol">HTTP & Security</span>
</p>

## Overview

Clapi supports a complete spectrum of authentication protocols. Authentication can be configured directly on individual requests or inherited automatically from parent folders and collections.

::: tip Inserting Credentials with Shortcuts
You can insert saved variables or external secrets into any credential input (Bearer tokens, Basic Auth passwords, API keys, OAuth client secrets) by pressing **`Ctrl + Space`** to open the autocomplete list. See the [Keyboard Shortcuts](/reference/shortcuts) page for details.
:::

## Supported Auth Types

### 1. Inherit from Parent
Delegates credential resolution to the parent folder or root collection. This allows updating credentials in one location and having all child requests automatically authenticate.

### 2. Bearer Token
Appends an `Authorization: Bearer <token>` header to the request.
- **Token Field**: Supports direct text entry or dynamic variables (e.g. <code v-pre>{{authToken}}</code>).
- **Prefix**: Defaults to `Bearer`, but can be customized (e.g. `JWT` or `Token`).

### 3. Basic Authentication
Encodes `username:password` using Base64 according to RFC 7617 and adds `Authorization: Basic <base64>`.
- **Password Masking**: The password field is securely masked by default.
- **Local Encryption**: Password credentials are saved locally encrypted via KSafe.

### 4. API Key
Adds an API key or secret token to the request:
- **Placement**: Choose between **Header** (e.g. `X-API-Key: {{apiKey}}`) or **Query Parameter** (e.g. `?api_key={{apiKey}}`).

### 5. OAuth 2.0 (with PKCE)
Clapi includes a full OAuth 2.0 client engine supporting all standard grant types:
- **Authorization Code with PKCE** (Proof Key for Code Exchange)
- **Client Credentials**
- **Implicit Grant**
- **Resource Owner Password Credentials**

#### Configuration Fields
- **Auth URL & Token URL**: Endpoints provided by your Identity Provider (Okta, Auth0, Keycloak, Google, GitHub, etc.).
- **Client ID & Client Secret**: Client credentials (stored encrypted via KSafe).
- **Scope**: Space-delimited list of requested scopes.
- **Auto-Refresh**: Clapi can automatically exchange refresh tokens when an access token expires.

### 6. AWS Signature v4
Cryptographically signs requests for Amazon Web Services (AWS) REST endpoints.
- **Access Key ID & Secret Access Key**: Encrypted via KSafe.
- **AWS Region**: e.g. `us-east-1`, `eu-west-1`.
- **Service Name**: e.g. `s3`, `execute-api`, `sqs`.
- **Session Token**: Optional temporary security credential token (`x-amz-security-token`).
