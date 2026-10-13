<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {createAgentStartClient} from '../client';
import {useAgentStartClient} from '../client/vue';
import {mergeAgentStartHeaders, useAgentStartConfig} from '../config';
import ConnectorActionTestDrawer from '../connector-hub/components/ConnectorActionTestDrawer.vue';
import ConnectorConnectionModal from '../connector-hub/components/ConnectorConnectionModal.vue';
import type {
  ConnectorAction,
  ConnectorConnection,
  ConnectorDefinition,
  ConnectorInstallation
} from '../connector-hub/types';
import { Button, Card } from '../ui';

const props = withDefaults(defineProps<{ tenantId?: string; title?: string }>(), {title: '插件管理'});
const global = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: global.apiBase ?? '/api',
  headers: () => mergeAgentStartHeaders(global.headers)
});
const plugins = ref<ConnectorDefinition[]>([]);
const installations = ref<ConnectorInstallation[]>([]);
const connections = ref<ConnectorConnection[]>([]);
const loading = ref(false);
const syncing = ref(false);
const error = ref('');
const query = ref('');
const category = ref('ALL');
const selected = ref<ConnectorDefinition>();
const selectedAction = ref<ConnectorAction>();
const configOpen = ref(false);
const testOpen = ref(false);
const categories = computed(() => ['ALL', ...new Set(plugins.value.map(p => p.category || 'other'))]);
const rows = computed(() => plugins.value.filter(p => (category.value === 'ALL' || (p.category || 'other') === category.value) && (!query.value || `${p.name} ${p.description} ${p.key.connectorId}`.toLowerCase().includes(query.value.toLowerCase()))));
const enabledCount = computed(() => installations.value.filter(i => i.enabled && plugins.value.some(p => p.key.provider === i.provider && p.key.connectorId === i.connectorId)).length);
const actionCount = computed(() => plugins.value.reduce((sum, p) => sum + p.actions.length, 0));

function installationOf(p?: ConnectorDefinition) {
  return p && installations.value.find(i => i.provider === p.key.provider && i.connectorId === p.key.connectorId);
}

function connectionOf(p?: ConnectorDefinition) {
  const i = installationOf(p);
  return i && connections.value.find(c => c.installationId === i.id);
}

async function load(sync = false) {
  loading.value = !sync;
  syncing.value = sync;
  error.value = '';
  try {
    const catalog = await (sync ? client.connectors.refresh() : client.connectors.list());
    plugins.value = catalog.filter(item => item.key.provider === 'plugin' || item.metadata?.kind === 'PLUGIN');
    installations.value = sync ? await client.connectors.synchronize(props.tenantId) : await client.connectors.listInstallations(props.tenantId);
    connections.value = await client.connectors.listConnections(props.tenantId);
  } catch (e: any) {
    error.value = e?.message ?? '插件加载失败';
  } finally {
    loading.value = false;
    syncing.value = false;
  }
}

async function toggle(p: ConnectorDefinition) {
  let i = installationOf(p);
  try {
    if (!i) {
      installations.value = await client.connectors.synchronize(props.tenantId);
      i = installationOf(p);
    }
    if (!i) throw new Error('插件同步失败');
    await client.connectors.setInstallationEnabled(i.id, !i.enabled, props.tenantId);
    await load();
  } catch (e: any) {
    error.value = e?.message ?? '更新失败';
  }
}

function configure(p: ConnectorDefinition) {
  selected.value = p;
  configOpen.value = true;
}

function testAction(p: ConnectorDefinition, a: ConnectorAction) {
  selected.value = p;
  selectedAction.value = a;
  testOpen.value = true;
}

onMounted(() => load());
</script>

