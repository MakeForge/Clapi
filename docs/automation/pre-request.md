---
title: Pre-Request Scripting
description: Generating HMAC signatures, timestamps, and dynamic headers before dispatching requests in Clapi.
---

# Pre-Request Scripting

<p class="badges">
  <span class="badge badge-protocol">Scripting</span>
</p>

## Overview

Pre-request scripts run immediately before a request is transmitted over the network. They are commonly used to calculate authentication signatures (HMAC-SHA256), refresh access tokens, generate random nonces, or compute dynamic timestamps.

## Common Use Cases

### 1. Generating Dynamic Timestamps and Nonces
```javascript
const timestamp = Math.floor(Date.now() / 1000);
clapi.environment.set("requestTimestamp", timestamp.toString());

const nonce = Math.random().toString(36).substring(2, 15);
clapi.environment.set("requestNonce", nonce);
```

### 2. Computing Request Signatures
```javascript
const secretKey = clapi.environment.get("apiSecret");
const timestamp = clapi.environment.get("requestTimestamp");
const payload = clapi.request.body || "";

// Assume CryptoJS or built-in crypto helper
const signature = clapi.crypto.hmacSha256(payload + timestamp, secretKey);
clapi.request.headers.set("X-Signature", signature);
```

### 3. Setting Dynamic Request Headers
```javascript
clapi.request.headers.set("X-Client-Trace-Id", clapi.utils.uuid());
```

## Dynamic Variables

If you would simply like to set a dynamic value per request, you may also like to look into the [Dynamic Variables](../environments/dynamic-variables) feature