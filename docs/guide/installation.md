---
title: Installation & Setup
description: How to download, install, and run Clapi on macOS, Linux, and Windows.
---

# Installation & Setup

<p class="badges">
  <span class="badge badge-protocol">Windows</span>
  <span class="badge badge-protocol">Linux</span>
  <span class="badge badge-protocol">macOS</span>
</p>

## System Requirements

| Operating System | Architecture | Minimum |
|---|---|---|
| **macOS** | Apple Silicon (M1/M2/M3/M4) / Intel (x86_64) | macOS 12 (Monterey)
| **Linux** | x86_64, aarch64 | Ubuntu 20.04+, Fedora 38+, Arch
| **Windows** | x64 | Windows 10

Clapi comes as fully-bundled application, **no external JDK or JRE installation is required** when using native installers (`.dmg`, `.deb`, `.rpm`, or `.msi`).

---

## Installation by Platform

### Windows
1. Download `Clapi-Setup-<version>.msi` or `.exe` from [GitHub Releases](https://github.com/makeforge/clapi/releases).
2. Run the installer and follow the setup wizard.
3. Clapi will install to `AppData\Local\Programs\Clapi` and create a Start Menu shortcut.

### Linux
Download the native package matching your distribution from [GitHub Releases](https://github.com/makeforge/clapi/releases):

#### Debian / Ubuntu / Linux Mint (`.deb`)
```bash
sudo dpkg -i Clapi-<version>-amd64.deb
sudo apt-get install -f
```

#### Fedora / RHEL / openSUSE (`.rpm`)
```bash
sudo rpm -i Clapi-<version>-x86_64.rpm
```

#### Standalone Tarball
```bash
tar -xzf Clapi-<version>-linux-x64.tar.gz
cd Clapi
./bin/Clapi
```

### macOS
1. Download the latest `.dmg` from the [GitHub Releases](https://github.com/makeforge/clapi/releases).
2. Open the `.dmg` file and drag **Clapi** into your `/Applications` directory.
3. Launch Clapi from Spotlight (`⌘ + Space`) or Finder.

::: tip Homebrew (Coming Soon)
```bash
brew install --cask clapi
```
:::

---

## Standalone Fat JAR (Universal)

If you prefer running via a system JVM (JDK 17 or JDK 21+):

```bash
# Download Clapi-<version>.jar and run directly
java -jar Clapi-<version>.jar
```

::: tip Verified JREs
Tested and optimized on **Eclipse Temurin JDK 21**.
:::
