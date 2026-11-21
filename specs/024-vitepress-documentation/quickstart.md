# Quickstart Validation Guide: VitePress Documentation Site

**Feature**: `024-vitepress-documentation`
**Date**: 2026-11-14
**Status**: Completed

This guide provides runnable scenarios to validate that the VitePress documentation site functions correctly, builds without broken links, renders responsive UI previews, and deploys cleanly to GitHub Pages.

---

## 1. Prerequisites

- **Node.js**: v20.x or v22.x LTS (`node --version`)
- **Package Manager**: npm v10+ (`npm --version`)
- **Repository Root**: `/home/user/IdeaProjects/Clapi`

---

## 2. Validation Scenarios

### Scenario 1: Local Development Server & Live Reload

Verify that the documentation environment starts up cleanly and renders locally.

```bash
# Navigate to the docs root
cd docs

# Install documentation dependencies
npm install

# Start VitePress local dev server
npm run docs:dev
```

**Expected Outcome**:
- Dev server binds to `http://localhost:5173/Clapi/` (or configured port).
- Landing page renders hero section, features grid, and navigation bar.
- Changes to any `.md` file trigger instant Hot Module Replacement (HMR) in the browser without losing scroll state.

---

### Scenario 2: Production Static Build & Dead Link Verification

Verify that VitePress compiles all markdown files into static HTML/CSS/JS bundles and that no internal markdown links are broken.

```bash
cd docs

# Execute production static build
npm run docs:build
```

**Expected Outcome**:
- The build exits with status code `0`.
- Output bundle is created under `docs/.vitepress/dist/`.
- Zero broken link errors (`dead link` warnings/errors from VitePress must be 0).
- Local search index is generated under `.vitepress/dist/hash-map.json` and client scripts.

---

### Scenario 3: Production Static Preview & Local Search Verification

Verify that the compiled static bundle renders properly when served statically and that client-side search functions offline.

```bash
cd docs

# Preview production build locally
npm run docs:preview
```

**Expected Outcome**:
- Static server runs at `http://localhost:4173/Clapi/`.
- Click on the search input (`⌘K` / `Ctrl+K`).
- Type queries like `"KSafe"`, `"Zero Telemetry"`, `"gRPC"`, `"MCP"`, `"clapi.expect"`.
- Results appear instantly (< 50ms) with highlighted snippets without any outbound network calls (verify via browser DevTools Network tab).

---

### Scenario 4: Responsive Screenshots & Theme Switching

Verify that all protocol pages display their parent screenshots and toggle seamlessly between Dark and Light modes.

1. Open `http://localhost:4173/Clapi/request-types/http/`.
   - Confirm [clapi-screenshot.png](file:///home/user/IdeaProjects/Clapi/docs/screenshots/clapi-screenshot.png) renders clearly with proper aspect ratio and alt text.
2. Open `http://localhost:4173/Clapi/request-types/mcp/`.
   - Confirm [mcp-tools.png](file:///home/user/IdeaProjects/Clapi/docs/images/mcp-tools.png) renders clearly.
3. Click the theme toggle icon in the top navigation bar.
   - Background, code blocks, borders, and callout containers switch smoothly between Material 3 Dark and Light themes.

---

### Scenario 5: CI/CD Deployment Dry-Run

Verify the GitHub Actions workflow syntax and artifact packaging.

```bash
# Verify GitHub Actions workflow syntax using actionlint (if installed) or dry-run validation
grep -E "(name:|uses:|run:)" .github/workflows/deploy-docs.yml
```

**Expected Outcome**:
- Workflow triggers only on pushes affecting `docs/**` or `.github/workflows/deploy-docs.yml`.
- Artifact path points accurately to `docs/.vitepress/dist`.
- Deploys cleanly to GitHub Pages using official actions (`actions/deploy-pages@v4`).