<template>
  <div class="ph as-management">
    <!-- 页面头部 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <span style="font-size: 18px">🧩</span>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">插件管理</div>
          <div class="as-page-subtitle">
            Java 插件 · 远程插件 · 动态加载
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <div class="ph-search as-management-search"><span>⌕</span><input v-model="query"
                                                                         placeholder="搜索插件名称、描述或插件 ID"></div>
        <Button type="primary" :loading="syncing" @click="load(true)">↻ 同步插件目录</Button>
      </div>
    </div>

    <section class="ph-hero as-management-toolbar">
      <div><p>统一管理动态加载的 Java 与远程插件，配置连接后即可用于 Agent 和工作流。</p></div>
    </section>
    <section class="ph-stats">
      <div><b>{{ plugins.length }}</b><span>已发现插件</span></div>
      <div><b>{{ enabledCount }}</b><span>已启用</span></div>
      <div><b>{{ connections.length }}</b><span>连接配置</span></div>
      <div><b>{{ actionCount }}</b><span>可用 Action</span></div>
    </section>
    <section class="ph-toolbar as-management-toolbar">
      <div class="ph-categories as-management-segments">
        <button v-for="item in categories" :key="item" :class="{active:category===item}" @click="category=item">
          {{ item === 'ALL' ? '全部' : item }}
        </button>
      </div>
    </section>
    <div v-if="error" class="ph-alert"><b>加载失败</b><span>{{ error }}</span>
      <button @click="load()">重试</button>
    </div>
    <div v-if="loading" class="ph-loading"><span></span>
      <p>正在读取插件目录…</p></div>
    <div v-else-if="!rows.length" class="ph-empty">
      <div>🧩</div>
      <h3>没有找到插件</h3>
      <p>请确认插件 JAR 已加入后端运行时，或调整当前筛选条件。</p></div>
    <section v-else class="ph-grid as-management-grid">
      <Card variant="management"
        v-for="p in rows"
        :key="p.key.connectorId"
        class="ph-card"
        :title="p.name"
        :subtitle="p.key.connectorId"
        :description="p.description || '此插件暂未提供说明。'"
      >
        <template #icon>
          <div class="ph-icon">{{ p.icon || '🧩' }}</div>
        </template>
        <template #badge>
          <span class="ph-status" :class="{online:installationOf(p)?.enabled}">
            <i></i>{{ installationOf(p)?.enabled ? '已启用' : '未启用' }}
          </span>
        </template>
        <template #default>
          <div class="ph-badges">
            <span>{{ p.metadata?.runtime === 'JAVA' ? 'Java Plugin' : 'Remote HTTP' }}</span>
            <span>v{{ p.version || '-' }}</span>
            <span>{{ p.category || 'other' }}</span>
          </div>
          <div class="ph-actions-list">
            <div v-for="a in p.actions" :key="a.id">
              <span><b>{{ a.name }}</b><small>{{ a.description || a.id }}</small></span>
              <em :class="String(a.riskLevel).toLowerCase()">{{ a.riskLevel || 'READ' }}</em>
              <button :disabled="!installationOf(p)?.enabled" @click="testAction(p,a)">试运行</button>
            </div>
          </div>
        </template>
        <template #meta>
          <div class="ph-connection">
            <span :class="{ready:connectionOf(p)}">{{ connectionOf(p) ? '✓' : '!' }}</span>
            <div>
              <b>{{ connectionOf(p)?.name || '尚未配置连接' }}</b>
              <small>{{ connectionOf(p) ? `${connectionOf(p)?.status} · 凭证${connectionOf(p)?.credentialsConfigured ? '已配置' : '未配置'}` : '配置 API Key、账号或插件参数' }}</small>
            </div>
          </div>
        </template>
        <template #actions>
          <div class="ph-card-buttons">
            <button class="ghost" @click="toggle(p)">{{ installationOf(p)?.enabled ? '停用' : '启用' }}</button>
            <button class="primary" @click="configure(p)">{{ connectionOf(p) ? '管理连接' : '立即配置' }}</button>
          </div>
        </template>
      </Card>
    </section>
    <ConnectorConnectionModal v-model:open="configOpen" :client="client" :connector="selected"
                              :installation="installationOf(selected)" :connection="connectionOf(selected)"
                              :tenant-id="tenantId" @saved="load()"/>
    <ConnectorActionTestDrawer v-model:open="testOpen" :client="client" :connector="selected" :action="selectedAction"
                               :tenant-id="tenantId"/>
  </div>
