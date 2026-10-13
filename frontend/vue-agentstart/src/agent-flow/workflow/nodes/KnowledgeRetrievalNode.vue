<script setup>
import { computed, ref } from 'vue';

import workflow_utils from '@/workflow/utils/workflow_utils';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: '知识检索',
      description: '从知识库中检索相关文档',
      queryVariableSelector: [],
      dataset: { datasets: [] },
      config: { top_k: 3 },
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);

const isHovered = ref(false);

const onMouseEnter = () => (isHovered.value = true);
const onMouseLeave = () => (isHovered.value = false);
const duplicateNode = () => emit('duplicate', props.id);
const deleteNode = () => emit('delete', props.id);
const onConnectionPlusClick = (event) => {
  emit('connection-plus-click', event, props.id);
};

const truncateText = (text, maxLength) => {
  if (!text) return '';
  return text.length > maxLength
    ? `${text.slice(0, Math.max(0, maxLength))}...`
    : text;
};

const getQueryVarLabel = (variableSelector) =>
  workflow_utils.getVariableLabel(variableSelector);

const datasets = computed(() => props.data?.dataset?.datasets ?? []);
const queryVar = computed(() => props.data?.queryVariableSelector ?? []);
const topK = computed(() => props.data?.config?.top_k ?? 3);
const isDynamic = computed(() => props.data?.dataset?.dynamic === true);
</script>

<template>
  <div
    class="wf-node knowledge-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="knowledge_retrieval"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="knowledge" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">
          <span>{{ isDynamic ? '动态数据集' : `已选 ${datasets.length} 个` }}</span>
          <span class="knowledge-topk-chip">top {{ topK }}</span>
        </div>
      </div>
    </div>

    <div class="wf-node-content">
      <div v-if="queryVar.length > 0" class="knowledge-query">
        <span class="knowledge-query-label">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="12" height="12">
            <path fill="currentColor" d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 0 0 1.48-5.34c-.47-2.78-2.79-5-5.59-5.34a6.505 6.505 0 0 0-7.27 7.27c.34 2.8 2.56 5.12 5.34 5.59a6.5 6.5 0 0 0 5.34-1.48l.27.28v.79l4.25 4.25c.41.41 1.08.41 1.49 0c.41-.41.41-1.08 0-1.49L15.5 14zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5S14 7.01 14 9.5S11.99 14 9.5 14z"/>
          </svg>
          查询
        </span>
        <span class="knowledge-query-value">
          {{ getQueryVarLabel(queryVar) }}
        </span>
      </div>

      <div v-if="datasets.length > 0" class="knowledge-dataset-list">
        <div
          v-for="item in datasets"
          :key="item.id"
          class="knowledge-dataset-row"
        >
          <span class="knowledge-dataset-icon">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14">
              <path fill="currentColor" d="M12 3C7.58 3 4 4.79 4 7v10c0 2.21 3.59 4 8 4s8-1.79 8-4V7c0-2.21-3.58-4-8-4m6 14c0 .5-2.13 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V17m0-5c0 .5-2.13 2-6 2s-6-1.5-6-2V9.77c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V12m-6-3c-3.87 0-6-1.5-6-2s2.13-2 6-2s6 1.5 6 2s-2.13 2-6 2Z"/>
            </svg>
          </span>
          <span class="knowledge-dataset-name" :title="item.name">
            {{ truncateText(item.name, 20) }}
          </span>
          <span v-if="item.tags && item.tags.length" class="knowledge-dataset-tag">
            {{ item.tags[0] }}
          </span>
        </div>
      </div>
      <div v-else-if="isDynamic" class="knowledge-dynamic-hint">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="12" height="12">
          <path fill="currentColor" d="M13 3a9 9 0 0 0-9 9H1l3.89 3.89l.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7s-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18Zm-1 5v5l4.28 2.54l.72-1.21l-3.5-2.08V8H12Z"/>
        </svg>
        <span>数据集由运行时变量决定</span>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">尚未选择知识库</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      :is-hovered="isHovered"
      class-name="flow-node-handle-left"
      :style="{ background: '#14b8a6', border: '2px solid #ffffff' }"
    />

    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#14b8a6', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.knowledge-node {
  --wf-accent: #14b8a6;
  --wf-accent-hover: #0d9488;
}

.knowledge-topk-chip {
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  color: color-mix(in srgb, var(--wf-accent) 90%, #000);
  background: color-mix(in srgb, var(--wf-accent) 15%, transparent);
  border-radius: 999px;
}

.knowledge-query {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 6px 8px;
  background: color-mix(in srgb, var(--wf-accent) 7%, transparent);
  border: 1px solid color-mix(in srgb, var(--wf-accent) 25%, transparent);
  border-radius: 6px;
  font-size: 11px;
}

.knowledge-query-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  color: color-mix(in srgb, var(--wf-accent) 85%, #000);
  font-weight: 500;
}

.knowledge-query-value {
  flex: 1;
  min-width: 0;
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-dataset-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.knowledge-dataset-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  font-size: 11px;
  background: var(--wf-node-surface);
  border: 1px solid var(--wf-node-border);
  border-radius: 6px;
  transition: border-color 0.15s, background 0.15s;
}

.knowledge-dataset-row:hover {
  border-color: var(--wf-accent);
  background: color-mix(in srgb, var(--wf-accent) 5%, #ffffff);
}

.knowledge-dataset-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 20px;
  height: 20px;
  color: var(--wf-accent);
  background: color-mix(in srgb, var(--wf-accent) 12%, transparent);
  border-radius: 4px;
}

.knowledge-dataset-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #1f2937;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.knowledge-dataset-tag {
  flex: none;
  padding: 1px 6px;
  font-size: 10px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 3px;
}

.knowledge-dynamic-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  font-size: 11px;
  color: color-mix(in srgb, var(--wf-accent) 85%, #000);
  background: color-mix(in srgb, var(--wf-accent) 6%, transparent);
  border: 1px dashed color-mix(in srgb, var(--wf-accent) 40%, transparent);
  border-radius: 6px;
}
</style>
