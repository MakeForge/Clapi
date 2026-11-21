---
title: Software Bill of Materials (SBOM) & Vulnerability Auditing
description: Continuous supply chain security, CycloneDX SBOM generation, OSV-Scanner checks, and GitHub Security reporting in Clapi.
---

# Software Bill of Materials (SBOM) & Vulnerability Auditing

<p class="badges">
  <span class="badge badge-protocol">Supply Chain Security</span>
  <span class="badge badge-version">CycloneDX 1.5</span>
</p>

## Overview

Software supply chain attacks are an increasing risk for enterprise developer tools. Clapi implements following, automated supply chain governance:
1. Every release publishes a machine-readable **CycloneDX Software Bill of Materials (SBOM)**.
2. Every code change is audited against the **Open Source Vulnerabilities (OSV)** database using Google's **OSV-Scanner**.
3. Security findings are reported directly to the repository's **GitHub Security** tab via SARIF.

## CycloneDX SBOM Generation

Clapi utilizes the official [CycloneDX Gradle Plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin) (`org.cyclonedx.bom`) to generate a comprehensive bill of materials during the build:

```bash
# Generate the CycloneDX SBOM locally
./gradlew cyclonedxBom --no-daemon
```

The resulting artifacts are saved to:
- `build/reports/sbom/bom.json` (CycloneDX JSON format)
- `build/reports/sbom/bom.xml` (CycloneDX XML format)

### What the SBOM Contains
- Complete dependency graph of all direct and transitive runtime libraries.
- Exact Package URLs (PURL), hashes (SHA-256), and component licenses.
- Cryptographic provenance enabling enterprise compliance ingestion (Dependency-Track, Snyk, Black Duck, etc.).

## Continuous Vulnerability Auditing with OSV-Scanner

Clapi's CI/CD pipeline runs `.github/workflows/security-sbom.yml` on every pull request, push to `main`, and on a weekly automated schedule:

```yaml
- name: Scan SBOM with OSV-Scanner
  run: |
    osv-scanner --format=sarif --output-file=osv-results.sarif -L build/reports/sbom/bom.json

- name: Upload OSV SARIF to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: osv-results.sarif
    category: osv-scanner
```

### Vulnerability Gates
- If any known high-severity Common Vulnerabilities and Exposures (CVE) are detected, the build gate fails, preventing unvetted code from being published.
- All release tags attach the generated `bom.json` directly to the [GitHub Release](https://github.com/makeforge/clapi/releases).
