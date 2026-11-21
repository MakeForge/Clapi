# Implementation Plan: Desktop Release Artifact Size Optimization

**Branch**: `022-release-artifact-size-optimization` | **Date**: 2026-11-14 | **Spec**: [spec.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/spec.md)

**Input**: Feature specification from `/specs/022-release-artifact-size-optimization/spec.md`

---

## Summary

Reduce the size of Clapi's desktop release artifacts (`desktopRuntimeClasspath`, standalone universal fat JAR `Clapi-{version}.jar`, and native platform installers `.deb`/`.dmg`/`.msi`/`.rpm`) by eliminating unused transitive dependencies, replacing shaded native gRPC transport with pure-JVM `grpc-okhttp`, stripping obsolete `protoc` 2.6.1 binaries and unused ICU4J localization/break-iterator data tables, removing unused Compose Material 2 and UI tooling from production runtime, and explicitly restricting bundled JRE modules in `nativeDistributions`.

Every improvement has been empirically measured against the `v1.20.2` baseline (`158.97 MB` classpath / `132.80 MB` fat JAR / `133.11 MB` `main` `.deb` & `149.05 MB` `main-release` `.deb`) and will be verified incrementally using `:ui-desktop:analyzeReleaseSize` so exact actual savings are recorded per step.

---

## Technical Context

**Language/Version**: Kotlin Multiplatform 2.1.21 (`jvm("desktop")` target), JDK 21
**Primary Dependencies**: Compose Multiplatform 1.8.1, Ktor 3.6.1, gRPC 1.85.1 (`grpc-okhttp` replacing `grpc-netty-shaded` in `desktopMain`), GraalVM Polyglot JS 24.1.1, Swagger Parser 2.1.48, Protoc-Jar 3.11.4
**Storage**: File-based OpenCollection JSON/YAML & SQLDelight (`core-storage`)
**Testing**: `kotlin.test` + JUnit 4 (`:core-network:desktopTest`, `:core-storage:allTests`, `:core-scripting:allTests`, `:ui-shared:desktopTest`, `:ui-desktop:desktopTest`, `:qa-tests:jvmTest`, `:ui-desktop:verifyPackagedRuntime`)
**Target Platform**: Desktop JVM (Linux `x64`, macOS `arm64`/`x64`, Windows `x64`)
**Project Type**: Desktop Application (Kotlin Multiplatform with Compose Desktop)
**Performance Goals**: Zero runtime performance or startup degradation; >=15% (~24 MB) reduction in Fat JAR size and >=20% (~22–38 MB) reduction in native installer size
**Constraints**:
- Must preserve universal cross-platform compatibility of `Clapi-{version}.jar` (keep all three OS binaries under `bin/3.11.4/*` for `protoc-jar`)
- Must preserve 100% GraalJS ES2022+ scripting compatibility (`org.graalvm.shadowed.icu4j` classes must remain loadable)
- Must not enable ProGuard bytecode shrinking (`Q3: Option A`)
**Scale/Scope**: 5 build/config files (`gradle/libs.versions.toml`, `core-storage/build.gradle.kts`, `core-network/build.gradle.kts`, `ui-shared/build.gradle.kts`, `ui-desktop/build.gradle.kts`) and 1 gRPC client implementation file (`NettyDynamicGrpcClient.kt`)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Architecture Boundaries (`docs/architecture.md` & `DEVELOPMENT.md`)**:
  - `ui-desktop` depends on `ui-shared` only: **PASS**
  - `core-network`, `core-storage`, `core-scripting` remain independent of UI modules: **PASS**
  - Public APIs and `expect`/`actual` contracts remain unchanged: **PASS**
- **Test-First / Verification Gate**:
  - All existing unit, integration, and E2E tests plus `verifyPackagedRuntime` must pass at every step: **PASS**

---

## Empirical Measurements & Expected Benefit Per Improvement

Detailed byte-level breakdowns from inspecting `Clapi-1.20.2.jar` (`139,245,698` bytes) and `clapi_1.20.2_amd64.deb` are documented in [research.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/research.md).

