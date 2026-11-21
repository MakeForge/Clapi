# Data Model & Build Configuration Entities: Desktop Release Artifact Size Optimization

**Branch**: `022-release-artifact-size-optimization`
**Date**: 2026-11-14

---

## Overview

This feature does not introduce new domain persistence models or user-facing data schemas. Instead, it modifies the **Build & Packaging Configuration Model** across `gradle/libs.versions.toml`, `core-network`, `core-storage`, `ui-shared`, and `ui-desktop`.

---

## 1. Version Catalog & Dependency Graph Model

### `gradle/libs.versions.toml`
* **Added Library Entry**:
  * `grpc-okhttp = { module = "io.grpc:grpc-okhttp", version.ref = "grpc" }`
* **Retained Library Entries**:
  * `grpc-netty-shaded = { module = "io.grpc:grpc-netty-shaded", version.ref = "grpc" }` *(Moved from `core-network:desktopMain` to `core-network:desktopTest` for in-process `NettyServerBuilder` tests)*

### Module Dependency Transitions

| Module | Source Set | Current Configuration | Target Configuration |
|---|---|---|---|
| `core-storage` | `desktopMain` | `implementation(libs.swagger.parser)` | `implementation(libs.swagger.parser) { exclude(group = "io.swagger.parser.v3", module = "swagger-parser-v2-converter") }` |
| `core-network` | `desktopMain` | `implementation(libs.grpc.netty.shaded)` | `implementation(libs.grpc.okhttp)` |
| `core-network` | `desktopTest` | *(inherits `grpc-netty-shaded` from `desktopMain`)* | `implementation(libs.grpc.netty.shaded)` |
| `ui-shared` | `desktopMain` | `implementation(compose.uiTooling)` | *(Removed — previews live exclusively in `ui-desktop`)* |
| `ui-desktop` | `desktopRuntimeClasspath` | Includes `org.jetbrains.compose.material:material-desktop` transitively | `configurations.named("desktopRuntimeClasspath") { exclude(group = "org.jetbrains.compose.material", module = "material"); exclude(group = "org.jetbrains.compose.material", module = "material-desktop") }` |

---

## 2. Resource Slimming & Artifact Transform Model (`ui-desktop`)

To ensure **both** the standalone fat JAR (`packageClapiJar`) and the native OS installers (`packageDeb`, `packageDmg`, `packageMsi`, `packageRpm`) benefit from stripping dead resources inside `protoc-jar` and `icu4j`, resource filtering is modeled as a deterministic exclusion set:

### Excluded Archive Entry Patterns

1. **`protoc-jar` Legacy Binaries**:
   * Pattern: `bin/2.6.1/**`
   * Reason: `ProtocJarParser` uses `3.11.4` exclusively; `2.6.1` binaries (`5.70 MB` raw / `1.83 MB` compressed) are never loaded.
2. **`icu4j` Unused Localization & Dictionary Tables**:
   * Patterns:
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/brkitr/**` (`2.26 MB` comp / `3.29 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/coll/**` (`1.10 MB` comp / `3.12 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/lang/**` (`1.13 MB` comp / `2.66 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/curr/**` (`0.92 MB` comp / `2.72 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/unit/**` (`0.88 MB` comp / `2.65 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/region/**` (`0.54 MB` comp / `1.14 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/translit/**` (`0.32 MB` comp / `1.04 MB` raw)
     * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/rbnf/**` (`0.11 MB` comp / `0.59 MB` raw)
   * Preserved: All `.class` files in `org/graalvm/shadowed/com/ibm/icu/**`, root `icudt74b/*.res`, `icudt74b/*.icu` (`uprops.icu`, `ucase.icu`, `norm2/*.nrm`), and `icudt74b/zone/**` (required for `Date` timezones, regex Unicode properties, and `ULocale` initialization).

---

## 3. Native Distribution Runtime Module Model (`ui-desktop`)

### `compose.desktop.application.nativeDistributions.modules`
Explicitly declared JDK 21 runtime modules for `jlink`:

| Module | Required By |
|---|---|
| `java.base` | Core JVM runtime |
| `java.datatransfer` | AWT/Compose clipboard & drag-and-drop |
| `java.desktop` | Compose Multiplatform Desktop / Skiko / AWT / Swing |
| `java.instrument` | GraalVM Truffle runtime |
| `java.logging` | SLF4J / JUL logging |
| `java.management` | GraalVM Truffle runtime & JMX |
| `java.naming` | gRPC DNS `NameResolver` (`javax.naming.directory`) |
| `java.net.http` | JDK HTTP client & WebSocket support |
| `java.prefs` | `java.util.prefs.Preferences` (`PlatformStorage` settings persistence) |
| `java.security.jgss` | Kerberos / GSS-API auth in network clients |
| `java.sql` | SQLDelight runtime (`java.sql.*`) |
| `java.xml` | XML parsing in Jackson / Swagger / KeePassXC |
| `jdk.crypto.ec` | Elliptic Curve cryptography for TLS 1.2 / 1.3 HTTPS & gRPC TLS |
| `jdk.unsupported` | `sun.misc.Unsafe` used by GraalVM Truffle, JNA, and Protobuf |
