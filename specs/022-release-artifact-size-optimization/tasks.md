---
description: "Task list for Desktop Release Artifact Size Optimization"
---

# Tasks: Desktop Release Artifact Size Optimization

**Input**: Design documents from `/specs/022-release-artifact-size-optimization/`

**Prerequisites**: [plan.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/plan.md) (required), [spec.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/spec.md) (required for user stories), [research.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/research.md), [data-model.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/data-model.md), [quickstart.md](file:///home/user/IdeaProjects/Clapi/specs/022-release-artifact-size-optimization/quickstart.md)

**Tests**: Existing module unit, integration, and packaged runtime verification suites are executed after each incremental optimization to validate zero regressions and record exact measured size deltas.

**Organization**: Tasks are grouped by user story (`US1`, `US2`, `US3`) to enable independent implementation, testing, and empirical measurement of each optimization.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Baseline Verification & Measurement Log Initialization)

**Purpose**: Confirm baseline metrics (`158.97 MB` `desktopRuntimeClasspath`, `132.80 MB` `Clapi-1.20.2.jar`, `133.11 MB` `main` `.deb` / `149.05 MB` `main-release` `.deb`) in `specs/022-release-artifact-size-optimization/research.md` before applying incremental optimizations.

- [ ] T001 Verify baseline `analyzeReleaseSize` output and initialize the Incremental Actual Measurements tracking table in `specs/022-release-artifact-size-optimization/research.md`

---

## Phase 2: Foundational (Version Catalog Prerequisites)

**Purpose**: Register version catalog entries required by downstream user stories before modifying module build scripts.

**⚠️ CRITICAL**: Complete before User Story phases that consume new catalog entries.

- [ ] T002 Add `grpc-okhttp = { module = "io.grpc:grpc-okhttp", version.ref = "grpc" }` under `[libraries]` in `gradle/libs.versions.toml`

**Checkpoint**: Version catalog ready — User Story implementation and per-step measurements can begin.

---

## Phase 3: User Story 1 - Eliminate Redundant & Unused Dependencies and Obsolete Natives (Priority: P1) 🎯 MVP

**Goal**: Remove the unused Swagger 2.0 compatibility converter (`swagger-parser-v2-converter` and its 21 legacy transitive JARs including `rhino`, `httpclient`, `joda-time`, `libphonenumber`, `json-schema-validator` 2.x), remove unused Compose Material 2 (`material-desktop`) and `uiTooling` from production runtime, and strip obsolete `protoc-jar` `bin/2.6.1/**` binaries while preserving `bin/3.11.4/**` across all target OSes.

**Independent Test**: Run `rtk gradlew :core-storage:allTests :ui-shared:desktopTest :ui-desktop:desktopTest :ui-desktop:verifyPackagedRuntime :ui-desktop:analyzeReleaseSize` and verify that OpenAPI 3.0+ imports succeed, Swagger 2.0 specs are cleanly rejected, `bin/2.6.1/**` is absent from `Clapi-1.20.2.jar`, and exact size savings are recorded per sub-step.

### Implementation & Measurement for User Story 1

- [ ] T003 [P] [US1] Exclude `io.swagger.parser.v3:swagger-parser-v2-converter` from `implementation(libs.swagger.parser)` in `core-storage/build.gradle.kts`
- [ ] T004 [US1] Run `rtk gradlew :core-storage:allTests :ui-desktop:analyzeReleaseSize` and record the exact measured `desktopRuntimeClasspath` and Fat JAR size delta for `swagger-parser-v2-converter` exclusion in `specs/022-release-artifact-size-optimization/research.md`
- [ ] T005 [P] [US1] Remove `implementation(compose.uiTooling)` from `desktopMain` in `ui-shared/build.gradle.kts` and exclude `org.jetbrains.compose.material:material` and `org.jetbrains.compose.material:material-desktop` from `desktopRuntimeClasspath` in `ui-desktop/build.gradle.kts`
- [ ] T006 [US1] Run `rtk gradlew :ui-shared:desktopTest :ui-desktop:desktopTest :ui-desktop:analyzeReleaseSize` and record the exact measured size delta for Compose Material 2 and UI Tooling removal in `specs/022-release-artifact-size-optimization/research.md`
- [ ] T007 [US1] Exclude obsolete `bin/2.6.1/**` binaries from `packageClapiJar` (and via a slimming `ArtifactTransform` or packaging filter so native installers also receive the stripped `protoc-jar`) while preserving `bin/3.11.4/**` in `ui-desktop/build.gradle.kts`
- [ ] T008 [US1] Run `rtk gradlew :core-network:desktopTest :ui-desktop:verifyPackagedRuntime :ui-desktop:analyzeReleaseSize` and record the exact measured size delta for `protoc-jar` `bin/2.6.1/**` removal in `specs/022-release-artifact-size-optimization/research.md`

