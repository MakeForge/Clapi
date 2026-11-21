---
title: RabbitMQ Authentication & TLS
description: Configuring AMQP authentication inheritance, Basic Auth, OAuth 2.0 JWT, and TLS client certificates for RabbitMQ in Clapi.
---

# RabbitMQ Authentication & TLS

<p class="badges">
  <span class="badge badge-protocol">RabbitMQ &bull; Security</span>
</p>

## Authentication Inheritance

RabbitMQ requests participate in Clapi's standard **Collection → Folder → Request** authentication hierarchy:

- Setting **Auth** to **Inherit** automatically resolves credentials from the nearest parent folder or collection root.
- All credential fields support `{{variable}}` interpolation, including write-only secret variables and external secret managers (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, HashiCorp Vault, Keeper Security, and KeePassXC).

---

## Supported AMQP Auth Mechanisms

| Clapi Auth Type | RabbitMQ AMQP Mechanism |
| :--- | :--- |
| **None** | Anonymous / RabbitMQ default credentials. |
| **Basic Auth** | AMQP `PLAIN` SASL using resolved `username` and `password`. |
| **Bearer Token / JWT** | RabbitMQ OAuth 2.0 backend (`rabbitmq_auth_backend_oauth2`) convention—passes the resolved token as the AMQP password. |
| **OAuth 2.0** | Acquires/resolves the OAuth 2.0 access token and supplies it as the AMQP password (with optional username). |
| **TLS Client Certificate** | Enable **TLS** in the connection bar and **Use TLS Client Certificate (`EXTERNAL` SASL)** in **Settings** to authenticate with collection/environment PKCS#12 or PEM client certificates over `amqps://`. |

---

## TLS (`amqps://`)

- Check **TLS** in the connection bar (or enter an `amqps://host:5671/vhost` URL) to encrypt the AMQP connection.
- When `port` is omitted, Clapi defaults to `5672` for `amqp://` and `5671` for `amqps://`.
