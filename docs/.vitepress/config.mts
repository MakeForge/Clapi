import { defineConfig } from 'vitepress';

export default defineConfig({
  title: 'Clapi',
  description: 'Documentation for Clapi — Bullshit-Free API Client & MCP Workbench',
  base: process.env.GITHUB_PAGES ? '/Clapi/' : '/',
  cleanUrls: true,

  head: [
    ['link', { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }],
    ['link', { rel: 'mask-icon', href: '/favicon.svg', color: '#ffffff' }],
    ['meta', { name: 'theme-color', content: '#6750a4' }]
  ],

  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'Clapi',

    nav: [
      {
        text: 'Getting Started',
        items: [
          { text: 'Overview & Highlights', link: '/guide/' },
          { text: 'Installation & Setup', link: '/guide/installation' },
          { text: 'Quickstart', link: '/guide/quickstart' },
          { text: 'Core Concepts & UI Anatomy', link: '/guide/core-concepts' }
        ],
        activeMatch: '/guide/'
      },
      {
        text: 'Request Types',
        items: [
          { text: 'HTTP / REST', link: '/request-types/http/' },
          { text: 'GraphQL', link: '/request-types/graphql/' },
          { text: 'WebSocket', link: '/request-types/websocket/' },
          { text: 'gRPC', link: '/request-types/grpc/' },
          { text: 'Model Context Protocol (MCP)', link: '/request-types/mcp/' },
          { text: 'Realtime Streams (SSE/MQTT)', link: '/request-types/realtime/' }
        ],
        activeMatch: '/request-types/'
      },
      {
        text: 'Organization',
        items: [
          { text: 'Collections & Folders', link: '/workspaces/collections' },
          { text: 'Request Defaults', link: '/workspaces/folders-and-inheritance' },
          { text: 'Variables & Environments', link: '/environments/variables' },
          { text: 'Secret Managers', link: '/environments/secret-managers' },
          { text: 'Git Sync Collaboration', link: '/collaboration/git-sync-overview' }
        ],
        activeMatch: '/(workspaces|environments|collaboration)/'
      },
      {
        text: 'Automation',
        items: [
          { text: 'V8 Scripting Engine', link: '/scripting/engine-architecture' },
          { text: 'Pre-Request Scripting', link: '/scripting/pre-request' },
          { text: 'Post-Response Assertions', link: '/scripting/post-response-tests' },
          { text: 'Scripting API Reference', link: '/scripting/api-reference' }
        ],
        activeMatch: '/scripting/'
      },
      {
        text: 'Settings',
        items: [
          { text: 'Keyboard Shortcuts', link: '/reference/shortcuts' },
          { text: 'Import & Export', link: '/import-export/import' },
          { text: 'Settings & Proxy', link: '/reference/settings' },
          { text: 'Encrypted Credentials', link: '/security/ksafe-encryption' }
        ],
        activeMatch: '/(reference|import-export|security/ksafe-encryption)/'
      },
      {
        text: 'About',
        items: [
          { text: 'SBOM & Vulnerability Audits', link: '/security/sbom-vulnerabilities' },
          { text: 'Zero Telemetry Guarantee', link: '/security/zero-telemetry' },
          { text: 'Open Source Libraries', link: '/security/external-libraries' }
        ],
        activeMatch: '/security/(sbom-vulnerabilities|zero-telemetry|external-libraries)'
      }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Getting Started',
          items: [
            { text: 'Overview & Highlights', link: '/guide/' },
            { text: 'Installation & Setup', link: '/guide/installation' },
            { text: 'Quickstart', link: '/guide/quickstart' },
            { text: 'Core Concepts & UI Anatomy', link: '/guide/core-concepts' }
          ]
        }
      ],
      '/request-types/': [
        {
          text: 'HTTP / REST',
          collapsed: false,
          items: [
            { text: 'HTTP Overview', link: '/request-types/http/' },
            { text: 'URL Parameters & Query', link: '/request-types/http/parameters' },
            { text: 'Headers & Inheritance', link: '/request-types/http/headers' },
            { text: 'Request Body Formats', link: '/request-types/http/body' },
            { text: 'Authentication Methods', link: '/request-types/http/authentication' },
            { text: 'Response Viewer & Metrics', link: '/request-types/http/response-viewer' },
            { text: 'cURL Integration', link: '/request-types/http/curl' }
          ]
        },
        {
          text: 'GraphQL',
          collapsed: false,
          items: [
            { text: 'GraphQL Overview', link: '/request-types/graphql/' },
            { text: 'Query & Mutation Editor', link: '/request-types/graphql/queries' },
            { text: 'Query Variables', link: '/request-types/graphql/variables' },
            { text: 'Schema Introspection', link: '/request-types/graphql/introspection' },
            { text: 'GraphQL Authentication', link: '/request-types/graphql/auth' }
          ]
        },
        {
          text: 'WebSocket',
          collapsed: false,
          items: [
            { text: 'WebSocket Overview', link: '/request-types/websocket/' },
            { text: 'Connection & Handshake', link: '/request-types/websocket/connection' },
            { text: 'Message Composer', link: '/request-types/websocket/composer' },
            { text: 'Live Stream Log & Filter', link: '/request-types/websocket/message-log' },
            { text: 'Lifecycle & Heartbeats', link: '/request-types/websocket/lifecycle' }
          ]
        },
        {
          text: 'gRPC',
          collapsed: false,
          items: [
            { text: 'gRPC Overview', link: '/request-types/grpc/' },
            { text: 'Protobuf Management', link: '/request-types/grpc/proto-management' },
            { text: 'Server Reflection', link: '/request-types/grpc/reflection' },
            { text: 'Method Selection & RPC', link: '/request-types/grpc/method-invocation' },
            { text: 'JSON-to-Protobuf Composer', link: '/request-types/grpc/message-composer' },
            { text: 'Metadata & Headers', link: '/request-types/grpc/metadata' },
            { text: 'TLS & Client Certificates', link: '/request-types/grpc/tls' }
          ]
        },
        {
          text: 'Model Context Protocol (MCP)',
          collapsed: false,
          items: [
            { text: 'MCP Overview', link: '/request-types/mcp/' },
            { text: 'Transports (HTTP, SSE, Stdio)', link: '/request-types/mcp/transports' },
            { text: 'Tools Discovery & Execution', link: '/request-types/mcp/tools' },
            { text: 'Resources & Subscriptions', link: '/request-types/mcp/resources' },
            { text: 'Prompts & LLM Sampling', link: '/request-types/mcp/prompts' },
            { text: 'MCP OAuth 2.1 & Auth', link: '/request-types/mcp/auth' },
            { text: 'JSON-RPC Activity Log', link: '/request-types/mcp/activity-log' }
          ]
        },
        {
          text: 'Realtime Streams',
          collapsed: false,
          items: [
            { text: 'SSE, Socket.IO & MQTT', link: '/request-types/realtime/' }
          ]
        }
      ],
      '/workspaces/': [
        {
          text: 'Workspace',
          items: [
            { text: 'Collections & Folders', link: '/workspaces/collections' },
            { text: 'Request Defaults', link: '/workspaces/folders-and-inheritance' },
            { text: 'Protobuf Settings', link: '/workspaces/protobuf-settings' }
          ]
        },
        {
          text: 'Variables & Environments',
          items: [
            { text: 'Variables', link: '/environments/variables' },
            { text: 'Environments', link: '/environments/environments' },
            { text: 'Secret Managers', link: '/environments/secret-managers' }
          ]
        },
        {
          text: 'Git Collaboration',
          items: [
            { text: 'Git Sync Collaboration', link: '/collaboration/git-sync-overview' },
            { text: 'In-App Git Operations', link: '/collaboration/git-operations' },
            { text: 'Team Workflows & PRs', link: '/collaboration/team-workflows' }
          ]
        }
      ],
      '/environments/': [
        {
          text: 'Workspace',
          items: [
            { text: 'Collections & Folders', link: '/workspaces/collections' },
            { text: 'Request Defaults', link: '/workspaces/folders-and-inheritance' },
            { text: 'Protobuf Settings', link: '/workspaces/protobuf-settings' }
          ]
        },
        {
          text: 'Variables & Environments',
          items: [
            { text: 'Variables', link: '/environments/variables' },
            { text: 'Environments', link: '/environments/environments' },
            { text: 'Secret Managers', link: '/environments/secret-managers' }
          ]
        },
        {
          text: 'Git Collaboration',
          items: [
            { text: 'Git Sync Collaboration', link: '/collaboration/git-sync-overview' },
            { text: 'In-App Git Operations', link: '/collaboration/git-operations' },
            { text: 'Team Workflows & PRs', link: '/collaboration/team-workflows' }
          ]
        }
      ],
      '/collaboration/': [
        {
          text: 'Workspace',
          items: [
            { text: 'Collections & Folders', link: '/workspaces/collections' },
            { text: 'Request Defaults', link: '/workspaces/folders-and-inheritance' },
            { text: 'Protobuf Settings', link: '/workspaces/protobuf-settings' }
          ]
        },
        {
          text: 'Variables & Environments',
          items: [
            { text: 'Variables', link: '/environments/variables' },
            { text: 'Environments', link: '/environments/environments' },
            { text: 'Secret Managers', link: '/environments/secret-managers' }
          ]
        },
        {
          text: 'Git Collaboration',
          items: [
            { text: 'Git Sync Collaboration', link: '/collaboration/git-sync-overview' },
            { text: 'In-App Git Operations', link: '/collaboration/git-operations' },
            { text: 'Team Workflows & PRs', link: '/collaboration/team-workflows' }
          ]
        }
      ],
      '/scripting/': [
        {
          text: 'Automation',
          items: [
            { text: 'V8 Engine Architecture', link: '/scripting/engine-architecture' },
            { text: 'Pre-Request Scripting', link: '/scripting/pre-request' },
            { text: 'Post-Response Assertions', link: '/scripting/post-response-tests' },
            { text: 'Scripting API Reference', link: '/scripting/api-reference' }
          ]
        }
      ],
      '/reference/': [
        {
          text: 'Settings',
          items: [
            { text: 'Keyboard Shortcuts', link: '/reference/shortcuts' },
            { text: 'Settings & Proxy', link: '/reference/settings' },
            { text: 'Encrypted Credentials', link: '/security/ksafe-encryption' },
            { text: 'Architecture Deep Dive', link: '/reference/architecture' },
            { text: 'Testing Reference', link: '/reference/testing' }
          ]
        },
        {
          text: 'Import & Export',
          items: [
            { text: 'Importing Specifications', link: '/import-export/import' },
            { text: 'Exporting Workspaces', link: '/import-export/export' }
          ]
        }
      ],
      '/import-export/': [
        {
          text: 'Settings',
          items: [
            { text: 'Keyboard Shortcuts', link: '/reference/shortcuts' },
            { text: 'Settings & Proxy', link: '/reference/settings' },
            { text: 'Encrypted Credentials', link: '/security/ksafe-encryption' },
            { text: 'Architecture Deep Dive', link: '/reference/architecture' },
            { text: 'Testing Reference', link: '/reference/testing' }
          ]
        },
        {
          text: 'Import & Export',
          items: [
            { text: 'Importing Specifications', link: '/import-export/import' },
            { text: 'Exporting Workspaces', link: '/import-export/export' }
          ]
        }
      ],
      '/security/': [
        {
          text: 'About',
          items: [
            { text: 'SBOM & Vulnerability Audits', link: '/security/sbom-vulnerabilities' },
            { text: 'Zero Telemetry Guarantee', link: '/security/zero-telemetry' },
            { text: 'Open Source Libraries', link: '/security/external-libraries' },
            { text: 'Encrypted Credentials', link: '/security/ksafe-encryption' }
          ]
        }
      ]
    },

    search: {
      provider: 'local',
      options: {
        detailedView: true,
        miniSearch: {
          searchOptions: {
            fuzzy: 0.2,
            prefix: true,
            boost: { title: 4, heading: 2, content: 1 }
          }
        }
      }
    },

    markdown: {
      codeTransformers: [
        {
          pre(node) {
            node.properties['v-pre'] = ''
          }
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/makeforge/clapi' }
    ],

    footer: {
      message: 'Released under the Apache-2.0 License',
      copyright: 'Copyright © 2026 Clapi Contributors'
    }
  }
});
