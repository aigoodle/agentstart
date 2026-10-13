<script setup lang="ts">
/**
 * 访问 API tab — reproduces the Dify docs layout (Snipaste_09-01-20):
 *   - big heading + subtitle
 *   - Base URL block
 *   - Auth block
 *   - Per-endpoint anchor list on the right
 *   - Endpoint sections with request/response code samples
 *
 * All strings are host-provided so the same panel can back any agent. Anchors
 * scroll to their sections inside the panel's scroll container.
 */
import { computed, onMounted, ref, watch } from 'vue';
import { KeyOutlined } from '@ant-design/icons-vue';
import { message, Modal } from '../../ui';

import type { AppStudioApi, StudioApiKey } from '../api/types';

import ApiKeyManager from './ApiKeyManager.vue';

export interface ApiEndpoint {
  id: string;
  title: string;
  method?: string;
  path?: string;
  description?: string;
  code?: string;
}

interface Props {
  title?: string;
  subtitle?: string;
  baseUrl: string;
  /** Placeholder shown in the "Authorization: Bearer" example. */
  apiKey?: string;
  endpoints?: ApiEndpoint[];
  /** Server status label ("运行中" etc.). */
  serverStatus?: string;
  /**
   * The app the panel is showing docs for. Required to hit the api-tokens
   * endpoints; when absent the "API 密钥" button is hidden.
   */
  appId?: string;
  /**
   * Callback bag from the studio host. Only the {@code listApiKeys /
   * createApiKey / deleteApiKey} fields are used here — everything else is
   * for the log/monitor panels.
   */
  api?: AppStudioApi;
}

const props = withDefaults(defineProps<Props>(), {
  title: '工作流编排对话型应用 API',
  subtitle: '对话应用支持会话持久化，可将之前的聊天记录作为上下文进行回答，可适用于聊天/客服 AI 等。',
  apiKey: '{API_KEY}',
  endpoints: () => [],
  serverStatus: '运行中',
  appId: '',
  api: () => ({}),
});

const emit = defineEmits<{
  (e: 'copy', text: string): void;
}>();

// ── API 密钥 manager wiring ────────────────────────────────────────────────
// Whether the host wired any key-management endpoints. When none, the button
// disappears — no point offering an action that goes nowhere.
const canManageKeys = computed(
  () => !!props.appId && (!!props.api?.listApiKeys || !!props.api?.createApiKey),
);

const managerOpen = ref(false);
const keys = ref<StudioApiKey[]>([]);
const loadingKeys = ref(false);
const creatingKey = ref(false);

async function refreshKeys() {
  if (!props.appId || !props.api?.listApiKeys) return;
  loadingKeys.value = true;
  try {
    keys.value = (await props.api.listApiKeys(props.appId)) ?? [];
  } catch (e) {
    message.error(`加载 API 密钥失败：${(e as Error).message ?? e}`);
  } finally {
    loadingKeys.value = false;
  }
}

async function onCreateKey() {
  if (!props.appId || !props.api?.createApiKey) {
    message.warning('未接入创建密钥接口');
    return;
  }
  creatingKey.value = true;
  try {
    const row = await props.api.createApiKey(props.appId);
    keys.value = [row, ...keys.value];
    // Auto-copy the fresh token — Dify's UX pattern — plus a toast so the user
    // knows the copy happened without having to click again.
    try {
      await navigator.clipboard?.writeText(row.token);
      message.success('已创建并复制新密钥');
    } catch {
      message.success('已创建新密钥');
    }
  } catch (e) {
    message.error(`创建密钥失败：${(e as Error).message ?? e}`);
  } finally {
    creatingKey.value = false;
  }
}

async function onDeleteKey(row: StudioApiKey) {
  if (!props.appId || !props.api?.deleteApiKey) {
    message.warning('未接入删除密钥接口');
    return;
  }
  try {
    await props.api.deleteApiKey(props.appId, row.id);
    keys.value = keys.value.filter((k) => k.id !== row.id);
    message.success('已删除密钥');
  } catch (e) {
    message.error(`删除密钥失败：${(e as Error).message ?? e}`);
  }
}

function openManager() {
  managerOpen.value = true;
  refreshKeys();
}

// Refresh whenever the manager is toggled open externally.
watch(managerOpen, (v) => {
  if (v) refreshKeys();
});

const container = ref<HTMLElement | null>(null);
const activeAnchor = ref('');

const anchors = computed(() =>
  props.endpoints.map((e) => ({ id: e.id, title: e.title })),
);

function goto(id: string) {
  activeAnchor.value = id;
  const el = document.getElementById(`ep-${id}`);
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function copy(text: string) {
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    navigator.clipboard.writeText(text).catch(() => {});
  }
  emit('copy', text);
}

onMounted(() => {
  if (props.endpoints[0]) activeAnchor.value = props.endpoints[0].id;
});
</script>

