---
title: Response Viewer & Performance Metrics
description: Inspecting response bodies, HTTP headers, cookies, and network latency breakdowns in Clapi.
---

# Response Viewer & Performance Metrics

<p class="badges">
  <span class="badge badge-protocol">HTTP</span>
</p>

## Overview

When an HTTP request finishes, Clapi presents the returned data in the **Response Viewer** pane. It provides instant high-level metadata (HTTP status code, total roundtrip time, and byte size) alongside deep inspection views.

---

## Response Tabs

### 1. Pretty
- **Structured Rendering**: Formats JSON, XML, HTML, and YAML payloads with syntax coloring and collapsible bracket folds.
- **Search**: In-buffer regex and string search with match count navigation (`⌘ + F` / `Ctrl + F`).
- **Word Wrap**: Toggle between line wrapping and horizontal scrolling for wide payloads.
- **Copy**: One-click button to copy the formatted or raw body to your clipboard.

### 2. Raw
Displays the raw, unparsed stream bytes as received over the wire. Useful for debugging malformed payloads or verifying exact byte streams.

### 3. Preview
Renders visual content directly inside Clapi:
- **HTML Preview**: Sandboxed HTML document preview.
- **Image Preview**: Visual rendering of PNG, JPEG, GIF, and SVG images with dimensions and byte size.
- **PDF Preview**: Document thumbnail preview.

### 4. Headers
Displays all HTTP response headers in a searchable key-value table (e.g. `Cache-Control`, `ETag`, `Set-Cookie`, `Strict-Transport-Security`).

### 5. Cookies
Parsed table of cookies set by the remote server, including `Domain`, `Path`, `Expires`, `HttpOnly`, and `Secure` attributes.

---

## Performance Metrics Breakdown

Clicking the latency badge opens the detailed connection waterfall breakdown:

```
[DNS Lookup: 12ms] ──> [TCP Handshake: 24ms] ──> [TLS Negotiation: 38ms] ──> [TTFB: 42ms] ──> [Download: 14ms]
Total: 130 ms
```

| Metric | Description |
|---|---|
| **DNS Lookup** | Time spent resolving the hostname to an IP address. |
| **TCP Connect** | Time required to establish the initial TCP socket connection. |
| **TLS Handshake** | Time to negotiate SSL/TLS encryption cipher suites and exchange certificates. |
| **TTFB (First Byte)** | Time elapsed from dispatch until the first byte of response data arrives from the server. |
| **Content Download** | Time required to stream the remainder of the payload body over the network. |
| **Total Duration** | Cumulative roundtrip time from click to final render. |
