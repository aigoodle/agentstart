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
      label: '变量赋值',
      description: '把上游变量写回到会话/环境变量',
      assignments: [],
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

const preview = computed(() => (props.data.assignments || []).slice(0, 3));
const total = computed(() => props.data.assignments?.length || 0);
</script>

<template>
  <div
    class="wf-node variable-assigner-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="variable_assigner"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="variable" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">变量赋值</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="total > 0" class="wf-node-preview">
        <div class="wf-node-preview-label">赋值项 ({{ total }})</div>
        <div class="assigner-list">
          <div v-for="a in preview" :key="a.target" class="assigner-row">
            <span class="assigner-target">{{ a.target || '?' }}</span>
            <span class="assigner-arrow">←</span>
            <span class="assigner-source">{{ a.source || '?' }}</span>
          </div>
          <div v-if="total > 3" class="assigner-more">
            +{{ total - 3 }} 项
          </div>
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">配置赋值项</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#059669', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#059669', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.variable-assigner-node {
  --wf-accent: #059669;
  --wf-accent-hover: #047857;
}

.assigner-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 4px;
}

.assigner-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 6px;
  font-size: 11px;
  background: color-mix(in srgb, var(--wf-accent) 8%, transparent);
  border-radius: 4px;
}

.assigner-target {
  color: color-mix(in srgb, var(--wf-accent) 85%, #000);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-weight: 500;
}

.assigner-arrow {
  color: #6b7280;
  font-weight: 600;
}

.assigner-source {
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assigner-more {
  padding: 2px 6px;
  font-size: 10px;
  font-style: italic;
  color: #9ca3af;
}
</style>
