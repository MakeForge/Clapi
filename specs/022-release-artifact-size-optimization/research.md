# Research & Empirical Size Measurements: Desktop Release Artifact Size Optimization

**Branch**: `022-release-artifact-size-optimization`
**Date**: 2026-11-14
**Baseline Version**: `v1.20.2`

---

## 1. Empirical Baseline Measurements (`v1.20.2`)

Measured via `./gradlew :ui-desktop:analyzeReleaseSize` and `dpkg-deb -c`:

| Artifact / Metric | Baseline Size | Details |
|---|---|---|
| `desktopRuntimeClasspath` | **158.97 MB** (183 JARs) | Sum of all resolved runtime dependency JARs |
| `Clapi-1.20.2.jar` (Standalone Fat JAR) | **132.80 MB** (`139,245,698` bytes) | Merged ZIP archive (`ui-desktop/build/distribute/`) |
| `clapi_1.20.2_amd64.deb` (`packageDeb`) | **133.11 MB** | Standard installer published by `.github/workflows/release.yml` |
| `clapi_1.20.2_amd64.deb` (`packageReleaseDeb`) | **149.05 MB** | Release installer with wider 16-module JRE image (`main-release`) |
| Bundled JRE Runtime (`lib/runtime/lib/modules`) | **52.71 MB** (`55,269,278` bytes) | Uncompressed `jlink` module image inside `main/deb` (87 MB total `lib/runtime` dir) |

---

## 2. Per-Improvement Research, Decisions & Exact Measured Savings

### Improvement 1: Exclude `swagger-parser-v2-converter` & Legacy Transitive Dependencies (`core-storage`)

* **Decision**: Depend on `io.swagger.parser.v3:swagger-parser` with `exclude(group = "io.swagger.parser.v3", module = "swagger-parser-v2-converter")` in `core-storage/build.gradle.kts` (or depend directly on `io.swagger.parser.v3:swagger-parser-v3`).
* **Rationale**:
  * `OpenApiImporter.kt` (lines 37–42 & 61–65) explicitly rejects Swagger 2.0 before parsing (`"Swagger 2.0 is not supported. Please provide an OpenAPI v3.0+ specification."`).
  * `swagger-parser-v2-converter` is only invoked when converting Swagger 2.0 specs to OpenAPI 3.0. Yet it transitively pulls in **22 legacy JARs** that are never used at runtime.
* **Exact Measured Size Breakdown**:

  | Transitive Artifact Dropped | JAR Size (`desktopRuntimeClasspath` / `.deb`) | Compressed in Fat JAR | Uncompressed in Fat JAR |
  |---|---|---|---|
  | `org.mozilla:rhino:1.7.15.1` | 1.35 MB (`1,412,631` B) | 1.25 MB | 2.66 MB |
  | `org.apache.httpcomponents:httpclient:4.5.14` | 0.75 MB (`785,639` B) | 0.67 MB | 1.66 MB |
  | `joda-time:joda-time:2.10.5` | 0.61 MB (`643,043` B) | 0.52 MB | 1.36 MB |
  | `com.googlecode.libphonenumber:libphonenumber:8.11.1` | 0.33 MB (`344,549` B) | 0.31 MB | 0.56 MB |
  | `commons-codec:commons-codec:1.11` | 0.32 MB (`335,042` B) | 0.28 MB | 0.62 MB |
  | `org.apache.httpcomponents:httpcore:4.4.16` | 0.31 MB (`327,891` B) | 0.28 MB | 0.68 MB |
  | `com.github.java-json-tools:json-schema-validator:2.2.14` + `core:1.2.14` + `json-patch:1.13` + `jackson-coreutils` + `uri-template` + `msg-simple` + `btf` | 0.57 MB (`598,080` B) | 0.45 MB | 1.04 MB |
  | `io.swagger:swagger-core:1.6.16` + `swagger-models:1.6.16` + `swagger-parser:1.0.76` + `swagger-compat-spec-parser:1.0.76` + `swagger-annotations:1.6.16` + `swagger-parser-safe-url-resolver:1.0.76` + `swagger-parser-v2-converter:2.1.48` | 0.56 MB (`582,393` B) | 0.48 MB | 1.15 MB |
  | `net.sf.jopt-simple:jopt-simple:5.0.4` + `javax.validation:validation-api:1.1.0.Final` + `commons-logging:1.2` | 0.19 MB (`203,752` B) | 0.16 MB | 0.38 MB |
  | **Total Savings (Improvement 1)** | **4.99 MB (22 JARs removed)** | **4.40 MB** | **10.11 MB** |

