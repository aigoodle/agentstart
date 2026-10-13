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
      label: '迭代',
      description: '对列表变量的每一项执行子图',
      iteratorVariableSelector: [],
      outputVariableSelector: [],
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

const iterVarLabel = computed(() =>
  workflow_utils.getVariableLabel(props.data.iteratorVariableSelector || []),
);
</script>

<template>
  <div
    class="wf-node iteration-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="iteration"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="loop" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">迭代子图</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="iterVarLabel" class="wf-node-preview">
        <div class="wf-node-preview-label">迭代变量</div>
        <div class="wf-node-preview-value">{{ iterVarLabel }}</div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">选择要迭代的列表变量</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#f472b6', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#f472b6', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.iteration-node {
  --wf-accent: #f472b6;
  --wf-accent-hover: #ec4899;
}
</style>
