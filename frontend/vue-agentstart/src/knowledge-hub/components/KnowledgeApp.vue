<script setup lang="ts">
/**
 * KnowledgeApp — the single-drop-in knowledge base UI.
 *
 * Give it an `api-base` (default `/api`) and it wires everything for you:
 *   • card grid + create wizard drawer + detail drawer + chunks browser
 *   • the "先注册 Embedding 模型" empty-state nudge
 *   • toasts via the built-in UI `message` service (or your own via slots/events)
 *   • dataset URL copy via navigator.clipboard
 *
 * Zero adapter wiring on the host side:
 *
 *   <KnowledgeApp />                     // uses /api
 *   <KnowledgeApp api-base="/my-api" />  // custom prefix
 *
 * Hosts wanting to override any single behaviour can:
 *   • pass an explicit `api` prop (skips the built-in factory), or
 *   • listen to `@go-to-embedding-setup` to intercept the router push.
 */
import { computed } from 'vue';

import { message } from '../../ui';

import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import { createSpringAgentStartAdapter } from '../adapters/springAgentStart';
import type { KnowledgeHubApi } from '../types';
import KnowledgeHubApp from './KnowledgeHubApp.vue';

interface Props {
  apiBase?: string;
  api?: KnowledgeHubApi;
  /** Extra headers; a function is evaluated again before every request. */
  headers?: AgentStartHeaders;
  showEmbeddingSetupHint?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  api: undefined,
  showEmbeddingSetupHint: true,
});

const globalConfig = useAgentStartConfig();
const resolvedApiBase = computed(
  () => props.apiBase ?? globalConfig.apiBase ?? '/api',
);

const emit = defineEmits<{
  (e: 'go-to-embedding-setup'): void;
}>();

const resolvedApi = computed<KnowledgeHubApi>(() => {
  if (props.api) return props.api;
  return createSpringAgentStartAdapter({
    baseUrl: resolvedApiBase.value,
    headers: () => mergeAgentStartHeaders(globalConfig.headers, props.headers),
    onSuccess: (msg: string) => message.success(msg),
    onError: (msg: string) => message.error(msg),
    onCopyApi: (id: string) => {
      const url = `${window.location.origin}${resolvedApiBase.value.replace(/\/+$/, '')}/datasets/${id}`;
      navigator.clipboard.writeText(url).then(
        () => message.success('已复制 API 地址'),
        () => message.error('复制失败'),
      );
    },
    onGoToEmbeddingSetup: props.showEmbeddingSetupHint
      ? () => emit('go-to-embedding-setup')
      : undefined,
  });
});
</script>

<template>
  <KnowledgeHubApp :api="resolvedApi" />
</template>
