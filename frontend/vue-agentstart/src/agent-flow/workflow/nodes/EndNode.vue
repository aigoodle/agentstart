<script setup>
import { ref } from 'vue';

import NodeHandle from '@/workflow/NodeHandle.vue';
import NodeHoverToolbar from '@/workflow/NodeHoverToolbar.vue';
import workflow_utils from '@/workflow/utils/workflow_utils';

import Icon from '../Icon.vue';

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: '结束',
      description: '工作流结束节点',
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);

const isCompleted = ref(false);
const isHovered = ref(false);

const onMouseEnter = () => {
  isHovered.value = true;
};

const onMouseLeave = () => {
  isHovered.value = false;
};

const duplicateNode = () => {
  emit('duplicate', props.id);
};

const deleteNode = () => {
  emit('delete', props.id);
};

const getAnswerTexts = (answer) => {
  const label = [];
  const list = workflow_utils.splitExpressions(answer);
  for (let i = 0, len = list.length; i < len; i++) {
    const it = list[i];
    if (it.startsWith('{{#')) {
      label.push(it);
    }
  }
  return label;
};

const getAnswerTextLabel = (variableSelector) => {
  return workflow_utils.getVariableLabel(variableSelector);
};
</script>

<template>
  <div
    class="wf-node end-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="end"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="end" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-description">{{ data.description }}</div>
      </div>
    </div>

    <div v-if="isCompleted" class="wf-node-status">
      <div class="wf-node-status-indicator wf-is-completed"></div>
    </div>

    <div class="wf-node-content">
      <div v-if="data.output && data.output.length" class="wf-node-answer-preview">
        <div class="wf-node-answer-text">
          <div v-for="it in data.output" :key="it.name" class="wf-node-answer-row">
            <div>{{ it.name }}</div>
            <div>{{ getAnswerTextLabel(it.variableSelector) }}</div>
          </div>
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">尚未配置输出</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      :is-hovered="isHovered"
      class-name="flow-node-handle-left"
      :style="{
        background: '#ef4444',
        border: '2px solid #ffffff',
      }"
    />
  </div>
</template>

<style scoped>
.end-node {
  --wf-accent: #ef4444;
  --wf-accent-hover: #dc2626;
}
</style>
