<script setup lang="ts">
import { createAgentStartClient, type AgentStartClient } from '../../client';
import { useAgentStartClient } from '../../client/vue';
import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import ChannelConnectionPanel from './ChannelConnectionPanel.vue';

const props = defineProps<{
  apiBase?: string;
  headers?: AgentStartHeaders;
  tenantId?: string;
  client?: AgentStartClient;
}>();

const global = useAgentStartConfig();
const injected = useAgentStartClient();
const client = props.client ?? injected ?? createAgentStartClient({
  baseUrl: props.apiBase ?? global.apiBase ?? '/api',
  getTenant: () => props.tenantId,
  headers: () => mergeAgentStartHeaders(global.headers, props.headers),
});
</script>

<template>
  <div class="channel-hub-app">
    <ChannelConnectionPanel :client="client" :tenant-id="tenantId" />
  </div>
</template>

<style scoped>
.channel-hub-app { display: flex; flex-direction: column; gap: 20px; box-sizing: border-box; padding: 20px 24px; color: #111827; }
:global(.dark) .channel-hub-app { color: #f3f4f6; }
@media (max-width: 640px) { .channel-hub-app { padding: 12px 16px; } }
</style>
