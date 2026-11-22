# Security Policy

## Supported Versions

Security updates are provided for the latest released version of Clapi. Please ensure you are running the most recent release from [GitHub Releases](https://github.com/MakeForge/Clapi/releases/latest) before reporting an issue.

| Version | Supported          |
| ------- | ------------------ |
| Latest (`1.x`) | ✅|
| Older releases | ❌                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, discussions, or pull requests.**

Instead, please use GitHub's **Private Vulnerability Reporting** feature:

👉 **[Report a vulnerability privately](https://github.com/MakeForge/Clapi/security/advisories/new)**

(You can also reach this from the repository's **Security** tab → **Advisories** → **Report a vulnerability**.)

### What to Include

To help us triage and resolve the issue quickly, please include as much of the following as possible:

- **Affected version** of Clapi and **operating system / platform** (macOS, Linux, Windows, or Fat JAR)
- **Type of vulnerability** and potential security impact (e.g., secret exposure, script sandbox escape, RCE, credential leakage, MCP server access control)
- **Step-by-step instructions** to reproduce the issue (or a minimal proof-of-concept collection/request)
- **Suggested mitigation or patch**, if you have one

### What to Expect

1. **Acknowledgment**: We aim to acknowledge receipt of your report within **3–5 business days**.
2. **Assessment & Triage**: We will investigate, confirm the impact, and communicate with you directly through the private GitHub Security Advisory thread.
3. **Fix & Coordinated Disclosure**: Once a fix is prepared and verified, we will publish a new release and disclose the vulnerability via [GitHub Security Advisories](https://github.com/MakeForge/Clapi/security/advisories), crediting the reporter unless anonymity is requested.

## Security Practices & Transparency

Clapi uses the following automated security controls and supply-chain transparency measures:

- **GitHub Security Advisories & Private Vulnerability Reporting**: Coordinated vulnerability intake and public advisory publication via [GitHub Security Advisories](https://github.com/MakeForge/Clapi/security/advisories).
- **Dependabot**: Automated weekly dependency and GitHub Actions security/version updates configured in [`.github/dependabot.yml`](.github/dependabot.yml).
- **Code Scanning**: Automated static analysis and SARIF vulnerability reporting enabled in the repository's **Security → Code scanning** tab.
- **SBOM & OSV-Scanner**: Automated CycloneDX Software Bill of Materials (SBOM) generation and [OSV-Scanner](https://github.com/google/osv-scanner) vulnerability scanning run on pushes to `main`, pull requests, release tags, and a weekly schedule via [`.github/workflows/security-sbom.yml`](.github/workflows/security-sbom.yml). SBOM and OSV-Scanner results are attached to releases for independent verification.

## Security Guidance for Users

- **Download only from official releases**: Obtain installers and binaries exclusively from [MakeForge/Clapi GitHub Releases](https://github.com/MakeForge/Clapi/releases).
- **Review untrusted collections and scripts**: Clapi supports JavaScript pre/post-request scripting, dynamic variables, and external integrations. Review imported collections and scripts from untrusted sources before running them.
- **Protect secrets in Git-synced collections**: Use Clapi's secret manager integrations or local secret environment variables rather than storing plaintext credentials in Git-tracked collection files.
