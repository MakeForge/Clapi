---
title: Techstack
description: Core architecture and catalog of open source libraries powering Clapi with direct links to upstream GitHub repositories.
---

# Techstack

<p class="badges">
  <span class="badge badge-protocol">Open Source</span>
</p>

## Overview

Clapi is built on **Kotlin**, **Jetpack Compose** (Compose Multiplatform), and the native Rust-based **Nucleus** desktop backend **Tao** ([NucleusFramework/Nucleus](https://github.com/NucleusFramework/Nucleus)) for native windowing, Ahead-Of-Time (AOT) startup caching, and cross-platform packaging (soon also GraalVM native based with even lower memory usages/size). This techstack is vastly superior to typical Electron-Based apps like Postman or Insomnia which waste hundreds of megabytes in size for embedded browsers.

In the spirit of open-source transparency, this page indexes the primary runtime libraries used by Clapi, with direct links to their official GitHub source repositories and licenses.

For exact library coordinates, pinned versions, and build plugins, refer to the Gradle version catalog on the `main` branch:
👉 [**`gradle/libs.versions.toml`**](https://github.com/MakeForge/Clapi/blob/main/gradle/libs.versions.toml)

## Runtime Libraries Catalog

| Library | License | Upstream GitHub Repository | Purpose in Clapi |
|---|---|---|---|
| **Kotlin** | Apache-2.0 | [JetBrains/kotlin](https://github.com/JetBrains/kotlin) | Core programming language |
| **Kotlin Coroutines** | Apache-2.0 | [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Asynchronous runtime & concurrency |
| **Kotlinx Serialization** | Apache-2.0 | [Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Type-safe JSON & YAML serialization |
| **Kotlinx DateTime** | Apache-2.0 | [Kotlin/kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) | Multiplatform date and time handling |
| **Compose Multiplatform** | Apache-2.0 | [JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform) | Declarative Jetpack Compose desktop UI toolkit |
| **Nucleus (Tao)** | MIT | [NucleusFramework/Nucleus](https://github.com/NucleusFramework/Nucleus) | Native Rust-based Tao windowing backend, AOT cache, and native packaging |
| **FileKit** | MIT | [vinceglb/FileKit](https://github.com/vinceglb/FileKit) | Native cross-platform file picker & save dialogs |
| **Ktor** | Apache-2.0 | [ktorio/ktor](https://github.com/ktorio/ktor) | Network engine, HTTP, WebSockets, OAuth2, and embedded MCP server |
| **KSafe** | Apache-2.0 | [ioannisa/ksafe](https://github.com/ioannisa/ksafe) | Hardware-accelerated local AES-256-GCM encryption |
| **Koin** | Apache-2.0 | [InsertKoinIO/koin](https://github.com/InsertKoinIO/koin) | Dependency injection framework |
| **Javet** | Apache-2.0 | [caoccao/Javet](https://github.com/caoccao/Javet) | Embedded Google V8 JavaScript engine for scripts |
| **gRPC Java** | Apache-2.0 | [grpc/grpc-java](https://github.com/grpc/grpc-java) | gRPC HTTP/2 transport & reflection client |
| **Protocol Buffers** | BSD-3-Clause | [protocolbuffers/protobuf](https://github.com/protocolbuffers/protobuf) | Protobuf schema compilation & JSON mapping |
| **protoc-jar** | Apache-2.0 | [os72/protoc-jar](https://github.com/os72/protoc-jar) | Embedded `protoc` runner for `.proto` descriptor parsing |
| **RabbitMQ Java Client** | Apache-2.0 / MPL-2.0 | [rabbitmq/rabbitmq-java-client](https://github.com/rabbitmq/rabbitmq-java-client) | RabbitMQ AMQP 0-9-1 Publish, Subscribe, and RPC client |
| **KeePassXC Proxy Access** | MIT | [purejava/keepassxc-proxy-access](https://github.com/purejava/keepassxc-proxy-access) | Native messaging proxy client for KeePassXC |
| **Keeper Secrets Manager** | MIT | [Keeper-Security/secrets-manager](https://github.com/Keeper-Security/secrets-manager) | Keeper vault secret resolution SDK |
| **NetworkNT Schema Validator** | Apache-2.0 | [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator) | MCP & JSON Schema validation engine |
| **Kaml** | Apache-2.0 | [charleskorn/kaml](https://github.com/charleskorn/kaml) | YAML parser for OpenCollection & OpenAPI |
| **Swagger Parser** | Apache-2.0 | [swagger-api/swagger-parser](https://github.com/swagger-api/swagger-parser) | OpenAPI 3.0 / 3.1 specification importer |
| **Jackson** | Apache-2.0 | [FasterXML/jackson](https://github.com/FasterXML/jackson) | JSON & YAML processing for schema validation and OpenAPI parsing |
| **Guava** | Apache-2.0 | [google/guava](https://github.com/google/guava) | Core utilities supporting gRPC and Protobuf |
| **tinylog** | Apache-2.0 | [tinylog-org/tinylog](https://github.com/tinylog-org/tinylog) | Lightweight structured logging backend |
| **kotlin-logging** | Apache-2.0 | [oshai/kotlin-logging](https://github.com/oshai/kotlin-logging) | Idiomatic Kotlin logging facade |
| **SLF4J** | MIT | [qos-ch/slf4j](https://github.com/qos-ch/slf4j) | Logging API facade |
| **JSON-java (`org.json`)** | Public Domain | [stleary/JSON-java](https://github.com/stleary/JSON-java) | Lightweight JSON parsing support |

::: tip Version Catalog
To view the authoritative and pinned coordinates, dependencies, and version definitions used by Clapi, see the version catalog on GitHub:
[`gradle/libs.versions.toml`](https://github.com/MakeForge/Clapi/blob/main/gradle/libs.versions.toml).
:::
