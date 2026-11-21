# Feature Specification: Desktop Release Artifact Size Optimization

**Feature Branch**: `022-release-artifact-size-optimization`

**Created**: 2026-11-14

**Status**: Ready for Planning

**Input**: User description: "@[scratch/clapi_desktop_release_size_optimization_plan.md] gives some hints on maybe how we can optimize the release artifacts sizes. Evalute the described potential improvements for validity and usefulness and assess risks. Then create a plan on which improvements to go for and how"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Eliminate Redundant & Unused Dependencies and Obsolete Natives (Priority: P1)

As a desktop user downloading Clapi or running the standalone distribution archive, I want the application package to exclude obsolete compiler binaries, unused legacy converter libraries, and duplicate UI framework modules, so that downloads complete faster and disk usage is minimized without any change in application behavior.

**Why this priority**: Pruning unused transitive dependencies, obsolete embedded binaries (such as historical protocol buffer compiler versions), and unused compatibility layers delivers immediate, guaranteed size reductions (~10–15 MB) with near-zero functional risk across both the standalone archive and native platform installers.

**Independent Test**: Build the standalone distribution archive and platform installer, verify that excluded libraries and obsolete binaries are absent from the packaged output, and run the automated test suite covering OpenAPI v3 import, gRPC schema parsing, gRPC requests, and desktop UI rendering.

**Acceptance Scenarios**:

1. **Given** the packaged distribution artifact, **When** inspecting its embedded protocol compiler resources, **Then** obsolete legacy compiler versions (`bin/2.6.1/**`) are completely absent while the active `3.x` compiler binaries continue to parse `.proto` files across all supported operating systems.
2. **Given** an OpenAPI 3.0+ specification file (JSON or YAML), **When** a user imports the specification into Clapi, **Then** the specification imports accurately while legacy Swagger 2.0 converter libraries and their transitive dependencies (such as Mozilla Rhino, legacy HTTP client 4.x, Joda-Time, LibPhoneNumber, and duplicate JSON schema validators) are excluded from the distribution.
3. **Given** a Swagger 2.0 specification file, **When** a user attempts to import it, **Then** Clapi rejects it with the existing clear diagnostic message before parser invocation.
4. **Given** the desktop user interface, **When** navigating all screens, editors, and dialogs in both light and dark modes, **Then** all Material 3 components render identically without bundling unused legacy Material 2 desktop libraries or development-only preview tooling in shared production modules.

---

### User Story 2 - Slim Down Embedded Runtime Image in Native Installers (Priority: P2)

As a user installing Clapi via a native desktop installer (`.deb`, `.rpm`, `.dmg`, `.msi`), I want the bundled Java runtime image to include only the runtime modules actually required by Clapi and its security/networking features, rather than the entire default development runtime image, so that the installer download and installed disk footprint are significantly smaller.

**Why this priority**: Native desktop installers bundle a custom runtime image that currently defaults to a broad module set. Restricting the bundled runtime modules to the verified required set (including security/TLS and preferences modules) saves 20–35 MB on every platform installer with low risk when properly validated.

**Independent Test**: Build a native desktop installer with the restricted runtime module list, launch the installed binary, and verify startup, local preferences persistence, HTTPS/TLS handshake with remote endpoints, external secret manager connections, and script execution.

**Acceptance Scenarios**:

1. **Given** a native platform installer built with the explicit runtime module configuration, **When** compared against the baseline installer size, **Then** the bundled runtime image size is reduced by at least 20 MB.
2. **Given** the installed native application, **When** making HTTPS requests to TLS-enabled endpoints (including ECDSA/ECDHE certificates) and saving workspace preferences, **Then** all cryptographic handshakes and preference storage operations succeed without missing-module errors.

---

### User Story 3 - Optimize gRPC Transport & Scripting Resource Footprint (Priority: P3)

