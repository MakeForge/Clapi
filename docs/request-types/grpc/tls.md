---
title: gRPC TLS & Client Certificates
description: Configuring Plaintext (h2c), TLS, and Mutual TLS (mTLS) for gRPC in Clapi.
---

# gRPC TLS & Client Certificates

<p class="badges">
  <span class="badge badge-protocol">gRPC & Security</span>
</p>

## Overview

gRPC runs strictly over HTTP/2. Clapi supports both unencrypted plaintext HTTP/2 (`h2c`) for local development and secure TLS/mTLS for production deployments.

## Security Modes

### 1. Plaintext (`h2c`)
Used when developing locally against microservices without SSL (e.g. `localhost:50051`).
- Disable the **TLS** toggle in the request bar.
- Clapi establishes an unencrypted HTTP/2 connection.

### 2. TLS Encryption
Used when connecting to external or cloud endpoints (e.g. `grpc.example.com:443`).
- Enable the **TLS** toggle.
- Clapi validates the remote server certificate against the system trust store.

### 3. Mutual TLS (mTLS) / Client Certificates
For zero-trust corporate architectures requiring client identity validation:
- Supply your **Client Certificate** (`.crt` / `.pem`) and **Private Key** (`.key`).
- Private keys are encrypted locally using KSafe.
