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
      label: '参数提取',
      description: '从上游文本中抽取结构化参数',
      parameters: [],
      model: { modelName: '' },
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

const paramCount = computed(() => props.data.parameters?.length || 0);
const params = computed(() => props.data.parameters?.slice(0, 3) || []);
const modelProvider = computed(
  () => props.data.model?.provider || props.data.model?.providerName || '',
);
const modelName = computed(() => props.data.model?.modelName || '');
</script>

<template>
  <div
    class="wf-node parameter-extractor-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="parameter_extractor"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="variable" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">
          <template v-if="modelName">
            <div v-if="modelProvider">{{ modelProvider }}</div>
            <div>{{ modelName }}</div>
          </template>
          <div v-else class="wf-node-subtitle-empty">未选择模型</div>
        </div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="paramCount > 0" class="wf-node-preview">
        <div class="wf-node-preview-label">抽取参数 · {{ paramCount }} 个</div>
        <div class="wf-node-preview-list">
          <div v-for="p in params" :key="p.name" class="param-row">
            <span class="param-name">{{ p.name }}</span>
            <span class="param-type">{{ p.type || 'string' }}</span>
          </div>
          <div v-if="paramCount > 3" class="param-more">
            +{{ paramCount - 3 }} 个更多
          </div>
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">配置抽取字段</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      class-name="flow-node-handle-left"
      :style="{ background: '#eab308', border: '2px solid #ffffff' }"
    />
    <NodeHandle
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#eab308', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.parameter-extractor-node {
  --wf-accent: #eab308;
  --wf-accent-hover: #ca8a04;
}

.wf-node-subtitle-empty {
  font-style: italic;
  color: #9ca3af;
  font-weight: 400;
}

.param-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 6px;
  font-size: 11px;
  background: color-mix(in srgb, var(--wf-accent) 8%, transparent);
  border-radius: 4px;
}

.param-name {
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.param-type {
  color: color-mix(in srgb, var(--wf-accent) 85%, #000);
  font-weight: 500;
}

.param-more {
  padding: 2px 6px;
  font-size: 10px;
  font-style: italic;
  color: #9ca3af;
}
</style>
