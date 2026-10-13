<script setup lang="ts">
import { ref } from 'vue';

import workflow_utils from '@/workflow/utils/workflow_utils';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: '直接回复',
      description: '直接回复节点',
      config: { text: '' },
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete']);
const isHovered = ref(false);

const onMouseEnter = () => (isHovered.value = true);
const onMouseLeave = () => (isHovered.value = false);
const duplicateNode = () => emit('duplicate', props.id);
const deleteNode = () => emit('delete', props.id);

const getAnswerTexts = (answer: string) => {
  const label: string[] = [];
  const list = workflow_utils.splitExpressions(answer);
  for (const it of list) {
    if (it.startsWith('{{#')) label.push(it);
  }
  return label;
};

const getAnswerTextLabel = (str: string) => {
  const list = str.replaceAll(/\{\{#(.*?)#\}\}/g, '$1').split('.');
  return workflow_utils.getVariableLabel(list);
};
</script>

<template>
  <div
    class="wf-node answer-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="answer"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="answer" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">直接回复用户</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="data.answer" class="wf-node-preview">
        <div class="wf-node-preview-list">
          <template v-for="text in getAnswerTexts(data.answer)" :key="text">
            <div v-if="text.startsWith('{{#')">{{ getAnswerTextLabel(text) }}</div>
            <div v-else>{{ text }}</div>
          </template>
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">尚未配置回复内容</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      :is-hovered="isHovered"
      class-name="flow-node-handle-left"
      :style="{ background: '#f97316', border: '2px solid #ffffff' }"
    />
  </div>
</template>

<style scoped>
.answer-node {
  --wf-accent: #f97316;
  --wf-accent-hover: #ea580c;
}
</style>