* **Alternatives Considered**: Replacing `swagger-parser` with a custom `kaml`/`kotlinx-serialization` parser was rejected because `swagger-parser-v3` (`~0.65 MB`) handles `$ref` resolution and polymorphic OpenAPI 3.0/3.1 schemas reliably.

---

### Improvement 2: Replace `grpc-netty-shaded` with `grpc-okhttp` (`core-network`)

* **Decision**: Replace `implementation(libs.grpc.netty.shaded)` in `core-network` `desktopMain` with `implementation(libs.grpc.okhttp)` (and `com.squareup.okhttp3:okhttp` / `okio` already on classpath or transitive), and move `libs.grpc.netty.shaded` to `desktopTest` (where `DynamicGrpcClientTest` uses `NettyServerBuilder` for its embedded test server). Update `NettyDynamicGrpcClient.kt` to build channels via `OkHttpChannelBuilder.forAddress(key.host, key.port)` (with `.useTransportSecurity()` / `.usePlaintext()`).
* **Rationale**:
  * `grpc-netty-shaded:1.85.1` is **10.41 MB** (`10,919,120` bytes) and embeds 5 native `netty_tcnative` libraries (`linux_x86_64`, `linux_aarch_64`, `osx_x86_64`, `osx_aarch_64`, `windows_x86_64`) totaling **5.81 MB compressed / 14.74 MB raw**, plus **3.85 MB compressed / 10.43 MB raw** of shaded Netty classes.
  * `NettyDynamicGrpcClient` only uses standard unary channel building (`forAddress`, `useTransportSecurity`, `usePlaintext`).
  * `io.grpc:grpc-okhttp:1.85.1` (`~0.19 MB` + `com.squareup.okhttp:okhttp:2.7.5` `~0.41 MB`, with `okio` already present on `desktopRuntimeClasspath` via KSafe/Kaml) is pure JVM, uses standard JDK 21 TLS (`SSLSocketFactory`), and requires zero native `.so`/`.dll`/`.jnilib` libraries.
* **Exact Measured Size Breakdown**:
  * Removed (`grpc-netty-shaded:1.85.1` + `io.perfmark:perfmark-api` if unused): **-10.41 MB** JAR (`-9.66 MB` compressed / `-25.17 MB` raw in Fat JAR).
  * Added (`io.grpc:grpc-okhttp:1.85.1` + `com.squareup.okhttp:okhttp:2.7.5`): **+0.60 MB** JAR (`+0.55 MB` compressed in Fat JAR).
  * **Total Net Savings (Improvement 2)**:
    * `desktopRuntimeClasspath`: **-9.81 MB**
    * `Clapi-{version}.jar` (Fat JAR): **-9.11 MB compressed** (`-24.0 MB` raw)
    * `.deb` Installer: **~-7.5 MB**

---

### Improvement 3: Strip Obsolete `protoc-jar` `bin/2.6.1/**` Binaries (`ui-desktop`)

* **Decision**: Exclude `bin/2.6.1/**` when packaging `packageClapiJar` (and strip `bin/2.6.1/**` plus foreign-OS `bin/3.11.4/*` binaries from `protoc-jar` when building OS-specific native installers, or via a Gradle `ArtifactTransform` / filtered JAR if applicable).
* **Rationale**:
  * `ProtocJarParser.kt` invokes `Protoc.runProtoc(args, stdout, stderr)`, which defaults to extracting and running the bundled `3.11.4` binary.
  * `bin/2.6.1/` contains 3 legacy Protobuf 2.6.1 executables (`protoc-2.6.1-linux-x86_64.exe`, `protoc-2.6.1-osx-x86_64.exe`, `protoc-2.6.1-windows-x86_64.exe`) that are never called.
  * Note on `bin/3.11.4/*`: In the universal `Clapi-{version}.jar` (built once on macOS in CI and distributed for all OSes), all three `bin/3.11.4/*` binaries (**4.81 MB compressed / 18.69 MB raw**) MUST be kept so Linux, macOS, and Windows fat-JAR users can compile `.proto` files.
* **Exact Measured Size Breakdown**:
  * `bin/2.6.1/**` in Fat JAR: **3 files, 1.83 MB compressed / 5.70 MB raw**.
  * If foreign-OS `bin/3.11.4/*` + `bin/2.6.1/*` are also stripped for OS-specific native installers (e.g. keeping only `protoc-3.11.4-linux-x86_64.exe` on Linux `.deb`): saves an additional **3.33 MB compressed / 13.84 MB raw** on Linux `.deb` (**5.16 MB total** saved from `protoc-jar`).

