<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { SearchOutlined, ToolOutlined } from '@ant-design/icons-vue';
import { createAgentStartClient, type ToolCatalogItem } from '../../../client';
import { useAgentStartClient } from '../../../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../../../config';
import { Modal } from '../../../ui';

const emit = defineEmits<{ (e: 'formSubmit', value: ToolCatalogItem[]): void }>();
const config = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({
  baseUrl: config.apiBase ?? '/api',
  headers: () => mergeAgentStartHeaders(config.headers),
});
const open = ref(false);
const loading = ref(false);
const error = ref('');
const query = ref('');
const category = ref('全部');
const tools = ref<ToolCatalogItem[]>([]);
const selectedNames = ref<string[]>([]);

const categories = computed(() => ['全部', ...new Set(tools.value.map(tool => tool.category || tool.source || '内置'))]);
const filteredTools = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  return tools.value.filter((tool) => {
    const group = tool.category || tool.source || '内置';
    return (category.value === '全部' || category.value === group) && (!keyword ||
      [tool.name, tool.label, tool.description, tool.provider].some(value => value?.toLowerCase().includes(keyword)));
  });
});

async function load() {
  loading.value = true;
  error.value = '';
  try { tools.value = await client.tools.list(); }
  catch (reason: any) { error.value = reason?.message || '工具目录加载失败'; }
  finally { loading.value = false; }
}
function showModal(selected: Array<string | { name?: string }> = []) {
  selectedNames.value = selected.map(item => typeof item === 'string' ? item : item.name)
    .filter((name): name is string => Boolean(name));
  query.value = '';
  category.value = '全部';
  open.value = true;
  void load();
}
function hideModal() { open.value = false; }
function toggle(name: string) {
  selectedNames.value = selectedNames.value.includes(name)
    ? selectedNames.value.filter(item => item !== name)
    : [...selectedNames.value, name];
}
function submit() {
  const byName = new Map(tools.value.map(tool => [tool.name, tool]));
  emit('formSubmit', selectedNames.value.map(name => byName.get(name) ?? { name }));
  open.value = false;
}

onMounted(load);
defineExpose({ showModal, hideModal });
</script>

<template>
  <Modal v-model:open="open" title="选择工具" width="720px" ok-text="添加所选工具" cancel-text="取消" @ok="submit">
    <div class="wf-tool-chooser">
      <a-input v-model:value="query" allow-clear placeholder="搜索工具名称、描述或提供方">
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <div class="wf-tool-tabs">
        <button v-for="item in categories" :key="item" type="button" :class="{ active: category === item }" @click="category = item">{{ item }}</button>
      </div>
      <div v-if="loading" class="wf-tool-state"><a-spin /> 正在加载工具目录…</div>
      <a-alert v-else-if="error" type="error" :message="error" show-icon />
      <div v-else-if="!filteredTools.length" class="wf-tool-state">没有匹配的工具</div>
      <div v-else class="wf-tool-list">
        <button v-for="tool in filteredTools" :key="tool.name" type="button" class="wf-tool-row" :class="{ selected: selectedNames.includes(tool.name) }" @click="toggle(tool.name)">
          <a-checkbox :checked="selectedNames.includes(tool.name)" @click.stop="toggle(tool.name)" />
          <span class="wf-tool-icon"><ToolOutlined /></span>
          <span class="wf-tool-copy"><b>{{ tool.label || tool.name }}</b><small>{{ tool.description || '暂无描述' }}</small></span>
          <span class="wf-tool-meta"><a-tag>{{ tool.category || tool.source || '内置' }}</a-tag><small v-if="tool.provider">{{ tool.provider }}</small></span>
        </button>
      </div>
      <div class="wf-tool-summary">已选择 {{ selectedNames.length }} 个工具</div>
    </div>
  </Modal>
</template>

<style scoped>
.wf-tool-chooser{display:flex;flex-direction:column;gap:12px;padding-top:6px}.wf-tool-tabs{display:flex;gap:6px;overflow:auto}.wf-tool-tabs button{padding:5px 10px;border:1px solid #e5e7eb;border-radius:16px;background:#fff;color:#6b7280;cursor:pointer;white-space:nowrap}.wf-tool-tabs button.active{border-color:#6366f1;background:#eef2ff;color:#4338ca}.wf-tool-list{max-height:420px;overflow:auto;border:1px solid #e5e7eb;border-radius:8px}.wf-tool-row{display:flex;width:100%;align-items:center;gap:10px;padding:11px 12px;border:0;border-bottom:1px solid #f3f4f6;background:#fff;text-align:left;cursor:pointer}.wf-tool-row:last-child{border-bottom:0}.wf-tool-row:hover,.wf-tool-row.selected{background:#f5f3ff}.wf-tool-icon{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;background:#ede9fe;color:#6d28d9}.wf-tool-copy{display:flex;min-width:0;flex:1;flex-direction:column}.wf-tool-copy small{overflow:hidden;color:#6b7280;text-overflow:ellipsis;white-space:nowrap}.wf-tool-meta{display:flex;align-items:flex-end;flex-direction:column}.wf-tool-meta small{color:#9ca3af}.wf-tool-state{display:flex;min-height:180px;align-items:center;justify-content:center;gap:8px;color:#9ca3af}.wf-tool-summary{text-align:right;font-size:12px;color:#6b7280}
</style>
