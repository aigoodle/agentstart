<script setup lang="ts">
import { Drawer as ShadcnDrawer } from '../../ui/components/Drawer';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

import { ref, watch } from 'vue';

import type { AgentStartClient } from '../../client';
import type {
  ConnectorAction,
  ConnectorDefinition,
  ConnectorResult,
} from '../types';
import JsonSchemaForm from '../../ui/components/JsonSchemaForm.vue';

const props = defineProps<{
  open: boolean;
  client: AgentStartClient;
  connector?: ConnectorDefinition;
  action?: ConnectorAction;
  tenantId?: string;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
}>();

const args = ref<Record<string, unknown>>({});
const running = ref(false);
const result = ref<ConnectorResult>();

watch(
  () => props.action?.id,
  () => {
    args.value = {};
    result.value = undefined;
  },
);

async function run() {
  if (!props.connector || !props.action) return;
  running.value = true;
  try {
    result.value = await props.client.connectors.execute(
      props.connector.key.provider,
      props.connector.key.connectorId,
      props.action.id,
      args.value,
      props.tenantId,
    );
  } catch (e: any) {
    result.value = {
      success: false,
      error: { code: 'request_failed', message: e?.message ?? '调用失败' },
    };
  } finally {
    running.value = false;
  }
}
</script>

<template>
  <ShadcnDrawer :open="open" class="catd-drawer" width="min(520px, 92vw)" :closable="false"
    :body-style="{ padding: 0, display: 'flex', flexDirection: 'column', minHeight: 0 }"
    @update:open="emit('update:open', $event)">
        <header class="catd-header">
          <div class="catd-header-text">
            <b>测试 · {{ action?.name }}</b>
            <small>{{ connector?.name }}</small>
          </div>
          <AsShadcnButton variant="ghost" class="catd-close" @click="emit('update:open', false)">
            ✕
          </AsShadcnButton>
        </header>

        <main class="catd-main">
          <JsonSchemaForm v-model="args" :schema="action?.inputSchema" :ui-schema="action?.metadata?.uiSchema" />
          <AsShadcnButton variant="ghost"
            class="catd-run"
            :disabled="running"
            @click="run"
          >
            {{ running ? '执行中…' : '执行 Action' }}
          </AsShadcnButton>
          <pre
            v-if="result"
            :class="['catd-result', { 'is-bad': !result.success }]"
          >{{ JSON.stringify(result, null, 2) }}</pre>
        </main>

  </ShadcnDrawer>
</template>

<style scoped>
/* -------- 遮罩与抽屉容器 -------- */
.catd-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 1100;
}
.catd-drawer {
  position: absolute;
  right: 0;
  top: 0;
  height: 100%;
  width: min(520px, 92vw);
  display: flex;
  flex-direction: column;
  background: var(--background);
  box-shadow: -6px 0 22px rgba(15, 23, 42, 0.14);
}
.dark .catd-drawer {
  background: #1f1f1f;
}

/* 进出场动画 */
.catd-enter-active,
.catd-leave-active {
  transition: opacity 0.18s ease;
}
.catd-enter-active .catd-drawer,
.catd-leave-active .catd-drawer {
  transition: transform 0.18s ease;
}
.catd-enter-from,
.catd-leave-to {
  opacity: 0;
}
.catd-enter-from .catd-drawer,
.catd-leave-to .catd-drawer {
  transform: translateX(48px);
}

/* -------- 头部 -------- */
.catd-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.catd-header-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.catd-header-text b {
  font-size: 15px;
  color: var(--foreground);
}
.catd-header-text small {
  font-size: 12px;
  color: var(--muted-foreground);
}
.catd-close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  font-size: 14px;
  color: var(--muted-foreground);
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.catd-close:hover {
  background: var(--muted);
  color: var(--foreground);
}
.dark .catd-header {
  border-bottom-color: #2d2d2d;
}
.dark .catd-header-text b {
  color: #f3f4f6;
}
.dark .catd-close:hover {
  background: #2d2d2d;
  color: #f3f4f6;
}

/* -------- 主体 -------- */
.catd-main {
  flex: 1;
  overflow: auto;
  padding: 20px;
  display: grid;
  gap: 18px;
  align-content: start;
}

/* -------- 执行按钮 -------- */
.catd-run {
  padding: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: #6366f1;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.catd-run:hover {
  background: #4f46e5;
}
.catd-run:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.dark .catd-run:hover {
  background: #818cf8;
}

/* -------- 执行结果 -------- */
.catd-result {
  margin: 0;
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: #166534;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  max-height: 340px;
  overflow: auto;
}
.catd-result.is-bad {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}
.dark .catd-result {
  color: #34d399;
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.3);
}
.dark .catd-result.is-bad {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.12);
  border-color: rgba(220, 38, 38, 0.3);
}
</style>