As a user executing API requests with pre-request/post-response scripts and gRPC calls, I want the gRPC transport layer to use a lightweight pure-JVM client and the scripting runtime to omit unused CJK/transliteration dictionary data in packaged archives while preserving full modern ES2022+ JavaScript compatibility, so that the application size is substantially reduced without sacrificing scripting or gRPC capabilities.

**Why this priority**: Replacing the heavy shaded Netty gRPC transport (10.41 MB with 5 embedded native libraries) with the lightweight OkHttp gRPC client (~1.2 MB) and stripping unused ICU4J break-iterator/transliteration data from the packaged JAR saves an additional ~14–17 MB while keeping 100% compatibility with existing JavaScript scripts and gRPC endpoints.

**Independent Test**: Execute the full scripting end-to-end test suite (including ES6+ syntax, regular expressions, JSON manipulation, and Postman-compatible `clapi`/`pm` assertions) and the gRPC unary plaintext/TLS test suite against the optimized runtime.

**Acceptance Scenarios**:

1. **Given** pre-request and post-response scripts using modern JavaScript features, assertions, and regular expressions, **When** executed in the GraalJS runtime with slimmed ICU4J data resources, **Then** 100% of script tests pass without runtime errors.
2. **Given** unary gRPC requests over both plaintext and TLS connections with custom metadata, **When** executed through the OkHttp-backed gRPC client, **Then** requests succeed and return accurate responses and metadata trailers without bundling native `netty_tcnative` binaries in production.

---

### Edge Cases

- **Cross-Platform Fat JAR Portability**: The standalone `Clapi-{version}.jar` is built once on the macOS CI runner and published as a universal JAR for users on macOS, Linux, and Windows. Stripping OS-specific native binaries for active tools (such as `protoc` 3.11.4 executables for Linux/Windows) from the universal `packageClapiJar` would break gRPC `.proto` compilation for Linux and Windows fat-JAR users; therefore, only obsolete versions (`bin/2.6.1/**`) are stripped from the universal JAR, keeping all three OS binaries under `bin/3.11.4/*`.
- **GraalJS Truffle Classpath Integrity**: GraalJS (`js-language`) requires `org.graalvm.shadowed.icu4j` classes (`ULocale`, etc.) on the classpath. To avoid `NoClassDefFoundError` or module resolution failures, the `icu4j` dependency remains on the classpath while only unused heavy dictionary/break-iterator data resources (such as `cjdict.dict` and unused transliteration/collation tables) are excluded during archive packaging.
- **gRPC In-Process Server in Unit Tests**: While `grpc-okhttp` provides a lightweight client transport for production (`desktopMain`), existing gRPC integration tests in `core-network` spin up an in-process test server using `NettyServerBuilder`. Moving `grpc-netty-shaded` to `desktopTest` preserves full test server capabilities while removing it completely from production artifacts.
- **Missing Cryptographic or Unsafe Modules in JRE Image**: Static module analysis tools (`suggestRuntimeModules`) do not detect dynamically loaded security providers (`jdk.crypto.ec` for HTTPS/TLS elliptic-curve cipher suites, `jdk.crypto.cryptoki`) or `java.net.http` / `java.desktop` / `java.logging`. Explicitly including these alongside statically detected modules prevents runtime TLS or UI failures in packaged installers.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The build system MUST exclude obsolete protocol compiler binaries (`bin/2.6.1/**`) from packaged artifacts while preserving active `bin/3.11.4/**` protocol compiler binaries required for runtime `.proto` parsing across all target operating systems.
- **FR-002**: The build system MUST exclude the unused legacy Swagger 2.0 compatibility converter (`io.swagger.parser.v3:swagger-parser-v2-converter`) and its transitive dependencies (including `rhino`, `swagger-compat-spec-parser`, `swagger-core` 1.x, `httpclient` 4.x, `joda-time`, and `libphonenumber`) from `core-storage` while maintaining full OpenAPI 3.0+ import functionality.
- **FR-003**: The build system MUST exclude unused Compose Material 2 (`org.jetbrains.compose.material:material` and `material-desktop`) from the production runtime classpath and remove development-only `compose.uiTooling` from `ui-shared`'s production dependencies.
- **FR-004**: The gRPC client in `core-network` (`desktopMain`) MUST use `io.grpc:grpc-okhttp` instead of `io.grpc:grpc-netty-shaded` for production channel creation (with `grpc-netty-shaded` scoped only to `desktopTest` for test server execution), eliminating shaded Netty and its embedded `META-INF/native/*netty_tcnative*` binaries from production distributions.
- **FR-005**: The scripting runtime in `core-scripting` MUST retain GraalJS (`org.graalvm.polyglot:js-community`) for full ES2022+ compatibility, while the fat JAR packaging task strips unused heavy ICU4J dictionary/break-iterator data resources (such as `org/graalvm/shadowed/com/ibm/icu/impl/data/icudt*/brkitr/*`) after verifying that all scripting tests and `verifyPackagedRuntime` pass.
- **FR-006**: Native desktop distributions (`nativeDistributions` in `ui-desktop`) MUST configure an explicit set of Java runtime modules combining `suggestRuntimeModules` output (`java.instrument`, `java.management`, `java.naming`, `java.prefs`, `java.sql`, `jdk.jfr`, `jdk.unsupported`) with required runtime modules (`java.base`, `java.desktop`, `java.logging`, `java.net.http`, `java.rmi`, `java.scripting`, `java.security.jgss`, `jdk.crypto.ec`, `jdk.crypto.cryptoki`).
- **FR-007**: Standard native packaging tasks (`packageDeb`, `packageDmg`, `packageMsi`, `packageRpm`) MUST remain the primary distribution tasks without enabling ProGuard bytecode shrinking, avoiding reflection-related runtime regressions and build-time overhead.
- **FR-008**: The release verification suite (`verifyPackagedRuntime` and automated module tests) MUST validate that packaged artifacts start cleanly and pass HTTP, HTTPS/TLS, gRPC, OpenAPI v3 import, and scripting checks after optimizations are applied.

