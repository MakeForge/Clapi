---
title: External Open Source Libraries Directory
description: Complete catalog of open source libraries powering Clapi with direct links to upstream GitHub repositories.
---

# External Open Source Libraries Directory

<p class="badges">
  <span class="badge badge-protocol">Open Source</span>
</p>

## Overview

In the spirit of open-source transparency, this page indexes the primary external runtime libraries used by Clapi, with direct links to their official GitHub source repositories and licenses.

For exact library coordinates, pinned versions, and build plugins, refer to the Gradle version catalog on the `main` branch:
👉 [**`gradle/libs.versions.toml`**](https://github.com/MakeForge/Clapi/blob/main/gradle/libs.versions.toml)

## Runtime Libraries Catalog

| Library | License | Upstream GitHub Repository | Purpose in Clapi |
|---|---|---|---|
| **Kotlin** | Apache-2.0 | [JetBrains/kotlin](https://github.com/JetBrains/kotlin) | Core programming language |
| **Kotlin Coroutines** | Apache-2.0 | [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Asynchronous runtime & concurrency |
| **Compose Multiplatform** | Apache-2.0 | [JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform) | Declarative desktop UI toolkit |
| **Ktor Client** | Apache-2.0 | [ktorio/ktor](https://github.com/ktorio/ktor) | Network engine, HTTP, WebSockets, OAuth2 |
| **KSafe** | Apache-2.0 | [ioannisa/ksafe](https://github.com/ioannisa/ksafe) | Hardware-accelerated local AES-256-GCM encryption |
| **Koin** | Apache-2.0 | [InsertKoinIO/koin](https://github.com/InsertKoinIO/koin) | Dependency injection framework |
| **Javet** | Apache-2.0 | [caoccao/Javet](https://github.com/caoccao/Javet) | Embedded Google V8 JavaScript engine for scripts |
| **gRPC Java** | Apache-2.0 | [grpc/grpc-java](https://github.com/grpc/grpc-java) | gRPC HTTP/2 transport & reflection client |
| **Protocol Buffers** | BSD-3-Clause | [protocolbuffers/protobuf](https://github.com/protocolbuffers/protobuf) | Protobuf schema compilation & JSON mapping |
| **KeePassXC Proxy Access** | MIT | [purejava/keepassxc-proxy-access](https://github.com/purejava/keepassxc-proxy-access) | Native messaging proxy client for KeePassXC |
| **Keeper Secrets Manager** | MIT | [Keeper-Security/secrets-manager](https://github.com/Keeper-Security/secrets-manager) | Keeper vault secret resolution SDK |
| **NetworkNT Schema Validator** | Apache-2.0 | [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator) | MCP & JSON Schema validation engine |
| **Kaml** | Apache-2.0 | [charleskorn/kaml](https://github.com/charleskorn/kaml) | YAML parser for OpenCollection & OpenAPI |
| **Swagger Parser** | Apache-2.0 | [swagger-api/swagger-parser](https://github.com/swagger-api/swagger-parser) | OpenAPI 3.0 / 3.1 specification importer |

::: tip Version Catalog
To view the authoritative and pinned coordinates, dependencies, and version definitions used by Clapi, see the version catalog on GitHub:
[`gradle/libs.versions.toml`](https://github.com/MakeForge/Clapi/blob/main/gradle/libs.versions.toml).
:::
