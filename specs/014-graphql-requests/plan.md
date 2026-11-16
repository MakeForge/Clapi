# Implementation Plan: GraphQL Requests

**Branch**: `014-graphql-requests` | **Date**: 2026-11-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-graphql-requests/spec.md`

## Summary

This feature elevates GraphQL to a first-class request type in Clapi in strict compliance with the OpenCollection v1.0.0 specification. The domain data model is extended with `GraphQLRequestItem`, `GraphQLRequestInfo`, `GraphQLRequestDetails`, `GraphQLBody`, `GraphQLBodyVariant`, `GraphQLBodyPayload`, `GraphQLRequestSettings`, and `GraphQLRequestRuntime`. In the UI, a new theme color `MethodGraphQl` (`#e10098`) powers the `GraphQLBadge` ("GQL") in the sidebar collection tree and editor tabs. The sidebar "+" menu adds a "New GraphQL Request" creation action. A dedicated `GraphQLRequestEditor` provides an address bar (URL with dynamic variables, HTTP method POST/GET), a body editor with syntax highlighting for GraphQL queries and JSON variables with query formatting and body variants support, headers, query parameters, authentication, runtime scripts, execution settings, and documentation tabs. Execution leverages `encodeGraphQlEnvelope` and Ktor HTTP transport to execute queries and mutations seamlessly against GraphQL backends. Bidirectional OpenCollection JSON/YAML serialization and end-to-end integration tests in `qa-tests` guarantee round-trip fidelity and schema compliance.

## Technical Context

**Language/Version**: Kotlin 2.1 (JVM 21 / 17 compatible)

**Primary Dependencies**:
- Ktor HTTP Client (`io.ktor:ktor-client-core`, `io.ktor:ktor-client-cio`)
- Compose Multiplatform 1.8.1 (Desktop)
- `kotlinx.serialization` (JSON polymorphic serializer)
- `kaml` (YAML parser and serializer)
- `kotlinx.coroutines`

**Storage**:
- Canonical OpenCollection format (`core-storage`, `OpenCollectionModelMapping.kt`) supporting `GraphQLRequestItem` serialization in JSON and YAML adhering to `opencollection_v1.0.0.json`.

**Testing**:
- Unit tests for OpenCollection serialization and polymorphic deserialization in `core-model` and `core-storage`
- UI component, badge, and tab state tests in `ui-shared`
- End-to-end integration and round-trip tests in `qa-tests` (`GraphQLRequestE2ETest`, `OpenCollectionRoundTripE2ETest`)

**Target Platform**: Desktop (Linux, macOS, Windows)

**Project Type**: Multiplatform Desktop Application

**Performance Goals**:
- Query execution dispatch latency < 10ms overhead.
- Query syntax highlighting and brace folding render at 60 fps for queries up to 1,000 lines.
- Instantaneous (<16ms) variable interpolation in URL, headers, and query/variables.

**Constraints**:
- Strict conformance to OpenCollection v1.0.0 schema (`additionalProperties: false` on `GraphQLRequest`).
- Backward compatibility for existing collections containing HTTP requests with `BodyType.GRAPHQL`.
- Standardized GraphQL badge color `#e10098` across dark and light themes.

**Scale/Scope**:
- First-class multi-protocol support: HTTP, gRPC, WebSocket, GraphQL.
- Support single-query payloads and multi-variant query payloads.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Library-First / Modular Architecture**: PASS. Domain types live in `core-model`, network serialization in `core-network`, persistence in `core-storage`, and UI in `ui-shared`.
- **OpenCollection Schema Compliance**: PASS. Conforms directly to the official `opencollection_v1.0.0.json` schema without introducing ad-hoc proprietary structures.
- **Test-First / E2E Verification**: PASS. Dedicated automated E2E tests in `qa-tests` against `DummyApiServer` and round-trip collection tests are included.
- **Simplicity / YAGNI**: PASS. Reuses existing HTTP execution infrastructure, syntax highlighters, and response viewers rather than adding heavy external GraphQL runtime dependencies.

---

## Project Structure

### Documentation (this feature)

```text
specs/014-graphql-requests/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── graphql-editor-contract.md
│   ├── graphql-execution-contract.md
│   └── graphql-opencollection-contract.md
└── checklists/
    └── requirements.md  # Quality validation checklist
```

### Source Code (repository root)

```text
core-model/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/model/
    ├── McpModels.kt                             # Add GRAPHQL to RequestKind
    └── opencollection/
        └── OpenCollectionItem.kt                # GraphQLRequestItem, details, payload, settings, serializer

core-network/
└── src/commonMain/kotlin/io/github/makeforge/clapi/core/network/
    └── GraphQlEnvelope.kt                       # Reused for envelope encoding & variable interpolation

core-storage/
├── src/commonMain/kotlin/io/github/makeforge/clapi/core/storage/
│   ├── exporters/OpenApiExporter.kt             # Handle GraphQLRequestItem export
│   ├── importers/OpenCollectionImporter.kt      # Handle GraphQLRequestItem count & import
│   └── opencollection/OpenCollectionSerializer.kt # KNOWN_GRAPHQL_REQUEST_KEYS and serializer handling
└── src/desktopMain/kotlin/io/github/makeforge/clapi/core/storage/importers/
    └── OpenApiImporter.kt                       # Count & handle GraphQLRequestItem

ui-shared/
└── src/commonMain/kotlin/io/github/makeforge/clapi/ui/shared/
    ├── components/
    │   ├── GraphQLBadge.kt                      # Or in Sidebar.kt: GQL badge with #e10098
    │   ├── GraphQLRequestEditor.kt              # Dedicated first-class GraphQL editor
    │   ├── RequestEditor.kt                     # Route tab.kind == GRAPHQL to GraphQLRequestEditor
    │   ├── RequestExecutor.kt                   # Execute GraphQL requests (POST envelope or GET params)
    │   ├── RequestTabsBar.kt                    # Display GraphQLBadge on tabs
    │   └── Sidebar.kt                           # Add "New GraphQL Request" in + menu & render badge
    ├── persistence/
    │   └── OpenCollectionModelMapping.kt        # Import/export mapping for GraphQLRequestItem
    ├── state/
    │   └── AppState.kt / RequestTabState.kt     # RequestTabState & CollectionNode GraphQL fields
    └── theme/
        ├── AppColorPalette.kt                   # methodGraphQl = Color(0xFFE10098)
        └── ClapiTheme.kt                        # ClapiColors.MethodGraphQl accessor

qa-tests/
└── src/test/kotlin/io/github/makeforge/clapi/qa/
    ├── GraphQLRequestE2ETest.kt                 # E2E query/variable execution against DummyApiServer
    └── OpenCollectionRoundTripE2ETest.kt        # Round-trip JSON/YAML test for GraphQLRequestItem
```

**Structure Decision**: Multi-project Kotlin Multiplatform architecture leveraging existing `core-model`, `core-storage`, `ui-shared`, and `qa-tests` modules.

## Complexity Tracking

> No constitution violations detected.
