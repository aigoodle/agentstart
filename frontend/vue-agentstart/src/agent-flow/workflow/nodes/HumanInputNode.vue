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
      label: '人工介入',
      description: '暂停工作流等待人工审批或补充信息',
      approvalType: 'approve',
      formFields: [],
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

const fieldCount = computed(() => props.data.formFields?.length || 0);
const timeoutLabel = computed(() => {
  if (!props.data.timeoutEnabled || !props.data.timeoutSeconds) return '不限时';
  const units = { minute: '分钟', hour: '小时', day: '天' };
  return `${props.data.timeoutValue || 1} ${units[props.data.timeoutUnit] || '小时'}`;
});
</script>

<template>
  <div
    class="wf-node human-input-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="human_input"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="user" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">等待人工响应</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div class="wf-node-preview">
        <div class="wf-node-preview-label">表单填写</div>
        <div class="wf-node-preview-value">{{ timeoutLabel }}</div>
      </div>
      <div v-if="fieldCount > 0" class="human-field-hint">
        {{ fieldCount }} 个表单字段
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#f59e0b', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#f59e0b', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.human-input-node {
  --wf-accent: #f59e0b;
  --wf-accent-hover: #d97706;
}

.human-field-hint {
  margin-top: 4px;
  padding: 4px 8px;
  font-size: 11px;
  color: #78716c;
  background: #fef3c7;
  border-radius: 4px;
}
</style>