</template>

<style scoped>
.ph {
  --panel: #fff;
  --soft: #f8fafc;
  --line: #e5e7eb;
  --text: #172033;
  --muted: #667085;
  --primary: #635bff;
  padding: 24px;
  color: var(--text)
}

:global(.dark) .ph {
  --panel: #1f1f1f;
  --soft: #262626;
  --line: #353535;
  --text: #f5f5f5;
  --muted: #a3a3a3
}

.ph-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  border: 1px solid #ddd9ff;
  border-radius: 16px;
  background: linear-gradient(120deg, #f7f5ff 0%, #fff 58%, #eff8ff 100%)
}

:global(.dark) .ph-hero {
  border-color: #39345f;
  background: linear-gradient(120deg, #25213b, #1f1f1f 58%, #172b36)
}

.ph-kicker {
  color: var(--primary);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: .14em
}

.ph h2 {
  margin: 5px 0 6px;
  font-size: 25px
}

.ph-hero p {
  max-width: 760px;
  margin: 0;
  color: var(--muted);
  line-height: 1.7
}

.ph button {
  font: inherit;
  cursor: pointer
}

.ph-primary, .ph-card .primary {
  border: 0;
  color: #fff;
  background: var(--primary);
  box-shadow: 0 5px 14px #635bff35
}

.ph-primary {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 10px 16px;
  border-radius: 9px;
  font-weight: 650
}

.ph-stats {
  display: grid;
  grid-template-columns:repeat(4, 1fr);
  margin: 18px 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel)
}

.ph-stats div {
  display: grid;
  gap: 3px;
  padding: 16px 20px;
  border-right: 1px solid var(--line)
}

.ph-stats div:last-child {
  border: 0
}

.ph-stats b {
  font-size: 21px
}

.ph-stats span {
  color: var(--muted);
  font-size: 12px
}

.ph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 22px 0 16px
}

.ph-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(420px, 100%);
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--panel)
}

.ph-search input {
  width: 100%;
  padding: 9px 0;
  border: 0;
  outline: 0;
  color: var(--text);
  background: transparent
}

.ph-categories {
  display: flex;
  gap: 5px;
  padding: 4px;
  border-radius: 9px;
  background: var(--soft)
}

.ph-categories button {
  padding: 6px 12px;
  border: 0;
  border-radius: 6px;
  color: var(--muted);
  background: transparent;
  font-size: 12px
}

.ph-categories button.active {
  color: var(--text);
  background: var(--panel);
  box-shadow: 0 1px 4px #0001
}

.ph-grid {
  display: grid;
  grid-template-columns:repeat(auto-fill, minmax(430px, 1fr));
  gap: 16px
}

.ph-card {
  min-height: 355px;
  padding: 20px;
}

.ph-card header {
  display: flex;
  align-items: center;
  gap: 12px
}

.ph-icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 11px;
  background: linear-gradient(135deg, #ede9fe, #dbeafe);
  font-size: 23px
}

.ph-title {
  min-width: 0;
  flex: 1
}

.ph-title h3 {
  margin: 0 0 3px;
  font-size: 16px
}

.ph-title code {
  color: var(--muted);
  font-size: 11px
}

.ph-status {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--muted);
  background: var(--soft);
  font-size: 11px
}

.ph-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #98a2b3
}

.ph-status.online {
  color: #027a48;
  background: #ecfdf3
}

.ph-status.online i {
  background: #12b76a
}

.ph-desc {
  min-height: 44px;
  margin: 15px 0 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65
}

.ph-badges {
  display: flex;
  gap: 6px
}

.ph-badges span {
  padding: 3px 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--muted);
  font-size: 10px
}

