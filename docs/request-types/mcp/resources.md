---
title: MCP Resources & Subscriptions
description: Reading static and dynamic resource URIs and managing live subscriptions in Clapi.
---

# MCP Resources & Subscriptions

<p class="badges">
  <span class="badge badge-protocol">MCP</span>
</p>

## Overview

MCP Resources are data artifacts exposed by servers (e.g. database schemas, file contents, system logs, documentation) identified by URIs (e.g. `file:///var/log/syslog`, `postgres://schema/public`).

## Reading Resources

- **Resource Browser**: View all resources returned by `resources/list`.
- **Direct Read**: Click a resource to dispatch `resources/read`. Text and binary content render directly in the Response pane.
- **Resource Templates**: For parameterized URIs (e.g. `github://repos/{owner}/{repo}/issues`), Clapi presents input fields to supply URI template variables.

## Live Subscriptions

If the server advertises subscription capabilities (`resources/subscribe`):
- Click **Subscribe** on any resource.
- When the underlying resource changes on the server, a notification (`notifications/resources/updated`) is received.
- Clapi automatically re-reads the resource and updates your response view with the latest content in real time.
