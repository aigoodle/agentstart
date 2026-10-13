<script setup>
import { ref } from 'vue';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: 'Agent',
      description: '智能体节点',
      config: {
        model: 'gpt-3.5-turbo',
        temperature: 0.7,
        max_tokens: 1000,
        prompt: '',
      },
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);

const isRunning = ref(false);
const showMetrics = ref(true);
const isHovered = ref(false);

const onMouseEnter = () => (isHovered.value = true);
const onMouseLeave = () => (isHovered.value = false);
const duplicateNode = () => emit('duplicate', props.id);
const deleteNode = () => emit('delete', props.id);
const onConnectionPlusClick = (event, nodeId, handleId) => {
  emit('connection-plus-click', event, nodeId, handleId);
};

const truncateText = (text, maxLength) => {
  if (!text) return '';
  return text.length > maxLength
    ? `${text.slice(0, Math.max(0, maxLength))}...`
    : text;
};
</script>

<template>
  <div
    class="wf-node agent-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="agent"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="agent" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">
          <div>{{ data.model?.provider }}</div>
          <div>{{ data.model?.modelName || 'GPT-3.5-turbo' }}</div>
        </div>
      </div>
      <div v-if="isRunning" class="wf-node-status">
        <div class="wf-node-status-indicator wf-is-running"></div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="data.config?.prompt" class="wf-node-config-preview">
        <div class="wf-node-config-label">提示词预览</div>
        <div class="wf-node-config-value">
          {{ truncateText(data.config.prompt, 50) }}
        </div>
      </div>
    </div>

    <div v-if="showMetrics" class="wf-node-metrics">
      <div class="wf-node-metric">
        <span class="wf-node-metric-label">温度</span>
        <span class="wf-node-metric-value">{{ data.config?.temperature ?? 0.7 }}</span>
      </div>
      <div class="wf-node-metric">
        <span class="wf-node-metric-label">最大令牌</span>
        <span class="wf-node-metric-value">{{ data.config?.max_tokens ?? 1000 }}</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#8b5cf6', border: '2px solid #ffffff' }"
    />

    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#8b5cf6', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.agent-node {
  --wf-accent: #8b5cf6;
  --wf-accent-hover: #7c3aed;
}
</style>