.ph-actions-list {
  display: grid;
  gap: 7px;
  margin: 16px 0
}

.ph-actions-list > div {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 8px;
  background: var(--soft)
}

.ph-actions-list span {
  display: grid;
  min-width: 0;
  flex: 1
}

.ph-actions-list b {
  font-size: 12px
}

.ph-actions-list small {
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap
}

.ph-actions-list em {
  padding: 2px 5px;
  border-radius: 4px;
  color: #175cd3;
  background: #eff8ff;
  font-size: 9px;
  font-style: normal
}

.ph-actions-list em.write {
  color: #b54708;
  background: #fffaeb
}

.ph-actions-list em.destructive {
  color: #b42318;
  background: #fef3f2
}

.ph-actions-list button {
  padding: 4px 8px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--text);
  background: var(--panel);
  font-size: 10px
}

.ph-actions-list button:disabled {
  opacity: .45;
  cursor: not-allowed
}

.ph-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid var(--line)
}

.ph-connection {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0
}

.ph-connection > span {
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
  color: #b54708;
  background: #fffaeb
}

.ph-connection > span.ready {
  color: #027a48;
  background: #ecfdf3
}

.ph-connection div {
  display: grid;
  min-width: 0
}

.ph-connection b {
  font-size: 11px
}

.ph-connection small {
  overflow: hidden;
  max-width: 220px;
  color: var(--muted);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap
}

.ph-card-buttons {
  display: flex;
  gap: 6px
}

.ph-card-buttons button {
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 11px
}

.ph-card .ghost {
  border: 1px solid var(--line);
  color: var(--text);
  background: var(--panel)
}

.ph-alert, .ph-empty, .ph-loading {
  padding: 35px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  text-align: center;
  background: var(--soft)
}

.ph-alert {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  color: #b42318;
  text-align: left
}

.ph-alert span {
  flex: 1
}

.ph-alert button {
  border: 0;
  color: #b42318;
  background: transparent
}

.ph-empty div {
  font-size: 34px
}

.ph-empty h3 {
  margin: 9px 0 4px
}

.ph-empty p, .ph-loading p {
  margin: 0;
  color: var(--muted)
}

.ph-loading span {
  display: inline-block;
  width: 22px;
  height: 22px;
  border: 2px solid #ddd9ff;
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin .8s linear infinite
}

@keyframes spin {
  to {
    transform: rotate(360deg)
  }
}

@media (max-width: 800px) {
  .ph {
    padding: 14px
  }

  .ph-hero {
    align-items: flex-start;
    flex-direction: column
  }

  .ph-stats {
    grid-template-columns:repeat(2, 1fr)
  }

  .ph-stats div:nth-child(2) {
    border-right: 0
  }

  .ph-toolbar {
    align-items: stretch;
    flex-direction: column
  }

  .ph-grid {
    grid-template-columns:1fr
  }

  .ph-card footer {
    align-items: flex-start;
    flex-direction: column
  }
}

.ph.as-management {
  --panel: var(--as-mgmt-panel);
  --soft: var(--as-mgmt-soft);
  --line: var(--as-mgmt-line);
  --text: var(--as-mgmt-text);
  --muted: var(--as-mgmt-muted);
  --primary: var(--as-primary);
  padding: 20px
}

.ph.as-management .ph-hero {
  align-items: center;
  margin-bottom: 16px;
  padding: 0;
  border: 0;
  background: transparent
}

.ph.as-management .ph-hero p {
  margin: 0;
  font-size: 12px
}

.ph.as-management .ph-stats {
  margin: 0 0 16px
}

.ph.as-management .ph-grid {
  grid-template-columns:repeat(auto-fill, minmax(380px, 1fr));
  gap: 12px
}

.ph.as-management .ph-card {
  border-radius: 12px
}

@media (max-width: 760px) {
  .ph.as-management {
    padding: 14px
  }

  .ph.as-management .ph-grid {
    grid-template-columns:1fr
  }
}
</style>