---

### Improvement 4: Strip Unused ICU4J Data Tables in GraalJS (`ui-desktop`)

* **Decision**: Retain `org.graalvm.polyglot:js-community:24.1.1` (and `org.graalvm.shadowed:icu4j:24.1.1` classes so `module-info` and `ULocale` load cleanly), and exclude unused heavy ICU4J data subdirectories in `packageClapiJar`:
  * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/brkitr/**` (CJK word/line break dictionary tables — only used by `Intl.Segmenter`)
  * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/translit/**` (ICU transliteration rules — not exposed in standard ECMAScript)
  * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/coll/**` (Locale collation tailoring tables)
  * `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/rbnf/**` (Rule-based number spellout formatting tables)
* **Rationale**:
  * Pre-request and post-response API test scripts use standard JS (`JSON.parse`, `RegExp`, `Date`, `Array`, `String`, `clapi.expect`, `pm.test`).
  * Keeping core root `.res`/`.icu` files (`norm2`, `uprops`, `ucase`, `tzdbNames`, `zone`) ensures standard `Date`, `String.prototype.normalize`, case folding, and regex Unicode property escapes continue to work while shedding heavy dictionary blobs.
* **Exact Measured Size Breakdown**:

  | ICU4J Data Subdirectory | Files | Compressed in Fat JAR | Uncompressed in Fat JAR |
  |---|---|---|---|
  | `icudt74b/brkitr/**` (Break Iterator / `cjdict.dict`) | 39 | **2.26 MB** | 3.29 MB |
  | `icudt74b/coll/**` (Collation tables) | 172 | **1.10 MB** | 3.12 MB |
  | `icudt74b/lang/**` (Display language names) | 512 | **1.13 MB** | 2.66 MB |
  | `icudt74b/curr/**` (Currency display names) | 583 | **0.92 MB** | 2.72 MB |
  | `icudt74b/unit/**` (Measurement unit names) | 503 | **0.88 MB** | 2.65 MB |
  | `icudt74b/region/**` (Region display names) | 512 | **0.54 MB** | 1.14 MB |
  | `icudt74b/translit/**` (Transliteration rules) | 3 | **0.32 MB** | 1.04 MB |
  | `icudt74b/rbnf/**` (Spellout number rules) | 111 | **0.11 MB** | 0.59 MB |
  | **Total Strippable ICU4J Subdirectories** | **2,435** | **7.26 MB** | **17.21 MB** |

  *(Note: Applying the same resource stripping to the `icu4j` and `protoc-jar` JARs packaged into `nativeDistributions` via a Gradle `ArtifactTransform` or packaging task hook saves the same **7.26 MB + 5.16 MB = 12.42 MB** inside `.deb` / `.dmg` / `.msi` installers as well!)*

---

### Improvement 5: Exclude Compose Material 2 (`material-desktop`) & Remove `compose.uiTooling` from `ui-shared`

* **Decision**:
  1. Remove `implementation(compose.uiTooling)` and `implementation(compose.components.uiToolingPreview)` from `ui-shared/build.gradle.kts` (`ui-shared` has zero `@Preview` functions; all `@Preview` functions live in `ui-desktop`).
  2. Exclude `org.jetbrains.compose.material:material` and `org.jetbrains.compose.material:material-desktop` from `desktopRuntimeClasspath` (pulled transitively by `org.jetbrains.compose.desktop:desktop-jvm`, while `org.jetbrains.compose.material:material-ripple` is kept because `material3` uses it).
* **Rationale**:
  * Verified via ripgrep: 0 files in the entire repository import `androidx.compose.material.*`; 100% of UI uses `androidx.compose.material3.*`.
* **Exact Measured Size Breakdown**:
  * `org.jetbrains.compose.material:material-desktop:1.8.1`: **1.62 MB** JAR (`1,699,950` B) / **1.50 MB** compressed (`4.00 MB` raw) in Fat JAR.
  * `ui-tooling-desktop` + `ui-tooling-data-desktop` (if moved to `compileOnly` or kept out of `ui-shared`): **0.07 MB** JAR.
  * **Total Savings (Improvement 5)**: **1.69 MB** on `desktopRuntimeClasspath` / **1.54 MB** compressed in Fat JAR.

---

### Improvement 6: Bundled JRE Runtime Image Optimization (`nativeDistributions` / `jlink`)

* **Discovery & Empirical Finding**:
  * Inspecting `ui-desktop/build/compose/tmp/main/runtime/release` revealed why `main/deb` (`133.11 MB`) is already smaller than `main-release/deb` (`149.05 MB`):
    * When `modules(...)` is **not** specified, Compose Multiplatform's `packageDeb` defaults to `includeAllModules = false` with 7 base modules (`java.base`, `java.datatransfer`, `java.xml`, `java.prefs`, `java.desktop`, `java.logging`, `jdk.crypto.ec`), producing an **87 MB** runtime directory (`52.71 MB` `lib/modules`).
    * However, that default 7-module list is **missing** `jdk.unsupported` (needed by GraalVM Truffle & JNA), `java.sql` (needed by SQLDelight), `java.naming` (needed by gRPC DNS resolver), `java.management` / `java.instrument` (needed by Truffle runtime), and `java.net.http` / `java.security.jgss`.
    * Furthermore, Compose's `createRuntimeImage` task runs `jlink` with `--no-header-files --no-man-pages --strip-debug`, **but does NOT pass `--compress=zip-6` (or `--compress=2`)** by default!
  * **Empirical `jlink` Measurement on JDK 21**:
    * Without `--compress=zip-6`: `lib/runtime` with the required 12 modules is **91 MB** (`lib/modules` = `55.3 MB` uncompressed).
    * With `--compress=zip-6`: `lib/runtime` drops to **59 MB** (`lib/modules` = `24.8 MB`) — a **32 MB raw reduction** in installed JRE footprint!
* **Decision**:
  * Configure `nativeDistributions` in `ui-desktop/build.gradle.kts` with the exact required module list:
    ```kotlin
    modules(
        "java.base",
        "java.datatransfer",
        "java.desktop",
        "java.instrument",
        "java.logging",
        "java.management",
        "java.naming",
        "java.net.http",
        "java.prefs",
        "java.security.jgss",
        "java.sql",
        "java.xml",
        "jdk.crypto.ec",
        "jdk.unsupported",
    )
    ```
  * Additionally, evaluate stripping unused `protoc` (`bin/2.6.1/**` and non-target OS `bin/3.11.4/*`) and `icu4j` dictionary resources from the app JARs packaged into `nativeDistributions` (or via `ArtifactTransform`) so the native `.deb`/`.dmg`/`.msi` installers receive the exact same JAR slimming as `packageClapiJar`.

---

## 3. Consolidated Expected Savings Summary Table

| # | Optimization | `desktopRuntimeClasspath` Savings | Fat JAR (`Clapi-1.20.2.jar`) Savings | Native Installer (`.deb`) Savings | Risk Level |
|---|---|---|---|---|---|
| **1** | Exclude `swagger-parser-v2-converter` (drops Rhino, HttpClient4, Joda, LibPhoneNumber, Swagger v1/v2, fge) | **-4.99 MB** (-22 JARs) | **-4.40 MB** (-10.11 MB raw) | **-4.20 MB** | Very Low |
| **2** | Switch gRPC client from `grpc-netty-shaded` to `grpc-okhttp` | **-9.81 MB** (net) | **-9.11 MB** (-24.00 MB raw) | **-7.50 MB** | Low |
| **3** | Strip obsolete `protoc-jar` `bin/2.6.1/**` (+ foreign OS in native installer) | *0 MB* (or **-1.83 MB** via transform) | **-1.83 MB** (-5.70 MB raw) | **-1.83 to -5.16 MB** | Zero |
| **4** | Strip unused ICU4J data tables (`brkitr`, `coll`, `translit`, `rbnf`, `lang`, `curr`, `unit`, `region`) | *0 MB* (or **-7.26 MB** via transform) | **-7.26 MB** (-17.21 MB raw) | **-7.26 MB** (with transform) | Low |
| **5** | Exclude Compose Material 2 (`material-desktop`) & `ui-shared` `uiTooling` | **-1.69 MB** (-3 JARs) | **-1.54 MB** (-4.10 MB raw) | **-1.50 MB** | Very Low |
| **6** | Explicit `modules(...)` + `ArtifactTransform` for slimmed `protoc-jar` & `icu4j` in `nativeDistributions` | *(Included above via ArtifactTransform)* | *(Included above)* | **-10 to -22 MB** (vs `main` / `main-release`) | Low |
| **Total** | **Combined Projected Impact** | **-16.49 MB** (or **-25.58 MB** with ArtifactTransform) | **-24.14 MB (-18.2%)**<br>`132.80 MB` → **~108.66 MB** | **-22.3 to -38.2 MB**<br>`149.05 MB` (`main-release`) / `133.11 MB` (`main`) → **~110.8 MB** | **Low** |

*(Note: During implementation, we will execute `:ui-desktop:analyzeReleaseSize` and `:ui-desktop:packageDeb` after each individual step and record the exact measured delta in the build verification log.)*

---

## 4. Incremental Actual Measurements Log (Live Build Verification)

| Step | Task ID | Optimization Applied | `desktopRuntimeClasspath` (MB / JARs) | Step Classpath Delta | `Clapi-1.20.2.jar` Fat JAR (MB) | Step Fat JAR Delta | `.deb` Installer (`main` / `main-release`) | Verification Status |
|---|---|---|---|---|---|---|---|---|
| **Baseline** | `T001` | Initial `v1.20.2` baseline | **158.97 MB** (183 JARs) | — | **132.80 MB** (`139,245,698` B) | — | `133.11 MB` (`main`) / `149.05 MB` (`main-release`) | PASS |
| **Step 1** | `T003`–`T004` | Exclude `swagger-parser-v2-converter` in `core-storage` | **153.98 MB** (159 JARs) | **-4.99 MB** (-24 JARs) | **128.00 MB** | **-4.80 MB** | *(Measured after all US1/US2 steps)* | PASS (`:core-storage:allTests`) |
| **Step 2** | `T005`–`T006` | Exclude Compose Material 2 (`material-desktop`) & `ui-shared` `uiTooling` | **152.29 MB** (156 JARs) | **-1.69 MB** (-3 JARs) | **126.32 MB** | **-1.68 MB** | *(Measured after all US1/US2 steps)* | PASS (`:ui-shared:desktopTest`, `:ui-desktop:desktopTest`) |
| **Step 3** | `T007`–`T008` | Strip obsolete `protoc-jar` `bin/2.6.1/**` binaries in `ui-desktop` | **152.29 MB** (156 JARs) | **0.00 MB** *(in-archive filter)* | **124.49 MB** | **-1.83 MB** (-5.70 MB raw) | *(Measured after all US1/US2 steps)* | PASS (`:core-network:desktopTest`, `:ui-desktop:verifyPackagedRuntime`) |
| **Phase 1–3 Subtotal** | `T001`–`T008` | **Cumulative Phase 1–3 (US1 MVP) Savings** | **152.29 MB** (156 JARs) | **-6.68 MB** (-27 JARs) | **124.49 MB** | **-8.31 MB (-6.3%)** | — | **100% PASS** |
| **Step 4** | `T009`–`T010` | Configure explicit 14-module JRE `modules(...)` in `ui-desktop` `nativeDistributions` | **152.29 MB** (156 JARs) | *0.00 MB* | **124.49 MB** | *0.00 MB* | **`128.35 MB` (`main`)** (**-4.76 MB** vs `133.11 MB` `main`, **-20.70 MB** vs `149.05 MB` `main-release`) | PASS (`:ui-desktop:packageDeb`) |
| **Step 5** | `T011`–`T013` | Switch gRPC client from `grpc-netty-shaded` to `grpc-okhttp` in `core-network` | **142.13 MB** (156 JARs) | **-10.16 MB** | **114.32 MB** | **-10.17 MB** | *(Measured in Phase 6)* | PASS (`:core-network:desktopTest`, `verifyPackagedRuntime`) |
| **Step 6** | `T014`–`T015` | Strip unused ICU4J data tables (`brkitr`, `coll`, `lang`, `curr`, `unit`, `region`, `translit`, `rbnf`) & legacy `protoc` `bin/2.*/**` | **142.13 MB** (156 JARs) | *0.00 MB* *(in-JAR filter)* | **103.53 MB** | **-10.79 MB** (`-7.28 MB` ICU4J + `-3.51 MB` `bin/2.4.1` & `bin/2.5.0`) | *(Measured in Phase 6)* | PASS (`:core-scripting:allTests`, `:qa-tests:jvmTest`, `verifyPackagedRuntime`) |
| **Final (Step 7)** | `T016`–`T017` | **All Phases 1–6 Combined (Final Release Artifacts)** | **142.13 MB** (156 JARs) | **-16.84 MB (-10.6%)**<br>(-27 JARs) | **103.53 MB** (`108,560,673` B) | **-29.27 MB (-22.0%)** | **`119.31 MB` (`main/deb`)**<br>(**-13.80 MB / -10.4%** vs `133.11 MB` `main`;<br>**-29.74 MB / -20.0%** vs `149.05 MB` `main-release`) | **100% PASS** (All module test suites + `verifyPackagedRuntime` + `packageDeb`) |








