<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { createAgentStartClient, type AgentStartClient } from '../../client';
import { useAgentStartClient } from '../../client/vue';
import {
  mergeAgentStartHeaders,
  type AgentStartHeaders,
  useAgentStartConfig,
} from '../../config';
import { Card } from '../../ui';
import type {
  ConnectorAction,
  ConnectorConnection,
  ConnectorDefinition,
  ConnectorExecutionRecord,
  ConnectorInstallation,
} from '../types';
import ConnectorActionTestDrawer from './ConnectorActionTestDrawer.vue';
import ConnectorConnectionModal from './ConnectorConnectionModal.vue';
import ConnectorDetailDrawer from './ConnectorDetailDrawer.vue';

type HubTab = 'connectors' | 'executions';

const props = defineProps<{
    apiBase?: string;
    headers?: AgentStartHeaders;
    tenantId?: string;
    client?: AgentStartClient;
  }>();

const global = useAgentStartConfig();
const injected = useAgentStartClient();
const client =
  props.client ??
  injected ??
  createAgentStartClient({
    baseUrl: props.apiBase ?? global.apiBase ?? '/api',
    getTenant: () => props.tenantId,
    headers: () => mergeAgentStartHeaders(global.headers, props.headers),
  });

// -------- state
const tab = ref<HubTab>('connectors');
const connectors = ref<ConnectorDefinition[]>([]);
const installations = ref<ConnectorInstallation[]>([]);
const connections = ref<ConnectorConnection[]>([]);
const executions = ref<ConnectorExecutionRecord[]>([]);
const loading = ref(false);
const error = ref('');
const search = ref('');
const source = ref('全部');

const selected = ref<ConnectorDefinition>();
const action = ref<ConnectorAction>();
const testing = ref(false);
const connectionOpen = ref(false);
const detailsOpen = ref(false);
const editingConnection = ref<ConnectorConnection>();

// -------- computed
const installation = computed(() =>
  selected.value ? installationOf(selected.value) : undefined,
);
const connectorConnections = computed(() =>
  connections.value.filter((c) => c.installationId === installation.value?.id),
);
const filtered = computed(() =>
  connectors.value.filter(
    (c) =>
      (source.value === '全部' || c.key.provider === source.value) &&
      (!search.value ||
        `${c.name} ${c.description ?? ''} ${c.key.connectorId}`
          .toLowerCase()
          .includes(search.value.toLowerCase())),
  ),
);
const sources = computed(() => [
  '全部',
  ...new Set(connectors.value.map((c) => c.key.provider)),
]);

// -------- helpers
function installationOf(c: ConnectorDefinition) {
  return installations.value.find(
    (i) =>
      i.provider === c.key.provider && i.connectorId === c.key.connectorId,
  );
}
function riskClass(level?: string) {
  switch ((level ?? '').toUpperCase()) {
    case 'READ':
      return 'is-read';
    case 'DESTRUCTIVE':
      return 'is-destructive';
    default:
      return 'is-write';
  }
}
function statusClass(status?: string) {
  const s = (status ?? '').toLowerCase();
  if (s.includes('success') || s === 'ok') return 'is-success';
  if (s.includes('fail') || s.includes('error')) return 'is-danger';
  return 'is-neutral';
}

// -------- data loading
async function load() {
  loading.value = true;
  error.value = '';
  try {
    [connectors.value, installations.value, connections.value] =
      await Promise.all([
        client.connectors.list(),
        client.connectors.listInstallations(props.tenantId),
        client.connectors.listConnections(props.tenantId),
      ]);
    if (connectors.value.length && installations.value.length === 0) {
      installations.value = await client.connectors.synchronize(
        props.tenantId,
      );
    }
  } catch (e: any) {
    error.value = e?.message ?? '加载失败';
  } finally {
    loading.value = false;
  }
}
async function refresh() {
  await client.connectors.refresh();
  await client.connectors.synchronize(props.tenantId);
  await load();
}
async function toggle(c: ConnectorDefinition) {
  const i = installationOf(c);
  if (!i) {
    await client.connectors.synchronize(props.tenantId);
    return load();
  }
  await client.connectors.setInstallationEnabled(i.id, !i.enabled, props.tenantId);
  await load();
}
async function openConnection(
  c: ConnectorDefinition,
  connection?: ConnectorConnection,
) {
  selected.value = c;
  editingConnection.value = connection;
  if (!installationOf(c)) {
    installations.value = await client.connectors.synchronize(props.tenantId);
  }
  connectionOpen.value = true;
}
function test(c: ConnectorDefinition, a: ConnectorAction) {
  selected.value = c;
  action.value = a;
  testing.value = true;
}
function details(c: ConnectorDefinition) {
  selected.value = c;
  detailsOpen.value = true;
}
async function loadExecutions() {
  try {
    executions.value = await client.connectors.listExecutions({
      tenantId: props.tenantId,
      limit: 100,
    });
  } catch (e: any) {
    error.value = e?.message ?? '加载审计失败';
  }
}
async function openAudits() {
  tab.value = 'executions';
  await loadExecutions();
}

