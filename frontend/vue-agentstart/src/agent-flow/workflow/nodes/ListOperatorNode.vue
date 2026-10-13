<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { computed, ref } from 'vue';

import workflow_utils from '@/workflow/utils/workflow_utils';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const OP_LABELS: Record<string, string> = {
  filter: '过滤',
  map: '映射',
  reduce: '归约',
  slice: '截取',
  sort: '排序',
  reverse: '反转',
  count: '计数',
};

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: '列表操作',
      description: '对列表变量做过滤/映射/排序等操作',
      inputVariableSelector: [],
      operation: 'filter',
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

const inputLabel = computed(() =>
  workflow_utils.getVariableLabel(props.data.inputVariableSelector || []),
);
const opLabel = computed(
  () => OP_LABELS[props.data.operation] || props.data.operation || '未选择',
);
</script>

<template>
  <div
    class="wf-node list-operator-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="list_operator"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="variable" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">{{ opLabel }}</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="inputLabel" class="wf-node-preview">
        <div class="wf-node-preview-label">输入列表</div>
        <div class="wf-node-preview-value">{{ inputLabel }}</div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">选择输入列表变量</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#22d3ee', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#22d3ee', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.list-operator-node {
  --wf-accent: #22d3ee;
  --wf-accent-hover: #0891b2;
}
</style>