| Step | Improvement Description | `desktopRuntimeClasspath` Delta | Fat JAR (`Clapi-1.20.2.jar`) Delta | Native Installer (`.deb`) Delta | Risk & Verification |
|---|---|---|---|---|---|
| **Baseline** | Current `v1.20.2` state | **158.97 MB** (183 JARs) | **132.80 MB** (`139.25 MB` bytes) | **133.11 MB** (`main`)<br>**149.05 MB** (`main-release`) | — |
| **Step 1** | **Exclude `swagger-parser-v2-converter`** in `core-storage` (removes `rhino`, `httpclient`, `httpcore`, `joda-time`, `libphonenumber`, `commons-codec`, `swagger-core` 1.x, `json-schema-validator` 2.x — 22 JARs total) | **-4.99 MB** (-22 JARs) | **-4.40 MB** (`-10.11 MB` raw) | **-4.20 MB** | **Very Low** — Verify with `:core-storage:allTests` (OpenAPI v3 import + Swagger v2 rejection) |
| **Step 2** | **Switch gRPC Client to `grpc-okhttp`** in `core-network` `desktopMain` (move `grpc-netty-shaded` to `desktopTest`; removes 5 embedded `netty_tcnative` native libs + shaded Netty classes) | **-9.81 MB** (net) | **-9.11 MB** (`-24.00 MB` raw) | **-7.50 MB** | **Low** — Verify with `:core-network:desktopTest` (unary plaintext, TLS, metadata, errors) |
| **Step 3** | **Exclude Compose Material 2 (`material-desktop`) & `ui-shared` `uiTooling`** (0 files use `androidx.compose.material.*`; previews live only in `ui-desktop`) | **-1.69 MB** (-3 JARs) | **-1.54 MB** (`-4.10 MB` raw) | **-1.50 MB** | **Very Low** — Verify with `:ui-shared:desktopTest` and `:ui-desktop:desktopTest` |
| **Step 4** | **Strip Obsolete `protoc-jar` `bin/2.6.1/**`** (via `packageClapiJar` & `ArtifactTransform` so both Fat JAR and native installers drop legacy 2.6.1 binaries while keeping `bin/3.11.4/**`) | **-1.83 MB** (with transform) | **-1.83 MB** (`-5.70 MB` raw) | **-1.83 MB** (or **-5.16 MB** if foreign OS `3.11.4` stripped in `.deb`) | **Zero** — Verify with `ProtocJarParserTest` & `DynamicGrpcClientTest` |
| **Step 5** | **Strip Unused ICU4J Data Tables in GraalJS** (`brkitr`, `coll`, `translit`, `rbnf`, `lang`, `curr`, `unit`, `region` under `icudt74b/`, keeping all `.class` files, root `.res`/`.icu`, and `zone/**`) | **-7.26 MB** (with transform) | **-7.26 MB** (`-17.21 MB` raw) | **-7.26 MB** (with transform) | **Low** — Verify with `:core-scripting:allTests`, `:qa-tests:jvmTest`, & `verifyPackagedRuntime` |
| **Step 6** | **Configure Explicit JRE `modules(...)`** in `ui-desktop` `nativeDistributions` (14 verified modules vs 16 in `main-release`, plus ensuring `jdk.unsupported`, `java.sql`, `java.naming`, `jdk.crypto.ec` are present) | *0 MB* | *0 MB* | **-5.0 MB** vs `main-release` (+ **~20 MB** if `jlink` zip-6 compression applied) | **Low** — Verify with `:ui-desktop:packageDeb` and binary smoke test |
| **Total** | **Cumulative Projected Result** | **~133.39 MB** (**-25.58 MB / -16.1%**, ~158 JARs) | **~108.66 MB** (**-24.14 MB / -18.2%**) | **~110.8 MB** (or **~90 MB** with compressed `jlink` runtime; **-22 to -58 MB**) | — |

---

## Implementation Strategy (Step-by-Step Execution & Measurement Protocol)

To guarantee we know the exact real-world benefit of each individual change, implementation will proceed sequentially through the 6 steps above. After **each** step:
1. Apply the single improvement.
2. Run `rtk gradlew :ui-desktop:analyzeReleaseSize :ui-desktop:verifyPackagedRuntime` and the relevant module test suite.
3. Record the exact measured `desktopRuntimeClasspath` MB, `Clapi-1.20.2.jar` MB, and delta in the optimization report.
4. After all JAR/dependency optimizations are verified, build `:ui-desktop:packageDeb` and record the final `.deb` installer size.

---

## Project Structure

### Documentation (this feature)

```text
specs/022-release-artifact-size-optimization/
├── plan.md              # This implementation plan with empirical measurements
├── research.md          # Phase 0 research and byte-level archive breakdown
├── data-model.md        # Phase 1 build & dependency configuration model
├── quickstart.md        # Phase 1 verification and measurement commands
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Phase 2 actionable task list (/speckit-tasks output)
```

### Source Code (repository root)

```text
gradle/
└── libs.versions.toml                                                                # Add grpc-okhttp library entry

core-storage/
└── build.gradle.kts                                                                  # Exclude swagger-parser-v2-converter from swagger-parser

core-network/
├── build.gradle.kts                                                                  # Replace grpc-netty-shaded with grpc-okhttp in desktopMain; keep grpc-netty-shaded in desktopTest
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/
    └── NettyDynamicGrpcClient.kt                                                     # Use OkHttpChannelBuilder instead of NettyChannelBuilder

ui-shared/
└── build.gradle.kts                                                                  # Remove unused compose.uiTooling / uiToolingPreview from production runtime

ui-desktop/
└── build.gradle.kts                                                                  # Exclude compose material-desktop, strip bin/2.6.1/** & unused icu4j data tables, configure nativeDistributions modules(...)
```

**Structure Decision**: Existing multi-module Kotlin Multiplatform (`jvm("desktop")`) layout is preserved without adding or removing any Gradle subprojects. All optimizations are localized to build scripts and the internal channel builder call in `NettyDynamicGrpcClient.kt`.

---

## Complexity Tracking

No constitution violations. No new modules or external architectural abstractions introduced.