**Checkpoint**: User Story 1 complete — ~8.5 MB of dead dependencies and legacy compiler binaries removed, tested, and measured independently.

---

## Phase 4: User Story 2 - Slim Down Embedded Runtime Image in Native Installers (Priority: P2)

**Goal**: Configure an explicit, verified JDK 21 runtime module list in `nativeDistributions` (`java.base`, `java.datatransfer`, `java.desktop`, `java.instrument`, `java.logging`, `java.management`, `java.naming`, `java.net.http`, `java.prefs`, `java.security.jgss`, `java.sql`, `java.xml`, `jdk.crypto.ec`, `jdk.unsupported`) so native installers bundle only required JRE modules while guaranteeing TLS/ECDSA, SQL, preferences, DNS naming, and Truffle unsafe support.

**Independent Test**: Build the native installer via `rtk gradlew :ui-desktop:packageDeb :ui-desktop:analyzeReleaseSize`, inspect the bundled runtime modules in `ui-desktop/build/compose/tmp/main/runtime/release`, and verify the packaged binary starts and passes smoke checks.

### Implementation & Measurement for User Story 2

- [ ] T009 [US2] Configure `modules("java.base", "java.datatransfer", "java.desktop", "java.instrument", "java.logging", "java.management", "java.naming", "java.net.http", "java.prefs", "java.security.jgss", "java.sql", "java.xml", "jdk.crypto.ec", "jdk.unsupported")` inside `compose.desktop.application.nativeDistributions` in `ui-desktop/build.gradle.kts`
- [ ] T010 [US2] Run `rtk gradlew :ui-desktop:packageDeb :ui-desktop:analyzeReleaseSize` and record the measured `.deb` installer size and bundled runtime module list in `specs/022-release-artifact-size-optimization/research.md`

**Checkpoint**: User Story 2 complete — Native installer JRE runtime module set explicitly locked and verified.

---

## Phase 5: User Story 3 - Optimize gRPC Transport & Scripting Resource Footprint (Priority: P3)

**Goal**: Replace `io.grpc:grpc-netty-shaded` (10.41 MB with 5 embedded native `netty_tcnative` libraries) in `core-network` `desktopMain` with pure-JVM `io.grpc:grpc-okhttp` (~0.6 MB total), scoping `grpc-netty-shaded` to `desktopTest` only, and strip unused ICU4J localization/break-iterator dictionary subdirectories (`brkitr/**`, `coll/**`, `lang/**`, `curr/**`, `unit/**`, `region/**`, `translit/**`, `rbnf/**` under `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/`) while keeping GraalJS ES2022+ and all ICU4J `.class`, root `.res`/`.icu`, and `zone/**` resources intact.

**Independent Test**: Run `rtk gradlew :core-network:desktopTest :core-scripting:allTests :qa-tests:jvmTest :ui-desktop:verifyPackagedRuntime :ui-desktop:analyzeReleaseSize` and verify all gRPC unary/TLS/metadata tests and all GraalJS scripting tests pass.

### Implementation & Measurement for User Story 3

- [ ] T011 [P] [US3] Replace `implementation(libs.grpc.netty.shaded)` with `implementation(libs.grpc.okhttp)` in `desktopMain` and add `implementation(libs.grpc.netty.shaded)` to `desktopTest` in `core-network/build.gradle.kts`
- [ ] T012 [US3] Update `NettyDynamicGrpcClient` in `core-network/src/desktopMain/kotlin/io/github/makeforge/clapi/core/network/grpc/NettyDynamicGrpcClient.kt` to replace `io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder` with `io.grpc.okhttp.OkHttpChannelBuilder`
- [ ] T013 [US3] Run `rtk gradlew :core-network:desktopTest :ui-desktop:verifyPackagedRuntime :ui-desktop:analyzeReleaseSize` and record the exact measured size delta for switching from `grpc-netty-shaded` to `grpc-okhttp` in `specs/022-release-artifact-size-optimization/research.md`
- [ ] T014 [US3] Exclude unused ICU4J data subdirectories (`org/graalvm/shadowed/com/ibm/icu/impl/data/icudt74b/brkitr/**`, `coll/**`, `lang/**`, `curr/**`, `unit/**`, `region/**`, `translit/**`, `rbnf/**`) during JAR packaging / `ArtifactTransform` in `ui-desktop/build.gradle.kts` while keeping all `.class` files, root `icudt74b/*` files, and `zone/**`
- [ ] T015 [US3] Run `rtk gradlew :core-scripting:allTests :qa-tests:jvmTest :ui-desktop:verifyPackagedRuntime :ui-desktop:analyzeReleaseSize` and record the exact measured size delta for ICU4J data slimming in `specs/022-release-artifact-size-optimization/research.md`

**Checkpoint**: User Story 3 complete — gRPC transport switched to pure-JVM `grpc-okhttp` (~9.1 MB Fat JAR savings) and ICU4J dictionary bloat stripped (~7.3 MB Fat JAR savings) with 100% test pass rate.

