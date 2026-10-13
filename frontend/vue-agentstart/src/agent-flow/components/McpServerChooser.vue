<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ApiOutlined } from '@ant-design/icons-vue';
import { createAgentStartClient, type McpServerCatalogItem } from '../../client';
import { useAgentStartClient } from '../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../config';
import { Modal } from '../../ui';

const emit = defineEmits<{ (e: 'submit', value: McpServerCatalogItem[]): void }>();
const config = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({ baseUrl: config.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(config.headers) });
const open = ref(false);
const loading = ref(false);
const error = ref('');
const servers = ref<McpServerCatalogItem[]>([]);
const selectedIds = ref<string[]>([]);

async function load() {
  loading.value = true; error.value = '';
  try { servers.value = await client.tools.listMcpServers(); }
  catch (reason: any) { error.value = reason?.message || 'MCP 服务加载失败'; }
  finally { loading.value = false; }
}
function showModal(selected: string[] = []) { selectedIds.value = [...selected]; open.value = true; void load(); }
function toggle(id: string) { selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(item => item !== id) : [...selectedIds.value, id]; }
function submit() { emit('submit', selectedIds.value.map(id => servers.value.find(server => server.id === id)).filter(Boolean) as McpServerCatalogItem[]); open.value = false; }
onMounted(load);
defineExpose({ showModal });
</script>

<template>
  <Modal v-model:open="open" title="选择 MCP 服务" width="640px" ok-text="添加所选服务" cancel-text="取消" @ok="submit">
    <div v-if="loading" class="mcp-state"><a-spin /> 正在加载 MCP 服务…</div>
    <a-alert v-else-if="error" type="error" :message="error" show-icon />
    <div v-else-if="!servers.length" class="mcp-state">尚未配置 MCP 服务，请先前往 MCP 配置页添加。</div>
    <div v-else class="mcp-list">
      <button v-for="server in servers" :key="server.id" type="button" :disabled="!server.enabled" :class="{ selected: selectedIds.includes(server.id) }" @click="toggle(server.id)">
        <a-checkbox :checked="selectedIds.includes(server.id)" :disabled="!server.enabled" @click.stop="toggle(server.id)" />
        <span class="mcp-icon"><ApiOutlined /></span>
        <span class="mcp-copy"><b>{{ server.name }}</b><small>{{ server.transport }} · {{ server.toolCount ?? '待发现' }} 个工具</small></span>
        <a-tag :color="server.enabled ? 'green' : 'default'">{{ server.enabled ? (server.status || '已启用') : '已停用' }}</a-tag>
      </button>
    </div>
  </Modal>
</template>

<style scoped>
.mcp-list{display:flex;max-height:420px;flex-direction:column;overflow:auto;border:1px solid #e5e7eb;border-radius:8px}.mcp-list button{display:flex;align-items:center;gap:10px;padding:12px;border:0;border-bottom:1px solid #f3f4f6;background:#fff;text-align:left;cursor:pointer}.mcp-list button:last-child{border-bottom:0}.mcp-list button.selected{background:#eef2ff}.mcp-list button:disabled{cursor:not-allowed;opacity:.55}.mcp-icon{display:grid;place-items:center;width:34px;height:34px;border-radius:8px;background:#e0f2fe;color:#0369a1}.mcp-copy{display:flex;flex:1;flex-direction:column}.mcp-copy small{color:#6b7280}.mcp-state{display:flex;min-height:180px;align-items:center;justify-content:center;gap:8px;color:#9ca3af}
</style>
