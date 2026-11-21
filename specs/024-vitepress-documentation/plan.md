# Implementation Plan: Production VitePress Documentation Site

**Branch**: `024-vitepress-documentation` | **Date**: 2026-11-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/024-vitepress-documentation/spec.md`

## Summary

Implement a production-grade, markdown-based **VitePress** documentation site housed directly in `docs/` to serve as the single source of truth for Clapi. The site replaces legacy disconnected documentation files, features full guides and subpages for all request types (HTTP, GraphQL, WebSocket, gRPC, MCP, Realtime) with parent screenshots, prominently highlights trust pillars (zero telemetry, KSafe encrypted credentials, CycloneDX SBOM + OSV vulnerability scans, and external open-source libraries), documents Git sync and scripting APIs, and deploys automatically to GitHub Pages via GitHub Actions.

## Technical Context

**Language/Version**: Node.js 22 LTS, Markdown, TypeScript / JavaScript, Vue 3 (VitePress 1.6+)

**Primary Dependencies**: `vitepress`, `@vue/theme`, `minisearch` (built-in local search), `shiki` (syntax highlighter)

**Storage**: Static files, client-side indexed search cache (`minisearch` hash map in browser memory)

**Testing**: VitePress build-time dead-link validation (`vitepress build`), schema validation for frontmatter and configs, CI automated deployment test

**Target Platform**: GitHub Pages (Static hosting), local offline browsers, intranet mirrors

**Project Type**: Web documentation site / static site generator (SSG)

**Performance Goals**:
- Initial page load First Contentful Paint (FCP) < 1.0s
- Client-side route transition < 100ms
- Local search query latency < 50ms
- Static site production build < 30s

**Constraints**:
- Zero telemetry / zero external tracking pixels (strict air-gapped readiness)
- Zero external search service queries (built-in local search only)
- Complete replacement of legacy `/docs/*.md` files without loss of architecture knowledge

**Scale/Scope**:
- ~35 documentation pages across 9 major sections
- 5 parent protocol pages with UI screenshots + 18 deep-dive subpages
- Complete `clapi.*` scripting assertion API reference
- Transparent catalog of 16+ external open-source libraries

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Gate | Requirement | Status | Assessment |
|---|---|---|---|
| **I. Single Source of Truth** | All app documentation must be consolidated in a canonical location | PASSED | `docs/` becomes the VitePress root, deprecating all legacy scattered files |
| **II. Privacy & Local-First** | No external data exfiltration or third-party query tracking | PASSED | Local search (`minisearch`) keeps all search index data in the browser; zero analytics |
| **III. Automated Verification** | All pages and links must be validated during build | PASSED | `vitepress build` enforces zero broken internal links |
| **IV. Open Source Governance** | Transparent reporting of dependencies and licenses | PASSED | Dedicated external libraries directory with upstream GitHub links and SBOM assets |
| **V. Continuous Delivery** | Docs stay synchronized with code changes | PASSED | Automated GitHub Actions workflow deploys whenever `docs/**` changes on `main` |

## Project Structure

### Documentation (this feature)

```text
specs/024-vitepress-documentation/
├── spec.md              # Feature specification
├── plan.md              # Implementation plan (this file)
├── research.md          # Technical research & decision records
├── data-model.md        # Entities, information architecture & relationships
├── quickstart.md        # Validation scenarios & local dev guide
├── contracts/           # Configuration, frontmatter & workflow schemas
│   ├── vitepress-config.schema.json
│   ├── page-frontmatter.schema.json
│   └── deploy-docs-workflow.yml
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
clapi/
├── docs/                                # VitePress Root (replaces legacy docs)
│   ├── .vitepress/
│   │   ├── config.mts                   # VitePress config: navigation, sidebar, search
│   │   ├── theme/
│   │   │   ├── index.ts                 # Theme entry point
│   │   │   └── custom.css               # Material 3 styling tokens
│   │   └── dist/                        # Static build target (gitignored)
│   ├── public/
│   │   ├── favicon.ico
│   │   ├── logo.svg
│   │   └── screenshots/                 # Centralized screenshots repository
│   │       ├── clapi-screenshot.png
│   │       ├── clapi-screenshot-light.png
│   │       ├── mcp-tools.png
│   │       ├── clapi-graphql.png
│   │       ├── clapi-websocket.png
│   │       └── clapi-grpc.png
│   ├── guide/                           # Getting Started & Concepts
│   │   ├── index.md
│   │   ├── installation.md
│   │   ├── quickstart.md
│   │   └── core-concepts.md
│   ├── security/                        # Trust, Privacy & Supply Chain
│   │   ├── zero-telemetry.md
│   │   ├── ksafe-encryption.md
│   │   ├── sbom-vulnerabilities.md
│   │   └── external-libraries.md
│   ├── request-types/                   # Multi-Protocol Request Guides
│   │   ├── http/
│   │   │   ├── index.md                 # Parent page with screenshot
│   │   │   ├── parameters.md
│   │   │   ├── headers.md
│   │   │   ├── body.md
│   │   │   ├── authentication.md
│   │   │   ├── response-viewer.md
│   │   │   └── curl.md
│   │   ├── graphql/
│   │   │   ├── index.md                 # Parent page with screenshot
│   │   │   ├── queries.md
│   │   │   ├── variables.md
│   │   │   ├── introspection.md
│   │   │   └── auth.md
│   │   ├── websocket/
│   │   │   ├── index.md                 # Parent page with screenshot
│   │   │   ├── connection.md
│   │   │   ├── composer.md
│   │   │   ├── message-log.md
│   │   │   └── lifecycle.md
│   │   ├── grpc/
│   │   │   ├── index.md                 # Parent page with screenshot
│   │   │   ├── proto-management.md
│   │   │   ├── reflection.md
│   │   │   ├── method-invocation.md
│   │   │   ├── message-composer.md
│   │   │   ├── metadata.md
│   │   │   └── tls.md
│   │   ├── mcp/
│   │   │   ├── index.md                 # Parent page with screenshot
│   │   │   ├── transports.md
│   │   │   ├── tools.md
│   │   │   ├── resources.md
│   │   │   ├── prompts.md
│   │   │   ├── auth.md
│   │   │   └── activity-log.md
│   │   └── realtime/
│   │       └── index.md
│   ├── workspaces/                      # Collections, Folders & Inheritance
│   │   ├── collections.md
│   │   ├── folders-and-inheritance.md
│   │   └── protobuf-settings.md
│   ├── collaboration/                   # Git Sync & Team Workflows
│   │   ├── git-sync-overview.md
│   │   ├── git-operations.md
│   │   └── team-workflows.md
│   ├── environments/                    # Variables & Secret Managers
│   │   ├── variables.md
│   │   ├── environments.md
│   │   └── secret-managers.md
│   ├── scripting/                       # Script Engine & API Reference
│   │   ├── engine-architecture.md
│   │   ├── pre-request.md
│   │   ├── post-response-tests.md
│   │   └── api-reference.md
│   ├── import-export/                   # Specification Portability
│   │   ├── import.md
│   │   └── export.md
│   ├── reference/                       # Configuration, Shortcuts & Specs
│   │   ├── settings.md
│   │   ├── shortcuts.md
│   │   ├── architecture.md
│   │   └── testing.md
│   ├── index.md                         # Landing / Hero Page
│   ├── package.json                     # Docs dependencies & build scripts
│   └── package-lock.json
└── .github/
    └── workflows/
        └── deploy-docs.yml              # Automated GitHub Pages CI/CD
```

**Structure Decision**: Selected standard VitePress root directory inside `docs/`. This eliminates subfolder duplication, preserves git history for relocated docs, and integrates cleanly with GitHub Pages and GitHub Actions.

## Complexity Tracking

> No constitutional violations or unwarranted complexity introduced. Architecture relies on standard static site generator conventions and lightweight client-side tooling.
