---
layout: home

hero:
  name: Clapi
  text: The Bullshit-Free API Client 
  tagline: Open-Source multi-protocol Desktop API development tool with zero telemetry or cloud lock-in, extensive Secret Manager support, Git-native sync and built-in MCP Server for Windows, Linux, macOS for free.
  image:
    light: /generated/screenshots/MainScreenPreviewsKt.MainScreenHttpPreviewLight.W1600dp_H800dp.png
    dark: /generated/screenshots/MainScreenPreviewsKt.MainScreenHttpPreviewDark.W1600dp_H800dp.png
    alt: Clapi Desktop HTTP Request Workbench
  actions:
    - theme: brand
      text: Get Started
      link: /guide/installation
    - theme: alt
      text: Explore Protocols
      link: /request-types/http/
    - theme: alt
      text: Privacy
      link: /about/zero-telemetry

features:
  - title: 🚀 Quick Start
    details: Easily import your existing work from other tools like Postman or Insomnia and get up and running with Clapi.
    link: /guide/migrate-to-clapi
  - title: 🌐 Multi-Protocol Support
    details: First-class support for REST, GraphQL, WebSocket, SSE, gRPC unary/streaming, RabbitMQ (Publish/Subscribe & RPC), and Model Context Protocol (MCP).
    link: /request-types/http/
  - title: 🗂️ Organization
    details: Organize your requests via Collections and Folders to easily maintain a multitude of service endpoints of any protocol.
    link: /organization/collections
  - title: '<svg class="git-feature-icon" viewBox="0 0 256 256" width="20" height="20" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMinYMin meet"><path d="M251.172 116.594L139.4 4.828c-6.433-6.437-16.873-6.437-23.314 0l-23.21 23.21 29.443 29.443c6.842-2.312 14.688-.761 20.142 4.693 5.48 5.489 7.02 13.402 4.652 20.266l28.375 28.376c6.865-2.365 14.786-.835 20.269 4.657 7.663 7.66 7.663 20.075 0 27.74-7.665 7.666-20.08 7.666-27.749 0-5.764-5.77-7.188-14.235-4.27-21.336l-26.462-26.462-.003 69.637a19.82 19.82 0 0 1 5.188 3.71c7.663 7.66 7.663 20.076 0 27.747-7.665 7.662-20.086 7.662-27.74 0-7.663-7.671-7.663-20.086 0-27.746a19.654 19.654 0 0 1 6.421-4.281V94.196a19.378 19.378 0 0 1-6.421-4.281c-5.806-5.798-7.202-14.317-4.227-21.446L81.47 39.442l-76.64 76.635c-6.44 6.443-6.44 16.884 0 23.322l111.774 111.768c6.435 6.438 16.873 6.438 23.316 0l111.251-111.249c6.438-6.44 6.438-16.887 0-23.324" fill="#DE4C36"/></svg> Collaborate via Git'
    details: Collaborate on API collections directly through synchronization via standard Git repositories and zero cloud lock-in.
    link: /organization/collaboration/git-sync-overview
  - title: 🤖 MCP Server
    details: Let your AI Agent access Clapi to manage and execute requests, collections and environments for you via the built-in MCP Server.
    link: /automation/mcp-server
  - title: 🔑 External Secret Managers
    details: Use secret managers like Azure Vault, AWS Secrets, HashiCorp Vault, GCP Secrets, Keeper Security or KeepassXC to securely provide any type of credentials.
    link: /security/ksafe-encryption
  - title: ⚡ Scripting Support
    details: Write pre-request scripts, dynamic variables and post-response assertions powered by an isolated V8 JS runtime with full Chai-compatible assertions.
    link: /automation/pre-request
  - title: 🔒 Zero Telemetry or Sign-In Required
    details: Absolute privacy. No background telemetry and no analytics SDKs. All collections and secrets remain strictly on your local disk with ability to work 100% offline.
    link: /security/zero-telemetry
---
