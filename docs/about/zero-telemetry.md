---
title: Zero Telemetry & Privacy Guarantee
description: Clapi's strict privacy policy, zero telemetry architecture, offline-first design, and enterprise compliance.
---

# Zero Telemetry & Privacy Guarantee

<p class="badges">
  <span class="badge badge-protocol">Security</span>
</p>

## The Clapi Privacy Covenant

Unlike most modern commercial API clients (e.g. Postman, Insomnia) that enforce mandatory cloud accounts, continuously sync private endpoint data to third-party SaaS databases, and inject analytics tracking SDKs, **Clapi is built on an absolute privacy guarantee**:

::: tip The Guarantee
1. **Zero Telemetry**: Clapi contains **no** telemetry agents, tracking pixels, usage analytics, or crash-reporting SDKs (no Sentry, Google Analytics, Mixpanel, Segment, or PostHog). No telemetry at all is collected.
2. **No Cloud Sign-In Required**: There are zero user accounts, zero sign-in walls, and no mandatory cloud registrations. You can download and immediately use every feature of Clapi without creating an account or providing an email.
3. **100% Offline Capability**: Clapi operates completely offline by default. All collections, environments, certificates, and settings live strictly on your local disk and require no internet connection to function.
4. **Zero Spying or Tracking**: Clapi does not collect personal identifiers, device hardware fingerprints, keystrokes, or request payloads.
5. **No Phantom Network Calls**: Clapi initiates network calls **only** when you explicitly trigger **Send** or connect to an MCP/WebSocket/gRPC/RabbitMQ server you specifically configured.
6. **Local Data Sovereignty**: All collections, environments, certificates, and workspaces live strictly on your local filesystem under your direct control.
:::

## Air-Gapped & Enterprise Compliance

Because Clapi has zero external cloud dependencies and operates 100% offline, it can run reliably inside:
- **Air-Gapped Networks**: High-security, isolated server rooms without public internet access.
- **Financial & Healthcare Institutions**: Environments regulated by SOC 2, HIPAA, GDPR, and PCI-DSS where leaking proprietary API payloads to SaaS vendors is prohibited.
- **Defense & Government**: Systems requiring strict supply chain auditing, air-gapped isolation, and zero unauthorized outbound network sockets.
