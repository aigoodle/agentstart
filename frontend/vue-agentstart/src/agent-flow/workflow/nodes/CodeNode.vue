<script setup>
import { computed, ref } from 'vue';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: '代码执行',
      description: '代码执行节点',
      config: {
        language: 'python',
        code: '',
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

const lineCount = computed(
  () => props.data.config?.code?.split('\n').length || 0,
);

const truncateCode = (code, maxLength) => {
  if (!code) return '';
  return code.length > maxLength
    ? `${code.slice(0, Math.max(0, maxLength))}...`
    : code;
};
</script>

<template>
  <div
    class="wf-node code-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="code"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="code" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">{{ data.config?.language || 'Python' }}</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="data.config?.code" class="code-preview">
        <div class="code-preview-header">
          <span class="code-lang">{{ data.config.language || 'Python' }}</span>
          <span class="code-lines">{{ lineCount }} 行</span>
        </div>
        <pre class="code-preview-body"><code>{{ truncateCode(data.config.code, 80) }}</code></pre>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">点击编辑代码</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#84cc16', border: '2px solid #ffffff' }"
    />

    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#84cc16', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.code-node {
  --wf-accent: #84cc16;
  --wf-accent-hover: #65a30d;
}

.wf-node-subtitle {
  text-transform: capitalize;
}

.code-preview {
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--wf-accent) 30%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--wf-accent) 8%, transparent);
}

.code-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  border-bottom: 1px solid color-mix(in srgb, var(--wf-accent) 30%, transparent);
  background: color-mix(in srgb, var(--wf-accent) 14%, transparent);
}

.code-lang {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--wf-accent) 90%, #000);
}

.code-lines {
  font-size: 10px;
  color: #6b7280;
}

.code-preview-body {
  margin: 0;
  padding: 8px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 11px;
  line-height: 1.4;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
