<p align="center">
  <img src="ui-desktop/src/desktopMain/resources/icons/clapi-icon-128.png" alt="Clapi" width="100" />
</p>

<h1 align="center">Clapi</h1>

<p align="center">
  <strong>The Bullshit-Free API Client</strong><br/>
  Open-Source multi-protocol Desktop API development tool with zero telemetry or cloud lock-in, extensive Secret Manager support, Git-native sync and built-in MCP Server for Windows, Linux, macOS for free.
</p>

<p align="center">
  <a href="https://makeforge.github.io/Clapi">
    <img src="https://img.shields.io/badge/Documentation-makeforge.github.io%2FClapi-6750a4?style=for-the-badge" alt="Official Documentation" />
  </a>
  <a href="https://github.com/MakeForge/Clapi/releases/latest">
    <img src="https://img.shields.io/badge/Download-Latest%20Release-2ea44f?style=for-the-badge" alt="Download Latest Release" />
  </a>
</p>

<p align="center">
  <strong>📖 Official Documentation:</strong> <a href="https://makeforge.github.io/Clapi">https://makeforge.github.io/Clapi</a>
</p>

<p align="center">
  <a href="https://makeforge.github.io/Clapi/request-types/http/" title="HTTP / REST"><img src="docs/public/icons/request-types/http.svg" alt="HTTP / REST" width="32" height="32" /></a>
  &nbsp;&nbsp;
  <a href="https://makeforge.github.io/Clapi/request-types/graphql/" title="GraphQL"><img src="docs/public/icons/request-types/graphql.svg" alt="GraphQL" width="32" height="32" /></a>
  &nbsp;&nbsp;
  <a href="https://makeforge.github.io/Clapi/request-types/websocket/" title="WebSocket"><img src="docs/public/icons/request-types/websocket.svg" alt="WebSocket" width="32" height="32" /></a>
  &nbsp;&nbsp;
  <a href="https://makeforge.github.io/Clapi/request-types/grpc/" title="gRPC"><img src="docs/public/icons/request-types/grpc.svg" alt="gRPC" width="44" /></a>
  &nbsp;&nbsp;
  <a href="https://makeforge.github.io/Clapi/request-types/rabbitmq/" title="RabbitMQ (AMQP)"><img src="docs/public/icons/request-types/rabbitmq.svg" alt="RabbitMQ (AMQP)" width="32" height="32" /></a>
  &nbsp;&nbsp;
  <a href="https://makeforge.github.io/Clapi/request-types/mcp/" title="Model Context Protocol (MCP)"><img src="docs/public/icons/request-types/mcp-server.svg" alt="Model Context Protocol (MCP)" width="32" height="32" /></a>
  &nbsp;&nbsp;
  <a href="https://makeforge.github.io/Clapi/request-types/realtime/" title="Server-Sent Events (SSE)"><img src="docs/public/icons/request-types/sse.svg" alt="Server-Sent Events (SSE)" width="32" height="32" /></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/platform-macOS%20%7C%20Linux%20%7C%20Windows-lightgrey.svg" alt="Platform" />
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/kotlin-2.1.21-purple.svg" alt="Kotlin" /></a>
  <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/compose--multiplatform-1.8.1-orange.svg" alt="Compose Multiplatform" /></a>
  <a href="https://github.com/MakeForge/clapi/actions"><img src="https://github.com/MakeForge/clapi/actions/workflows/release.yml/badge.svg" alt="Build" /></a>
</p>

---

<p align="center">
  <img src="docs/public/generated/screenshots/MainScreenPreviewsKt.MainScreenHttpPreviewDark.W1600dp_H800dp.png" alt="Clapi — dark theme" width="100%" />
</p>

---

## Quick Start

Clapi ships as a self-contained desktop application — **no external JDK or JRE is required** when using the native installers. Download the latest release from [GitHub Releases](https://github.com/MakeForge/Clapi/releases/latest) or view the full [Installation Guide](https://makeforge.github.io/Clapi/guide/installation).

### Windows

1. Download `Clapi-Setup-<version>.msi` (or `.exe`) from [GitHub Releases](https://github.com/MakeForge/Clapi/releases/latest).
2. Run the installer and follow the setup wizard.

### Linux

Download the native package matching your distribution from [GitHub Releases](https://github.com/MakeForge/Clapi/releases/latest):

- **Debian / Ubuntu / Linux Mint (`.deb`)**:
  ```bash
  sudo dpkg -i Clapi-<version>-amd64.deb
  sudo apt-get install -f
  ```
- **Fedora / RHEL / openSUSE (`.rpm`)**:
  ```bash
  sudo rpm -i Clapi-<version>-x86_64.rpm
  ```
- **Standalone Tarball (`.tar.gz`)**:
  ```bash
  tar -xzf Clapi-<version>-linux-x64.tar.gz && cd Clapi && ./bin/Clapi
  ```

### macOS

1. Download the latest `.dmg` from [GitHub Releases](https://github.com/MakeForge/Clapi/releases/latest).
2. Open the `.dmg` file and drag **Clapi** into `/Applications`.
3. Launch Clapi from Spotlight (`⌘ + Space`) or Finder.

> **macOS First Launch Note:** If you see a warning that the app "cannot be opened because Apple cannot check it for malicious software", right-click the app → **Open** → **Open**, or go to **System Settings → Privacy & Security → Open Anyway**.

For system requirements, universal Fat JAR instructions, and full setup details, see the [Installation Guide](https://makeforge.github.io/Clapi/guide/installation).

---

## Continuous Integration

Clapi uses [GitHub Actions](https://github.com/features/actions) for CI. Push to `main` runs the quality gate; tags matching `v*` run the full desktop, network, web, and QA test gate, then build and publish a GitHub Release.

See the workflow: [`.github/workflows/release.yml`](https://github.com/MakeForge/clapi/actions)

## License

Apache 2.0 — see [LICENSE](LICENSE).
