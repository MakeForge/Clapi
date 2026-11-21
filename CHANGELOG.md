<!-- Modified by MakeForge (2026) -->
# Changelog

All notable changes to Clapi are documented here.

---

## [1.0.0] - TBD

### New Features

- **Core API Client & Protocols**: Desktop-native multi-protocol API client with HTTP/REST, Server-Sent Events (SSE), LLM streaming, GraphQL ([#30](https://github.com/MakeForge/Clapi/issues/30)), WebSocket ([#29](https://github.com/MakeForge/Clapi/issues/29)), gRPC with collection-level Protobuf management and proto folder workflow ([#21](https://github.com/MakeForge/Clapi/issues/21), [#23](https://github.com/MakeForge/Clapi/issues/23)), and RabbitMQ messaging (Publish/Subscribe & RPC).
- **OpenCollection Data Model & Multiple Collections**: Canonical OpenCollection storage format with support for managing multiple collections and folder-level settings/request defaults inheritance ([#7](https://github.com/MakeForge/Clapi/issues/7), [#19](https://github.com/MakeForge/Clapi/issues/19), [#24](https://github.com/MakeForge/Clapi/issues/24)).
- **Collection Import**: Import collections and environments from OpenAPI, OpenCollection, Postman, and Insomnia v5 ([#20](https://github.com/MakeForge/Clapi/issues/20), [#41](https://github.com/MakeForge/Clapi/issues/41)).
- **Git-Native Sync**: Built-in Git collection synchronization workflow ([#25](https://github.com/MakeForge/Clapi/issues/25)).
- **Authentication & OAuth2**: Support for Basic, Bearer, API Key, and OAuth2 authentication flows with dynamic OAuth2 access token variables ([#26](https://github.com/MakeForge/Clapi/issues/26), [#27](https://github.com/MakeForge/Clapi/issues/27)).
- **External Secret Managers**: External secret variable resolution ([#22](https://github.com/MakeForge/Clapi/issues/22)) with integrations for KeePassXC ([#28](https://github.com/MakeForge/Clapi/issues/28)), AWS Secrets Manager ([#31](https://github.com/MakeForge/Clapi/issues/31)), Azure Key Vault ([#32](https://github.com/MakeForge/Clapi/issues/32)), GCP Secret Manager ([#33](https://github.com/MakeForge/Clapi/issues/33)), and Keeper Security Cloud ([#34](https://github.com/MakeForge/Clapi/issues/34)).
- **Environments & Dynamic Variables**: Environment inheritance, visual customization, import/export enhancements ([#42](https://github.com/MakeForge/Clapi/issues/42)), user-defined scriptable dynamic variables ([#43](https://github.com/MakeForge/Clapi/issues/43)), and typed dynamic variables with modernized variable editor UI ([#44](https://github.com/MakeForge/Clapi/issues/44)).
- **Built-in MCP Server & Client**: Embedded Model Context Protocol (MCP) server for AI agent integration alongside MCP client request support ([#45](https://github.com/MakeForge/Clapi/issues/45)).
- **Scripting & Code Editor**: Javet V8 JavaScript pre/post-request scripting engine and custom Compose code editor with syntax highlighting, find/replace, go-to-line, folding, and diagnostics.
- **Desktop Workspace UX**: Drag-and-drop request tab reordering, collapsible/detachable response viewer, desktop UI polish ([#37](https://github.com/MakeForge/Clapi/issues/37)), native window titlebar integration, and JDK 25 AOT-cached native installers (macOS `.dmg`, Linux `.deb`, Windows `.exe`, and universal Fat JAR).
- **Undoable Action Snackbar**: Bottom-right action snackbar with one-click Undo for deleting (requests, folders with nested contents, collections, environments, history items), moving, and renaming items, enforcing single-snackbar replacement with a configurable auto-dismiss duration (1–60 seconds, default 5s) in General Global Settings.
- **Persistent Logging & Documentation**: Asynchronous persistent file and diagnostics logging ([#46](https://github.com/MakeForge/Clapi/issues/46)) and VitePress documentation site ([#40](https://github.com/MakeForge/Clapi/issues/40)).

### Fixes

- **Apache 2.0 License Compliance & Packaging**: Bundled `LICENSE` and `NOTICE` in native installers and the runnable fat JAR (preserving third-party dependency notices/licenses), added License & Attribution details to Help & About, preserved upstream ReqLab copyright attribution in `NOTICE`, and added modification notices to ReqLab-derived source files.

---