onMounted(load);
</script>

<template>
  <div class="ch-root">
    <!-- 页面头部 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <span style="font-size: 18px">🔌</span>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">Connector 中心</div>
          <div class="as-page-subtitle">
            业务连接器 · 凭据管理 · 执行记录
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <button class="ch-btn ch-btn-primary" @click="refresh">
          刷新并同步
        </button>
      </div>
    </div>

    <!-- 页签 -->
    <nav class="ch-tabs">
      <button
        class="ch-tab"
        :class="{ 'is-active': tab === 'connectors' }"
        @click="tab = 'connectors'"
      >
        业务连接器
      </button>
      <button
        class="ch-tab"
        :class="{ 'is-active': tab === 'executions' }"
        @click="openAudits"
      >
        执行审计
      </button>
    </nav>

    <div v-if="error" class="ch-alert">{{ error }}</div>

    <!-- 连接器目录 -->
    <template v-if="tab === 'connectors'">
      <div class="ch-filters">
        <input
          v-model="search"
          class="ch-input ch-search"
          placeholder="搜索连接器、描述或 ID"
        />
        <select v-model="source" class="ch-input ch-select">
          <option v-for="s in sources" :key="s">{{ s }}</option>
        </select>
        <span class="ch-count">{{ filtered.length }} 个连接器</span>
      </div>

      <div v-if="loading" class="ch-state">加载中…</div>
      <div v-else-if="filtered.length === 0" class="ch-state">
        没有匹配的连接器
      </div>
      <div v-else class="ch-grid">
        <Card variant="management"
          v-for="c in filtered"
          :key="`${c.key.provider}:${c.key.connectorId}`"
          class="ch-card"
          :interactive="false"
          :title="c.name"
          :subtitle="`${c.key.provider} · ${c.version}`"
          :description="c.description"
        >
          <template #icon>
            <span class="ch-icon">{{ c.icon || '🔌' }}</span>
          </template>

          <template #badge>
            <span
              class="ch-pill"
              :class="installationOf(c)?.enabled ? 'is-on' : 'is-off'"
            >
              {{ installationOf(c)?.enabled ? '已启用' : '已禁用' }}
            </span>
          </template>

          <template #default>
            <div class="ch-badges">
              <span v-if="c.metadata?.kind === 'PLUGIN'">{{ c.metadata.runtime === 'JAVA' ? 'Java 插件' : '独立服务插件' }}</span>
              <span>{{ c.category || '其他' }}</span>
            <span>{{ c.trustLevel || 'UNTRUSTED' }}</span>
            <span>{{ c.actions.length }} Actions</span>
          </div>

          <div class="ch-action-list">
            <button
              v-for="a in c.actions.slice(0, 4)"
              :key="a.id"
              class="ch-action"
              @click="test(c, a)"
            >
              <span>{{ a.name }}</span>
              <small :class="['ch-risk', riskClass(a.riskLevel)]">
                {{ a.riskLevel || 'WRITE' }}
              </small>
              </button>
            </div>
          </template>

          <template #actions>
            <button class="ch-btn" @click="details(c)">查看详情</button>
            <button class="ch-btn" @click="openConnection(c)">
              + 新建连接
            </button>
            <button class="ch-btn" @click="toggle(c)">
              {{ installationOf(c)?.enabled ? '禁用' : '启用' }}
            </button>
          </template>
        </Card>
      </div>
    </template>

    <!-- 执行审计 -->
    <div v-else class="ch-audit">
      <div class="ch-audit-toolbar">
        <button class="ch-btn" @click="loadExecutions">刷新执行记录</button>
      </div>
      <div v-if="executions.length === 0" class="ch-state">暂无 Connector 执行记录</div>
      <table v-else>
        <thead>
          <tr>
            <th>时间</th>
            <th>连接器 / Action</th>
            <th>来源</th>
            <th>状态</th>
            <th>耗时</th>
            <th>错误</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in executions" :key="e.id">
            <td>{{ e.createdAt || '-' }}</td>
            <td>{{ e.connectorId }} / {{ e.actionId }}</td>
            <td>{{ e.agentId ? 'Agent' : e.workflowId ? 'Workflow' : 'API' }}</td>
            <td>
              <span class="ch-pill" :class="statusClass(e.status)">
                {{ e.status }}
              </span>
            </td>
            <td>{{ e.durationMs ?? '-' }} ms</td>
            <td :class="{ 'ch-error-cell': e.errorMessage }">
              {{ e.errorMessage || '-' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ConnectorDetailDrawer
      v-model:open="detailsOpen"
      :connector="selected"
      :installation="installation"
      :connections="connectorConnections"
      @test="(a) => selected && test(selected, a)"
      @connection="(c) => selected && openConnection(selected, c)"
      @toggle="selected && toggle(selected)"
    />
    <ConnectorActionTestDrawer
      v-model:open="testing"
      :client="client"
      :connector="selected"
      :action="action"
      :tenant-id="tenantId"
    />
    <ConnectorConnectionModal
      v-model:open="connectionOpen"
      :client="client"
      :connector="selected"
      :installation="installation"
      :connection="editingConnection"
      :tenant-id="tenantId"
      @saved="load"
    />
  </div>
</template>

<style scoped>
/* -------- 页面骨架（对齐 ProviderHubShell 的全页布局语言） -------- */
.ch-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-sizing: border-box;
  padding: 20px 24px;
  color: #111827;
}
@media (max-width: 640px) {
  .ch-root {
    padding: 12px 16px;
  }
}
:global(.dark) .ch-root {
  color: #f3f4f6;
}

/* -------- 头部工具栏卡片 -------- */
.ch-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.ch-toolbar-head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.ch-toolbar-logo {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}
.ch-toolbar-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.2px;
  line-height: 1.3;
  color: #111827;
}
.ch-toolbar-subtitle {
  margin: 3px 0 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
:global(.dark) .ch-toolbar {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
:global(.dark) .ch-toolbar-logo {
  background: linear-gradient(135deg, #818cf8, #6366f1);
}
:global(.dark) .ch-toolbar-title {
  color: #f3f4f6;
}
:global(.dark) .ch-toolbar-subtitle {
  color: #9ca3af;
}

/* -------- 页签 -------- */
.ch-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid #e5e7eb;
}
.ch-tab {
  appearance: none;
  border: 0;
  background: none;
  padding: 10px 14px;
  margin-bottom: -1px;
  font-size: 14px;
  font-weight: 500;
  color: #6b7280;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s ease;
}
.ch-tab:hover {
  color: #111827;
}
.ch-tab.is-active {
  color: #4f46e5;
  border-bottom-color: #4f46e5;
}
:global(.dark) .ch-tabs {
  border-bottom-color: #2d2d2d;
}
:global(.dark) .ch-tab {
  color: #9ca3af;
}
:global(.dark) .ch-tab:hover {
  color: #f3f4f6;
}
:global(.dark) .ch-tab.is-active {
  color: #818cf8;
  border-bottom-color: #818cf8;
}

/* -------- 错误提示条 -------- */
.ch-alert {
  padding: 10px 14px;
  font-size: 13px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
}
:global(.dark) .ch-alert {
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.15);
  border-color: rgba(220, 38, 38, 0.35);
}

/* -------- 过滤栏 -------- */
.ch-filters {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.ch-input {
  height: 36px;
  padding: 0 12px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.ch-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}
.ch-search {
  width: 280px;
  max-width: 100%;
}
.ch-search::placeholder {
  color: #9ca3af;
}
.ch-select {
  cursor: pointer;
}
.ch-count {
  margin-left: auto;
  font-size: 13px;
  color: #6b7280;
}
:global(.dark) .ch-input {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .ch-count {
  color: #9ca3af;
}

/* -------- 加载中 / 空状态 -------- */
.ch-state {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

/* -------- 连接器卡片网格 -------- */
.ch-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}
.ch-card {
  gap: 12px;
  min-height: 0;
}
.ch-icon {
  font-size: 22px;
}

.ch-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.ch-badges span {
  padding: 2px 8px;
  font-size: 11px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #374151;
}
:global(.dark) .ch-badges span {
  background: #2d2d2d;
  color: #d1d5db;
}

/* -------- Action 快捷测试列表 -------- */
.ch-action-list {
  display: grid;
  gap: 6px;
}
.ch-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  font-size: 13px;
  text-align: left;
  color: #111827;
  background: #fafafa;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}
.ch-action:hover {
  border-color: #a5b4fc;
  background: #eef2ff;
}
:global(.dark) .ch-action {
  background: #191919;
  border-color: #2d2d2d;
  color: #f3f4f6;
}
:global(.dark) .ch-action:hover {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.12);
}
.ch-risk {
  font-size: 11px;
  font-weight: 600;
}
.ch-risk.is-read {
  color: #059669;
}
.ch-risk.is-write {
  color: #b45309;
}
.ch-risk.is-destructive {
  color: #dc2626;
}
:global(.dark) .ch-risk.is-read {
  color: #34d399;
}
:global(.dark) .ch-risk.is-write {
  color: #fbbf24;
}
:global(.dark) .ch-risk.is-destructive {
  color: #f87171;
}

.ch-card:deep(.as-management-card__content) {
  display: grid;
  gap: 12px;
}
.ch-card:deep(.as-management-card__actions) {
  width: 100%;
  justify-content: flex-end;
}

/* -------- 按钮 -------- */
.ch-btn {
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.ch-btn:hover {
  background: #f3f4f6;
}
.ch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ch-btn-primary {
  color: #fff;
  background: #6366f1;
  border-color: #6366f1;
}
.ch-btn-primary:hover {
  background: #4f46e5;
}
:global(.dark) .ch-btn {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .ch-btn:hover {
  background: #3d3d3d;
}
:global(.dark) .ch-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
}
:global(.dark) .ch-btn-primary:hover {
  background: #818cf8;
}

/* -------- 状态徽章 -------- */
.ch-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 10px;
  white-space: nowrap;
}
.ch-pill.is-on,
.ch-pill.is-success {
  color: #059669;
  background: #ecfdf5;
}
.ch-pill.is-off,
.ch-pill.is-neutral {
  color: #6b7280;
  background: #f3f4f6;
}
.ch-pill.is-danger {
  color: #dc2626;
  background: #fef2f2;
}
:global(.dark) .ch-pill.is-on,
:global(.dark) .ch-pill.is-success {
  color: #34d399;
  background: rgba(16, 185, 129, 0.15);
}
:global(.dark) .ch-pill.is-off,
:global(.dark) .ch-pill.is-neutral {
  color: #9ca3af;
  background: #2d2d2d;
}
:global(.dark) .ch-pill.is-danger {
  color: #f87171;
  background: rgba(220, 38, 38, 0.15);
}

/* -------- 执行审计表格 -------- */
.ch-audit {
  overflow: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.ch-audit-toolbar { padding: 12px; border-bottom: 1px solid #e5e7eb; }
.ch-audit-switch, .ch-audit-filters { display: flex; gap: 8px; flex-wrap: wrap; }
.ch-audit-filters { margin-top: 10px; }
.ch-audit-filters .ch-input { min-width: 120px; flex: 1; }
.ch-audit-details { min-width: 220px; max-width: 420px; overflow-wrap: anywhere; }
.ch-muted { margin-top: 3px; color: #9ca3af; font-size: 11px; }
.ch-audit table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.ch-audit th {
  text-align: left;
  padding: 10px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  white-space: nowrap;
}
.ch-audit td {
  padding: 10px 14px;
  border-bottom: 1px solid #f3f4f6;
  color: #374151;
  vertical-align: top;
}
.ch-audit tbody tr:last-child td {
  border-bottom: 0;
}
.ch-audit tbody tr:hover td {
  background: #f9fafb;
}
.ch-error-cell {
  color: #dc2626;
  max-width: 260px;
}
:global(.dark) .ch-audit {
  background: #1f1f1f;
  border-color: #2d2d2d;
}
:global(.dark) .ch-audit th {
  background: #191919;
  border-bottom-color: #2d2d2d;
  color: #9ca3af;
}
:global(.dark) .ch-audit td {
  border-bottom-color: #2d2d2d;
  color: #d1d5db;
}
:global(.dark) .ch-audit tbody tr:hover td {
  background: #191919;
}
:global(.dark) .ch-error-cell {
  color: #f87171;
}
</style>
