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
      label: '服务接口',
      description: '调用内部服务接口',
      method: 'GET',
      url: '',
      headers: [],
      parameters: [],
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

const headerCount = computed(() => {
  const headers = props.data.headers;
  if (!Array.isArray(headers)) return 0;
  return headers.filter((h) => h && h.name).length;
});

const method = computed(() => (props.data.method || 'GET').toUpperCase());
const url = computed(() => props.data.url || '');

const truncateText = (text, maxLength) => {
  if (!text) return '';
  return text.length > maxLength
    ? `${text.slice(0, Math.max(0, maxLength))}...`
    : text;
};
</script>

<template>
  <div
    class="wf-node service-api-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="service-api"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="service" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">服务接口</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div class="wf-node-preview http-preview">
        <div class="http-row">
          <span :class="['http-method', `is-${method.toLowerCase()}`]">{{ method }}</span>
          <span class="http-url" :title="url">{{ url ? truncateText(url, 30) : '未配置接口地址' }}</span>
        </div>
        <div v-if="headerCount > 0" class="http-row">
          <span class="http-meta-label">头部</span>
          <span class="http-meta-value">{{ headerCount }} 个</span>
        </div>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#0ea5a4', border: '2px solid #ffffff' }"
    />

    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#0ea5a4', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.service-api-node {
  --wf-accent: #0ea5a4;
  --wf-accent-hover: #0d9488;
}

.http-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.http-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.http-method {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
  background: #6b7280;
  border-radius: 4px;
  letter-spacing: 0.5px;
}
.http-method.is-get { background: #2563eb; }
.http-method.is-post { background: #059669; }
.http-method.is-put { background: #d97706; }
.http-method.is-delete { background: #dc2626; }
.http-method.is-patch { background: #7c3aed; }

.http-url {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.http-meta-label {
  color: color-mix(in srgb, var(--wf-accent) 90%, #000);
  font-weight: 500;
}

.http-meta-value {
  color: #1f2937;
}
</style>
