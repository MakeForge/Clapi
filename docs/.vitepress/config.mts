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
    appearance: 'dark',
    nav: [
      {
        text: 'Getting Started',
        items: [
          { text: 'Installation', link: '/guide/installation' },
          { text: 'Quickstart', link: '/guide/quickstart' },
          { text: 'Migrating to Clapi', link: '/guide/migrate-to-clapi' },
          { text: 'Core Concepts', link: '/guide/core-concepts' }
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
          { text: 'RabbitMQ (AMQP)', link: '/request-types/rabbitmq/' },
          { text: 'Model Context Protocol (MCP)', link: '/request-types/mcp/' },
          { text: 'Realtime Streams (SSE/MQTT)', link: '/request-types/realtime/' }
        ],
        activeMatch: '/request-types/'
      },
      {
        text: 'Organization',
        items: [
          {
            text: 'Workspace',
            items: [
              { text: 'Collections & Folders', link: '/organization/workspace/collections' },
              { text: 'Request Defaults', link: '/organization/workspace/request-defaults' },
              { text: 'Protobuf Settings', link: '/organization/workspace/protobuf-settings' }
            ]
          },
          {
            text: 'Variables & Environments',
            items: [
              { text: 'Variables', link: '/organization/environments/variables' },
              { text: 'Dynamic Variables', link: '/organization/environments/dynamic-variables' },
              { text: 'Environments', link: '/organization/environments/environments' },
              { text: 'Secret Managers', link: '/organization/environments/secret-managers' }
            ]
          },
          {
            text: 'Git Collaboration',
            items: [
              { text: 'Git Sync Collaboration', link: '/organization/collaboration/git-sync-overview' },
              { text: 'In-App Git Operations', link: '/organization/collaboration/git-operations' },
              { text: 'Team Workflows & PRs', link: '/organization/collaboration/team-workflows' }
            ]
          }
        ],
        activeMatch: '/organization/'
      },
      {
        text: 'Automation',
        items: [
          { text: 'V8 Scripting Engine', link: '/automation/engine-architecture' },
          { text: 'Pre-Request Scripting', link: '/automation/pre-request' },
          { text: 'Post-Response Assertions', link: '/automation/post-response-tests' },
          { text: 'Scripting API Reference', link: '/automation/api-reference' },
          { text: 'MCP Server', link: '/automation/mcp-server' }
        ],
        activeMatch: '/automation/'
      },
      {
        text: 'Settings',
        items: [
          { text: 'Keyboard Shortcuts', link: '/settings/shortcuts' },
          { text: 'Settings & Proxy', link: '/settings/settings' },
          { text: 'Import & Export', link: '/settings/import-export/import' },
        ],
        activeMatch: '/settings/'
      },
      {
        text: 'About',
        items: [
          { text: 'SBOM & Vulnerability Audits', link: '/about/sbom-vulnerabilities' },
          { text: 'Zero Telemetry Guarantee', link: '/about/zero-telemetry' },
          { text: 'Techstack', link: '/about/techstack' },
          { text: 'Encrypted Credentials', link: '/about/encrypted-credentials' }
        ],
        activeMatch: '/about/'
      }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Getting Started',
          items: [
            { text: 'Installation', link: '/guide/installation' },
            { text: 'Quickstart', link: '/guide/quickstart' },
            { text: 'Migrating to Clapi', link: '/guide/migrate-to-clapi' },
            { text: 'Core Concepts', link: '/guide/core-concepts' }
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
          text: 'RabbitMQ (AMQP)',
          collapsed: false,
          items: [
            { text: 'RabbitMQ Overview', link: '/request-types/rabbitmq/' },
            { text: 'Publish & Subscribe', link: '/request-types/rabbitmq/publish-subscribe' },
            { text: 'RPC (Request-Reply)', link: '/request-types/rabbitmq/rpc' },
            { text: 'Authentication & TLS', link: '/request-types/rabbitmq/auth' }
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
      '/organization/': [
        {
          text: 'Workspace',
          items: [
            { text: 'Collections & Folders', link: '/organization/workspace/collections' },
            { text: 'Request Defaults', link: '/organization/workspace/request-defaults' },
            { text: 'Protobuf Settings', link: '/organization/workspace/protobuf-settings' }
          ]
        },
        {
          text: 'Variables & Environments',
          items: [
            { text: 'Variables', link: '/organization/environments/variables' },
            { text: 'Dynamic Variables', link: '/organization/environments/dynamic-variables' },
            { text: 'Environments', link: '/organization/environments/environments' },
            { text: 'Secret Managers', link: '/organization/environments/secret-managers' }
          ]
        },
        {
          text: 'Git Collaboration',
          items: [
            { text: 'Git Sync Collaboration', link: '/organization/collaboration/git-sync-overview' },
            { text: 'In-App Git Operations', link: '/organization/collaboration/git-operations' },
            { text: 'Team Workflows & PRs', link: '/organization/collaboration/team-workflows' }
          ]
        }
      ],
      '/automation/': [
        {
          text: 'Automation',
          items: [
            { text: 'V8 Engine Architecture', link: '/automation/engine-architecture' },
            { text: 'Pre-Request Scripting', link: '/automation/pre-request' },
            { text: 'Post-Response Assertions', link: '/automation/post-response-tests' },
            { text: 'Scripting API Reference', link: '/automation/api-reference' },
            { text: 'MCP Server', link: '/automation/mcp-server' }
          ]
        }
      ],
      '/settings/': [
        {
          text: 'Settings',
          items: [
            { text: 'Keyboard Shortcuts', link: '/settings/shortcuts' },
            { text: 'Settings & Proxy', link: '/settings/settings' },
            { text: 'Encrypted Credentials', link: '/about/encrypted-credentials' },
            { text: 'Architecture Deep Dive', link: '/settings/architecture' }
          ]
        },
        {
          text: 'Import & Export',
          items: [
            { text: 'Importing Specifications', link: '/settings/import-export/import' },
            { text: 'Exporting Workspaces', link: '/settings/import-export/export' }
          ]
        }
      ],
      '/about/': [
        {
          text: 'About',
          items: [
            { text: 'SBOM & Vulnerability Audits', link: '/about/sbom-vulnerabilities' },
            { text: 'Zero Telemetry Guarantee', link: '/about/zero-telemetry' },
            { text: 'Techstack', link: '/about/techstack' },
            { text: 'Encrypted Credentials', link: '/about/encrypted-credentials' }
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
      copyright: 'Copyright © 2026 MakeForge'
    }
  }
});
