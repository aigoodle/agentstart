<script setup lang="ts">
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

/**
 * AgentStudioShell — Dify-style editor-page frame.
 *
 *   ┌─────┬──────────────────────────────────────────────┐
 *   │ 🤖  │                                              │
 *   │ AAA │                                              │
 *   │ ─── │                default slot                  │
 *   │ 编排 │            (active tab content)              │
 *   │ API │                                              │
 *   │ 日志 │                                              │
 *   │ 监测 │                                              │
 *   └─────┴──────────────────────────────────────────────┘
 *
 * The active tab is passed in via v-model:tab
 * and rendered by the host via <slot :tab="tab">. Nav clicks fire `update:tab`.
 * Data permissions use the application ID and the authenticated SDK client.
 */
import { computed, markRaw, ref } from 'vue';
import {
  ApiOutlined,
  AppstoreOutlined,
  FileTextOutlined,
  LineChartOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue';

import AppPermissionsModal from './AppPermissionsModal.vue';
import { createAgentStartClient } from '../../client';
import type { AgentsNamespace } from '../../client/agents';
import { useAgentStartClient } from '../../client/vue';
import { useAgentStartConfig, mergeAgentStartHeaders, type AgentStartHeaders } from '../../config';

import type { StudioNavItem, StudioTab } from '../types';

interface Props {
  /** The active tab id (v-model). */
  tab: StudioTab;
  /** The agent being edited — shown in the top-left cell. */
  agentName: string;
  /** Application ID required to persist permissions. */
  appId?: string;
  tenantId?: string;
  permissionsApi?: AgentsNamespace;
  apiBase?: string;
  headers?: AgentStartHeaders;
  agentType?: string;
  /** Icon + background for the top-left avatar. */
  icon?: string;
  iconBg?: string;
  /**
   * Override the default 4-nav set. If omitted, the shell shows Dify's canonical
   * 编排 / 访问 API / 日志与标注 / 监测.
   */
  nav?: StudioNavItem[];
}

const props = withDefaults(defineProps<Props>(), {
  icon: '🤖',
  iconBg: '#EEF4FF',
  agentType: 'AGENT',
});

const emit = defineEmits<{
  (e: 'update:tab', v: StudioTab): void;
  (e: 'back'): void;
  (e: 'permissions-saved'): void;
}>();

const permissionsOpen = ref(false);
const injectedClient = useAgentStartClient();
const globalConfig = useAgentStartConfig();
const permissionsApi = computed(() => props.permissionsApi ?? injectedClient?.agents ?? createAgentStartClient({
  baseUrl: props.apiBase ?? globalConfig.apiBase ?? '/api',
  headers: () => mergeAgentStartHeaders(globalConfig.headers, props.headers),
}).agents);

// Antd icons are frozen with markRaw so Vue doesn't wrap them in reactivity —
// they're stateless components and don't need to be observed.
const DEFAULT_NAV: StudioNavItem[] = [
  {
    id: 'orchestrate',
    title: '编排',
    icon: '',
    iconComponent: markRaw(AppstoreOutlined),
  },
  {
    id: 'api',
    title: '访问 API',
    icon: '',
    iconComponent: markRaw(ApiOutlined),
  },
  {
    id: 'logs',
    title: '日志与标注',
    icon: '',
    iconComponent: markRaw(FileTextOutlined),
  },
  {
    id: 'monitor',
    title: '监测',
    icon: '',
    iconComponent: markRaw(LineChartOutlined),
  },
];

const items = computed<StudioNavItem[]>(() => props.nav ?? DEFAULT_NAV);
</script>

<template>
  <div class="studio-shell">
    <!-- Left rail -->
    <aside class="studio-rail">
      <div class="studio-brand" @click="emit('back')" title="返回应用列表">
        <div class="studio-brand-icon" :style="{ background: iconBg }">
          {{ icon }}
        </div>
        <div class="studio-brand-meta">
          <div class="studio-brand-name" :title="agentName">{{ agentName }}</div>
          <div class="studio-brand-type">{{ agentType }}</div>
        </div>
      </div>
      <nav class="studio-nav">
        <AsShadcnButton variant="ghost"
          v-for="it in items"
          :key="it.id"
          type="button"
          class="studio-nav-item"
          :class="{ 'studio-nav-item-active': !permissionsOpen && tab === it.id }"
          @click="permissionsOpen = false; emit('update:tab', it.id)"
        >
          <component
            :is="it.iconComponent"
            v-if="it.iconComponent"
            class="studio-nav-icon"
          />
          <span v-else class="studio-nav-icon">{{ it.icon }}</span>
          <span>{{ it.title }}</span>
        </AsShadcnButton>
        <AsShadcnButton variant="ghost" type="button" class="studio-nav-item" :class="{ 'studio-nav-item-active': permissionsOpen }" :disabled="!appId"
          :title="appId ? '设置应用数据访问范围' : '请先保存应用后设置数据权限'"
 @click="permissionsOpen = true">
          <SafetyCertificateOutlined class="studio-nav-icon" />
          <span>数据权限设置</span>
        </AsShadcnButton>
      </nav>
    </aside>

    <!-- Main pane — host renders the active tab -->
    <main class="studio-main">
      <AppPermissionsModal v-if="appId && permissionsOpen" :open="true" presentation="inline"
        :app-id="appId" :app-name="agentName" :tenant-id="tenantId" :api="permissionsApi"
        @saved="emit('permissions-saved')" />
      <slot v-else :tab="tab" />
    </main>
  </div>
</template>

<style scoped>
.studio-shell {
  display: flex;
  min-height: 100%;
  background: var(--muted);
}
.studio-rail {
  width: 208px;
  flex-shrink: 0;
  background: var(--background);
  border-right: 1px solid var(--border);
  padding: 14px 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.studio-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 4px 12px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
}
.studio-brand-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 20px;
}
.studio-brand-meta {
  min-width: 0;
  flex: 1;
}
.studio-brand-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.studio-brand-type {
  margin-top: 2px;
  font-size: 10px;
  font-weight: 600;
  color: var(--muted-foreground);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.studio-nav {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.studio-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  color: var(--muted-foreground);
  cursor: pointer;
  text-align: left;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.studio-nav-item:disabled { opacity: 0.5; cursor: not-allowed; }
.studio-nav-item:hover:not(:disabled) {
  background: var(--muted);
  color: var(--foreground);
}
.studio-nav-item-active {
  background: color-mix(in srgb, var(--primary) 10%, var(--background));
  color: var(--primary);
  font-weight: 600;
}
.studio-nav-icon {
  font-size: 15px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: inherit;
}
.studio-main {
  flex: 1;
  min-width: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
</style>
