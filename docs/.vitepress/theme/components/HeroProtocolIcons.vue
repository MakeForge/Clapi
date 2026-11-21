<script setup lang="ts">
import { withBase } from 'vitepress';

interface ProtocolIcon {
  name: string;
  src: string;
  href: string;
  monochrome?: boolean;
  wide?: boolean;
}

const protocols: ProtocolIcon[] = [
  { name: 'HTTP / REST', src: '/icons/request-types/http.svg', href: '/request-types/http/', monochrome: true },
  { name: 'GraphQL', src: '/icons/request-types/graphql.svg', href: '/request-types/graphql/' },
  { name: 'gRPC', src: '/icons/request-types/grpc.svg', href: '/request-types/grpc/', wide: true },
  { name: 'WebSocket', src: '/icons/request-types/websocket.svg', href: '/request-types/websocket/', monochrome: true },
  { name: 'RabbitMQ (AMQP)', src: '/icons/request-types/rabbitmq.svg', href: '/request-types/rabbitmq/' },
  { name: 'Server-Sent Events (SSE)', src: '/icons/request-types/sse.svg', href: '/request-types/realtime/', monochrome: true },
  { name: 'Model Context Protocol (MCP)', src: '/icons/request-types/mcp-server.svg', href: '/request-types/mcp/', monochrome: true },
];
</script>

<template>
  <div class="hero-protocol-icons" aria-label="Supported protocols">
    <a
      v-for="p in protocols"
      :key="p.name"
      :href="withBase(p.href)"
      :title="p.name"
      class="hero-protocol-link"
    >
      <img
        :src="withBase(p.src)"
        :alt="p.name"
        :class="['hero-protocol-icon', { monochrome: p.monochrome, wide: p.wide }]"
      />
    </a>
  </div>
</template>
