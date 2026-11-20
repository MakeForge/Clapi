---
title: Application Settings & Configuration
description: Configuring visual themes, font sizes, HTTP proxies, and SSL/TLS verification in Clapi.
---

# Application Settings & Configuration

<p class="badges">
  <span class="badge badge-protocol">Preferences</span>
</p>

## Overview

<ThemedScreenshot
  light="/generated/screenshots/SettingsDialogPreviewsKt.SettingsDialogPreviewLight.W760dp_H640dp.png"
  dark="/generated/screenshots/SettingsDialogPreviewsKt.SettingsDialogPreviewDark.W760dp_H640dp.png"
  alt="Clapi Application Settings & Preferences"
  :crop="false"
  max-width="760px"
/>

Access the settings modal by clicking the gear icon in the top toolbar or pressing `⌘ + ,` / `Ctrl + ,`.

## Configuration Tabs

### 1. General & Appearance
- **Theme**: Select between **System Default**, **Material 3 Light**, or **Material 3 Dark**.
- **Editor Font**: Choose your preferred monospace coding font (e.g. JetBrains Mono, Fira Code, Source Code Pro).
- **Font Size & Ligatures**: Customize editor font size and toggle font ligatures.

### 2. Network & Proxy
- **System Proxy**: Automatically detect OS proxy settings.
- **Custom HTTP/HTTPS/SOCKS5 Proxy**: Specify host, port, username, and password for corporate proxies.
- **Connection Timeout**: Custom connection and socket read timeouts (default: 30 seconds).

### 3. SSL / TLS Certificates
- **Validate SSL Certificates**: Toggle strict certificate checking (useful when testing against self-signed local services).
- **Custom CA Certificates**: Add custom root Certificate Authority `.pem` or `.crt` files.