<template>
  <div class="api">
    <header class="api-top">
      <h2 class="api-top-title">访问 API</h2>
      <div class="api-top-right">
        <span class="api-status">
          <span class="api-status-dot" />
          {{ serverStatus }}
        </span>
        <button
          v-if="canManageKeys"
          type="button"
          class="api-key-btn"
          @click="openManager"
        >
          <KeyOutlined />
          <span>API 密钥</span>
        </button>
        <span v-else class="api-key-hint">API 密钥</span>
      </div>
    </header>

    <ApiKeyManager
      v-model:open="managerOpen"
      :keys="keys"
      :loading="loadingKeys"
      :creating="creatingKey"
      @create="onCreateKey"
      @delete="onDeleteKey"
      @refresh="refreshKeys"
    />

    <div ref="container" class="api-body">
      <div class="api-main">
        <div class="api-headline">
          <h1 class="api-h1">{{ title }}</h1>
          <p class="api-lead">{{ subtitle }}</p>
        </div>

        <section class="api-section">
          <h3 class="api-h3">基础 URL</h3>
          <div class="api-codeblock">
            <div class="api-codeblock-head">Code</div>
            <pre class="api-code"><code>{{ baseUrl }}</code></pre>
            <button class="api-copy" @click="copy(baseUrl)">复制</button>
          </div>
        </section>

        <section class="api-section">
          <h3 class="api-h3">鉴权</h3>
          <p class="api-p">
            Service API 使用 <code>API-Key</code> 进行鉴权。
            <b>强烈建议开发者把 <code>API-Key</code> 放在后端存储，而非分享或者放在客户端存储</b>，以免
            <code>API-Key</code> 泄露，导致财产损失。所有 API 请求都应在
            <code>Authorization</code> HTTP Header 中包含您的 <code>API-Key</code>，如下所示：
          </p>
          <div class="api-codeblock">
            <div class="api-codeblock-head">Code</div>
            <pre class="api-code"><code>Authorization: Bearer {{ apiKey }}</code></pre>
            <button class="api-copy" @click="copy(`Authorization: Bearer ${apiKey}`)">
              复制
            </button>
          </div>
        </section>

        <section
          v-for="ep in endpoints"
          :id="`ep-${ep.id}`"
          :key="ep.id"
          class="api-section"
        >
          <span
            v-if="ep.method"
            class="api-method"
            :class="`api-method-${ep.method.toLowerCase()}`"
          >
            {{ ep.method }}
          </span>
          <code v-if="ep.path" class="api-path">{{ ep.path }}</code>
          <h3 class="api-h3">{{ ep.title }}</h3>
          <p v-if="ep.description" class="api-p">{{ ep.description }}</p>
          <div v-if="ep.code" class="api-codeblock api-codeblock-dark">
            <div class="api-codeblock-head api-codeblock-head-dark">Request</div>
            <pre class="api-code api-code-dark"><code>{{ ep.code }}</code></pre>
            <button class="api-copy api-copy-dark" @click="copy(ep.code ?? '')">
              复制
            </button>
          </div>
        </section>
      </div>

      <aside v-if="anchors.length > 0" class="api-toc">
        <div class="api-toc-title">目录</div>
        <button
          v-for="a in anchors"
          :key="a.id"
          type="button"
          class="api-toc-item"
          :class="{ 'api-toc-item-active': activeAnchor === a.id }"
          @click="goto(a.id)"
        >
          {{ a.title }}
        </button>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.api {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.api-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.api-top-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.api-top-right {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #64748b;
}
.api-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #16a34a;
}
.api-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #22c55e;
}
.api-key-hint {
  padding: 3px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
}
.api-key-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid #c7d2fe;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.api-key-btn:hover {
  background: #e0e7ff;
  border-color: #a5b4fc;
}
.api-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 0;
  overflow-y: auto;
}
.api-main {
  padding: 24px 40px;
  max-width: 900px;
}
.api-headline {
  margin-bottom: 24px;
}
.api-h1 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #0f172a;
}
.api-lead {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}
.api-section {
  margin-top: 28px;
}
.api-h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}
.api-p {
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}
.api-p code {
  padding: 1px 6px;
  background: #f1f5f9;
  border-radius: 4px;
  color: #dc2626;
  font-family: ui-monospace, monospace;
  font-size: 12px;
}
.api-codeblock {
  position: relative;
  margin-top: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
.api-codeblock-head {
  padding: 6px 12px;
  background: #eef2f7;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
}
.api-code {
  margin: 0;
  padding: 12px 14px;
  background: transparent;
  color: #0f172a;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.api-copy {
  position: absolute;
  top: 4px;
  right: 6px;
  padding: 2px 8px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
}
.api-copy:hover {
  color: #4338ca;
}
.api-codeblock-dark {
  background: #0f172a;
  border-color: #1e293b;
}
.api-codeblock-head-dark {
  background: #1e293b;
  color: #94a3b8;
}
.api-code-dark {
  color: #e2e8f0;
}
.api-copy-dark {
  color: #94a3b8;
}
.api-method {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
  margin-right: 6px;
}
.api-method-post {
  background: #16a34a;
  color: #fff;
}
.api-method-get {
  background: #2563eb;
  color: #fff;
}
.api-method-delete {
  background: #dc2626;
  color: #fff;
}
.api-path {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: #475569;
}

/* TOC */
.api-toc {
  padding: 24px 12px;
  border-left: 1px solid #e5e7eb;
  background: #fff;
}
.api-toc-title {
  padding: 4px 8px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.api-toc-item {
  display: block;
  width: 100%;
  padding: 5px 8px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 12px;
  color: #64748b;
  text-align: left;
  cursor: pointer;
}
.api-toc-item:hover {
  background: #f1f5f9;
  color: #0f172a;
}
.api-toc-item-active {
  background: #eef2ff;
  color: #4338ca;
  font-weight: 500;
}
</style>
