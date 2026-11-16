# Technical Research & Decision Records: VitePress Documentation Site

**Feature**: `024-vitepress-documentation`
**Date**: 2026-11-14
**Status**: Completed

This document records the architectural evaluations, technology selections, and design decisions for creating the production-grade VitePress documentation site for Clapi.

---

## 1. Static Site Generator Selection

### Decision
Select **VitePress 1.6+** as the static documentation engine for Clapi.

### Rationale
- **Zero-Config Markdown Power**: Native support for VitePress custom markdown containers (`::: tip`, `::: warning`, `::: info`), code groups, line highlighting, and Vue SFC components inside markdown.
- **Fast Build & Hot Reload**: Powered by Vite and Rollup; builds hundreds of pages in seconds and offers instant local hot-module reloading during authoring.
- **Single-Page Application (SPA) Navigation**: Initial page load delivers pre-rendered static HTML (SEO and fast first-contentful-paint), while subsequent navigation is fast client-side routing.
- **Direct GitHub Pages Compatibility**: Official first-class actions and configuration (`base: '/Clapi/'` or custom root) with standard GitHub Pages deployment workflows.

### Alternatives Considered
- **Docusaurus**: Feature-rich, but heavier dependency footprint (React, Webpack), slower build times, and requires more boilerplate configuration compared to VitePress.
- **MkDocs (Material for MkDocs)**: Excellent for Python projects, but requires Python runtime/pip environments, whereas Clapi's CI/CD and developer tooling ecosystem integrates more naturally with standard Node/npm GitHub Actions.
- **Astro / Starlight**: Strong modern alternative, but VitePress has deeper out-of-the-box support for technical API documentation styling, tabs, and code groups.

---

## 2. Search Engine Architecture: Privacy & Offline Readiness

### Decision
Use VitePress's **Built-in Local Search** powered by `minisearch`.

```typescript
// .vitepress/config.mts snippet
search: {
  provider: 'local',
  options: {
    detailedView: true,
    miniSearch: {
      searchOptions: {
        fuzzy: 0.2,
        prefix: true,
        boost: { title: 4, heading: 2, content: 1 }
      }
    }
  }
}
```

### Rationale
- **Zero-Telemetry Alignment**: Cloud-based search services (e.g. Algolia DocSearch) transmit user search queries to external remote servers. Clapi's core philosophy is **zero telemetry and air-gapped readiness**. Built-in local search indexes the entire site into a client-side bundle loaded in the browser.
- **Works Completely Offline**: Users running or browsing documentation on intranet mirrors, local files, or isolated machines can search seamlessly without an active internet connection.
- **Zero Ongoing Operational Overhead**: No API keys, search crawler configs, index recreation cron jobs, or account management needed.

### Alternatives Considered
- **Algolia DocSearch**: Industry standard for public open-source docs, but violates Clapi's strict local-first, zero-telemetry philosophy by transmitting user search queries to third-party infrastructure.
- **Pagefind**: Excellent standalone static search engine, but VitePress 1.x already includes native, fully integrated `minisearch` with zero extra build tools.

---

## 3. Directory Layout & Obsolescence of Legacy `/docs`

### Decision
Migrate the repository's root `docs/` folder directly into the VitePress root:

```
clapi/
├── docs/                                # VitePress Documentation Root
│   ├── .vitepress/
│   │   ├── config.mts                   # Site config, sidebar, nav, search
│   │   ├── theme/
│   │   │   ├── index.ts                 # Custom theme entry
│   │   │   └── custom.css               # Material 3 color variables
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
│   ├── guide/                           # Getting Started & Core Concepts
│   ├── request-types/                   # Dedicated Protocol Guides & Subpages
│   │   ├── http/
│   │   ├── graphql/
│   │   ├── websocket/
│   │   ├── grpc/
│   │   ├── mcp/
│   │   └── realtime/
│   ├── workspaces/                      # Collections, Folders & Defaults
│   ├── collaboration/                   # Git Sync & OpenCollection
│   ├── environments/                    # Variables & Secret Managers
│   ├── scripting/                       # Javet V8 Script Engine & API Reference
│   ├── security/                        # Zero Telemetry, KSafe, SBOM, Libraries
│   ├── reference/                       # Shortcuts, Settings, Specifications
│   ├── index.md                         # Documentation Home / Hero Landing
│   ├── package.json                     # VitePress scripts & dependencies
│   └── package-lock.json
```

### Rationale
- Having `docs/` serve directly as the documentation root prevents folder nesting bloat (e.g. `docs/docs/` or `website/docs/`) and aligns with GitHub's default conventions.
- Old disconnected files (`../../docs/old/mcp.md`, `../../docs/old/collections.md`, `../../docs/old/scripts.md`, etc.) are synthesized and upgraded into dedicated chapters, eliminating stale redundancy while preserving all architectural knowledge.

---

## 4. Code Syntax Highlighting & Custom Language Grammars

### Decision
Configure Shiki in VitePress to support:
- `kotlin`, `java`
- `javascript`, `typescript`
- `json`, `json5`
- `yaml`
- `graphql`
- `proto` (Protocol Buffers v3)
- `http` (HTTP raw request/response format)
- `bash`, `shell`

### Rationale
Clapi is an API client supporting REST, GraphQL, WebSocket, gRPC, and MCP. Users frequently view and copy raw requests, `.proto` schemas, GraphQL queries, Javet JavaScript snippets, and Kotlin code. All of these require first-class syntax coloring in both light and dark themes.

---

## 5. Security & Trust Showcase Strategy

### Decision
Group security and compliance into a prominent top-level navigation section:
1. **Zero-Telemetry Guarantee**: Clear statements of privacy, lack of background pings, and instructions on packet inspection.
2. **Local Credential Encryption via KSafe**: Documenting AES-256-GCM encryption, PBKDF2 derivation, and key storage on disk.
3. **SBOM & Automated Vulnerability Scanning**: Documenting CycloneDX generation (`./gradlew cyclonedxBom`), automated OSV-Scanner checks, SARIF uploads to GitHub Security, and release SBOM downloads.
4. **Open Source Library Catalog**: Transparent table of all libraries from `gradle/libs.versions.toml` with direct links to upstream GitHub repositories and stated licenses.

### Rationale
Developers and enterprise procurement teams increasingly require supply chain transparency and strict data isolation guarantees before deploying desktop developer tools. Highlighting these features builds immediate trust and sets Clapi apart from cloud-monetized competitors.

---

## 6. GitHub Actions Continuous Deployment Workflow

### Decision
Implement `.github/workflows/deploy-docs.yml` utilizing GitHub Pages official deployment primitives (`actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`, `actions/deploy-pages@v4`).

### Rationale
- Triggers strictly when changes occur in `docs/**` or `.github/workflows/deploy-docs.yml` on `main`, avoiding wasteful CI runs when only Kotlin backend code changes.
- Uses official GitHub Pages OIDC authentication with zero custom deployment tokens or third-party SSH keys.
- Enforces strict link checking during `vitepress build` to ensure no dead links reach production.