---

### Key Entities

- **Runtime Classpath (`desktopRuntimeClasspath`)**: The resolved collection of library archives bundled into the application distribution (baseline: 183 JARs, 158.97 MB).
- **Standalone Fat JAR (`Clapi-{version}.jar`)**: The single-file runnable archive containing merged application classes, resources, and service descriptors (baseline: 132.80 MB).
- **Native Platform Installer (`.deb`, `.rpm`, `.dmg`, `.msi`)**: The OS-specific installer package bundling the application JARs and a custom-linked Java runtime image (baseline `.deb`: 133.11 MB `main` / 149.05 MB `main-release`).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The standalone `Clapi-{version}.jar` fat JAR size is reduced by at least 15% (minimum ~20 MB reduction from the 132.80 MB baseline) without breaking cross-platform fat JAR execution.
- **SC-002**: The native Linux installer (`clapi_{version}_amd64.deb`) size is reduced by at least 20% (minimum ~25–35 MB reduction) through combined dependency pruning and runtime image module restriction.
- **SC-003**: 100% of existing automated unit, integration, and end-to-end tests (`core-network`, `core-storage`, `core-scripting`, `ui-shared`, `ui-desktop`, `qa-tests`) pass with zero regressions.
- **SC-004**: The headless packaged runtime smoke test (`verifyPackagedRuntime`) completes with exit code `0`.

---

## Assumptions

- OpenAPI 2.0 (Swagger 2.0) remains unsupported by design (as enforced by existing specification import gates and tests), making the Swagger v2 converter library safe to exclude.
- Jackson 2.x (required by `swagger-parser` v3) and Jackson 3.x (`tools.jackson.core`, required by `networknt:json-schema-validator` 3.0.9) use distinct package namespaces and coexist without conflict; excluding the legacy `json-schema-validator` 2.2.14 pulled by Swagger v2 converter removes the third duplicate schema library.
- ProGuard bytecode shrinking is explicitly deferred out of scope in favor of deterministic dependency exclusions, resource filtering, and JRE `jlink` module restriction.
