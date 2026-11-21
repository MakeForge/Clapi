# Quickstart & Verification Guide: Desktop Release Artifact Size Optimization

**Branch**: `022-release-artifact-size-optimization`
**Date**: 2026-11-14

---

## Prerequisites

- **JDK**: 21 (with `jlink` and `jdeps` available in `$JAVA_HOME/bin`)
- **OS**: Linux (`dpkg-deb` for `.deb` inspection), macOS, or Windows
- **CLI Wrapper**: Always prefix commands with `rtk` (e.g., `rtk gradlew ...`)

---

## Step-by-Step Verification & Measurement Commands

### 1. Measure Baseline / Post-Optimization Artifact Sizes
Run the release size analyzer task to inspect `desktopRuntimeClasspath` (total MB and top 25 JARs), `Clapi-{version}.jar` (compressed/uncompressed size and top packages/files), and native installer sizes:

```bash
rtk gradlew :ui-desktop:analyzeReleaseSize
```

### 2. Verify Headless Packaged Fat JAR Runtime
Execute the standalone fat JAR through its headless startup and shutdown verification path:

```bash
rtk gradlew :ui-desktop:verifyPackagedRuntime
```
* **Expected Outcome**: Exits with code `0` with no `NoClassDefFoundError`, `ServiceConfigurationError`, or missing resource exceptions.

### 3. Verify gRPC Client (`grpc-okhttp`) & `protoc-jar` Schema Parsing
Run the `core-network` desktop test suite (which compiles `.proto` files via `ProtocJarParser` and executes unary gRPC calls, metadata propagation, and error handling against an embedded server):

```bash
rtk gradlew :core-network:desktopTest --rerun-tasks
```

### 4. Verify OpenAPI 3.0+ Import & Swagger 2.0 Rejection (`core-storage`)
Run the `core-storage` test suite to confirm that excluding `swagger-parser-v2-converter` preserves all OpenAPI 3.0+ YAML/JSON imports and still cleanly rejects Swagger 2.0 files:

```bash
rtk gradlew :core-storage:allTests --rerun-tasks
```

### 5. Verify GraalJS Scripting with Slimmed ICU4J Resources (`core-scripting` & `qa-tests`)
Run the scripting unit tests and end-to-end integration tests:

```bash
rtk gradlew :core-scripting:allTests :qa-tests:jvmTest
```

### 6. Build & Inspect the Native Installer
Build the native `.deb` (or platform-equivalent) installer and re-run the size analyzer:

```bash
rtk gradlew :ui-desktop:packageDeb :ui-desktop:analyzeReleaseSize
```
* **Expected Outcomes**:
  * `Clapi-{version}.jar` is reduced from `132.80 MB` to **~108.7 MB** (`-24.1 MB` / `-18.2%`).
  * `desktopRuntimeClasspath` is reduced from `183 JARs (158.97 MB)` to **~158 JARs (~133.4 MB with transform / ~142.5 MB without transform)**.
  * `clapi_{version}_amd64.deb` is reduced from `133.11 MB` (`main`) / `149.05 MB` (`main-release`) to **~110.8 MB**.
