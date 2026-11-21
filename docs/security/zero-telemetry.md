---
title: Zero Telemetry & Privacy Guarantee
description: Clapi's strict privacy policy, zero telemetry architecture, and air-gapped network verification.
---

# Zero Telemetry & Privacy Guarantee

<p class="badges">
  <span class="badge badge-protocol">Privacy First</span>
  <span class="badge badge-version">Core Guarantee</span>
</p>

## The Clapi Privacy Covenant

Unlike most modern commercial API clients (e.g. Postman, Insomnia) that enforce mandatory cloud logins, continuously sync private endpoint data to third-party databases, and inject analytics tracking SDKs, **Clapi is built on an absolute privacy guarantee**:

::: tip The Guarantee
1. **Zero Telemetry**: Clapi contains **no** telemetry agents, tracking pixels, usage analytics, crash-reporting SDKs (no Sentry, Google Analytics, Mixpanel, Segment, or PostHog).
2. **Zero Spying or Tracking**: Clapi does not collect personal identifiers, device hardware fingerprints, keystrokes, or request payloads.
3. **No Phantom Network Calls**: Clapi initiates network calls **only** when you explicitly press **Send** or connect to an MCP/WebSocket/gRPC server you specifically configured.
4. **Local Data Sovereignty**: All collections, environments, certificates, and workspaces live strictly on your local disk.
:::

---

## Air-Gapped & Enterprise Compliance

Because Clapi has zero external cloud dependencies, it can run reliably inside:
- **Air-Gapped Networks**: High-security, isolated server rooms without public internet access.
- **Financial & Healthcare Institutions**: Environments regulated by SOC 2, HIPAA, GDPR, and PCI-DSS where leaking proprietary API payloads to SaaS vendors is prohibited.
- **Defense & Government**: Systems requiring strict supply chain auditing and zero unauthorized outbound network sockets.

---

## How to Verify Zero Telemetry

We encourage developers and security teams to verify our privacy claims independently.

### Option 1: Packet Capture with `tcpdump`
Launch `tcpdump` on your network interface and start Clapi:
```bash
sudo tcpdump -i any -n "not port 53" -w clapi_traffic.pcap
```
1. Open Clapi.
2. Browse collections, switch environments, and inspect settings.
3. Observe the packet capture output: **zero network packets are emitted**.

### Option 2: Local Proxy Inspection (mitmproxy / Wireshark)
Configure your system HTTP proxy to route through `mitmproxy` (`mitmweb`). You will observe that Clapi makes zero background requests on startup, idle, or shutdown.