---

## Phase 6: Polish & Cross-Cutting Concerns (Final End-to-End Verification & Benchmarking)

**Purpose**: Rebuild all release distribution targets (`packageClapiJar`, `packageDeb`), run the complete quality gate test suite, and update both `research.md` and `scratch/clapi_desktop_release_size_optimization_plan.md` with the final before/after numbers.

- [ ] T016 Run full build and verification suite (`rtk gradlew :core-network:desktopTest :core-storage:allTests :core-scripting:allTests :editor-core:desktopTest :editor-ui:allTests :ui-shared:desktopTest :ui-desktop:desktopTest :qa-tests:jvmTest :ui-desktop:packageDeb :ui-desktop:verifyPackagedRuntime :ui-desktop:analyzeReleaseSize`)
- [ ] T017 [P] Record final cumulative before/after comparison table across `desktopRuntimeClasspath`, `Clapi-1.20.2.jar`, and `clapi_1.20.2_amd64.deb` in `specs/022-release-artifact-size-optimization/research.md` and `scratch/clapi_desktop_release_size_optimization_plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 — adds `grpc-okhttp` to `gradle/libs.versions.toml` (required by US3).
- **User Story 1 (Phase 3 - P1)**: Can start immediately after Phase 1/2.
- **User Story 2 (Phase 4 - P2)**: Modifies `ui-desktop/build.gradle.kts` (`nativeDistributions`); runs sequentially after US1 to keep per-step `analyzeReleaseSize` measurements isolated.
- **User Story 3 (Phase 5 - P3)**: Depends on T002 (`grpc-okhttp` in `gradle/libs.versions.toml`); runs sequentially after US2 so each step's exact size reduction is measured cleanly.
- **Polish (Phase 6)**: Depends on completion of US1, US2, and US3.

### User Story Dependencies

- **User Story 1 (P1)**: Independent (`core-storage/build.gradle.kts`, `ui-shared/build.gradle.kts`, `ui-desktop/build.gradle.kts`).
- **User Story 2 (P2)**: Independent (`ui-desktop/build.gradle.kts` `nativeDistributions` block).
- **User Story 3 (P3)**: Depends only on T002 (`gradle/libs.versions.toml`); touches `core-network/build.gradle.kts`, `NettyDynamicGrpcClient.kt`, and `ui-desktop/build.gradle.kts`.

### Parallel Opportunities

- If executing purely for speed rather than sequential step-by-step measurement, **T003** (`core-storage/build.gradle.kts`), **T005** (`ui-shared/build.gradle.kts`), and **T011/T012** (`core-network/build.gradle.kts` + `NettyDynamicGrpcClient.kt`) touch completely separate modules and can be edited in parallel.
- To capture isolated empirical measurements after each individual optimization (as planned in `plan.md`), execute the measurement tasks (**T004**, **T006**, **T008**, **T010**, **T013**, **T015**) sequentially after each change.

---

## Parallel Example: Cross-Module Edits

```bash
# Module build script edits that touch independent files:
Task T003: "Exclude swagger-parser-v2-converter in core-storage/build.gradle.kts"
Task T005: "Remove compose.uiTooling in ui-shared/build.gradle.kts"
Task T011: "Switch grpc-netty-shaded to grpc-okhttp in core-network/build.gradle.kts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & Phase 2 (`T001`–`T002`).
2. Complete Phase 3: User Story 1 (`T003`–`T008`).
3. **STOP and VALIDATE**: Verify ~8.5 MB reduction in `desktopRuntimeClasspath` and ~7.8 MB reduction in `Clapi-1.20.2.jar` with zero code changes.

### Incremental Delivery & Measurement

1. **Step 1 (`T003`–`T004`)**: Exclude `swagger-parser-v2-converter` → Measure (`~-4.99 MB` classpath / `~-4.40 MB` Fat JAR).
2. **Step 2 (`T005`–`T006`)**: Remove Material 2 & `uiTooling` → Measure (`~-1.69 MB` classpath / `~-1.54 MB` Fat JAR).
3. **Step 3 (`T007`–`T008`)**: Strip `protoc-jar` `bin/2.6.1/**` → Measure (`~-1.83 MB` Fat JAR).
4. **Step 4 (`T009`–`T010`)**: Lock `nativeDistributions` JRE `modules(...)` → Measure `.deb` installer.
5. **Step 5 (`T011`–`T013`)**: Switch gRPC client to `grpc-okhttp` → Measure (`~-9.81 MB` classpath / `~-9.11 MB` Fat JAR).
6. **Step 6 (`T014`–`T015`)**: Strip unused ICU4J dictionary subdirectories → Measure (`~-7.26 MB` Fat JAR).
7. **Step 7 (`T016`–`T017`)**: Full test gate + final `.deb` & Fat JAR benchmark report.
