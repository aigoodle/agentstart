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
      label: '模板转换',
      description: '模板转换节点',
      config: {
        template: '',
        variables: [],
      },
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

const truncateText = (text, maxLength) => {
  if (!text) return '';
  return text.length > maxLength
    ? `${text.slice(0, Math.max(0, maxLength))}...`
    : text;
};
</script>

<template>
  <div
    class="wf-node template-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="template"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="template" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">模板转换</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="data.config?.template" class="wf-node-preview">
        <div class="wf-node-preview-label">模板预览</div>
        <div class="wf-node-preview-value">
          {{ truncateText(data.config.template, 50) }}
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">配置转换模板</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#a855f7', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#a855f7', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.template-node {
  --wf-accent: #a855f7;
  --wf-accent-hover: #9333ea;
}
</style>
