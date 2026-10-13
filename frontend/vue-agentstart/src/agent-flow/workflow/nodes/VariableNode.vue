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
      label: '变量聚合',
      description: '变量聚合节点',
      output: [{ children: [] }],
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);

const isHovered = ref(false);

const onMouseEnter = () => (isHovered.value = true);
const onMouseLeave = () => (isHovered.value = false);
const duplicateNode = () => emit('duplicate', props.id);
const deleteNode = () => emit('delete', props.id);
const onConnectionPlusClick = (event, nodeId, handleId) => {
  emit('connection-plus-click', event, nodeId, handleId);
};

const getAnswerTextLabel = (variableSelector) =>
  workflow_utils.getVariableLabel(variableSelector);

const aggregated = computed(() => props.data?.output?.[0]?.children ?? []);
</script>

<template>
  <div
    class="wf-node variable-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="variable"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="variable" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">变量聚合</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="aggregated.length > 0" class="wf-node-preview">
        <div class="wf-node-preview-label">聚合变量</div>
        <div class="wf-node-preview-list">
          <div v-for="it in aggregated" :key="it.name" class="variable-row">
            <div class="variable-name">{{ it.name }}</div>
            <div class="variable-source">
              {{ getAnswerTextLabel(it.variableSelector) }}
            </div>
          </div>
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">配置变量聚合</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#0ea5e9', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#0ea5e9', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.variable-node {
  --wf-accent: #0ea5e9;
  --wf-accent-hover: #0284c7;
}

.variable-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.variable-name {
  flex: none;
  padding: 1px 6px;
  color: #ffffff;
  background: var(--wf-accent);
  border-radius: 4px;
}

.variable-source {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #4b5563;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
