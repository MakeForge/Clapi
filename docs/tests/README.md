# Scripted Assertions (Scripting Reference)

> **Note:** This file documents post-request script assertions. For the full scripting guide including pre-request scripts and variable access, see [docs/scripts.md](../scripts.md).

Clapi post-request scripts are written using the `clapi` namespace (configurable in **Settings → Scripts**).

## Pattern

1. Wrap checks in `clapi.test("name", function() { ... })`.
2. Use `clapi.expect(...)` for assertions.
3. Access response data via `clapi.response.*`.
4. Persist extracted values with `clapi.environment.set(...)` when chaining requests.

## Example

```javascript
clapi.test("Status is 200", function() {
  clapi.expect(clapi.response.code).to.equal(200)
})

clapi.test("Response time is acceptable", function() {
  clapi.expect(clapi.response.responseTime).to.be.below(2000)
})

clapi.test("Body contains users", function() {
  clapi.expect(clapi.response.text()).to.include("users")
})

clapi.test("Token extracted", function() {
  clapi.expect(clapi.response.json().token).to.exist
  clapi.environment.set("token", clapi.response.json().token)
})
```

## Assertions

- `.to.equal(v)`, `.to.not.equal(v)` — strict equality
- `.to.eql(v)` — deep equality
- `.to.include(text)` — substring or array containment
- `.to.match(/regex/)` — regex match
- `.to.be.above(n)`, `.to.be.below(n)` — numeric comparison
- `.to.be.at.least(n)`, `.to.be.at.most(n)` — inclusive bounds
- `.to.be.oneOf([a, b, c])` — value in set
- `.to.exist` — not null/undefined
- `.to.be.ok` — truthy
- `.to.be.empty` — empty string/array
- `.to.be.null` — strictly null

## Notes

- Assertion results are displayed in the **Test Results** panel after request execution.
- The global (prefix-free) API (`test`, `expect`, `env.set`, …) is still supported for backward compatibility.
- Change the namespace prefix in **Settings → Scripts** — any identifier works (e.g. `api`, `rl`).

---

## SBOM & Vulnerability Scanning (OSV-Scanner)

Clapi integrates automated **CycloneDX SBOM generation** and **OpenSSF OSV-Scanner** vulnerability analysis to verify dependency security across all runtime Multiplatform packages.

### What It's For

- **Software Bill of Materials (SBOM):** Generates a comprehensive, standard CycloneDX v1.6 inventory (`bom.json`) capturing all direct and transitive runtime dependencies, cryptographic hashes, and licenses shipped with the desktop application while excluding test harness frameworks.
- **OSV-Scanner:** Checks dependencies against the [OpenSSF OSV database](https://osv.dev) (aggregating CVEs, GitHub Security Advisories, and Maven advisories) using Package URL (`purl`) matching for accurate, low-noise vulnerability detection without requiring API keys or heavy database mirrors.

### How to Generate and Scan

1. **Generate the SBOM:**
   ```bash
   ./gradlew cyclonedxBom
   ```
   The aggregate JSON SBOM is written to:
   ```
   build/reports/sbom/bom.json
   ```

2. **Scan the SBOM Locally:**
   Install the [OSV-Scanner CLI](https://github.com/google/osv-scanner) and run:
   ```bash
   # Terminal summary report
   osv-scanner -L build/reports/sbom/bom.json

   # Or export SARIF format for IDEs and static analysis tools
   osv-scanner --format=sarif --output-file=osv-results.sarif -L build/reports/sbom/bom.json
   ```

3. **Suppress False Positives:**
   Verified false positives or deferred advisories can be ignored in `osv-scanner.toml` located at the root of the repository.

4. **Automated CI/CD Workflow:**
   The `.github/workflows/security-sbom.yml` workflow automatically runs on pushes to `main`, daily schedules, and release tags (`v*`). It publishes SARIF results directly to the repository's **Security → Code scanning** tab and attaches `bom.json` to GitHub Releases.


