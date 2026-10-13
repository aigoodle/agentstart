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
      label: '开始',
      description: '工作流开始节点',
      variables: [],
    }),
  },
});

const variables = computed(() =>
  Array.isArray(props.data.variables) ? props.data.variables : [],
);

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);

const isRunning = ref(false);
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

const onConnectionPlusClick = (event, nodeId, handleId) => {
  emit('connection-plus-click', event, nodeId, handleId);
};
</script>

<template>
  <div
    class="wf-node start-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="start"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="start" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-row">
          <div class="wf-node-description">{{ data.description }}</div>
        </div>
      </div>
    </div>

    <div class="wf-node-content">
      <div v-if="data.triggersEnabled && data.triggers" class="wf-node-triggers">
        <Icon :name="data.triggers.type === 'connector' ? 'connector' : 'start'" class="wf-node-triggers-icon" />
        <div>{{ data.triggers.type === 'connector' ? (data.triggers.channelName || '消息连接器') : (data.triggers.name || data.triggers.type) }}</div>
        <div class="wf-node-triggers-scope">{{ data.triggers.type === 'connector' ? (data.triggers.connectionName || '全部账号') : '全部' }}</div>
      </div>

      <div v-if="variables.length > 0" class="wf-node-preview start-var-preview">
        <div class="wf-node-preview-label">输入变量</div>
        <div class="wf-node-preview-list">
          <div
            v-for="v in variables"
            :key="v.id ?? v.name"
            class="start-var-row"
          >
            <span class="start-var-left">
              <span class="start-var-name">{{ v.name }}</span>
              <span v-if="v.label && v.label !== v.name" class="start-var-label">
                {{ v.label }}
              </span>
            </span>
            <span class="start-var-meta">
              <span class="start-var-type">{{ v.type || 'string' }}</span>
              <span v-if="v.required" class="start-var-required">必填</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isRunning" class="wf-node-status">
      <div class="wf-node-status-indicator wf-is-running"></div>
    </div>

    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{
        background: '#10b981',
        border: '2px solid #ffffff',
      }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.start-node {
  --wf-accent: #10b981;
  --wf-accent-hover: #059669;
}

.start-var-preview {
  margin-top: 8px;
}

.start-var-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 6px;
  font-size: 11px;
  background: color-mix(in srgb, var(--wf-accent) 8%, transparent);
  border-radius: 4px;
}

.start-var-left {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.start-var-name {
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.start-var-label {
  color: #6b7280;
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.start-var-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.start-var-type {
  color: color-mix(in srgb, var(--wf-accent) 85%, #000);
  font-weight: 500;
}

.start-var-required {
  padding: 0 4px;
  font-size: 10px;
  color: #b45309;
  background: #fef3c7;
  border-radius: 3px;
}

.start-var-more {
  padding: 2px 6px;
  font-size: 10px;
  font-style: italic;
  color: #9ca3af;
}
</style>
